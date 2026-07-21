#!/usr/bin/env python3
"""Build module-based bug summaries from collected XiangShan issue/PR JSONL."""

from __future__ import annotations

import argparse
import json
import re
from collections import defaultdict
from pathlib import Path
from typing import Any


MODULE_ORDER = ("frontend", "backend", "mem", "cache", "chiselAIA", "chiselIOPMP")

TRIGGER_RULES: dict[str, tuple[str, ...]] = {
    "exception": (
        r"\bexception\b",
        r"\bfault\b",
        r"\bpage\s*fault\b",
        r"\baccess\s*fault\b",
        r"\billegal\s*instruction\b",
        r"\btrap\b",
        r"\bmcause\b",
        r"\bscause\b",
        r"\bmtval\b",
        r"\bstval\b",
        r"\bmtval2\b",
        r"\bxtval\b",
        r"\bxepc\b",
        r"\bmepc\b",
        r"\bsepc\b",
        r"\bdret\b",
        r"\bmret\b",
        r"\bsret\b",
        r"\bex_[a-z0-9_]+\b",
        r"异常",
        r"陷入",
        r"页错误",
        r"访问错误",
        r"非法指令",
    ),
    "interrupt": (
        r"\binterrupt\b",
        r"\bintr\b",
        r"\birq\b",
        r"\bnmi\b",
        r"\bmip\b",
        r"\bmie\b",
        r"\bmsip\b",
        r"\bmeip\b",
        r"\bmtip\b",
        r"\bsei\b",
        r"\bstip\b",
        r"\bvsei\b",
        r"\bvstopi\b",
        r"\bstopi\b",
        r"\bmtopi\b",
        r"\bhvictl\b",
        r"\baia\b",
        r"\bwfi\b",
        r"中断",
    ),
}

