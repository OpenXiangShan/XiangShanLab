#!/usr/bin/env python3
"""Summarize exception-related XiangShan bugs.

The script scans xiangshan-bugs-src/issue-*/metadata.json plus issue text and
writes a Markdown report.  It deliberately treats commit logs as optional
context instead of primary matching input, because code diffs often contain
generic names such as exceptionVec that would over-count unrelated fixes.
"""

from __future__ import annotations

import argparse
import json
import re
from collections import Counter
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable


TEXT_SUFFIXES = {".md", ".txt", ".log", ".S", ".s", ".c", ".h", ".scala"}

KEYWORD_GROUPS: dict[str, list[str]] = {
    "exception/trap": [
        r"\bexception(s)?\b",
        r"\btrap(s|ped|ping)?\b",
        r"\bprecise trap(s)?\b",
        r"\bnested exception\b",
        r"\bdouble[- ]?trap\b",
        r"\bdbltrp\b",
        r"\bEX_[A-Z0-9_]+\b",
    ],
    "fault": [
        r"\bfault(s|ing)?\b",
        r"\bpage[- ]?fault(s|ing)?\b",
        r"\bguest[- ]?page[- ]?fault(s|ing)?\b",
        r"\baccess[- ]?fault(s|ing)?\b",
        r"\binstruction[- ]?page[- ]?fault\b",
        r"\bload[- ]?access[- ]?fault\b",
        r"\bstore(/amo)?[- ]?access[- ]?fault\b",
        r"\bIGPF\b",
        r"\bLAF\b",
        r"\bSAF\b",
    ],
    "illegal instruction": [
        r"\billegal[- ]?instruction(s)?\b",
        r"\billegal `[^`]+`",
        r"\billegal opcode\b",
        r"\bEX_II\b",
        r"\breserved (encoding|instruction|vsew)\b",
    ],
    "misalignment": [
        r"\bmisalign(ed|ment)?\b",
        r"\baddress[- ]?misaligned\b",
        r"\bcross[- ]?page\b",
        r"\bcross[- ]?16B\b",
    ],
    "trap CSR state": [
        r"\bmcause\b",
        r"\bscause\b",
        r"\bvscause\b",
        r"\bmepc\b",
        r"\bsepc\b",
        r"\bvsepc\b",
        r"\bmtval2?\b",
        r"\bstval\b",
        r"\bvstval\b",
        r"\bhtval\b",
        r"\bmtinst\b",
        r"\bhtinst\b",
        r"\bvstart\b",
    ],
    "interrupt/NMI": [
        r"\binterrupt(s)?\b",
        r"\bNMI\b",
        r"\bNMIE\b",
        r"\bWFI\b",
        r"\bSEI\b",
        r"\bMSI\b",
    ],
    "debug/breakpoint": [
        r"\bbreakpoint(s)?\b",
        r"\bwatchpoint(s)?\b",
        r"\bdebug mode\b",
        r"\bDRET\b",
        r"\bEX_BP\b",
    ],
}

GROUP_PATTERNS = {
    group: [re.compile(pattern, re.IGNORECASE) for pattern in patterns]
    for group, patterns in KEYWORD_GROUPS.items()
}

INCLUSION_GROUPS = {
    "exception/trap",
    "fault",
    "illegal instruction",
    "interrupt/NMI",
    "debug/breakpoint",
}


@dataclass(frozen=True)
class Match:
    number: int
    title: str
    url: str
    state: str
    author: str
    created_at: str
    is_pull_request: bool
    groups: tuple[str, ...]
    evidence_files: tuple[str, ...]
    source_file_count: int


def read_text(path: Path, limit: int | None = None) -> str:
    try:
        data = path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return ""
    if limit is not None and len(data) > limit:
        return data[:limit]
    return data


def issue_dirs(src: Path) -> Iterable[Path]:
    def key(path: Path) -> int:
        try:
            return int(path.name.split("-", 1)[1])
        except (IndexError, ValueError):
            return 0

    yield from sorted((p for p in src.glob("issue-*") if p.is_dir()), key=key)


