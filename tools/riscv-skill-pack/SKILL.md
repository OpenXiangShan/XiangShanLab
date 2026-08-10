---
name: riscv-skill-pack
version: "1.0"
updated: "2026-07-21"
description: >
  One-stop RISC-V skill pack covering specification learning, gap analysis,
  requirement analysis, and ecosystem governance. Points to four specialized
  sub-skills under sub-skills/. Activate whenever a task touches RISC-V ISA
  specs, feature gap analysis, CPU requirement management, or upstream Task
  Group governance.
license: Apache-2.0
---

# RISC-V Skill Pack

> **Meta-skill / entry point.** This file is a dispatcher: it tells you which
> sub-skill to load for a given task. The actual workflows live in the four
> files under `sub-skills/`. Always load the relevant sub-skill before
> executing — this file only routes.

---

## 概述 (Overview)

RISC-V **规范学习、Gap 分析、需求分析、治理跟进** 的一站式 skill pack。

The pack consolidates four previously separate skills into a single,
role-aware entry point. Each sub-skill is self-contained and can be loaded
independently. This file helps you pick the right one.

**Design principles**

- **Evidence-first**: every claim must carry a source tag (`[T1-VERIFIED]`,
  `[T2-CROSS-CHECKED]`, or `[UNVERIFIED]`). No improvisation.
- **Redacted by default**: all sub-skills use generic placeholders
  (`MEMBER-XX-XXX`, `ORG-00`, `RVI-XXX`). Replace them with your project's
  actual identifiers before use.
- **Sequential where needed**: `gap-analysis` Part 2 (slides) REQUIRES a
  Part 1 doc as input — never re-derive evidence inside slides.

---

## 功能概览 (Feature Overview)

| Sub-skill | File | Core capability | Typical scenarios |
|-----------|------|-----------------|-------------------|
| **spec-learning** | `sub-skills/spec-learning.md` | ISA spec document navigation (5-layer hierarchy: UDB → Normative → Ratified → post-v0.7 → Un-ratified) + Tier-1 source mapping methodology (RISC-V ↔ Arm/x86) | Looking up a spec/extension/CSR/instruction; verifying ratification status; mapping RISC-V features to Arm/x86 analogues; producing source-backed comparison tables |
| **gap-analysis** | `sub-skills/gap-analysis.md` | Single-feature Gap Analysis doc (9-section markdown, §1 Exec Summary → §9 Revision Log) + strict 3-slides-per-feature PPTX review deck sourced exclusively from the doc | Feature 差距 deep-dive (MMIO Outstanding, HW Profiling, Vectorization, I/D Coherent); red-line conflict resolution; F{N} gap docs; review 评审 PPT |
| **requirement-analysis** | `sub-skills/requirement-analysis.md` | Requirement analysis workflow (spec → feature-mapping CSV with source tags & mapping types) + feature list reference (SoC features, platform requirements, Rx Arch Freeze) + requirement document query | SoC 功能规划; release gate freeze; consultant-visible vs internal release zip; V2R2→V3R3 delta queries; member-need cross-referencing |
| **governance** | `sub-skills/governance.md` | Task Group (TG) spec maturity tracking + ratification milestone monitoring + partner requirement transmission (9-dimension framework, 7-step process) + bilingual (CN↔EN) technical glossary | "Will spec X ratify before delivery milestone Y?"; partner requirement baseline transmission; acceptance-failure root cause; architecture document translation |

> **Loading convention**: reference sub-skills by relative path from this
> file, e.g. `sub-skills/spec-learning.md`. Do not hardcode absolute paths.

---

## 角色推荐 (Role Recommendations)

| Role | Primary sub-skills | Secondary | Typical tasks |
|------|-------------------|-----------|---------------|
| **Software Developer** (软件开发) | `spec-learning`, `requirement-analysis` | `gap-analysis` | 查询 ISA spec / CSR 语义; 理解 SoC feature 需求; 确认 Linux/toolchain 支持状态 |
| **Hardware Developer** (硬件开发) | `spec-learning`, `gap-analysis` | `requirement-analysis` | 规范映射 (RISC-V ↔ Arm/x86); feature gap 分析; 红线冲突识别 |
| **Verification Engineer** (验证) | `spec-learning`, `gap-analysis` | `requirement-analysis` | 规范验证 (MUST/SHOULD/MAY); gap 文档证据复核; GitHub 仓库实现状态交叉验证 |
| **Product Manager** (PM) | `requirement-analysis`, `governance` | `spec-learning`, `gap-analysis` | 需求矩阵管理; Rx Arch Freeze; TG 跟进; 会员/伙伴协作; release zip 打包 |
| **Architect** (架构师) | `spec-learning`, `gap-analysis`, `requirement-analysis` | `governance` | 竞品架构对比; Tier-1 证据映射; V2R2→V3R3 delta; 投资与竞争分析 |

