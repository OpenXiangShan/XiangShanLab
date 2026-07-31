#!/usr/bin/env python3
"""Generate a two-dimensional XiangShan issue classification report."""

from __future__ import annotations

import argparse
import json
import re
from collections import Counter, defaultdict
from pathlib import Path
from typing import Any


ARCHITECTURE_RULES: list[tuple[str, tuple[str, ...]]] = [
    ("RVV 向量扩展", ("vector", "rvv", "vstart", "vtype", "vset", "vle", "vse", "vlseg", "vsseg", "vfirst", "vcompress")),
    ("F/D 浮点扩展", ("float", "fpu", "fp ", "fadd", "fsub", "fmul", "fdiv", "fsqrt", "fcvt", "flw", "fld", "fsw", "fsd", "fflags", "frm")),
    ("H/AIA 与虚拟化中断扩展", ("hypervisor", "hstatus", "hgatp", "vsatp", "vs-mode", "vs mode", "vgein", "imsic", "aia", "hvip", "hvip", "hlv.", "hsv.")),
    ("虚拟内存、PMP/PMA 与地址翻译", ("mmu", "tlb", "ptw", "page fault", "pagefault", "pmp", "pma", "satp", "sv39", "virtual address", "canonical address", "unmapped")),
    ("Load/Store 与 RISC-V 内存语义", ("memory ordering", "fence", "misaligned", "unaligned", "address fault", "access fault", "uncache", "load instruction", "store instruction")),
    ("C 压缩指令扩展", ("compressed instruction", "compressed extension", "rvc", "c extension")),
    ("特权架构、CSR、异常与中断", ("csr", "mstatus", "sstatus", "mcause", "scause", "mepc", "sepc", "mtval", "stval", "trap", "exception", "interrupt", "privilege", "ecall", "ebreak", "illegal instruction", "wfi", "sret", "mret")),
    ("M 乘除法扩展", ("mulh", "mulw", "divw", "remw", "multiply instruction", "divide instruction", "multiplication", "division", "divider")),
    ("标量整数、控制流与基础 ISA", ("branch", "jump", "jal", "jalr", "auipc", "lui", "shift", "sign extension", "zero extension", "instruction decode", "opcode")),
]

MICROARCHITECTURE_RULES: list[tuple[str, tuple[str, ...]]] = [
    ("浮点与向量执行单元", ("vsegmentunit", "vector", "rvv", "vle", "vse", "vlseg", "vsseg", "vstart", "fpu", "float", "hardfloat")),
    ("MMU：TLB、PTW、PMP/PMA 与权限检查", ("mmu", "tlb", "ptw", "pmp", "pma", "satp", "hgatp", "vsatp", "page fault", "unmapped", "canonical address")),
    ("访存流水线：LSQ、Load/Store、StoreBuffer 与转发", ("lsq", "loadunit", "storeunit", "load unit", "store unit", "storebuffer", "store buffer", "forward", "memory dependence", "amo", "atomic", "store access fault", "load access fault")),
    ("Cache 与一致性：DCache、L2、MSHR、预取与 NoC", ("module: memory", "dcache", "cache", "mshr", "l2", "huancun", "coherence", "prefetch", "noc", "uncache")),
    ("CSR、异常、中断与 AIA/IMSIC", ("csr", "trap", "exception", "interrupt", "mcause", "scause", "mepc", "sepc", "imsic", "aia", "vstopei", "mstatus", "sstatus")),
    ("前端：取指、分支预测、FTQ、ICache 与译码", ("module: frontend", "frontend", "ifu", "icache", "ftq", "fetch", "predictor", "bpu", "btb", "ras", "tage", "decoder", "decode", "ibuffer")),
    ("后端：Rename、Dispatch、ROB、提交与控制恢复", ("module: backend", "rob", "rename", "dispatch", "checkpoint", "redirect", "flush", "backend")),
    ("调度与标量执行单元", ("issue queue", "scheduler", "alu", "mul", "div", "execution unit", "writeback", "wakeup")),
    ("验证、Difftest、NEMU 与测试环境", ("module: tool", "difftest", "nemu", "golden", "reference model", "test", "cputest", "coremark", "regression", "assert")),
    ("构建、CI、配置与文档", ("module: documentation", "build", "compile", "make", "mill", "sbt", "verilator", "ci", "toolchain", "readme", "documentation", "config")),
]

ARCHITECTURE_FALLBACK = "非 ISA 功能问题或证据不足"
MICROARCHITECTURE_FALLBACK = "未能从 issue 文本定位微架构模块"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--issues", default="xiangshan-bug-lib/issues.jsonl")
    parser.add_argument("--metadata", default="xiangshan-bug-lib/README.md")
    parser.add_argument("--out", default="bugs-analysis-report.md")
    return parser.parse_args()


