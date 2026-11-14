#!/usr/bin/env python3
"""Fail closed before distributing a production APK. Requires official SDK tools."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import struct
from zipfile import ZipFile


def output(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT)



def verify_native_alignment(apk):
    checked = []
    with ZipFile(apk) as archive:
        for name in archive.namelist():
            if not name.startswith("lib/") or not name.endswith(".so"):
                continue
            data = archive.read(name)
            if data[:4] != b"\x7fELF":
                raise ValueError(f"Invalid native library: {name}")
            if data[4] != 2:  # 32-bit ABIs retain the Android 9 baseline.
                continue
            endian = "<" if data[5] == 1 else ">"
            offset = struct.unpack_from(endian + "Q", data, 32)[0]
            size, count = struct.unpack_from(endian + "HH", data, 54)
            loads = 0
            for index in range(count):
                entry = offset + index * size
                if struct.unpack_from(endian + "I", data, entry)[0] != 1:
                    continue
                _, _, file_offset, address, _, _, _, alignment = struct.unpack_from(endian + "IIQQQQQQ", data, entry)
                if alignment < 16384 or file_offset % 16384 != address % 16384:
                    raise ValueError(f"Native library is not compatible with 16 KiB pages: {name}")
                loads += 1
            if not loads:
                raise ValueError(f"No native load segments: {name}")
            checked.append(name)
    return checked


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
    modern_signature = any(f"Verified using v{scheme} scheme (APK Signature Scheme v{scheme}): true" in signatures for scheme in (2, 3))
    if not modern_signature:
        raise ValueError("A verified APK v2 or v3 signature is required")
    badging = output(str(tools / "aapt2"), "dump", "badging", str(apk))
    expected = ("name='dev.esuite.app'", f"versionCode='{code}'", f"versionName='{version}'", "sdkVersion:'28'", "targetSdkVersion:'37'")
    if not all(item in badging for item in expected) or "application-debuggable" in badging:
        raise ValueError("APK package/version/SDK/debuggable metadata does not match the release contract")
    if "maxSdkVersion" in badging or ".validation" in badging:
        raise ValueError("A maximum SDK or validation package must not be distributed")
    output(str(tools / "zipalign"), "-c", "-P", "16", "4", str(apk))
    native_libraries = verify_native_alignment(apk)
    return {"nativeLibraries16KiB": native_libraries, "file": apk.name, "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
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
