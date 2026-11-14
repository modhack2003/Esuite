#!/usr/bin/env python3
"""Fail closed before distributing a production APK. Requires official SDK tools."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess


def output(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT)


def inspect(apk, sdk, fingerprint, version, code):
    if not re.fullmatch(r"[0-9a-fA-F]{64}", fingerprint):
        raise ValueError("ESUITE_CERT_SHA256 must pin the production certificate's SHA-256 (64 hex characters)")
    tools = sdk / "build-tools" / "36.0.0"
    signatures = output(str(tools / "apksigner"), "verify", "--verbose", "--print-certs", str(apk))
    if "Android Debug" in signatures or "AndroidDebugKey" in signatures:
        raise ValueError("A debug signing certificate cannot be published")
    certificates = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)", signatures)
    if len(certificates) != 1 or certificates[0].lower() != fingerprint.lower():
        raise ValueError("APK signer does not match the pinned production certificate")
    if "Verified using v2 scheme (APK Signature Scheme v2): true" not in signatures:
        raise ValueError("APK v2 signature is required for the Android 9 baseline")
    badging = output(str(tools / "aapt2"), "dump", "badging", str(apk))
    expected = ("name='dev.esuite.app'", f"versionCode='{code}'", f"versionName='{version}'", "sdkVersion:'28'", "targetSdkVersion:'37'")
    if not all(item in badging for item in expected) or "application-debuggable" in badging:
        raise ValueError("APK package/version/SDK/debuggable metadata does not match the release contract")
    if "maxSdkVersion" in badging or ".validation" in badging:
        raise ValueError("A maximum SDK or validation package must not be distributed")
    output(str(tools / "zipalign"), "-c", "-P", "16", "4", str(apk))
    return {"file": apk.name, "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
            "certificateSha256": certificates[0].lower(), "versionName": version,
            "versionCode": int(code), "minSdk": 28, "targetSdk": 37, "debuggable": False}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    properties = dict(line.split("=", 1) for line in Path("release/version.properties").read_text().splitlines() if "=" in line)
    metadata = inspect(args.apk, Path(os.environ["ANDROID_HOME"]), os.environ["ESUITE_CERT_SHA256"], properties["versionName"], properties["versionCode"])
    metadata.update(sourceCommit=os.environ["SOURCE_SHA"], workflowRun=os.environ["GITHUB_RUN_ID"])
    args.output.write_text(json.dumps(metadata, indent=2) + "\n")
    print(json.dumps(metadata, indent=2))
