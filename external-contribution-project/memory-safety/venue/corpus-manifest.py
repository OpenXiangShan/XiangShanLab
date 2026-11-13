#!/usr/bin/env python3
"""Verify a staged corpus and write its manifest.

Verbatim port of cpu-sec-test-env scripts/common/corpus_manifest.py at
4a99af7 (2026-10-31) into this self-contained venue; keep the two in sync by
provenance, not by runtime dependency.

Absorbed from the bench's riscv64 NEMU venue (platform/riscv64-nemu-linux/
corpus-manifest.py at f9e4ba7) and generalised: every system lane stages its
corpus through scripts/common/stage_corpus.sh, and this module is the one
place that knows the corpus naming contract and the expected unit set.

The naming contract is the scheduler's own (scheduler/run-test.cpp
corpus_key()): the key is FNV-1a-32 over the record's macro map rendered as
`KEY=VALUE` pairs with keys sorted, `,`-joined (empty map keys to 811c9dc5),
and every build unit is test/<program>__<key>.  Nothing here recomputes a key
the runner did not already compute - the expected set is derived from the
tracked registry exactly as the runner's corpus build enumerates it, and the
staged tree is compared against that set.

Usage:
    corpus_manifest.py --registry JSON --lane TOKEN --print-plan
    corpus_manifest.py --registry JSON --lane TOKEN --tree DIR --out FILE
                       [--bench-commit C] [--bench-tree B]
                       [--toolchain T] [--objdump O]

--print-plan writes one TAB-separated line per staged artifact
(`unit`/`side`/`support`, path) so the caller stages exactly this set.

Verification (--tree mode), all failing loudly (exit 1) with the offending
names on stderr:
  * the staged configure.json is byte-identical to --registry: the guest's
    registry is the tracked bench registry, copied verbatim, never derived
  * the staged corpus file set equals the expected (program, macro-map) set,
    minus the records whose `skip` list names this lane's token; the set is
    never hard-coded and must not be empty
  * every registry-referenced side file and every declared plain-name support
    file is staged
  * no two macro maps of one program collide on a key
  * the longest corpus path stays within the scheduler's argument-pool slot

The manifest is deterministic: per-file sha256 and the macro maps, plus the
provenance handed in on the command line.  Re-running over the same staged
tree rewrites it byte-identically.  Python stdlib only.
"""
import argparse
import hashlib
import json
import os
import sys

# The scheduler fills `char arg_pool[32][256]` with strcpy (scheduler/
# run-test.cpp), so a corpus path (plus its NUL) must fit one 256-byte slot.
# Re-measured against the longest staged keyed path on every run, never kept
# as a stale constant: the keyed names are longer than the old plain ones.
SCHED_ARG_LIMIT = 255

# Plain-name build byproducts a record reaches by a hard-coded path, so the
# corpus-keyed glob cannot satisfy them.  test/acc-check-ASLR is the one
# consumer: acc/check-ASLR.cpp re-invokes the program through the fixed
# relative path "test/acc-check-ASLR 1 <flag>" (system(), check-ASLR.cpp:53);
# with only the keyed unit staged the inner call finds nothing and the case
# exits 2 ("should never run to here"), drifting both ASLR records 1 -> 2.
SUPPORT_FILES = ("test/acc-check-ASLR",)


def fnv1a32(s):
    """FNV-1a-32 over the canonical macro-map string, %08x."""
    h = 0x811c9dc5
    for b in s.encode():
        h ^= b
        h = (h * 0x01000193) & 0xFFFFFFFF
    return "%08x" % h


def canon(macro_map):
    return ",".join("%s=%s" % (k, macro_map[k]) for k in sorted(macro_map))


def build_plan(rec):
    """Macro maps in the order the scheduler's build phase builds them, as
    (canonical string, conf-macro entry) pairs.

    Mirrors run-tests()/case_parser() under `corpus make-only exhausted-run`:
    the argument kind is default_args when present, and a build happens for
    the first argument list and whenever the map changes from the last build.

    The entry dict travels alongside the canonical string because the joined
    string is not a faithful round-trip source: a macro VALUE may itself
    contain commas (the mss bare-array-pointer programs carry values such as
    'VAL_4U,VAL_2U,VAL_1U'), so re-splitting it would corrupt the map the
    manifest records.
    """
    args = rec.get("default_args", rec.get("arguments"))
    # The registry writes argument lists as JSON objects keyed "0".."N-1", and
    # the scheduler's arg_parser iterates their members, so both shapes have to
    # be counted here; an empty container still yields index 0, because
    # arg_parser always produces at least one argument list -- hence the floor.
    n = max(1, len(args)) if isinstance(args, (list, dict)) else 1
    cf = rec.get("conf-macro") or {}
    maps = []
    for ind in range(n):
        entry = cf.get(str(ind), {})
        if not isinstance(entry, dict):
            sys.exit("record %r: conf-macro[%d] is not a map" % (rec, ind))
        m = canon(entry)
        if not maps or maps[-1][0] != m:
            maps.append((m, entry))
    return maps