def text_files_for_issue(issue_dir: Path, include_commit_logs: bool) -> list[Path]:
    files: list[Path] = []
    for path in issue_dir.rglob("*"):
        if not path.is_file() or path.suffix not in TEXT_SUFFIXES:
            continue
        rel = path.relative_to(issue_dir).as_posix()
        if not include_commit_logs and rel.startswith("commit-log"):
            continue
        files.append(path)
    return sorted(files)


def matched_groups(text: str) -> set[str]:
    groups: set[str] = set()
    for group, patterns in GROUP_PATTERNS.items():
        if any(pattern.search(text) for pattern in patterns):
            groups.add(group)
    return groups


def collect_matches(src: Path, include_pulls: bool, include_commit_logs: bool) -> tuple[list[Match], Counter]:
    matches: list[Match] = []
    corpus_counts: Counter = Counter()

    for issue_dir in issue_dirs(src):
        metadata_path = issue_dir / "metadata.json"
        if not metadata_path.exists():
            continue
        metadata = json.loads(read_text(metadata_path))
        is_pull_request = bool(metadata.get("is_pull_request", False))
        corpus_counts["pull_requests" if is_pull_request else "issues"] += 1
        if is_pull_request and not include_pulls:
            continue

        title = metadata.get("title") or ""
        metadata_text = "\n".join(
            [
                title,
                " ".join(metadata.get("labels") or []),
                metadata.get("url") or "",
            ]
        )

        groups = matched_groups(metadata_text)
        evidence_files: set[str] = set()
        if groups:
            evidence_files.add("metadata.json")

        for path in text_files_for_issue(issue_dir, include_commit_logs):
            rel = path.relative_to(src).as_posix()
            text = read_text(path, limit=1_000_000)
            file_groups = matched_groups(text)
            if file_groups:
                groups.update(file_groups)
                evidence_files.add(rel)

        if not groups.intersection(INCLUSION_GROUPS):
            continue

        matches.append(
            Match(
                number=int(metadata.get("number") or issue_dir.name.split("-", 1)[1]),
                title=title,
                url=metadata.get("url") or "",
                state=metadata.get("state") or "",
                author=metadata.get("author") or "",
                created_at=metadata.get("created_at") or "",
                is_pull_request=is_pull_request,
                groups=tuple(sorted(groups)),
                evidence_files=tuple(sorted(evidence_files)),
                source_file_count=len(metadata.get("source_files") or []),
            )
        )

    return sorted(matches, key=lambda m: m.number, reverse=True), corpus_counts


def short_date(value: str) -> str:
    return value[:10] if value else "-"


def md_escape(value: str) -> str:
    return value.replace("|", r"\|").replace("\n", " ")


