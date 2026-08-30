#!/usr/bin/env python3
"""Summarize bugs likely caused by microarchitectural logic."""

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

MICRO_ARCH_GROUPS: dict[str, list[str]] = {
    "pipeline/control": [
        r"\bmicroarchitectur(al|e)\b",
        r"\bpipeline\b",
        r"\bflush(ed)?\b",
        r"\bredirect(ed|ion)?\b",
        r"\bwrong[- ]path\b",
        r"\bstale\b",
        r"\bstate machine\b",
        r"\bpriority\b",
    ],
    "forward progress": [
        r"\bdeadlock(s|ed)?\b",
        r"\bhang(s|ed)?\b",
        r"\bstall(s|ed)?\b",
        r"\breplay\b",
        r"\bresource(s)?\b",
    ],
    "queue/buffer": [
        r"\bqueue\b",
        r"\bbuffer\b",
        r"\bentry state\b",
        r"\bvalid[- ]?ready\b",
        r"\bforward(ing)?\b",
        r"\barbit(er|ration)\b",
    ],
    "pipeline stage": [
        r"\bwriteback\b",
        r"\bcommit stage\b",
        r"\bissue queue\b",
        r"\brename\b",
        r"\bdispatch\b",
        r"\bfetch\b",
    ],
    "prediction": [
        r"\bpredict(ion|or|ed)?\b",
        r"\btrain(ing)?\b",
        r"\bSRAM\b",
    ],
    "memory/protection": [
        r"\bTLB\b",
        r"\bPMP\b",
        r"\bPMA\b",
        r"\bCSR\b",
        r"\bcache\b",
        r"\bmiss\b",
        r"\bhit\b",
        r"\buncache\b",
        r"\bcross[- ]?page\b",
        r"\bcross[- ]?16B\b",
    ],
    "data correctness": [
        r"\bmask\b",
        r"\btruncate(d|ion)?\b",
        r"\boverflow\b",
    ],
    "root-cause note": [
        r"\broot cause\b",
    ],
}

MODULE_PATTERNS: list[tuple[str, str]] = [
    ("Frontend/BPU", r"\b(BPU|uTage|TAGE|ITTAGE|BTB|UBTB|FTB|FTQ|RAS|URAS|SC|ITTage|MainBTB|ABTB|predictor)\b"),
    ("Frontend/IFU", r"\b(IFU|ICache|InstrUncache|fetch|predecoder|predecode)\b"),
    ("Backend/ROB", r"\b(ROB|BusyTable|issue queue|IssueQueue|IQ|Rename|Dispatch)\b"),
    ("Backend/CSR/Trap", r"\b(NewCSR|CSR|Trap|TrapHandle|xRET|MRET|MNRET|DRET|mcause|mtval|mepc|vstart|interrupt|NMI|WFI)\b"),
    ("Backend/Execution", r"\b(Alu|ALU|BJU|JumpUnit|Mul|Div|FPU|VPU|Vector|VF|VPerm|VectorFloat|CVT|AtomicsUnit)\b"),
    ("Memory/LSU", r"\b(LSU|LSQ|LoadUnit|StoreUnit|LoadQueue|StoreQueue|VSegmentUnit|AtomicsUnit|UnalignQueue|Sbuffer|sbuffer)\b"),
    ("Memory/Cache", r"\b(DCache|L1|L2|MSHR|MissQueue|ProbeQueue|ReleaseQueue|cache|prefetch|L1PF)\b"),
    ("Memory/MMU", r"\b(MMU|TLB|DTLB|ITLB|PTW|PMP|PMA|PBMT|satp|hgatp|vsatp|page table)\b"),
    ("Interconnect/SoC", r"\b(TileLink|CHI|AXI|NoC|bus|interconnect|IMSIC|IOPMP|PLIC)\b"),
]

NON_MICRO_ARCH_HINTS = [
    r"\bdocumentation\b",
    r"\btypo in docs\b",
    r"\bbuild script\b",
    r"\bcompiler\b",
    r"\bready-to-run\b",
    r"\bnewest ref\b",
    r"\bNEMU.*misses\b",
    r"\bSpike.*bug\b",
]

