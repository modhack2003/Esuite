#!/usr/bin/env python3
"""Validate and summarize the ESuite 1.1.0 plan's item-by-item acceptance ledger."""

import argparse
from collections import Counter
import csv
from pathlib import Path


LEDGER = Path(__file__).resolve().parents[1] / "release" / "1.1.0-acceptance.csv"
FIELDS = ("id", "checkpoint", "requirement", "status", "build", "fixture", "expected", "actual", "evidence")
STATUSES = ("open", "implemented", "passed", "failed", "blocked")
EXPECTED = {
    f"{prefix}{number:03d}": (number - 1) // 100 + 1
    for prefix in ("F", "Q") for number in range(1, 1001)
}


def load_ledger(path):
    with path.open(encoding="utf-8", newline="") as stream:
        reader = csv.DictReader(stream)
        if tuple(reader.fieldnames or ()) != FIELDS:
            raise ValueError("Acceptance ledger columns do not match the release plan")
        rows = {}
        for line, row in enumerate(reader, start=2):
            if None in row or any(value is None for value in row.values()):
                raise ValueError(f"Malformed acceptance row on line {line}")
            key = row["id"]
            if key not in EXPECTED or key in rows or row["checkpoint"] != str(EXPECTED[key]):
                raise ValueError(f"Unexpected, misplaced, or duplicate acceptance ID {key!r} on line {line}")
            if row["status"] not in STATUSES or not row["requirement"].strip() or not row["expected"].strip():
                raise ValueError(f"{key} must have a defined state and an acceptance expectation")
            if row["status"] in ("passed", "failed"):
                if not all(row[field].strip() for field in ("build", "fixture", "actual", "evidence")):
                    raise ValueError(f"{key} must record the build, fixture, observed result, and evidence")
            elif row["status"] == "implemented" and not row["evidence"].strip():
                raise ValueError(f"{key} must identify the implementation evidence")
            elif row["status"] == "blocked" and not row["actual"].strip():
                raise ValueError(f"{key} must identify its blocker")
            rows[key] = row
    missing = EXPECTED.keys() - rows.keys()
    if missing:
        raise ValueError(f"Missing {len(missing)} acceptance IDs starting with {min(missing)}")
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ledger", type=Path, default=LEDGER)
    parser.add_argument("--checkpoint", type=int, choices=range(1, 11))
    parser.add_argument("--candidate", help="Count passes only for this exact build")
    parser.add_argument("--complete", action="store_true", help="Require every feature and quality item to pass")
    args = parser.parse_args()
    try:
        rows = load_ledger(args.ledger)
    except (OSError, ValueError) as error:
        parser.exit(1, f"{error}\n")

    def status_for_build(row):
        if row["status"] == "passed" and args.candidate is not None and row["build"] != args.candidate:
            return "prior pass"
        return row["status"]

    for number in ([args.checkpoint] if args.checkpoint else range(1, 11)):
        counts = Counter(status_for_build(row) for key, row in rows.items() if EXPECTED[key] == number)
        summary = ", ".join(f"{counts[status]} {status}" for status in (*STATUSES, "prior pass") if counts[status])
        print(f"Checkpoint {number}: {summary}")
    remaining = sum(status_for_build(row) != "passed" for row in rows.values())
    scope = f" for {args.candidate}" if args.candidate else ""
    print(f"Total: {len(rows) - remaining} of {len(rows)} items passed{scope}")
    return int(args.complete and remaining != 0)


if __name__ == "__main__":
    raise SystemExit(main())