def normalized_text(issue: dict[str, Any]) -> str:
    body = issue.get("body") or ""
    description = re.search(r"^#{1,6}\s*(?:describe (?:the )?bug|bug description|问题描述)\s*$([\s\S]*)", body, flags=re.IGNORECASE | re.MULTILINE)
    if description:
        body = description.group(1)
    return "\n".join((issue.get("title") or "", body, " ".join(issue.get("labels") or []))).lower()


def classify(text: str, rules: list[tuple[str, tuple[str, ...]]], fallback: str) -> tuple[str, str]:
    for category, terms in rules:
        if any(contains_term(text, term) for term in terms):
            return category, "高"
    if text.strip():
        return fallback, "低"
    return fallback, "低"


def contains_term(text: str, term: str) -> bool:
    if re.fullmatch(r"[a-z0-9]+", term):
        return re.search(rf"\b{re.escape(term)}\b", text) is not None
    return term in text


def classify_architecture(issue: dict[str, Any], text: str) -> tuple[str, str]:
    title = (issue.get("title") or "").lower()
    if re.search(r"\bamo\b|\blr\.\w+|\bsc\.\w+", title):
        return "A 原子扩展", "高"
    return classify(text, ARCHITECTURE_RULES, ARCHITECTURE_FALLBACK)


def classify_microarchitecture(issue: dict[str, Any], text: str) -> tuple[str, str]:
    title = (issue.get("title") or "").lower()
    title_rules = [
        ("浮点与向量执行单元", ("vsegmentunit", "vector", "rvv", "vle", "vse", "vlseg", "vsseg", "vstart", "fpu", "float", "hardfloat")),
        ("访存流水线：LSQ、Load/Store、StoreBuffer 与转发", ("newloadunit", "loadunit", "storeunit", "lsq", "store buffer", "storebuffer", "store-to-load", "amo")),
        ("Cache 与一致性：DCache、L2、MSHR、预取与 NoC", ("dcache", "l2", "mshr", "huancun", "coherence", "prefetch", "noc")),
        ("MMU：TLB、PTW、PMP/PMA 与权限检查", ("mmu", "tlb", "ptw", "pmp", "pma", "satp", "hgatp", "vsatp", "canonical address", "page fault", "unmapped")),
        ("CSR、异常、中断与 AIA/IMSIC", ("imsic", "aia", "vstopei", "csr", "mcause", "scause", "mepc", "sepc", "interrupt")),
        ("前端：取指、分支预测、FTQ、ICache 与译码", ("frontend", "ifu", "icache", "ftq", "fetch", "predictor", "bpu", "btb", "ras", "tage", "decoder", "decode", "ibuffer")),
        ("后端：Rename、Dispatch、ROB、提交与控制恢复", ("rob", "rename", "dispatch", "checkpoint", "redirect", "flush", "backend")),
    ]
    for category, terms in title_rules:
        if any(contains_term(title, term) for term in terms):
            return category, "高"
    return classify(text, MICROARCHITECTURE_RULES, MICROARCHITECTURE_FALLBACK)


def bug_status(labels: list[str]) -> str:
    lower_labels = {label.lower() for label in labels}
    if "type: bug/confirmed" in lower_labels:
        return "已确认 bug"
    if "type: bug/fixed" in lower_labels:
        return "已修复 bug"
    if "type: bug/reported" in lower_labels:
        return "已报告 bug"
    if any(label.startswith("type: bug/") for label in lower_labels):
        return "已关闭/无效 bug 报告"
    if any(label.startswith("type: question") for label in lower_labels):
        return "问答"
    if any(label.startswith("type: problem") for label in lower_labels):
        return "问题求助"
    if any(label.startswith("type: feature") for label in lower_labels):
        return "功能请求"
    return "未标注类型"


def summary(body: str, title: str) -> str:
    description = re.search(r"^#{1,6}\s*(?:describe (?:the )?bug|bug description|问题描述)\s*$([\s\S]*)", body, flags=re.IGNORECASE | re.MULTILINE)
    text = description.group(1) if description else body
    text = re.sub(r"```.*?```", "", text, flags=re.DOTALL)
    text = re.sub(r"!\[[^\]]*\]\([^)]*\)", "", text)
    lines = [re.sub(r"^[#>*\-\s]+", "", line).strip() for line in text.splitlines()]
    lines = [line for line in lines if line and not line.startswith("http")]
    if not lines:
        return title
    candidate = re.sub(r"\s+", " ", lines[0])
    return candidate[:180] + ("…" if len(candidate) > 180 else "")


def escape(value: str) -> str:
    return value.replace("|", "\\|").replace("\n", " ")


def issue_reference(issue: dict[str, Any]) -> str:
    return f"[#{issue['number']}]({issue['html_url']}) {escape(issue['title'])}"