TERM_GROUP_PATTERNS = {
    group: [re.compile(pattern, re.IGNORECASE) for pattern in patterns]
    for group, patterns in MICRO_ARCH_GROUPS.items()
}
TERM_PATTERNS = [
    pattern for patterns in TERM_GROUP_PATTERNS.values() for pattern in patterns
]
NON_MICRO_PATTERNS = [re.compile(pattern, re.IGNORECASE) for pattern in NON_MICRO_ARCH_HINTS]


@dataclass(frozen=True)
class MicroArchBug:
    number: int
    url: str
    state: str
    created: str
    author: str
    module_name: str
    groups: tuple[str, ...]
    evidence_files: tuple[str, ...]
    description: str


def read_text(path: Path, limit: int | None = None) -> str:
    try:
        text = path.read_text(encoding="utf-8", errors="ignore")
    except OSError:
        return ""
    if limit is not None and len(text) > limit:
        return text[:limit]
    return text


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


def module_from_labels(labels: list[str]) -> list[str]:
    modules: list[str] = []
    for label in labels:
        if not label.startswith("module:"):
            continue
        name = label.split(":", 1)[1].strip()
        if name and name not in {"unknown", "documentation", "tool", "other"}:
            modules.append(name)
    return modules


def infer_modules(text: str) -> list[str]:
    modules: list[str] = []
    for module, pattern in MODULE_PATTERNS:
        if re.search(pattern, text, re.IGNORECASE):
            modules.append(module)
    return modules


def matched_groups(text: str) -> list[str]:
    groups: list[str] = []
    for group, patterns in TERM_GROUP_PATTERNS.items():
        if any(pattern.search(text) for pattern in patterns):
            groups.append(group)
    return groups


def has_micro_arch_evidence(text: str, labels: list[str]) -> bool:
    label_modules = module_from_labels(labels)
    if label_modules and any(pattern.search(text) for pattern in TERM_PATTERNS):
        return True
    if any(re.search(pattern, text, re.IGNORECASE) for _, pattern in MODULE_PATTERNS):
        return any(pattern.search(text) for pattern in TERM_PATTERNS)
    return False


def likely_non_micro_arch(title: str, text: str, labels: list[str]) -> bool:
    label_text = " ".join(labels)
    if "type: question" in label_text or "module: documentation" in label_text:
        return True
    haystack = f"{title}\n{text[:4000]}"
    return any(pattern.search(haystack) for pattern in NON_MICRO_PATTERNS)


def extract_description(title: str, description: str) -> str:
    return normalize_sentence(title)


def clean_markdown(text: str) -> str:
    text = re.sub(r"```.*?```", " ", text, flags=re.S)
    text = re.sub(r"<img\b[^>]*>", " ", text, flags=re.I)
    text = re.sub(r"\[[ xX]\]", " ", text)
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", text)
    text = re.sub(r"^#+\s*", "", text, flags=re.M)
    text = re.sub(r"^\s*[-*]\s*", "", text, flags=re.M)
    return text


def normalize_sentence(text: str) -> str:
    text = clean_markdown(text)
    text = re.sub(r"\s+", " ", text).strip()
    text = text.replace(";", ",")
    if len(text) > 260:
        text = text[:257].rstrip() + "..."
    return text


def md_escape(value: str) -> str:
    return value.replace("|", r"\|").replace("\n", " ")


