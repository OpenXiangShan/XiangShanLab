#!/usr/bin/env python3
"""Save generated XiangShan module analysis Markdown to the course deep-dive directory."""

from __future__ import annotations

import argparse
import re
import shutil
import sys
import os
from datetime import datetime
from pathlib import Path

XIANGSHANLAB_HOME_ENV = "xiangshanlab_home"
XIANGSHANLAB_HOME_ENV_UPPER = "XIANGSHANLAB_HOME"
DEFAULT_OUTPUT_DIR = Path(
    "xiangshan-course/docs/"
    "课程体系4：实现篇-香山高性能处理器微架构优化/"
    "中级-高性能香山处理器代码深入解析"
)


def sanitize_stem(name: str) -> str:
    stem = name.strip().replace("\\", "/").rstrip("/").split("/")[-1]
    stem = re.sub(r"\.(scala|md)$", "", stem, flags=re.IGNORECASE)
    stem = re.sub(r"[\\/:*?\"<>|]+", "-", stem)
    stem = re.sub(r"\s+", "-", stem).strip(".-")
    return stem or "module-analysis"


def unique_path(path: Path) -> Path:
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    return path.with_name(f"{path.stem}-{stamp}{path.suffix}")


def xiangshanlab_home() -> Path:
    raw = os.environ.get(XIANGSHANLAB_HOME_ENV) or os.environ.get(XIANGSHANLAB_HOME_ENV_UPPER)
    if not raw:
        raise RuntimeError(
            f"set {XIANGSHANLAB_HOME_ENV} or {XIANGSHANLAB_HOME_ENV_UPPER} to the XiangShanLab checkout"
        )
    return Path(raw).expanduser().resolve()


def resolve_output_dir(raw_output_dir: str) -> Path:
    output_dir = Path(raw_output_dir).expanduser()
    if output_dir.is_absolute():
        return output_dir
    return xiangshanlab_home() / output_dir


def main() -> int:
    parser = argparse.ArgumentParser(description="Save XiangShan module analysis Markdown")
    parser.add_argument("--module", required=True, help="module name or desired file stem")
    parser.add_argument("--input", required=True, help="generated Markdown file to save")
    parser.add_argument("--output-dir", default=str(DEFAULT_OUTPUT_DIR))
    parser.add_argument("--filename", default=None, help="explicit output filename")
    parser.add_argument("--overwrite", action="store_true", help="replace an existing output file")
    parser.add_argument("--unique-if-exists", action="store_true", help="append timestamp if output exists")
    args = parser.parse_args()

    src = Path(args.input)
    if not src.is_file():
        print(f"input Markdown does not exist: {src}", file=sys.stderr)
        return 2

    try:
        out_dir = resolve_output_dir(args.output_dir)
    except RuntimeError as exc:
        print(str(exc), file=sys.stderr)
        return 2
    out_dir.mkdir(parents=True, exist_ok=True)
    filename = args.filename or f"{sanitize_stem(args.module)}.md"
    if not filename.lower().endswith(".md"):
        filename += ".md"
    dst = out_dir / filename

    if dst.exists() and not args.overwrite:
        if args.unique_if_exists:
            dst = unique_path(dst)
        else:
            print(f"refusing to overwrite existing file: {dst}", file=sys.stderr)
            print("pass --overwrite or --unique-if-exists", file=sys.stderr)
            return 3

    shutil.copyfile(src, dst)
    print(dst)
    return 0


if __name__ == "__main__":
    sys.exit(main())
