#!/usr/bin/env python3
"""Summarize side-channel and other security-related XiangShan bugs.

The script scans issue metadata, descriptions, and text reproducers under
xiangshan-bugs-src/.  Commit logs are excluded by default because source
patches frequently contain generic security-adjacent implementation terms.
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


TEXT_SUFFIXES = {".md", ".txt", ".log", ".S", ".s", ".c", ".h", ".scala", ".json"}

# These expressions describe an observable channel, not merely the presence
# of a cache, predictor, or secret word in an unrelated implementation note.
SIDE_CHANNEL_GROUPS: dict[str, list[str]] = {
    "timing channel": [
        r"\bside[- ]?channel\b",
        r"\btiming[- ]?channel\b",
        r"\bsoftware[- ]visible timing channel\b",
        r"\bsecret[- ]dependent timing\b",
        r"\btiming oracle\b",
        r"\bleak(?:s|ed|ing)?\s+(?:one|a)\s+bit\b",
    ],
    "cache/predictor channel": [
        r"\bcache oracle\b",
        r"\bfetch oracle\b",
        r"\bicache\b.{0,80}\boracle\b",
        r"\bdcache\b.{0,80}\bside[- ]?channel\b",
        r"\btransient\b.{0,100}\b(?:i[- ]?cache|d[- ]?cache|cache)\b",
        r"\bpredictor[- ](?:interference|state).{0,100}\b(?:timing|channel)\b",
    ],
    "physical leakage": [
        r"\bpower\s*/\s*EM\b",
        r"\bpower[- ]side[- ]?channel\b",
        r"\b(?:power|EM|electromagnetic)\b.{0,100}\bleak(?:s|ed|ing)?\b",
        r"\bRF write bus\b.{0,100}\b(?:power|EM|side[- ]?channel|leak)\b",
    ],
}

SECURITY_GROUPS: dict[str, list[str]] = {
    "security label": [
        r"(?i)(?:^|\s)topic:\s*security(?:\s|$)",
        r"(?i)(?:^|\s)type:\s*security(?:\s|$)",
        r"(?i)\bsecurity[- ]HC\b",
    ],
    "explicit security impact": [
        r"\bsecurity\s+impact\b",
        r"\bsecurity[- ]related\b",
        r"\bsecurity\s+relevance\b",
        r"\bsecurity[- ]relevant\b",
        r"\bsecurity\s+vulnerabilit(?:y|ies)\b",
        r"\bsecurity\s+bypass\b",
        r"\bconfidential(?:ity)?\b",
        r"\binformation\s+leak(?:s|ed|ing)?\b",
        r"\bprivilege\s+escalation\b",
        r"\bisolation\s+(?:boundary|violation)\b",
    ],
    "memory/access protection": [
        r"\bPMP\b",
        r"\bPMA\b",
        r"\bpointer masking\b",
        r"\baccess[- ]control\b",
        r"\bpermission(?:s)?\b.{0,60}\b(?:bypass|ignore|missing|wrong|check)\b",
        r"\b(?:bypass|ignore|miss|skip|violate)[- ]?(?:es|s)?\b.{0,60}\bpermission(?:s)?\b",
    ],
    "speculation/wrong path": [
        r"\bwrong[- ]path\b.{0,100}\b(?:data|load|fetch|execute|execution)\b",
        r"\bspeculative\b.{0,100}\b(?:data|load|fetch|execution|access)\b",
        r"\btransient execution\b",
        r"\btransient\b.{0,100}\b(?:access|load|fetch)\b",
    ],
}

SIDE_PATTERNS = {
    group: [re.compile(pattern, re.IGNORECASE) for pattern in patterns]
    for group, patterns in SIDE_CHANNEL_GROUPS.items()
}
SECURITY_PATTERNS = {
    group: [re.compile(pattern, re.IGNORECASE) for pattern in patterns]
    for group, patterns in SECURITY_GROUPS.items()
}


@dataclass(frozen=True)
class SecurityBug:
    number: int
    title: str
    url: str
    state: str
    author: str
    created: str
    is_pull_request: bool
    kinds: tuple[str, ...]
    groups: tuple[str, ...]
    evidence_files: tuple[str, ...]


def read_text(path: Path, limit: int | None = None) -> str:
    try:
        text = path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return ""
    return text if limit is None else text[:limit]


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
        relative = path.relative_to(issue_dir).as_posix()
        if not include_commit_logs and relative.startswith("commit-log"):
            continue
        files.append(path)
    return sorted(files)


def matching_groups(text: str, patterns: dict[str, list[re.Pattern[str]]]) -> set[str]:
    return {
        group
        for group, group_patterns in patterns.items()
        if any(pattern.search(text) for pattern in group_patterns)
    }


def collect(
    src: Path, include_pulls: bool, include_commit_logs: bool
) -> tuple[list[SecurityBug], Counter]:
    bugs: list[SecurityBug] = []
    corpus_counts: Counter = Counter()

    for issue_dir in issue_dirs(src):
        metadata_path = issue_dir / "metadata.json"
        if not metadata_path.exists():
            continue
        try:
            metadata = json.loads(read_text(metadata_path))
        except json.JSONDecodeError:
            continue

        is_pull_request = bool(metadata.get("is_pull_request", False))
        corpus_counts["pull_requests" if is_pull_request else "issues"] += 1
        if is_pull_request and not include_pulls:
            continue

        title = str(metadata.get("title") or "")
        labels = [str(label) for label in metadata.get("labels") or []]
        metadata_text = "\n".join(
            [title, " ".join(labels), str(metadata.get("url") or "")]
        )
        description_path = issue_dir / "description.md"
        description = read_text(description_path, limit=1_000_000)
        primary_text = "\n".join([metadata_text, description])
        evidence_files: set[str] = set()

        security_groups = matching_groups(primary_text, SECURITY_PATTERNS)
        side_groups = matching_groups(primary_text, SIDE_PATTERNS)
        if matching_groups(metadata_text, SECURITY_PATTERNS) or matching_groups(
            metadata_text, SIDE_PATTERNS
        ):
            evidence_files.add("metadata.json")
        if matching_groups(description, SECURITY_PATTERNS) or matching_groups(
            description, SIDE_PATTERNS
        ):
            evidence_files.add(f"{issue_dir.name}/description.md")

        for path in text_files_for_issue(issue_dir, include_commit_logs):
            if path == description_path:
                continue
            text = read_text(path, limit=1_000_000)
            file_side_groups = matching_groups(text, SIDE_PATTERNS)
            # Reproducer files are useful for explicit side-channel evidence,
            # but generic PMP/PMA/permission words in logs are too noisy to
            # establish a security classification on their own.
            file_security_groups = matching_groups(text, SECURITY_PATTERNS)
            file_security_groups -= {"memory/access protection", "speculation/wrong path"}
            if file_security_groups or file_side_groups:
                evidence_files.add(path.relative_to(src).as_posix())
            security_groups.update(file_security_groups)
            side_groups.update(file_side_groups)

        # A side channel is intrinsically security-related.  For other
        # security matches, require an explicit security label/impact or a
        # concrete access-protection/speculation signal.
        kinds: list[str] = []
        if side_groups:
            kinds.append("side-channel")
        if security_groups:
            kinds.append("security")
        if not kinds:
            continue

        groups = tuple(sorted(set(side_groups) | set(security_groups)))
        bugs.append(
            SecurityBug(
                number=int(metadata.get("number") or issue_dir.name.split("-", 1)[1]),
                title=title,
                url=str(metadata.get("url") or ""),
                state=str(metadata.get("state") or ""),
                author=str(metadata.get("author") or ""),
                created=str(metadata.get("created_at") or "")[:10],
                is_pull_request=is_pull_request,
                kinds=tuple(kinds),
                groups=groups,
                evidence_files=tuple(sorted(evidence_files)),
            )
        )

    return sorted(bugs, key=lambda bug: bug.number, reverse=True), corpus_counts


def md_escape(value: str) -> str:
    return value.replace("|", r"\|").replace("\n", " ")


def write_markdown(
    output: Path,
    bugs: list[SecurityBug],
    corpus_counts: Counter,
    src: Path,
    include_pulls: bool,
    include_commit_logs: bool,
) -> None:
    issues = [bug for bug in bugs if not bug.is_pull_request]
    pull_requests = [bug for bug in bugs if bug.is_pull_request]
    kind_counts = Counter(kind for bug in bugs for kind in bug.kinds)
    group_counts = Counter(group for bug in bugs for group in bug.groups)
    state_counts = Counter(bug.state or "-" for bug in bugs)
    year_counts = Counter(bug.created[:4] for bug in bugs if bug.created)
    author_counts = Counter(bug.author or "-" for bug in bugs)
    generated_at = datetime.now(timezone.utc).isoformat(timespec="seconds")

    lines = [
        "# Security Bug Summary",
        "",
        f"- Source: `{src}`",
        f"- Generated at: `{generated_at}`",
        "- Matching scope: metadata, descriptions, and text reproducers"
        + ("; commit logs included" if include_commit_logs else "; commit logs excluded by default"),
        f"- Corpus: **{corpus_counts['issues']}** issues"
        + (f", **{corpus_counts['pull_requests']}** pull requests" if include_pulls else ""),
        f"- Security-related matches: **{len(issues)}** issues"
        + (f", **{len(pull_requests)}** pull requests" if include_pulls else ""),
        "",
        "## Classification Rules",
        "",
        "An item is counted when its metadata, description, or text reproducer contains an explicit security label/impact, a concrete access-protection or speculative-execution security signal, or side-channel evidence. Side-channel matches are reported as a security subset and may overlap with other security groups. Generic occurrences of words such as `cache`, `secret`, or `security` alone are not sufficient.",
        "",
        "## Match Type Counts",
        "",
        "| Match type | Matches |",
        "|---|---:|",
    ]
    for kind, count in kind_counts.most_common():
        lines.append(f"| {kind} | {count} |")

    lines.extend(["", "## Keyword Group Counts", "", "| Group | Matches |", "|---|---:|"])
    for group, count in group_counts.most_common():
        lines.append(f"| {md_escape(group)} | {count} |")

    for heading, counter in (
        ("State Counts", state_counts),
        ("Year Counts", year_counts),
        ("Author Counts", author_counts),
    ):
        lines.extend(["", f"## {heading}", "", "| Value | Matches |", "|---|---:|"])
        items = sorted(counter.items(), reverse=True) if heading == "Year Counts" else counter.most_common()
        for value, count in items:
            lines.append(f"| {md_escape(value)} | {count} |")

    def append_table(title: str, entries: list[SecurityBug], item_name: str) -> None:
        lines.extend(
            [
                "",
                f"## {title}",
                "",
                f"| {item_name} | State | Created | Author | Match types | Groups | Evidence files | Title |",
                "|---:|---|---|---|---|---|---:|---|",
            ]
        )
        for bug in entries:
            link = f"[#{bug.number}]({bug.url})" if bug.url else f"#{bug.number}"
            lines.append(
                "| "
                + " | ".join(
                    [
                        link,
                        md_escape(bug.state or "-"),
                        md_escape(bug.created or "-"),
                        md_escape(bug.author or "-"),
                        md_escape(", ".join(bug.kinds)),
                        md_escape(", ".join(bug.groups)),
                        str(len(bug.evidence_files)),
                        md_escape(bug.title or "-"),
                    ]
                )
                + " |"
            )

    append_table("Matched Issues", issues, "Issue")
    if include_pulls:
        append_table("Matched Pull Requests", pull_requests, "PR")
    lines.append("")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines), encoding="utf-8")


def parse_args() -> argparse.Namespace:
    default_root = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--src", type=Path, default=default_root / "xiangshan-bugs-src")
    parser.add_argument("--output", type=Path, default=default_root / "security-summary.md")
    parser.add_argument("--include-pulls", action="store_true")
    parser.add_argument("--include-commit-logs", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not args.src.exists():
        raise SystemExit(f"source directory not found: {args.src}")
    bugs, corpus_counts = collect(args.src, args.include_pulls, args.include_commit_logs)
    write_markdown(
        args.output,
        bugs,
        corpus_counts,
        args.src,
        args.include_pulls,
        args.include_commit_logs,
    )
    print(f"wrote {args.output}")
    print(f"matched {sum(not bug.is_pull_request for bug in bugs)} issues")
    if args.include_pulls:
        print(f"matched {sum(bug.is_pull_request for bug in bugs)} pull requests")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