MODULE_RULES: dict[str, tuple[str, ...]] = {
    "frontend": (
        r"\bfrontend\b",
        r"\bfront[- ]?end\b",
        r"\bifu\b",
        r"\bfetch\b",
        r"\bdecode\b",
        r"\bftq\b",
        r"\bbpu\b",
        r"\bbtb\b",
        r"\btage\b",
        r"\bras\b",
        r"\bittage\b",
        r"\butage\b",
        r"\bpredecode\b",
        r"src/main/scala/xiangshan/frontend",
        r"module:\s*frontend",
    ),
    "backend": (
        r"\bbackend\b",
        r"\bback[- ]?end\b",
        r"\bcsr\b",
        r"\brob\b",
        r"\brename\b",
        r"\bscheduler\b",
        r"\btrap\b",
        r"\bintr\b",
        r"\binterrupt\b",
        r"\bfpu\b",
        r"\balu\b",
        r"\bfu\b",
        r"\bvector\b",
        r"\brvv\b",
        r"\bvset",
        r"src/main/scala/xiangshan/backend",
        r"module:\s*backend",
    ),
    "mem": (
        r"\bmem\b",
        r"\bmemblock\b",
        r"\bload\b",
        r"\bstore\b",
        r"\blsq\b",
        r"\bldq\b",
        r"\bsq\b",
        r"\bmmio\b",
        r"\buncache\b",
        r"\bpma\b",
        r"\bpmp\b",
        r"\btlb\b",
        r"\bptw\b",
        r"\bmmu\b",
        r"\bpage\s*fault\b",
        r"\bgpf\b",
        r"\bhfence\b",
        r"src/main/scala/xiangshan/mem",
        r"module:\s*memory",
    ),
    "cache": (
        r"\bcache\b",
        r"\bdcache\b",
        r"\bicache\b",
        r"\bl1\b",
        r"\bl2\b",
        r"\bllc\b",
        r"\bmshr\b",
        r"\bcoupledl2\b",
        r"\bopenllc\b",
        r"\bprefetcher\b",
        r"\bcmo\b",
        r"\bcbo\.",
        r"src/main/scala/xiangshan/cache",
        r"src/main/scala/xiangshan/mem/cache",
    ),
    "chiselAIA": (
        r"\bchiselaia\b",
        r"chisel-aia",
        r"\baia\b",
        r"\baplic\b",
        r"\bimsic\b",
    ),
    "chiselIOPMP": (
        r"\bchiseliopmp\b",
        r"\biopmp\b",
        r"chisel-iopmp",
        r"chiseliopmp",
    ),
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bug_dir", type=Path, help="directory containing issues.jsonl and pulls.jsonl")
    parser.add_argument("--out", type=Path, help="output directory; defaults to BUG_DIR/bug-summarys")
    parser.add_argument("--limit", type=int, help="maximum rows per module table; defaults to all rows")
    parser.add_argument("--biweekly-notes", type=Path, help="biweekly bug notes JSON; defaults to BUG_DIR/biweekly-bug-notes.json")
    return parser.parse_args()


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    if not path.exists():
        return rows
    with path.open(encoding="utf-8") as file:
        for line in file:
            line = line.strip()
            if line:
                rows.append(json.loads(line))
    return rows


def markdown_escape(text: Any) -> str:
    return str(text or "").replace("|", "\\|").replace("\n", " ")


def read_biweekly_notes(path: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    return json.loads(path.read_text(encoding="utf-8"))


def biweekly_by_module(notes: list[dict[str, Any]]) -> dict[str, list[dict[str, Any]]]:
    buckets: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for note in notes:
        for module in note.get("modules") or []:
            if module in MODULE_ORDER:
                buckets[module].append(note)
    return buckets


def biweekly_ref_links(note: dict[str, Any]) -> str:
    links = []
    for ref in note.get("refs") or []:
        text = ref.get("text") or (f"#{ref.get('number')}" if ref.get("number") else ref.get("repo") or "link")
        url = ref.get("url") or ""
        links.append(f"[{markdown_escape(text)}]({url})" if url else markdown_escape(text))
    return ", ".join(links)


def normalized_text(row: dict[str, Any]) -> str:
    labels = " ".join(row.get("labels") or [])
    return "\n".join(
        [
            row.get("title") or "",
            row.get("body") or "",
            labels,
            row.get("head") or "",
            row.get("base") or "",
        ]
    ).lower()


def focused_text(row: dict[str, Any]) -> str:
    labels = " ".join(row.get("labels") or [])
    return "\n".join(
        [
            row.get("title") or "",
            labels,
            row.get("head") or "",
            row.get("base") or "",
        ]
    ).lower()


def matches_module(module: str, row: dict[str, Any]) -> bool:
    text = normalized_text(row)
    if module != "chiselAIA":
        return any(re.search(pattern, text, flags=re.IGNORECASE) for pattern in MODULE_RULES[module])

    strong_patterns = MODULE_RULES[module]
    focused = focused_text(row)
    if any(re.search(pattern, focused, flags=re.IGNORECASE) for pattern in strong_patterns):
        return True

    # AIA CSR names often appear in long CSR dumps inside unrelated vector/backend issues.
    # Treat them as ChiselAIA evidence only when they are in the title, labels, or branch names.
    focused_patterns = (
        r"\bmtopi\b",
        r"\bstopi\b",
        r"\bvstopi\b",
        r"\bhvictl\b",
        r"\bsireg\b",
        r"\bsiprios\b",
        r"\bvsei\b",
    )
    if any(re.search(pattern, focused, flags=re.IGNORECASE) for pattern in focused_patterns):
        return True

    # Full-body AIA matches need real AIA context, not incidental submodule status text.
    aia_context_patterns = (
        r"\baia\s+spec\b",
        r"\baplic\b",
        r"\bimsic\b",
    )
    return any(re.search(pattern, text, flags=re.IGNORECASE) for pattern in aia_context_patterns)


def classify_modules(row: dict[str, Any]) -> list[str]:
    text = normalized_text(row)
    modules = [module for module in MODULE_ORDER if matches_module(module, row)]
    return modules or ["uncategorized"]


def classify_triggers(row: dict[str, Any]) -> list[str]:
    text = normalized_text(row)
    return [
        trigger
        for trigger, patterns in TRIGGER_RULES.items()
        if any(re.search(pattern, text, flags=re.IGNORECASE) for pattern in patterns)
    ]


def item_kind(row: dict[str, Any]) -> str:
    if row.get("type") == "pull":
        return "PR"
    return "Issue"


def item_link(row: dict[str, Any]) -> str:
    return f"[#{row['number']}]({row['html_url']})"


def trigger_text(row: dict[str, Any]) -> str:
    return ", ".join(classify_triggers(row))


def write_module_file(
    path: Path,
    module: str,
    rows: list[dict[str, Any]],
    limit: int | None,
    biweekly_notes: list[dict[str, Any]] | None = None,
) -> None:
    title = module if module == "uncategorized" else f"{module} module"
    exception_count = sum("exception" in classify_triggers(row) for row in rows)
    interrupt_count = sum("interrupt" in classify_triggers(row) for row in rows)
    lines = [
        f"# {title} bug summary",
        "",
        f"- Count: `{len(rows)}`",
        f"- Exception-triggered: `{exception_count}`",
        f"- Interrupt-triggered: `{interrupt_count}`",
        "- Source: `issues.jsonl` and `pulls.jsonl`",
        "- Rule: classified from labels, title, body, branch names, and referenced directory/component names.",
        "- Trigger: `exception` and `interrupt` are highlighted from title/body/labels keywords.",
        "",
        "| Number | Type | State | Updated | Trigger | Labels | Title |",
        "| --- | --- | --- | --- | --- | --- | --- |",
    ]
    for row in sorted(rows, key=lambda item: item.get("updated_at") or "", reverse=True)[:limit] if limit else sorted(rows, key=lambda item: item.get("updated_at") or "", reverse=True):
        labels = ", ".join(row.get("labels") or [])
        lines.append(
            f"| {item_link(row)} | {item_kind(row)} | {markdown_escape(row.get('state'))} | "
            f"{markdown_escape(row.get('updated_at'))} | {markdown_escape(trigger_text(row))} | "
            f"{markdown_escape(labels)} | {markdown_escape(row.get('title'))} |"
        )
    if limit and len(rows) > limit:
        lines.extend(["", f"Only the latest `{limit}` rows are shown."])

    if biweekly_notes:
        lines.extend(
            [
                "",
                "## Biweekly Bug Cause Notes",
                "",
                f"- Count: `{len(biweekly_notes)}`",
                "- Source: official XiangShan English biweekly `Recent Developments` / `Bug fixes` entries.",
                "",
                "| Biweekly | Date | Section | Issue/PR | Bug cause / description | Source |",
                "| --- | --- | --- | --- | --- | --- |",
            ]
        )
        for note in sorted(biweekly_notes, key=lambda item: (item.get("biweekly") or 0, item.get("date") or ""), reverse=True):
            source = f"[#{note.get('biweekly')}]({note.get('source_url')})"
            lines.append(
                f"| {markdown_escape(note.get('biweekly'))} | {markdown_escape(note.get('date'))} | "
                f"{markdown_escape(note.get('section'))} | {biweekly_ref_links(note)} | "
                f"{markdown_escape(note.get('description'))} | {source} |"
            )
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_index(
    out_dir: Path,
    buckets: dict[str, list[dict[str, Any]]],
    total_rows: int,
    biweekly_buckets: dict[str, list[dict[str, Any]]] | None = None,
) -> None:
    lines = [
        "# Bug summaries by modified module",
        "",
        "Modules are based on the XiangShan directory/component split: "
        "`frontend`, `backend`, `mem`, `cache`, `chiselAIA`, `chiselIOPMP`.",
        "",
        f"- Input rows: `{total_rows}`",
        "- Items may appear in multiple modules when the issue/PR spans multiple areas.",
        "- `uncategorized` means no module rule matched.",
        "",
        "| Module | Count | Exception | Interrupt | Biweekly bug notes | Recent examples |",
        "| --- | ---: | ---: | ---: | ---: | --- |",
    ]
    for module in (*MODULE_ORDER, "uncategorized"):
        rows = buckets.get(module, [])
        if module == "uncategorized" and not rows:
            continue
        exception_count = sum("exception" in classify_triggers(row) for row in rows)
        interrupt_count = sum("interrupt" in classify_triggers(row) for row in rows)
        examples = sorted(rows, key=lambda item: item.get("updated_at") or "", reverse=True)[:5]
        links = ", ".join(item_link(item) for item in examples)
        biweekly_count = len((biweekly_buckets or {}).get(module, []))
        lines.append(f"| [{module}]({module}.md) | {len(rows)} | {exception_count} | {interrupt_count} | {biweekly_count} | {links} |")
    (out_dir / "README.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    args = parse_args()
    bug_dir = args.bug_dir
    out_dir = args.out or bug_dir / "bug-summarys"
    out_dir.mkdir(parents=True, exist_ok=True)

    rows = read_jsonl(bug_dir / "issues.jsonl") + read_jsonl(bug_dir / "pulls.jsonl")
    biweekly_path = args.biweekly_notes or bug_dir / "biweekly-bug-notes.json"
    biweekly_notes = read_biweekly_notes(biweekly_path)
    biweekly_buckets = biweekly_by_module(biweekly_notes)
    buckets: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for row in rows:
        for module in classify_modules(row):
            buckets[module].append(row)

    write_index(out_dir, buckets, len(rows), biweekly_buckets)
    for module in (*MODULE_ORDER, "uncategorized"):
        if module == "uncategorized" or module in MODULE_ORDER or buckets.get(module):
            write_module_file(
                out_dir / f"{module}.md",
                module,
                buckets.get(module, []),
                args.limit,
                biweekly_buckets.get(module, []),
            )


if __name__ == "__main__":
    main()