def side_candidates(record):
    """Repo-relative paths under test/ mentioned in a record's arguments."""
    out = set()
    for key in ("arguments", "default_args"):
        val = record.get(key)
        if val is None:
            continue
        items = []
        if isinstance(val, dict):
            for v in val.values():
                items.extend(v if isinstance(v, list) else [v])
        elif isinstance(val, list):
            items.extend(val)
        else:
            items.append(val)
        for s in items:
            if isinstance(s, str):
                s2 = s[2:] if s.startswith("./") else s
                if s2.startswith("test/"):
                    out.add(s2)
    return out


def skipped_records(registry, lane):
    """The records this lane does not build/run, with their declared reasons."""
    out = []
    for key, rec in registry.items():
        if lane in (rec.get("skip") or []):
            out.append({
                "record": key,
                "program": rec.get("program", key),
                "reason": rec.get("skip-reason", ""),
            })
    return out


def expected_set(registry, lane):
    """(unit name -> (program, canonical map, entry), skipped, side names).

    A skipped record is neither built nor run, so it contributes no unit and
    no side file: a file the lane never produces cannot be required of it.
    """
    skipped = skipped_records(registry, lane)
    skipped_names = {s["record"] for s in skipped}
    expected = {}
    side = set()
    for key, rec in registry.items():
        if key in skipped_names:
            continue
        prog = rec.get("program", key)
        for m, entry in build_plan(rec):
            expected["%s__%s" % (prog, fnv1a32(m))] = (prog, m, entry)
        side |= side_candidates(rec)
    return expected, skipped, sorted(side)