> **Cross-role note**: `spec-learning` is the foundational skill — every role
> benefits from loading it first to ground claims in tier-1 sources.

---

## 何时激活 (When to Activate)

Load **this entry file** when a task involves any of:

- Looking up or citing a RISC-V specification, extension, CSR, or instruction.
- Performing a feature gap analysis or producing a gap/review document.
- Building, refreshing, or querying a CPU requirement matrix.
- Tracking a Task Group's spec maturity or transmitting requirements to a partner.
- Translating architecture documents (CN ↔ EN) with consistent terminology.

Then **dispatch** to the matching sub-skill using the Feature Overview table
above. Do not execute the workflow from this file — load the sub-skill.

---

## 使用示例 (Usage Examples)

### Example 1 — Query a RISC-V specification (→ `spec-learning`)

```
/skill spec-learning
请查询 RISC-V Privileged spec v1.12 中关于 page table (Sv39/Sv48/Sv57) 的定义，
并标注 ratification 状态和引用层级 (UDB / Normative / Ratified)。
```

**Expected output**: a citation block with layer tags, e.g.
`[Layer 3] Ratified: https://github.com/riscv/riscv-isa-manual/releases — Sv39 layout`.

### Example 2 — Gap analysis for a feature (→ `gap-analysis`)

```
/skill gap-analysis
基于 MEMBER-00-GP 的 MMIO Outstanding 需求 (REQ-XXX)，对比 RISC-V Privileged spec
v1.12 与 Arm Neoverse V2，生成 F{N} gap 分析文档（9 节结构），并在 §4 执行
XiangShan 仓库复核。
```

**Expected output**: `_internal_Gap_Analysis_F{N}_MMIO_Outstanding.md` saved to
`<delivery-dir>/04_专题/`, with source-tag distribution (aim T1 > 90%) and
8-12 sub-requirement rows.

### Example 3 — Requirement query (→ `requirement-analysis`)

```
/skill requirement-analysis
查询 MEMBER-00-GP 的虚拟化需求：列出所有 RAS / IOMMU / confidential-compute
相关条目，标注优先级 (P0/P1/P2) 和 V2R2 → V3R3 delta。
```

**Expected output**: a structured view of matching requirement rows, each
with source tag, mapping type, and the V2R2/V3R3 carryover status.

### Example 4 — Governance: TG tracking (→ `governance`)

```
/skill governance
IOPMP TG spec 当前版本 v0.8.2 (Draft)，我们的 R1 产品使用自研静态 I/O 隔离方案。
请评估：spec ratification 延后到 2027 H1 对 R2/R3 的交付风险，并给出 mitigation。
```

**Expected output**: a TG tracking matrix row + risk assessment
(🟢/🟡/🔴) + self-developed fallback convergence plan.

### Example 5 — Cross-skill: spec → gap → requirement

A realistic flow spans multiple sub-skills:

```
1. /skill spec-learning      → ground the feature definition in tier-1 sources
2. /skill requirement-analysis → find the matching requirement row & priority
3. /skill gap-analysis        → produce the F{N} gap doc + review slides
```

Always start with `spec-learning` to establish the evidence base, then fan
out to the analytical sub-skills.

---

## 子文件参考路径 (Sub-skill Reference Paths)

All paths are relative to this file (`SKILL.md`):

```
riscv-skill-pack/
├── SKILL.md                          ← you are here
├── sub-skills/
│   ├── spec-learning.md              ← ISA spec navigation + Tier-1 mapping
│   ├── gap-analysis.md               ← Gap doc (Part 1) + review slides (Part 2)
│   ├── requirement-analysis.md       ← Requirement workflow + feature list + query
│   └── governance.md                 ← TG tracking + partner comms + glossary
├── examples/                         ← (populated in later task)
├── README.md                         ← public-facing readme (populated in later task)
└── LICENSE                           ← Apache-2.0
```

---

## 脱敏与占位符约定 (Redaction & Placeholder Convention)

All sub-skills in this pack are **redacted templates**. Before use, replace
the following placeholders with your project's actual identifiers:

| Placeholder | Represents | Example replacement |
|-------------|------------|---------------------|
| `ORG-00` | Your organization codename | (your company) |
| `MEMBER-XX-XXX` | Partner / member codename | (your partner) |
| `MEMBER-00-GP` | Your flagship product line | (your product) |
| `RVI-XXX` | RISC-V International role/person | (actual name) |
| `Internal-XXX` | Internal advisor / director | (actual name) |
| `<delivery-dir>` | Your project's delivery folder | `your-project/04_专题/` |

> **Do not** submit documents with placeholder names to external audiences.
> The `_internal_` file prefix marks docs that must never enter an external
> release zip.

---

## License

Apache-2.0 — see `LICENSE`.
