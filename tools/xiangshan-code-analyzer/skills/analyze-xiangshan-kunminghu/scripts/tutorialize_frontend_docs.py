#!/usr/bin/env python3
"""Turn the Mem-MDP-shaped Frontend docs into tutorial-oriented Markdown."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


TARGETS = [
    "Frontend-Bim.md",
    "Frontend-BPU.md",
    "Frontend-FauFTB.md",
    "Frontend-FTB.md",
    "Frontend-FTQ.md",
    "Frontend-IBuffer.md",
    "Frontend-ICache.md",
    "Frontend-ITTAGE.md",
    "Frontend-RAS.md",
    "Frontend-SC.md",
    "Frontend-Tage.md",
    "Frontend-IFU-and-Predecode-Deep-Dive.md",
    "Frontend-Overview-and-End-to-End-Signal-Analysis.md",
]

PLACEHOLDERS = {
    "## 3. Theory-to-Code Mapping": "本节的学习方法是：先读第 2 节的文件和行号，再把每个理论概念绑定到具体模块、输入、状态寄存器、握手和消费者；无法在源码中定位的设计意图必须留在第 18 节的差异说明中。",
    "## 5. Microarchitecture Parameters": "先从源码证据读取表深度、队列容量、位宽、端口数和配置开关，再判断它们对吞吐、冲突和恢复延迟的影响；不要用文档中的默认值替代当前 commit 的参数。",
    "## 7. 为什么模块存在": "把模块放回 Frontend 全链路理解：它解决的是预测带宽、取指正确性、存储层次延迟、投机恢复或上下游速率不匹配中的至少一个问题。",
    "## 8. 有效动态路径": "按 `valid -> ready -> fire -> register/state update -> consumer` 阅读动态路径，并同时检查正常、阻塞、flush、redirect、replay 和恢复后的 forward progress。",
    "## 9. Index 和地址/历史计算": "地址、PC、折叠历史、tag、set/way、line offset 和 FTQ offset 都必须追到源码表达式；索引冲突、回绕和跨边界情况在算法和验证章节中继续展开。",
    "## 11. 状态和存储结构": "把每个表、栈、FIFO、MSHR、uncache entry 和 pipeline register 记录为 `valid/full/empty/ready` 可观察状态，并说明谁写入、谁读取、何时清空以及满/空时谁被反压。",
    "## 12. Pipeline stage 分析": "阶段说明只使用源码中的寄存器、valid/ready 和 fire 条件；对 Frontend 使用 F0/F1/F2/F3，对 Backend 使用实际 Decode/Rename/Dispatch/Issue/Execute/Writeback/ROB 边界。",
    "## 13. Control path rationale": "控制路径按优先级阅读：reset、flush、backend redirect、BPU override、exception、replay 和正常 fire 发生冲突时，必须以源码条件顺序说明胜负关系。",
    "## 15. 异常、debug、privilege": "区分预测错误、replay、page/access/guest fault、MMIO side effect、debug redirect 和架构异常；说明异常产生者、优先级、清理对象、恢复入口和提交可见性。",
    "## 16. CSR 控制": "必须列出前端分支预测器使能控制链：`sbpctl` CSR 字段、`CustomCSRCtrlIO.bp_ctrl`、Frontend `bpu.io.ctrl`、BPU 子预测器 `io.enable`，并明确 `fallThrough`、`MicroTage`、`MicroRas` 固定使能以及 Constantin override 路径。",
    "## 19. 动态场景示例": "每个场景按 `stimulus -> producer -> transform/state -> consumer -> observation -> recovery` 展开，至少覆盖正常路径、资源阻塞、预测/数据冲突、redirect/flush 和恢复后的前向进展。",
}


HEADING_RE = re.compile(r'^(#{2,6})\s+(.*)$')


def renumber_headings(text: str) -> str:
    lines = text.splitlines()
    output: list[str] = []
    counters = [0, 0, 0, 0, 0]
    in_code = False
    for line in lines:
        if line.startswith("```"):
            in_code = not in_code
            output.append(line)
            continue
        if not in_code:
            m = HEADING_RE.match(line)
            if m:
                hashes, title = m.groups()
                level = len(hashes) - 1
                if 1 <= level <= 5:
                    counters[level - 1] += 1
                    for idx in range(level, len(counters)):
                        counters[idx] = 0
                    clean = re.sub(r'^\d+(?:\.\d+)*\.?\s*', '', title).strip()
                    nums = '.'.join(str(counters[idx]) for idx in range(level))
                    line = f'{hashes} {nums}. {clean}'
        output.append(line)
    return "\n".join(output).rstrip() + "\n"


def transform(text: str) -> str:
    if "<!-- frontend-tutorial-generated-by-analyze-xiangshan-kunminghu -->" in text:
        return renumber_headings(text)
    lines = text.splitlines()
    output: list[str] = []
    current_top = ""
    tutorial_note_added = False
    for line in lines:
        if line.startswith("## "):
            current_top = line

        if line == "<!-- frontend-max-collection-mem-mdp-layout -->":
            output.append("<!-- frontend-tutorial-generated-by-analyze-xiangshan-kunminghu -->")
            continue
        if line.startswith("> 目录结构按 `Mem-MDP.md`"):
            output.append("> 本文按 `Mem-MDP.md` 的统一目录组织为教程：先建立模块边界，再阅读源码证据、动态路径、算法、状态、跨边界行为和验证方法。")
            output.append("> 所有实现结论均限定在 `kunminghu-v2` commit `52262f303fc06daf84cdab7011d59b7df65ce7e8`；Design Doc 结论必须回到第 18 节的源码追溯矩阵。")
            tutorial_note_added = True
            continue
        if line.startswith("### 原章节："):
            output.append("### " + line[len("### 原章节："):])
            continue
        if line == "### 验证特别注意":
            output.append("### 验证矩阵与通用判定原则")
            continue
        if line.startswith("本节保存"):
            continue
        if "本模块原文没有单独的本节标题" in line:
            output.append(PLACEHOLDERS.get(current_top, "本节将这一类信息直接落到当前模块的源码、信号和验证观察点上；若模块不拥有该状态，则沿接口追踪到真实拥有者。"))
            continue
        output.append(line)

    if not tutorial_note_added:
        raise ValueError("expected Mem-MDP layout marker")
    return renumber_headings("\n".join(output))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    directory = args.directory.expanduser().resolve()
    for filename in TARGETS:
        path = directory / filename
        path.write_text(transform(path.read_text(encoding="utf-8")), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