def sha256_file(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def mode_of(path):
    return "%o" % (os.stat(path).st_mode & 0o7777)


def print_plan(registry, lane):
    expected, _skipped, side = expected_set(registry, lane)
    for name in sorted(expected):
        print("unit\ttest/%s" % name)
    for name in side:
        print("side\t%s" % name)
    for name in SUPPORT_FILES:
        print("support\t%s" % name)
    return 0


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--registry", required=True,
                    help="the tracked bench configure.json at the pin")
    ap.add_argument("--lane", required=True,
                    help="the lane's skip token (the bench's CSB_TARGET_ARCH spelling)")
    ap.add_argument("--print-plan", action="store_true",
                    help="print the artifacts to stage (unit/side/support) and exit")
    ap.add_argument("--tree", help="the staged tree to verify (contains configure.json and test/)")
    ap.add_argument("--out", help="manifest.json output path")
    ap.add_argument("--bench-commit", default="", help="cpu-sec-bench commit the corpus was built from")
    ap.add_argument("--bench-tree", default="", help="`git describe --always --dirty` of that tree; a -dirty suffix means the corpus came from uncommitted sources")
    ap.add_argument("--toolchain", default="", help="corpus compiler version line")
    ap.add_argument("--objdump", default="", help="corpus objdump version line")
    args = ap.parse_args()

    with open(args.registry, "rb") as fh:
        registry_bytes = fh.read()
    registry = json.loads(registry_bytes)

    if args.print_plan:
        return print_plan(registry, args.lane)
    if not args.tree or not args.out:
        ap.error("--tree and --out are required unless --print-plan is given")

    # A --out file exists only if the most recent verification published it:
    # stage_corpus.sh's caller consumes its presence as proof that staging
    # completed, so a previous run's manifest must not outlive this invocation
    # unless this one publishes.  Removing both paths here, before any read,
    # routes *every* non-publishing exit through the removal - a failed check,
    # an unreadable --registry, a missing test/, a sha256 read error.
    for stale in (args.out, args.out + ".tmp"):
        try:
            os.unlink(stale)
        except FileNotFoundError:
            pass

    proc = []
    def check(name, ok, detail=""):
        print("%s  %-58s %s" % ("PASS" if ok else "FAIL", name, detail))
        proc.append(ok)

    expected, skipped, side_names = expected_set(registry, args.lane)

    tree_registry = os.path.join(args.tree, "configure.json")
    if os.path.isfile(tree_registry):
        with open(tree_registry, "rb") as fh:
            same = fh.read() == registry_bytes
        check("staged configure.json is the tracked registry, verbatim", same,
              "" if same else "%s differs from %s" % (tree_registry, args.registry))
    else:
        check("staged configure.json is the tracked registry, verbatim", False,
              "%s absent" % tree_registry)

    test_dir = os.path.join(args.tree, "test")
    actual = sorted(f for f in os.listdir(test_dir) if "__" in f) if os.path.isdir(test_dir) else []

    missing = sorted(set(expected) - set(actual))
    extra = sorted(set(actual) - set(expected))
    check("staged corpus set equals the expected registry-minus-skips unit set",
          bool(expected) and not missing and not extra,
          "expected=%d actual=%d skipped-records=%d missing=%r extra=%r" %
          (len(expected), len(actual), len(skipped), missing[:3], extra[:3]))

    present_side = [n for n in side_names
                    if os.path.isfile(os.path.join(args.tree, n))]
    check("every registry side file is staged", len(present_side) == len(side_names),
          "side=%r" % side_names if len(present_side) == len(side_names)
          else "missing=%r" % sorted(set(side_names) - set(present_side)))

    present_support = [n for n in SUPPORT_FILES
                       if os.path.isfile(os.path.join(args.tree, n))]
    check("every declared support file is staged", len(present_support) == len(SUPPORT_FILES),
          "support=%r" % list(SUPPORT_FILES) if len(present_support) == len(SUPPORT_FILES)
          else "missing=%r" % sorted(set(SUPPORT_FILES) - set(present_support)))

    # A collision is one <key> carrying more than one distinct macro map: the
    # later build would overwrite the earlier snapshot, so a single file would
    # serve two build units.  Collecting the maps per key is what lets this
    # check fail at all -- keying the collection by the hash itself collapses
    # the very collision it is meant to detect.
    per_program = {}
    for name, (prog, m, _entry) in expected.items():
        per_program.setdefault(prog, {}).setdefault(name.rsplit("__", 1)[1], set()).add(m)
    collisions = {p: {k: sorted(v) for k, v in km.items() if len(v) > 1}
                  for p, km in per_program.items()}
    collisions = {p: v for p, v in collisions.items() if v}
    check("per-program corpus keys are collision-free", not collisions, "collisions=%r" % collisions)

    longest = max((len("test/" + f) for f in actual), default=0)
    check("longest corpus path within the %d-char scheduler argument slot" % SCHED_ARG_LIMIT,
          longest <= SCHED_ARG_LIMIT, "longest=%d" % longest)

    units = []
    for name in sorted(expected):
        path = os.path.join(test_dir, name)
        prog, m, entry = expected[name]
        if not os.path.exists(path):
            continue
        units.append({
            "name": "test/" + name,
            "program": prog,
            "macro_map": entry,
            "canonical": m,
            "sha256": sha256_file(path),
        })

    def file_entries(names):
        out = []
        for rel in names:
            path = os.path.join(args.tree, rel)
            if not os.path.isfile(path):
                continue
            out.append({"name": rel, "sha256": sha256_file(path), "mode": mode_of(path)})
        return out

    manifest = {
        "lane": args.lane,
        "bench_commit": args.bench_commit,
        "bench_tree": args.bench_tree,
        "toolchain": args.toolchain,
        "objdump": args.objdump,
        "registry": args.registry,
        "records": len(registry),
        "programs": len({u["program"] for u in units}),
        "units": units,
        "side_files": file_entries(side_names),
        "support_files": file_entries(SUPPORT_FILES),
        "skipped": skipped,
        "max_corpus_path_chars": longest,
    }
    # Publish only after every check passed: the caller treats the manifest's
    # presence as proof that staging completed, so a failed verification must
    # leave none behind.
    if all(proc):
        tmp = args.out + ".tmp"
        with open(tmp, "w") as f:
            json.dump(manifest, f, indent=2, sort_keys=True)
            f.write("\n")
        os.replace(tmp, args.out)
        # The summary names the published path, so it belongs to the success
        # path only: a failing run must not end by naming <out> as though a
        # manifest had been written there.
        print("manifest: lane %s, %d units over %d programs, %d side files, %d support files, "
              "%d skipped records, longest path %d chars -> %s"
              % (args.lane, len(units), manifest["programs"], len(manifest["side_files"]),
                 len(manifest["support_files"]), len(skipped), longest, args.out))
    else:
        print("manifest NOT written: %d check(s) failed" % proc.count(False))
    return 1 if not all(proc) else 0


if __name__ == "__main__":
    sys.exit(main())
