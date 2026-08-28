#!/usr/bin/env python3
"""Resolve and verify the external tools used by xiangshan-bug-fixer."""

from __future__ import annotations

import argparse
import json
import os
import subprocess
from pathlib import Path
from typing import Iterable


SKILL_ROOT = Path(__file__).resolve().parents[1]


def normalize_path(path: Path, preserve_symlink: bool = False) -> Path:
    expanded = path.expanduser()
    if not expanded.is_absolute():
        expanded = Path.cwd() / expanded
    return expanded.absolute() if preserve_symlink else expanded.resolve()


def existing_first(paths: Iterable[Path], preserve_symlink: bool = False) -> Path | None:
    for path in paths:
        normalized = normalize_path(path, preserve_symlink=preserve_symlink)
        if normalized.exists():
            return normalized
    return None


def find_repo_root(explicit: str | None) -> Path | None:
    if explicit:
        return Path(explicit).expanduser().resolve()
    configured = os.environ.get("XIANGSHAN_LAB_ROOT")
    if configured:
        return Path(configured).expanduser().resolve()
    for candidate in (SKILL_ROOT, *SKILL_ROOT.parents):
        if (
            (candidate / "tools" / "xiangshan-bugs-analyzer" / "xs-bug-replay.py").is_file()
            and (candidate / "xiangshan-course").is_dir()
        ):
            return candidate.resolve()
    return None


def choose_path(
    explicit: str | None,
    env_name: str,
    candidates: Iterable[Path],
    preserve_symlink: bool = False,
) -> Path | None:
    if explicit:
        return normalize_path(Path(explicit), preserve_symlink=preserve_symlink)
    configured = os.environ.get(env_name)
    if configured:
        return normalize_path(Path(configured), preserve_symlink=preserve_symlink)
    return existing_first(candidates, preserve_symlink=preserve_symlink)


def entry(path: Path | None, kind: str) -> dict[str, object]:
    exists = bool(path and (path.is_dir() if kind == "directory" else path.is_file()))
    return {"path": str(path) if path else None, "kind": kind, "exists": exists}


def check_wavekit_import(python: Path | None, source: Path | None) -> dict[str, object]:
    result: dict[str, object] = {"checked": True, "ok": False, "detail": None}
    if not python or not python.is_file() or not os.access(python, os.X_OK):
        result["detail"] = "WaveKit Python executable is missing or not executable"
        return result
    if not source or not (source / "wavekit" / "__init__.py").is_file():
        result["detail"] = "WaveKit source package is missing"
        return result

    environment = os.environ.copy()
    old_pythonpath = environment.get("PYTHONPATH")
    environment["PYTHONPATH"] = (
        str(source) if not old_pythonpath else str(source) + os.pathsep + old_pythonpath
    )
    try:
        completed = subprocess.run(
            [str(python), "-c", "from wavekit import FstReader; print(FstReader.__name__)"],
            check=False,
            capture_output=True,
            text=True,
            timeout=20,
            env=environment,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        result["detail"] = str(exc)
        return result
    result["ok"] = completed.returncode == 0 and "FstReader" in completed.stdout
    if not result["ok"]:
        result["detail"] = (completed.stderr or completed.stdout).strip()[:1000]
    return result


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo-root")
    parser.add_argument("--replay-script")
    parser.add_argument("--wavekit-skill")
    parser.add_argument("--course-root")
    parser.add_argument("--wavekit-python")
    parser.add_argument("--wavekit-src")
    parser.add_argument("--no-import-check", action="store_true")
    parser.add_argument("--json", action="store_true", help="Print JSON instead of a table.")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    repo_root = find_repo_root(args.repo_root)
    repo_candidates = [repo_root] if repo_root else []

    replay_script = choose_path(
        args.replay_script,
        "XIANGSHAN_BUG_REPLAY",
        [root / "tools" / "xiangshan-bugs-analyzer" / "xs-bug-replay.py" for root in repo_candidates],
    )
    wavekit_skill = choose_path(
        args.wavekit_skill,
        "XIANGSHAN_WAVEKIT_SKILL",
        [root / "tools" / "analyze-xiangshan-wavekit" / "SKILL.md" for root in repo_candidates],
    )
    course_root = choose_path(
        args.course_root,
        "XIANGSHAN_COURSE_SOURCE_ANALYSIS",
        [
            root
            / "xiangshan-course"
            / "docs"
            / "4-xiangshan-microarchitecture-analysis"
            / "3-xiangshan-source-code-analysis"
            for root in repo_candidates
        ],
    )
    wavekit_python = choose_path(
        args.wavekit_python,
        "WAVEKIT_PYTHON",
        [
            Path("/home/yanyusong/wavekit/.venv/bin/python"),
            *[root / "tools" / "wavekit-xslab" / ".venv" / "bin" / "python" for root in repo_candidates],
        ],
        preserve_symlink=True,
    )
    wavekit_source = choose_path(
        args.wavekit_src,
        "WAVEKIT_SRC",
        [
            Path("/home/yanyusong/wavekit/src"),
            *[root / "tools" / "wavekit-xslab" / "src" for root in repo_candidates],
        ],
    )
    wavekit_workflow = (
        wavekit_skill.parent / "references" / "workflow.md" if wavekit_skill else None
    )

    required = {
        "replay_script": entry(replay_script, "file"),
        "wavekit_skill": entry(wavekit_skill, "file"),
        "wavekit_workflow": entry(wavekit_workflow, "file"),
        "course_root": entry(course_root, "directory"),
        "wavekit_python": entry(wavekit_python, "file"),
        "wavekit_source": entry(wavekit_source, "directory"),
    }
    import_result = (
        {"checked": False, "ok": None, "detail": None}
        if args.no_import_check
        else check_wavekit_import(wavekit_python, wavekit_source)
    )
    paths_ok = all(bool(item["exists"]) for item in required.values())
    overall_ok = paths_ok and (import_result["ok"] is not False)
    report = {
        "ok": overall_ok,
        "skill_root": str(SKILL_ROOT),
        "repo_root": str(repo_root) if repo_root else None,
        "dependencies": required,
        "wavekit_import": import_result,
    }

    if args.json:
        print(json.dumps(report, ensure_ascii=False, indent=2))
    else:
        for name, item in required.items():
            status = "OK" if item["exists"] else "MISSING"
            print(f"[{status}] {name}: {item['path']}")
        if import_result["checked"]:
            status = "OK" if import_result["ok"] else "FAILED"
            print(f"[{status}] wavekit_import: {import_result['detail'] or 'FstReader import succeeded'}")
        print(f"overall: {'OK' if overall_ok else 'FAILED'}")
    return 0 if overall_ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
