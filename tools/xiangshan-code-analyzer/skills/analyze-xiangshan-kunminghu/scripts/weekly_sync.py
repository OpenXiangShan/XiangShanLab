#!/usr/bin/env python3
"""Weekly local docs/course sync helper for analyze-xiangshan-kunminghu.

Conservative behavior:
- Record the last check and exit when the interval has not elapsed.
- Run git fetch on configured repositories.
- Run git pull --ff-only only when the worktree is clean, unless --no-pull is used.
- Never reset, clean, stash, or overwrite local files.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

DEFAULT_INTERVAL_DAYS = 7
DEFAULT_STATE = Path.home() / ".cache" / "codex" / "analyze-xiangshan-kunminghu-weekly-sync.json"
DEFAULT_REPOS = [
    "/nfs/home/yuanmiaomiao/XiangShanLab",
    "/nfs/home/yuanmiaomiao/XiangShanLab/XiangShan-Design-Doc",
]
COURSE_ANALYSIS_DIRS = [
    "/nfs/home/yuanmiaomiao/XiangShanLab/xiangshan-course/docs/课程体系4：实现篇-香山高性能处理器微架构优化/中级-基于代码进行分析",
    "/nfs/home/yuanmiaomiao/XiangShanLab/xiangshan-course/docs/课程体系4：实现篇-香山高性能处理器微架构优化/中级-高性能香山处理器代码深入解析",
]


def run(cmd: list[str], cwd: Path) -> tuple[int, str]:
    proc = subprocess.run(cmd, cwd=str(cwd), text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    return proc.returncode, proc.stdout.strip()


def is_git_repo(path: Path) -> bool:
    code, out = run(["git", "rev-parse", "--is-inside-work-tree"], path)
    return code == 0 and out.splitlines()[-1:] == ["true"]


def repo_state(path: Path, allow_pull: bool) -> dict[str, Any]:
    result: dict[str, Any] = {"path": str(path), "exists": path.exists(), "git": False}
    if not path.exists():
        result["action"] = "missing"
        return result
    if not is_git_repo(path):
        result["action"] = "not-git"
        return result

    result["git"] = True
    code, branch = run(["git", "branch", "--show-current"], path)
    result["branch"] = branch if code == 0 else ""
    code, status = run(["git", "status", "--short"], path)
    dirty = bool(status.strip()) if code == 0 else True
    result["dirty"] = dirty

    code, fetch_out = run(["git", "fetch", "--all", "--prune"], path)
    result["fetch_code"] = code
    result["fetch_output_tail"] = "\n".join(fetch_out.splitlines()[-20:])
    if code != 0:
        result["action"] = "fetch-failed"
        return result

    if not allow_pull:
        result["action"] = "fetched-only"
        return result
    if dirty:
        result["action"] = "fetched-pull-skipped-dirty"
        return result

    code, pull_out = run(["git", "pull", "--ff-only"], path)
    result["pull_code"] = code
    result["pull_output_tail"] = "\n".join(pull_out.splitlines()[-20:])
    result["action"] = "pulled-ff-only" if code == 0 else "pull-failed"
    return result


def load_state(path: Path) -> dict[str, Any]:
    try:
        return json.loads(path.read_text())
    except Exception:
        return {}


def write_state(path: Path, state: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n")


def parse_repos(extra: str | None) -> list[Path]:
    repos = list(DEFAULT_REPOS)
    for raw in (os.environ.get("XIANGSHAN_SYNC_REPOS"), extra):
        if raw:
            repos.extend([x for x in raw.split(os.pathsep) if x])
    seen: set[str] = set()
    result: list[Path] = []
    for repo in repos:
        normalized = str(Path(repo))
        if normalized not in seen:
            seen.add(normalized)
            result.append(Path(normalized))
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description="Weekly sync helper for analyze-xiangshan-kunminghu")
    parser.add_argument("--force", action="store_true", help="sync even when the interval has not elapsed")
    parser.add_argument("--interval-days", type=int, default=DEFAULT_INTERVAL_DAYS)
    parser.add_argument("--state-file", default=str(DEFAULT_STATE))
    parser.add_argument("--extra-repos", default=None, help="os.pathsep-separated extra git repositories")
    parser.add_argument("--no-pull", action="store_true", help="only fetch; never pull")
    args = parser.parse_args()

    now = datetime.now(timezone.utc)
    state_file = Path(args.state_file)
    state = load_state(state_file)
    last_raw = state.get("last_sync_utc")
    if last_raw and not args.force:
        try:
            last = datetime.fromisoformat(last_raw)
            age_days = (now - last).total_seconds() / 86400
            if age_days < args.interval_days:
                print(f"skip: last sync {age_days:.2f} days ago < {args.interval_days} days")
                print(f"state_file: {state_file}")
                return 0
        except Exception:
            pass

    results = [repo_state(repo, allow_pull=not args.no_pull) for repo in parse_repos(args.extra_repos)]

    top = Path("/nfs/home/yuanmiaomiao/XiangShanLab")
    course_statuses: list[dict[str, Any]] = []
    for raw_dir in COURSE_ANALYSIS_DIRS:
        course_dir = Path(raw_dir)
        course_status: dict[str, Any] = {"path": str(course_dir), "exists": course_dir.exists()}
        if course_dir.exists() and top.exists():
            code, out = run(["git", "status", "--short", "--", str(course_dir)], top)
            course_status["git_status_code"] = code
            course_status["git_status"] = out
        course_statuses.append(course_status)

    new_state = {
        "last_sync_utc": now.isoformat(),
        "interval_days": args.interval_days,
        "repos": results,
        "course_analysis_dirs": course_statuses,
    }
    write_state(state_file, new_state)
    print(json.dumps(new_state, ensure_ascii=False, indent=2))
    return 0 if all(r.get("action") not in {"fetch-failed", "pull-failed"} for r in results) else 1


if __name__ == "__main__":
    sys.exit(main())
