#!/usr/bin/env python3
"""Build a machine-readable manifest for one xs-bug-replay run."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable


WAVE_SUFFIXES = {".fst", ".vcd", ".fsdb"}
WAVE_PATH_RE = re.compile(
    r"dump\s+wave\s+to\s+([^\s,]+?\.(?:fst|vcd|fsdb))",
    flags=re.IGNORECASE,
)
REPLAY_RETURN_RE = re.compile(r"回放结束：(.+?)，返回码=(-?\d+)")
ANCHOR_PATTERNS = (
    ("bug_marker", re.compile(r"BUG\s+REPRODUCED|HIT\s+BAD\s+TRAP|\bFAIL(?:ED|URE)?\b", re.I)),
    ("difftest", re.compile(r"difftest|mismatch|reference\s+model|dut\s*[:=]", re.I)),
    ("assertion", re.compile(r"\bassert(?:ion)?\b|\bpanic\b|\bfatal\b|\btimeout\b", re.I)),
    ("error", re.compile(r"\berror\b|\bexception\b|segmentation\s+fault", re.I)),
    ("trap_state", re.compile(r"\b[ms](?:cause|epc|tval)\b|\btrap\b|\binterrupt\b", re.I)),
    ("commit", re.compile(r"\bcommit\b.*\b(?:pc|instr|inst)\b", re.I)),
)


def positive_issue(value: str) -> int:
    try:
        issue = int(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("issue must be an integer") from exc
    if issue <= 0:
        raise argparse.ArgumentTypeError("issue must be positive")
    return issue


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def file_record(path: Path | None, hash_max_bytes: int | None = None) -> dict[str, object]:
    if path is None:
        return {"path": None, "exists": False}
    resolved = path.expanduser().resolve()
    record: dict[str, object] = {"path": str(resolved), "exists": resolved.is_file()}
    if not resolved.is_file():
        return record
    stat = resolved.stat()
    record.update({"size_bytes": stat.st_size, "mtime_ns": stat.st_mtime_ns})
    if hash_max_bytes is not None:
        record["sha256"] = sha256(resolved) if stat.st_size <= hash_max_bytes else None
        if stat.st_size > hash_max_bytes:
            record["hash_skipped_reason"] = f"file exceeds {hash_max_bytes} bytes"
    return record


def locate_replay_root(case_dir: Path, issue: int | None) -> Path:
    case_dir = case_dir.expanduser().resolve()
    if (case_dir / "xs-env" / "XiangShan").is_dir():
        return case_dir
    pattern = f"xs-bug-replay-{issue}" if issue else "xs-bug-replay-*"
    candidates = sorted(path for path in case_dir.glob(pattern) if path.is_dir())
    if len(candidates) == 1:
        return candidates[0].resolve()
    if not candidates:
        raise ValueError(f"cannot find {pattern} below {case_dir}")
    raise ValueError(f"multiple replay roots match below {case_dir}: {candidates}")


def external_files(root: Path) -> list[Path]:
    """Walk run artifacts without descending into the large xs-env checkout."""
    found: list[Path] = []
    for directory, dirnames, filenames in os.walk(root):
        dirnames[:] = [
            name for name in dirnames if name not in {".git", "xs-env", "__pycache__"}
        ]
        base = Path(directory)
        found.extend((base / name).resolve() for name in filenames)
    return sorted(set(found))


def waveform_files(build_root: Path) -> list[Path]:
    if not build_root.is_dir():
        return []
    found: list[Path] = []
    for directory, dirnames, filenames in os.walk(build_root):
        dirnames[:] = [name for name in dirnames if name not in {".git", "__pycache__"}]
        base = Path(directory)
        for name in filenames:
            candidate = base / name
            if candidate.suffix.lower() in WAVE_SUFFIXES and candidate.is_file():
                found.append(candidate.resolve())
    return sorted(set(found))


def read_wave_paths(log_path: Path) -> list[str]:
    matches: list[str] = []
    seen: set[str] = set()
    try:
        with log_path.open("r", encoding="utf-8", errors="replace") as stream:
            for line in stream:
                for match in WAVE_PATH_RE.finditer(line):
                    value = match.group(1)
                    if value not in seen:
                        seen.add(value)
                        matches.append(value)
    except OSError:
        pass
    return matches


def resolve_wave_path(
    raw: str,
    log_path: Path,
    case_dir: Path,
    replay_root: Path,
    source_root: Path,
    candidates: Iterable[Path],
) -> Path:
    supplied = Path(raw).expanduser()
    if supplied.is_absolute():
        return supplied.resolve()
    probes = (
        log_path.parent / supplied,
        case_dir / supplied,
        replay_root / supplied,
        source_root / supplied,
        source_root.parent / supplied,
    )
    for probe in probes:
        if probe.is_file():
            return probe.resolve()
    same_name = [path for path in candidates if path.name == supplied.name]
    if len(same_name) == 1:
        return same_name[0].resolve()
    return (log_path.parent / supplied).resolve()


def scan_anchors(log_path: Path, max_per_category: int) -> list[dict[str, object]]:
    anchors: list[dict[str, object]] = []
    counts: defaultdict[str, int] = defaultdict(int)
    try:
        with log_path.open("r", encoding="utf-8", errors="replace") as stream:
            for line_number, line in enumerate(stream, start=1):
                text = line.rstrip("\r\n")
                for category, pattern in ANCHOR_PATTERNS:
                    if counts[category] >= max_per_category or not pattern.search(text):
                        continue
                    anchors.append(
                        {
                            "file": str(log_path.resolve()),
                            "line": line_number,
                            "category": category,
                            "text": text[:800],
                        }
                    )
                    counts[category] += 1
    except OSError:
        return []
    return anchors


def run_git(source_root: Path, arguments: list[str]) -> tuple[str | None, str | None]:
    try:
        completed = subprocess.run(
            ["git", *arguments],
            cwd=source_root,
            check=False,
            capture_output=True,
            text=True,
            timeout=20,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        return None, str(exc)
    if completed.returncode != 0:
        return None, (completed.stderr or completed.stdout).strip()
    return completed.stdout.rstrip("\n"), None


def source_record(source_root: Path) -> dict[str, object]:
    record: dict[str, object] = {"path": str(source_root.resolve()), "exists": source_root.is_dir()}
    if not source_root.is_dir():
        return record
    head, head_error = run_git(source_root, ["rev-parse", "HEAD"])
    branch, branch_error = run_git(source_root, ["branch", "--show-current"])
    status, status_error = run_git(
        source_root, ["status", "--porcelain=v1", "--untracked-files=all"]
    )
    status_lines = status.splitlines() if status else []
    record.update(
        {
            "git_head": head,
            "git_head_error": head_error,
            "git_branch": branch or None,
            "git_branch_error": branch_error,
            "dirty": bool(status_lines),
            "status_lines": status_lines[:500],
            "status_truncated": len(status_lines) > 500,
            "status_error": status_error,
        }
    )
    return record


def load_driver_metadata(case_dir: Path, replay_root: Path) -> tuple[dict[str, object] | None, str | None]:
    candidates = (case_dir / "replay-command.json", replay_root.parent / "replay-command.json")
    for path in candidates:
        if not path.is_file():
            continue
        try:
            value = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            return None, f"cannot read {path}: {exc}"
        if isinstance(value, dict):
            value["metadata_path"] = str(path.resolve())
            return value, None
    return None, "replay-command.json not found"


def parse_replay_returns(driver_log: Path | None) -> list[dict[str, object]]:
    if driver_log is None or not driver_log.is_file():
        return []
    found: list[dict[str, object]] = []
    with driver_log.open("r", encoding="utf-8", errors="replace") as stream:
        for line_number, line in enumerate(stream, start=1):
            match = REPLAY_RETURN_RE.search(line)
            if match:
                found.append(
                    {
                        "image": match.group(1),
                        "return_code": int(match.group(2)),
                        "line": line_number,
                    }
                )
    return found


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--case-dir", required=True)
    parser.add_argument("--issue", type=positive_issue)
    parser.add_argument("--output", help="Write the JSON manifest here instead of stdout only.")
    parser.add_argument("--max-anchors-per-category", type=int, default=20)
    parser.add_argument("--hash-max-mib", type=int, default=512)
    parser.add_argument(
        "--require-reproduction",
        action="store_true",
        help="Return 2 unless the full structural replay-artifact gate passes; the issue oracle is still separate.",
    )
    return parser


def main() -> int:
    args = build_parser().parse_args()
    if args.max_anchors_per_category <= 0 or args.hash_max_mib <= 0:
        print("anchor and hash limits must be positive", file=sys.stderr)
        return 2
    case_dir = Path(args.case_dir).expanduser().resolve()
    if not case_dir.is_dir():
        print(f"case directory not found: {case_dir}", file=sys.stderr)
        return 2
    try:
        replay_root = locate_replay_root(case_dir, args.issue)
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        return 2

    source_root = replay_root / "xs-env" / "XiangShan"
    build_root = source_root / "build"
    emu_path = build_root / "emu"
    all_external = external_files(case_dir)
    replay_stdout = sorted(path for path in all_external if path.name.endswith(".replay.stdout"))
    log_files = sorted(
        path
        for path in all_external
        if path.name.endswith((".stdout", ".stderr", ".log"))
    )
    waves = waveform_files(build_root)
    hash_limit = args.hash_max_mib * 1024 * 1024

    pairs: list[dict[str, object]] = []
    for stdout_path in replay_stdout:
        base = Path(str(stdout_path)[: -len(".replay.stdout")])
        stderr_path = Path(str(base) + ".replay.stderr")
        disassembly_path = Path(str(base) + ".replay.diasm")
        raw_wave_paths = read_wave_paths(stdout_path)
        mapped_waves = [
            resolve_wave_path(raw, stdout_path, case_dir, replay_root, source_root, waves)
            for raw in raw_wave_paths
        ]
        pair_logs = [path for path in (stdout_path, stderr_path) if path.is_file()]
        pairs.append(
            {
                "image": file_record(base, hash_limit),
                "stdout": file_record(stdout_path),
                "stderr": file_record(stderr_path),
                "disassembly": file_record(disassembly_path),
                "logged_wave_paths": raw_wave_paths,
                "waveforms": [file_record(path) for path in mapped_waves],
                "anchors": [
                    anchor
                    for log_path in pair_logs
                    for anchor in scan_anchors(log_path, args.max_anchors_per_category)
                ],
            }
        )

    driver_metadata, driver_metadata_error = load_driver_metadata(case_dir, replay_root)
    driver_log_candidates = [
        case_dir / "replay-driver.log",
        replay_root.parent / "replay-driver.log",
    ]
    driver_log = next((path.resolve() for path in driver_log_candidates if path.is_file()), None)
    issue_context = [
        file_record(path)
        for path in all_external
        if re.search(r"issue[-_ ]?context|issue[-_ ]?\d+", path.name, re.I)
        and path.suffix.lower() in {".md", ".json", ".html", ".txt"}
    ]
    all_anchors = [
        anchor
        for log_path in log_files
        for anchor in scan_anchors(log_path, args.max_anchors_per_category)
    ]
    mapped_existing = [
        wave
        for pair in pairs
        for wave in pair["waveforms"]
        if isinstance(wave, dict) and wave.get("exists")
    ]
    images_existing = [pair for pair in pairs if pair["image"].get("exists")]
    complete_pairs = [
        pair
        for pair in pairs
        if pair["image"].get("exists")
        and int(pair["image"].get("size_bytes", 0)) > 0
        and int(pair["stdout"].get("size_bytes", 0)) > 0
        and any(
            isinstance(wave, dict)
            and wave.get("exists")
            and int(wave.get("size_bytes", 0)) > 0
            for wave in pair["waveforms"]
        )
    ]
    source = source_record(source_root)
    emu = file_record(emu_path)
    replay_returns = parse_replay_returns(driver_log)
    driver_required_fields = {
        "command",
        "replay_script_sha256",
        "started_at_utc",
        "ended_at_utc",
        "return_code",
    }
    driver_metadata_complete = bool(
        driver_metadata
        and driver_required_fields.issubset(driver_metadata)
        and isinstance(driver_metadata.get("command"), list)
        and driver_metadata.get("replay_script_sha256")
        and driver_metadata.get("started_at_utc")
        and driver_metadata.get("ended_at_utc")
        and isinstance(driver_metadata.get("return_code"), int)
    )
    issue_contract_nonempty = any(
        int(record.get("size_bytes", 0)) > 0 for record in issue_context
    )
    source_layout_valid = (
        source_root.joinpath("src", "main", "scala", "xiangshan").is_dir()
    )
    completeness = {
        "issue_contract_saved_nonempty": issue_contract_nonempty,
        "driver_metadata_complete": driver_metadata_complete,
        "source_tree_exists": bool(source["exists"]),
        "source_layout_looks_like_xiangshan": source_layout_valid,
        "source_git_head_recorded": bool(source.get("git_head")),
        "emu_exists_nonempty_executable": bool(
            emu["exists"]
            and int(emu.get("size_bytes", 0)) > 0
            and os.access(emu_path, os.X_OK)
        ),
        "replay_stdout_exists_nonempty": any(path.stat().st_size > 0 for path in replay_stdout),
        "replay_input_pair_exists_nonempty": any(
            int(pair["image"].get("size_bytes", 0)) > 0 for pair in images_existing
        ),
        "logged_waveform_mapping_exists_nonempty": any(
            int(wave.get("size_bytes", 0)) > 0 for wave in mapped_existing
        ),
        "complete_image_log_wave_pair_exists": bool(complete_pairs),
        "per_image_return_code_recorded": bool(replay_returns),
    }
    artifact_gate_passed = all(completeness.values())
    warnings: list[str] = []
    if driver_metadata_error:
        warnings.append(driver_metadata_error)
    if not issue_context:
        warnings.append("saved issue context was not found; preserve the issue contract separately")
    if not replay_stdout:
        warnings.append("no *.replay.stdout files were found")
    if replay_stdout and not mapped_existing:
        warnings.append("replay logs did not map to an existing waveform; do not select the newest FST")
    if waves and not mapped_existing:
        warnings.append("unbound waveform candidates exist under XiangShan/build")
    warnings.append(
        "artifact_gate_passed proves structural artifact completeness only; it does not validate ELF/FST semantics or prove that the issue symptom reproduced"
    )

    manifest: dict[str, object] = {
        "schema_version": 1,
        "generated_at_utc": utc_now(),
        "issue": args.issue,
        "case_dir": str(case_dir),
        "replay_root": str(replay_root),
        "driver_metadata": driver_metadata,
        "driver_log": file_record(driver_log),
        "per_image_replay_returns": replay_returns,
        "issue_context_files": issue_context,
        "source": source,
        "emu": emu,
        "replay_pairs": pairs,
        "all_log_anchors": all_anchors,
        "all_waveform_candidates": [file_record(path) for path in waves],
        "completeness": completeness,
        "artifact_gate_passed": artifact_gate_passed,
        "warnings": warnings,
    }
    rendered = json.dumps(manifest, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        output = Path(args.output).expanduser().resolve()
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(rendered, encoding="utf-8")
        print(f"manifest={output}")
    else:
        print(rendered, end="")
    if args.require_reproduction and not artifact_gate_passed:
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
