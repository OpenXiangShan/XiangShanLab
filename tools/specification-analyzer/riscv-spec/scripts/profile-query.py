#!/usr/bin/env python3
"""Query RISC-V profile extensions from UDB.

Usage:
    profile-query.py <Profile> [--mandatory|--optional|--all] [--details]

Examples:
    profile-query.py RVA23U64
    profile-query.py RVA23U64 --mandatory
    profile-query.py RVA23U64 --optional
    profile-query.py RVA23U64 --all --details
"""

import sys
import os
import yaml
import argparse

# Try RISCV_UDB_ROOT env, then common locations
_candidates = [
    os.environ.get("RISCV_UDB_ROOT"),
    os.path.expanduser("~/repos/riscv-unified-db"),
]
UDB_ROOT = next((p for p in _candidates if p and os.path.isdir(p)), None)
if not UDB_ROOT:
    print("Error: UDB not found. Set RISCV_UDB_ROOT or clone to ~/repos/riscv-unified-db")
    sys.exit(1)
PROFILE_DIR = os.path.join(UDB_ROOT, "cfgs", "profile")
EXT_DIR = os.path.join(UDB_ROOT, "spec", "std", "isa", "ext")


def load_profile(name):
    path = os.path.join(PROFILE_DIR, f"{name}.yaml")
    if not os.path.exists(path):
        print(f"Error: Profile '{name}' not found at {path}")
        print(f"Available profiles: {', '.join(p.replace('.yaml','') for p in sorted(os.listdir(PROFILE_DIR)) if p.endswith('.yaml'))}")
        sys.exit(1)
    with open(path) as f:
        return yaml.safe_load(f)


def get_ext_details(name):
    path = os.path.join(EXT_DIR, f"{name}.yaml")
    if not os.path.exists(path):
        return None
    with open(path) as f:
        data = yaml.safe_load(f)
    return {
        "long_name": data.get("long_name", ""),
        "state": data.get("versions", [{}])[0].get("state", "unknown") if data.get("versions") else "unknown",
        "ratification_date": data.get("versions", [{}])[0].get("ratification_date", "") if data.get("versions") else "",
    }


def print_extensions(exts, label, details=False):
    if not exts or not isinstance(exts, list):
        print(f"\n{label}: (none)")
        return
    print(f"\n{label} ({len(exts)}):")
    for e in sorted(exts, key=lambda x: x["name"]):
        line = f"  {e['name']:<16} {e.get('version', '')}"
        if details:
            info = get_ext_details(e["name"])
            if info:
                line += f"  | {info['long_name']}"
                if info["ratification_date"]:
                    line += f" (ratified {info['ratification_date']})"
        print(line)


def main():
    parser = argparse.ArgumentParser(description="Query RISC-V profile extensions")
    parser.add_argument("profile", help="Profile name (e.g., RVA23U64)")
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--mandatory", action="store_true", help="Show only mandatory extensions")
    group.add_argument("--optional", action="store_true", help="Show only non-mandatory (optional) extensions")
    group.add_argument("--all", action="store_true", help="Show both mandatory and optional")
    parser.add_argument("--details", action="store_true", help="Show extension long names and ratification dates")
    args = parser.parse_args()

    data = load_profile(args.profile)
    mandatory = data.get("mandatory_extensions", [])
    optional = data.get("non_mandatory_extensions", [])

    print(f"Profile: {args.profile}")

    if args.mandatory:
        print_extensions(mandatory, "Mandatory", args.details)
    elif args.optional:
        print_extensions(optional, "Non-mandatory (optional)", args.details)
    else:
        # Default: show both (--all or no flag)
        print_extensions(mandatory, "Mandatory", args.details)
        print_extensions(optional, "Non-mandatory (optional)", args.details)
        additional = data.get("additional_extensions")
        if additional is True:
            print("\nAdditional extensions: any extension is allowed")
        elif isinstance(additional, list):
            print_extensions(additional, "Additional", args.details)


if __name__ == "__main__":
    main()
