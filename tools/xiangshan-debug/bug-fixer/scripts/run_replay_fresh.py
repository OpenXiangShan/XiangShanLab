#!/usr/bin/env python3
"""Run xs-bug-replay.py in a fresh timestamped parent and capture its console log."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import signal
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path


SKILL_ROOT = Path(__file__).resolve().parents[1]


def positive_issue(value: str) -> int:
    try:
        issue = int(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("issue must be an integer") from exc
    if issue <= 0:
        raise argparse.ArgumentTypeError("issue must be a positive integer")
    return issue


def commit_hash(value: str) -> str:
    if not re.fullmatch(r"[0-9a-fA-F]{7,40}", value):
        raise argparse.ArgumentTypeError("commit hash must contain 7 to 40 hexadecimal digits")
    return value.lower()


def find_repo_root() -> Path | None:
    configured = os.environ.get("XIANGSHAN_LAB_ROOT")
    if configured:
        return Path(configured).expanduser().resolve()
    for candidate in (SKILL_ROOT, *SKILL_ROOT.parents):
        if (candidate / "tools" / "xiangshan-bugs-analyzer" / "xs-bug-replay.py").is_file():
            return candidate.resolve()
    return None


def resolve_replay_script(explicit: str | None) -> Path:
    if explicit:
        path = Path(explicit).expanduser().resolve()
    elif os.environ.get("XIANGSHAN_BUG_REPLAY"):
        path = Path(os.environ["XIANGSHAN_BUG_REPLAY"]).expanduser().resolve()
    else:
        repo_root = find_repo_root()
        if repo_root is None:
            raise FileNotFoundError("cannot locate XiangShanLab; pass --replay-script")
        path = repo_root / "tools" / "xiangshan-bugs-analyzer" / "xs-bug-replay.py"
    if not path.is_file():
        raise FileNotFoundError(f"xs-bug-replay.py not found: {path}")
    return path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def write_json(path: Path, value: dict[str, object]) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--issue", required=True, type=positive_issue)
    parser.add_argument("--commit-hash", type=commit_hash)
    parser.add_argument(
        "--work-root",
        required=True,
        help="Dedicated existing or new parent for the timestamped run.",
    )
    parser.add_argument("--replay-script", help="Override the xs-bug-replay.py path.")
    parser.add_argument("--dry-run", action="store_true", help="Print the plan without creating files.")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    try:
        replay_script = resolve_replay_script(args.replay_script)
    except FileNotFoundError as exc:
        print(str(exc), file=sys.stderr)
        return 2

    work_root = Path(args.work_root).expanduser().resolve()
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    run_dir = work_root / f"xiangshan-fix-{args.issue}-{stamp}"
    replay_dir = run_dir / f"xs-bug-replay-{args.issue}"
    command = [sys.executable, str(replay_script), "--issue", str(args.issue)]
    if args.commit_hash:
        command.extend(("--commit_hash", args.commit_hash))

    metadata: dict[str, object] = {
        "schema_version": 1,
        "issue": args.issue,
        "commit_hash_argument": args.commit_hash,
        "run_dir": str(run_dir),
        "replay_dir": str(replay_dir),
        "replay_script": str(replay_script),
        "replay_script_sha256": sha256(replay_script),
        "command": command,
        "started_at_utc": None,
        "ended_at_utc": None,
        "return_code": None,
        "interrupted": False,
    }
    if args.dry_run:
        print(json.dumps(metadata, ensure_ascii=False, indent=2))
        return 0

    work_root.mkdir(parents=True, exist_ok=True)
    try:
        run_dir.mkdir(parents=False, exist_ok=False)
    except FileExistsError:
        print(f"refusing to reuse existing run directory: {run_dir}", file=sys.stderr)
        return 2

    metadata_path = run_dir / "replay-command.json"
    driver_log = run_dir / "replay-driver.log"
    metadata["started_at_utc"] = utc_now()
    write_json(metadata_path, metadata)

    process: subprocess.Popen[str] | None = None
    return_code = 2
    try:
        process = subprocess.Popen(
            command,
            cwd=run_dir,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )
        assert process.stdout is not None
        with driver_log.open("w", encoding="utf-8") as log_stream:
            for line in process.stdout:
                print(line, end="", flush=True)
                log_stream.write(line)
                log_stream.flush()
        return_code = process.wait()
    except KeyboardInterrupt:
        metadata["interrupted"] = True
        if process and process.poll() is None:
            process.send_signal(signal.SIGINT)
            try:
                return_code = process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.terminate()
                return_code = process.wait()
        else:
            return_code = 130
    except OSError as exc:
        with driver_log.open("a", encoding="utf-8") as log_stream:
            log_stream.write(f"failed to start replay: {exc}\n")
        print(f"failed to start replay: {exc}", file=sys.stderr)
        return_code = 2
    finally:
        metadata["ended_at_utc"] = utc_now()
        metadata["return_code"] = return_code
        metadata["replay_dir_exists"] = replay_dir.is_dir()
        write_json(metadata_path, metadata)

    print(f"run_dir={run_dir}")
    print(f"driver_log={driver_log}")
    print(f"replay_return_code={return_code}")
    return return_code


if __name__ == "__main__":
    raise SystemExit(main())