def build_markdown(matches: list[Match], corpus_counts: Counter, args: argparse.Namespace) -> str:
    issue_matches = [m for m in matches if not m.is_pull_request]
    pr_matches = [m for m in matches if m.is_pull_request]
    group_counts: Counter = Counter()
    state_counts: Counter = Counter()
    author_counts: Counter = Counter()
    year_counts: Counter = Counter()

    for match in matches:
        state_counts[match.state or "-"] += 1
        author_counts[match.author or "-"] += 1
        if match.created_at:
            year_counts[match.created_at[:4]] += 1
        for group in match.groups:
            group_counts[group] += 1

    generated_at = datetime.now(timezone.utc).isoformat(timespec="seconds")
    lines: list[str] = [
        "# Exception-Related Bug Summary",
        "",
        f"- Source: `{args.src}`",
        f"- Generated at: `{generated_at}`",
        "- Matching scope: metadata, descriptions, and text reproducers"
        + ("; commit logs included" if args.include_commit_logs else "; commit logs excluded by default"),
        f"- Corpus: **{corpus_counts['issues']}** issues"
        + (f", **{corpus_counts['pull_requests']}** pull requests" if args.include_pulls else ""),
        f"- Exception-related matches: **{len(issue_matches)}** issues"
        + (f", **{len(pr_matches)}** pull requests" if args.include_pulls else ""),
        "",
        "## Classification Rules",
        "",
        "An item is counted when its metadata/title, description, or text reproducer contains a core exception-event term such as `exception`, `trap`, `fault`, `illegal instruction`, `interrupt`, `NMI`, `breakpoint`, `watchpoint`, or `DRET`. CSR names such as `mcause`/`mtval` and context terms such as `misaligned` are reported as groups after an item has matched the core exception-event rule. Commit logs are excluded unless `--include-commit-logs` is set, because implementation diffs often contain broad signal names that inflate the count.",
        "",
        "## Keyword Group Counts",
        "",
        "| Group | Matches |",
        "|---|---:|",
    ]

    for group, count in group_counts.most_common():
        lines.append(f"| {group} | {count} |")

    lines.extend(["", "## State Counts", "", "| State | Matches |", "|---|---:|"])
    for state, count in state_counts.most_common():
        lines.append(f"| {state} | {count} |")

    lines.extend(["", "## Year Counts", "", "| Year | Matches |", "|---|---:|"])
    for year, count in sorted(year_counts.items(), reverse=True):
        lines.append(f"| {year} | {count} |")

    lines.extend(["", "## Author Counts", "", "| Author | Matches |", "|---|---:|"])
    for author, count in author_counts.most_common():
        lines.append(f"| {md_escape(author)} | {count} |")

    lines.extend(
        [
            "",
            "## Matched Issues",
            "",
            "| Issue | State | Created | Author | Groups | Evidence files | Title |",
            "|---:|---|---|---|---|---:|---|",
        ]
    )
    for match in issue_matches:
        link = f"[#{match.number}]({match.url})" if match.url else f"#{match.number}"
        lines.append(
            "| "
            + " | ".join(
                [
                    link,
                    md_escape(match.state or "-"),
                    short_date(match.created_at),
                    md_escape(match.author or "-"),
                    md_escape(", ".join(match.groups)),
                    str(len(match.evidence_files)),
                    md_escape(match.title or "-"),
                ]
            )
            + " |"
        )

    if args.include_pulls:
        lines.extend(
            [
                "",
                "## Matched Pull Requests",
                "",
                "| PR | State | Created | Author | Groups | Evidence files | Title |",
                "|---:|---|---|---|---|---:|---|",
            ]
        )
        for match in pr_matches:
            link = f"[#{match.number}]({match.url})" if match.url else f"#{match.number}"
            lines.append(
                "| "
                + " | ".join(
                    [
                        link,
                        md_escape(match.state or "-"),
                        short_date(match.created_at),
                        md_escape(match.author or "-"),
                        md_escape(", ".join(match.groups)),
                        str(len(match.evidence_files)),
                        md_escape(match.title or "-"),
                    ]
                )
                + " |"
            )

    lines.append("")
    return "\n".join(lines)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    default_root = Path(__file__).resolve().parent
    parser.add_argument(
        "--src",
        type=Path,
        default=default_root / "xiangshan-bugs-src",
        help="Path to xiangshan-bugs-src.",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=default_root / "exception-summary.md",
        help="Markdown report path.",
    )
    parser.add_argument(
        "--include-pulls",
        action="store_true",
        help="Also count pull requests in addition to issues.",
    )
    parser.add_argument(
        "--include-commit-logs",
        action="store_true",
        help="Use commit-log.md and commit-log.diff as matching input.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not args.src.exists():
        raise SystemExit(f"source directory not found: {args.src}")
    matches, corpus_counts = collect_matches(args.src, args.include_pulls, args.include_commit_logs)
    markdown = build_markdown(matches, corpus_counts, args)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(markdown, encoding="utf-8")
    print(f"wrote {args.output}")
    print(f"matched {sum(1 for m in matches if not m.is_pull_request)} issues")
    if args.include_pulls:
        print(f"matched {sum(1 for m in matches if m.is_pull_request)} pull requests")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