def read_metadata(path: Path) -> dict[str, str]:
    fields: dict[str, str] = {}
    if not path.exists():
        return fields
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.match(r"- ([^:]+): `(.*)`", line)
        if match:
            fields[match.group(1)] = match.group(2)
    return fields


def main() -> int:
    args = parse_args()
    issues_path = Path(args.issues)
    issues = [json.loads(line) for line in issues_path.read_text(encoding="utf-8").splitlines() if line]
    issues.sort(key=lambda issue: issue["number"], reverse=True)
    metadata = read_metadata(Path(args.metadata))

    architecture_groups: dict[str, list[dict[str, Any]]] = defaultdict(list)
    microarchitecture_groups: dict[str, list[dict[str, Any]]] = defaultdict(list)
    status_counts: Counter[str] = Counter()
    for issue in issues:
        text = normalized_text(issue)
        issue["architecture"], issue["architecture_confidence"] = classify_architecture(issue, text)
        issue["microarchitecture"], issue["microarchitecture_confidence"] = classify_microarchitecture(issue, text)
        issue["status_class"] = bug_status(issue.get("labels") or [])
        issue["summary"] = summary(issue.get("body") or "", issue["title"])
        architecture_groups[issue["architecture"]].append(issue)
        microarchitecture_groups[issue["microarchitecture"]].append(issue)
        status_counts[issue["status_class"]] += 1

    lines = [
        "# XiangShan GitHub Issue Bug 分类报告",
        "",
        "## 采集范围与方法",
        "",
        f"- 数据源：`OpenXiangShan/XiangShan` 的 GitHub **非 PR issue**，共 **{len(issues)}** 条。",
        f"- 采集状态：`{metadata.get('Issue state', 'all')}`；采集时间见 `xiangshan-bug-lib/README.md`。",
        "- 分类证据：issue 的标题、正文和 GitHub 标签。每条 issue 各分配一个**主架构类**和一个**主微架构类**；这些是主题归属，不等同于已由 RTL 波形验证的根因。",
        "- “非 ISA 功能问题或证据不足”与“未能从 issue 文本定位微架构模块”是保守桶：包含构建、文档、问题求助、功能请求以及描述不足的报告。",
        "",
        "### Issue 类型概览",
        "",
        "| 类型 | 数量 |",
        "| --- | ---: |",
    ]
    lines.extend(f"| {status} | {count} |" for status, count in status_counts.most_common())

    lines.extend(("", "## 1. 提取到的 Issue 简单描述", "", "下表覆盖全部采集到的非 PR issue。`Issue 类型`来自标签；两个分类列是本报告的主类归属。", "", "| Issue | 状态 | Issue 类型 | 简单描述 | 架构分类 | 微架构分类 |", "| --- | --- | --- | --- | --- | --- |"))
    for issue in issues:
        lines.append(
            f"| [#{issue['number']}]({issue['html_url']}) | {issue['state']} | {issue['status_class']} | "
            f"{escape(issue['title'])} — {escape(issue['summary'])} | {issue['architecture']}（{issue['architecture_confidence']}） | "
            f"{issue['microarchitecture']}（{issue['microarchitecture_confidence']}） |"
        )

    lines.extend(("", "## 2. 按 RISC-V 架构分类", "", "架构分类优先根据明确的 ISA 扩展、特权行为和内存语义关键词归属。", "", "| 架构类 | 数量 | Issue |", "| --- | ---: | --- |"))
    for category, group in sorted(architecture_groups.items(), key=lambda item: (-len(item[1]), item[0])):
        references = "<br>".join(issue_reference(issue) for issue in group)
        lines.append(f"| {category} | {len(group)} | {references} |")

    lines.extend(("", "## 3. 按 XiangShan 微架构分类", "", "微架构分类优先使用明确模块标签和模块/信号名称；没有模块证据时不臆测具体硬件单元。", "", "| 微架构类 | 数量 | Issue |", "| --- | ---: | --- |"))
    for category, group in sorted(microarchitecture_groups.items(), key=lambda item: (-len(item[1]), item[0])):
        references = "<br>".join(issue_reference(issue) for issue in group)
        lines.append(f"| {category} | {len(group)} | {references} |")

    lines.extend(("", "## 使用说明", "", "- 需要确认具体 RTL 根因时，应以对应 issue 的复现、提交关联、仿真日志、波形及源码为准。", "- 本报告保留了所有 issue，而不只保留带 `bug` 标签的条目；因此统计反映 issue 主题分布，不应直接解释为已确认硬件缺陷数量。", ""))
    Path(args.out).write_text("\n".join(lines), encoding="utf-8")
    print(f"Wrote {len(issues)} issue classifications to {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