def collect(src: Path, include_pulls: bool, include_commit_logs: bool) -> list[MicroArchBug]:
    bugs: list[MicroArchBug] = []
    for issue_dir in issue_dirs(src):
        metadata_path = issue_dir / "metadata.json"
        if not metadata_path.exists():
            continue
        metadata = json.loads(read_text(metadata_path))
        if metadata.get("is_pull_request") and not include_pulls:
            continue

        labels = list(metadata.get("labels") or [])
        title = metadata.get("title") or ""
        description = read_text(issue_dir / "description.md", limit=1_000_000)
        metadata_text = "\n".join([title, " ".join(labels), metadata.get("url") or ""])
        chunks = [metadata_text, description]
        evidence_files: set[str] = set()
        if matched_groups(metadata_text):
            evidence_files.add("metadata.json")
        if matched_groups(description):
            evidence_files.add(f"{issue_dir.name}/description.md")

        for path in text_files_for_issue(issue_dir, include_commit_logs):
            if path.name == "description.md":
                continue
            path_text = read_text(path, limit=200_000)
            chunks.append(path_text)
            if matched_groups(path_text):
                evidence_files.add(path.relative_to(src).as_posix())
        text = "\n".join(chunks)

        if likely_non_micro_arch(title, text, labels):
            continue
        if not has_micro_arch_evidence(text, labels):
            continue

        modules = module_from_labels(labels) + infer_modules(text)
        module_name = ", ".join(dict.fromkeys(modules)) or "unknown"
        groups = tuple(dict.fromkeys(matched_groups(text)))
        bugs.append(
            MicroArchBug(
                number=int(metadata.get("number") or issue_dir.name.split("-", 1)[1]),
                url=metadata.get("url") or "",
                state=metadata.get("state") or "",
                created=(metadata.get("created_at") or "")[:10],
                author=metadata.get("author") or "",
                module_name=module_name,
                groups=groups,
                evidence_files=tuple(sorted(evidence_files)),
                description=extract_description(title, description),
            )
        )

    return sorted(bugs, key=lambda bug: bug.number, reverse=True)


def write_markdown(output: Path, bugs: list[MicroArchBug], src: Path) -> None:
    generated_at = datetime.now(timezone.utc).isoformat(timespec="seconds")
    group_counts: Counter = Counter()
    state_counts: Counter = Counter()
    author_counts: Counter = Counter()
    year_counts: Counter = Counter()

    for bug in bugs:
        state_counts[bug.state or "-"] += 1
        author_counts[bug.author or "-"] += 1
        if bug.created:
            year_counts[bug.created[:4]] += 1
        for group in bug.groups:
            group_counts[group] += 1

    lines = [
        "# Micro-Architecture Bug Summary",
        "",
        f"- Source: `{src}`",
        f"- Generated at: `{generated_at}`",
        f"- Micro-architecture caused issues: **{len(bugs)}**",
        "- Format: Markdown table",
        "",
        "## Keyword Group Counts",
        "",
        "| Group | Matches |",
        "|---|---:|",
    ]
    for group, count in group_counts.most_common():
        lines.append(f"| {md_escape(group)} | {count} |")

    lines.extend(["", "## State Counts", "", "| State | Matches |", "|---|---:|"])
    for state, count in state_counts.most_common():
        lines.append(f"| {md_escape(state)} | {count} |")

    lines.extend(["", "## Author Counts", "", "| Author | Matches |", "|---|---:|"])
    for author, count in author_counts.most_common():
        lines.append(f"| {md_escape(author)} | {count} |")

    lines.extend(["", "## Year Counts", "", "| Year | Matches |", "|---|---:|"])
    for year, count in sorted(year_counts.items(), reverse=True):
        lines.append(f"| {md_escape(year)} | {count} |")

    lines.extend(
        [
            "",
            "## Matched Issues",
            "",
            "| Issue | State | Created | Author | Module | Groups | Evidence files | Title |",
            "|---:|---|---|---|---|---|---:|---|",
        ]
    )
    for bug in bugs:
        lines.append(
            "| "
            + " | ".join(
                [
                    f"[#{bug.number}]({bug.url})" if bug.url else f"#{bug.number}",
                    md_escape(bug.state or "-"),
                    md_escape(bug.created or "-"),
                    md_escape(bug.author or "-"),
                    md_escape(bug.module_name or "-"),
                    md_escape(", ".join(bug.groups) or "-"),
                    str(len(bug.evidence_files)),
                    md_escape(bug.description or "-"),
                ]
            )
            + " |"
        )
    lines.append("")
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n".join(lines), encoding="utf-8")

def parse_args() -> argparse.Namespace:
    default_root = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--src", type=Path, default=default_root / "xiangshan-bugs-src")
    parser.add_argument("--output", type=Path, default=default_root / "micro-arch-summary.md")
    parser.add_argument("--include-pulls", action="store_true")
    parser.add_argument("--include-commit-logs", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if not args.src.exists():
        raise SystemExit(f"source directory not found: {args.src}")
    bugs = collect(args.src, args.include_pulls, args.include_commit_logs)
    write_markdown(args.output, bugs, args.src)
    print(f"wrote {args.output}")
    print(f"matched {len(bugs)} issues")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
