---
name: gap-analysis
description: "Produce a single-feature RISC-V Gap Analysis document AND/OR a strict 3-slides-per-feature review deck (PPTX) that quantifies 需求对比 (requirement comparison) between a target RISC-V core and competing ISAs. Part 1 generates a standardized 9-section Gap Analysis markdown doc (Exec Summary / Background / Competitive Evidence / GitHub Repo Cross-Check / Gap Matrix / Sub-Requirements / Recommendations / References / Revision Log). Part 2 generates a 4-Reqs Review Slide deck sourced EXCLUSIVELY from a Part 1 doc — never re-derive evidence inside slides. Use when the user asks for gap 分析, feature 差距 deep-dive (e.g., MMIO Outstanding, HW Profiling, Vectorization, I/D Coherent), red-line conflict resolution doc, F{N} doc, 需求对比, review slides, or evaluation deck. Triggers: 'gap 分析', 'F4 文档', 'feature 差距', '需求对比', 'gap analysis', 'REQ-XXX 深挖', '评审 PPT', 'gap slide'."
metadata:
  author: bamv2005
  version: '1.0'
  scope: generic-riscv
---

# Gap Analysis Skill (Doc + Slide)

This skill unifies two tightly-coupled sub-workflows:

- **Part 1 — Gap Doc**: produces a single-feature Gap Analysis markdown document (the evidence base).
- **Part 2 — Gap Slide**: produces a strict 3-slides-per-feature PPTX review deck, sourcing exclusively from a Part 1 doc.

The two parts are sequential: **Part 2 REQUIRES a Part 1 doc as input.** This coupling prevents citation drift between deck and doc (a hard lesson from prior sessions).

---

# Part 1 — Gap Analysis Document

## When to Use Part 1

Use when the user asks for a single-feature gap analysis document for a RISC-V requirement (need 对比 / 差距 拆解), e.g.:

- "为 ORG-00-XXX 做一份 gap 分析 / 需求对比"
- "F5 / F6 文档"
- "MMIO Outstanding / I/D Coherent / Vectorization 深挖"
- "把 L2 第 X 章抽出来更新到 gap 分析"
- "更新现有 F{N} 文档加入 GitHub 复核"

Pair with Part 2 (Gap Slide) when the user later asks for slides — slides must source from the doc produced here.

## Hard Conventions (DO NOT VIOLATE)

These rules come from prior sessions. Override only if the user explicitly says so.

1. **File name**: `_internal_Gap_Analysis_F{N}_{feature_name}.md`
   - `N` is the next available F-index (F1 ICache_DCache, F2 HW_Profiling, F3 Vectorization, F4 MMIO_Outstanding → next is F5).
   - `feature_name` uses underscore-joined English keywords (`ICache_DCache_Coherent`, `HW_Profiling_Counters`, `MMIO_Outstanding`).
   - `_internal_` prefix is mandatory — these docs never enter the external release zip.
2. **Save path**: `<delivery-dir>/04_专题/` (project-local delivery folder for deep-dive analyses).
3. **Language**: Chinese body, English code/spec/identifier names. Do not translate the body.
4. **Source tags on every claim**: `[T1-VERIFIED]` (primary spec or repo), `[T2-CROSS-CHECKED]` (≥2 secondary), `[UNVERIFIED]` (single source/inference). Aim for >90% T1.
5. **Mapping types on every sub-requirement**: `[exact]`, `[approximate]`, `[composite]`, `[N/A]`.
6. **Sub-requirement ID style**: `{PROJECT}-V3-{REQ-ID}.{seq}` for CSV ingestion (e.g., `PROJ-V3-592.1`). The doc body MAY also use a semantic alias `{PROJECT}-V3-{TOPIC}.{seq}` (e.g., `PROJ-V3-MMIO.4`) — if so, every sub-req row MUST cross-reference both forms.
7. **Red-line treatment**: Any newly discovered conflict must be flagged in §7 with a candidate red-line ID `RL-{TOPIC}-{LETTER}` (e.g., `RL-MMIO-D`) and a written recommendation to add it to the Master Report §1.1.
8. **GitHub repo cross-check is mandatory** for any feature whose upstream open-source implementation status is unclear. Use the `github` connector — see `references/03-github-cross-check.md`.
9. **Asset name on share**: `Gap_F{N}_{ShortName}` (e.g., `Gap_F4_MMIO_Outstanding`). Always use `share_file` with `should_validate=false` for markdown.
10. **DO NOT touch slides** unless the user explicitly says so. Doc-only by default.

## The 9-Section Template (F4 Baseline)

Every Gap doc MUST have these sections in this order. Section count and titles are fixed; only sub-section depth varies.

```
§1 Executive Summary（执行摘要）           ~30-50 lines
§2 背景：{feature} 的含义与术语              ~40-60 lines, ARM vs RISC-V 术语对照表
§3 竞品/规范一手证据                          ~80-150 lines, ARM TRM + 引用块
§4 仓库实现状态（GitHub 复核）— MANDATORY    ~60-120 lines, PR/Issue/源码片段
§5 Gap Analysis Matrix（差距分析矩阵）        ~30-50 lines, 表格 + 维度评级
§6 子需求清单（Sub-Requirements）             ~60-100 lines, 10±2 条
§7 建议（Recommendations）                    ~30-50 lines, 含红线候选
§8 参考文献                                    ~20-40 lines
§9 修订记录                                    ~10-20 lines
```

Target total length: **400-900 lines** (F1=427, F3=510, F4=491, F2=867).

Detailed per-section content rules: read `references/01-section-template.md` before drafting.

## Workflow (Part 1)

1. **Confirm scope with the user** (only if ambiguous):
   - Which requirement ID / feature?
   - Which L2 chapter or evidence file to extract from?
   - Is this a new F{N} or an update to existing?

2. **Locate source evidence**:
   - Check `space_files/.../L2_宏观架构分析.md` for top-level analysis chapters.
   - Check `past_session_contexts/` for prior evidence reports.
   - Check `<delivery-dir>/01_输入基线/` for the requirement row.
   - Read `references/02-evidence-extraction.md` for extraction patterns.

3. **GitHub repo cross-check** (mandatory if implementation status unclear):
   - Use the `github` connector against the upstream open-source RISC-V core repo (e.g., `OpenXiangShan/XiangShan`).
   - Branches to inspect: the project's current and next development branches (e.g., `master`, next-version branches).
   - Read `references/03-github-cross-check.md` for the exact query playbook (PR / parameter / CSR / source-file inspection).

4. **Draft §1-§9** following the template.
   - Use the F4 doc as the structural exemplar (best-in-class).
   - Use the F1 doc for "no GitHub evidence needed" cases (still keep §4 — write "本特性沿用前代已有实现，仓库无新增改动").

5. **QA self-check** before share:
   - 9 `## ` headings present and in order.
   - Source Tag count: aim for `T1 >> T2 >> UNVERIFIED`. Print distribution.
   - Sub-req count: 8-12 rows; mapping type distribution covers ≥3 of 4 types.
   - All red-line candidates have `RL-{TOPIC}-{LETTER}` IDs.
   - Line count within 400-900 range.

6. **Save and share**:
   - Save to `<delivery-dir>/04_专题/_internal_Gap_Analysis_F{N}_{feature}.md`
   - `share_file` with asset name `Gap_F{N}_{ShortName}`, `should_validate=false`.

7. **Downstream sync recommendations** (offer to user — do NOT auto-execute):
   - Master Report §1.1: insert/update the requirement row + any new red-line.
   - Sub-requirement CSV: append `{PROJECT}-V3-{REQ-ID}.1-.N` rows.
   - Slide deck: use Part 2 of this skill (Gap Slide).

## Anti-Patterns (Part 1 — Do Not Do)

- ❌ Skip §4 GitHub Cross-Check ("沿用前代实现" is OK, but the section must exist).
- ❌ Use a raw requirement-ID style inside the doc body — convert to `{PROJECT}-V3-{REQ-ID}.x` for sub-reqs.
- ❌ Translate Chinese sections to English (unless user explicitly asks).
- ❌ Drop `_internal_` prefix.
- ❌ Auto-resolve red-line conflicts; surface them in §7 instead.
- ❌ Include this doc in any external release zip.
- ❌ Re-write existing F1/F2/F3 unless user asks; default to creating new F{N+1}.

## Quick-Start (Part 1)

1. Confirm `{N}` and `{feature_name}` with user (or infer from request).
2. Read `references/01-section-template.md` for per-section content rules.
3. Locate evidence (L2 chapter, past session, requirements baseline).
4. Run GitHub cross-check per `references/03-github-cross-check.md`.
5. Draft §1-§9 → QA self-check → save to `04_专题/` → share with asset name `Gap_F{N}_{ShortName}`.
6. Offer Master Report §1.1 + CSV sync as next steps.

---

# Part 2 — Gap Review Slide Deck (PPTX)

## When to Use Part 2

Use when the user asks for an internal review slide deck for gap-analysis topics (需求对比 / 评审页面), e.g.:

- "帮我做 V3R1 评审 PPT"
- "把 F4 MMIO Outstanding 做成 3 页 slide"
- "更新 4-Reqs Review Slides"
- "gap slide / 评审页面 / 决策 PPT"

This part ONLY produces PPTX. Never create new evidence here — pull from an existing Part 1 (Gap Doc) output.

## HARD DEPENDENCY (Non-Negotiable)

**Part 2 REQUIRES an existing Gap Analysis Doc as input.** If the topic does not yet have a `_internal_Gap_Analysis_F{N}_*.md` in `<delivery-dir>/04_专题/`, STOP and:

1. Tell the user the doc is required.
2. Offer to run Part 1 (Gap Doc) first.
3. Do NOT improvise evidence inside slides.

Reasoning: prior sessions have shown that re-deriving evidence in slides leads to citation drift between deck and doc.

## STRICT Page Layout (Hard Constraint)

Every feature deck follows **exactly 3 slides per feature**. No cover slide. No Q&A slide. No transition slides. No appendix.

For an N-feature deck, total page count = **3 × N**.

| Feature N | Slide 1 (What) | Slide 2 (Where) | Slide 3 (Decision) |
|---|---|---|---|
| F4 MMIO | 规范/概念对照 | 缺失维度定量 | 方案矩阵 + 红线 |
| F1 I/D Coherent | JIT 开销次序 + 场景 | 7 家竞品矩阵 | 双轨方案落地 |
| F2 HW Profiling | 四类计数器矩阵 | AMU 等价表 + 最小可行 | 四维分 Gate 落地 |
| F3 Vectorization | gap 因素拆解 | RVV vs SVE2/NEON/AVX/AMX | VLEN/pipe/补全方案 |

### Slide 1 — "What" (规范/概念页)

**Title bar**: `需求 X / Y · {feature_name} ({REQ-num} · {RL-id if any})`

**Subtitle (≤1 line)**: 概念句，例如 "RISC-V I/O Channel 机制 vs ARM Device Memory Type"

**Body block (one only)**: 一张主对照表 or 主概念图。
- 若是对照表：≤3 列, ≤6 行, 每格 ≤2 行文本。
- 若是 PPO/规范节选：列出 ≤6 条编号规则，每条 ≤1 行。

**Insight strip (黑底白字)**: 一句话核心结论，≤80 字。
例：`Channel 是 RISC-V 的 region 级分组语义；ARM Device Type 是 peripheral 级 + 合并/Early Ack 两轴`

**Footer**: `资料来源：<spec1> · <spec2> · <spec3>` + `§<X> {feature 短名} — 1/3 {页标题}` + `<page-num> / <total> · _internal_ · 仅限项目组评审`

### Slide 2 — "Where" (现状/竞品页)

**Title bar**: 与 Slide 1 一致

**Subtitle**: 定量/对比角度，例如 "RISC-V 相对 ARM 缺失的 3 个 Outstanding 控制维度" 或 "7 家数据中心竞品落地状态对比"

**Body block (one only)**: 一张主矩阵 or 定量分解。
- 竞品矩阵：列 = 7 家数据中心 CPU（Intel SPR/GNR · AMD Zen5/Turin · ARM Neoverse V2/V3 · SiFive P870 · 本项目）；行 = 维度。
- 缺失维度拆解：≤3 维, 每维 ≤2 行（标题 + 一句话量化）。
- 数值表：必须给出本项目实际数 + 竞品数 + 比例（如 `本项目 ÷ ARM V2 = 4/32 = 12.5%`）。

**Insight strip**: 本项目在矩阵中的相对位置 + 量化结论。≤80 字。
例：`当前位置 · 在 7 家数据中心 CPU 中，仅本项目 + SiFive P870 处于"纯 SW-CMO"档，落后 x86 两代 + ARM 一代`

**Footer**: 同 Slide 1 模板，编号改为 `2/3`

### Slide 3 — "Decision" (决策/红线页)

**Title bar**: 与 Slide 1 一致

**Subtitle**: 决策角度，例如 "评审决策建议 — 补齐路径"

**Body block**:
1. **决策矩阵**（必含）：方案 A/B/C/D/E × 成本 × 风险 × Gate (R1/R2/R3)。
   - 推荐组合在矩阵下方加粗：`推荐组合：A (R1) + B (R2) + C (R2 选项) + D（R1，反转节点）；E 仅作社区跟踪`
2. **反问对答 (≤2 组)**：每组一对 Q+A，每 A ≤1 行。
   - 模式：`为何不直接做 X？` / `为何 Y 是关键？` / `Y 的反对意见` / `影响指标` 等。
3. **新增/关联红线 (黑底白字 strip)**：必须显示 `RL-{TOPIC}-{LETTER}` 标识 + 一句话描述 + 时间标记。

**Footer**: 同 Slide 1 模板，编号改为 `3/3`

## Universal Page Elements (All Slides)

Every slide MUST have:

1. **顶部色块标题条** with `需求 X / Y · {feature_name}` + optional `({REQ-num} · RL {id})`
2. **副标题行**：单行点题
3. **主内容区**：一张表或一张图，不要混排两个独立内容块
4. **黑底白字 Insight Strip**：一行核心结论或推荐
5. **底部页脚 4 段**：`资料来源：...` ｜ `§{X} {feature 短名} — N/3 {小标题}` ｜ `<page> / <total> · _internal_ · 仅限项目组评审`

## Forbidden Elements (Part 2)

- ❌ Cover slide (不要封面页)
- ❌ Agenda / TOC slide
- ❌ Q&A slide as last page
- ❌ Thank-you / 致谢 slide
- ❌ Per-feature transition divider slide
- ❌ Appendix or backup slides
- ❌ Mixing two unrelated tables on one slide
- ❌ More than 3 slides for any single feature (even if doc has 9 sections)
- ❌ Re-citing evidence not present in the source doc
- ❌ Translation of any Chinese text to English

## Source-to-Slide Mapping

For each feature, extract content from these doc sections:

| Slide | Pulls from Doc Sections |
|---|---|
| Slide 1 (What) | §2 背景与术语 + §3 一手规范节选 (pick the 1 most central excerpt) |
| Slide 2 (Where) | §3 竞品定量 + §5 Gap Matrix (use the matrix table as the slide table) |
| Slide 3 (Decision) | §7 建议（含 RL-X 新增红线）+ 关联 §6 sub-req IDs |

Section §4 GitHub Cross-Check is NOT a slide — it's evidence backing Slide 3's decision rationale (cite the SHA/PR in Insight Strip if reversing a prior decision).

## Workflow (Part 2)

1. **Verify source doc exists**:
   ```bash
   ls <delivery-dir>/04_专题/_internal_Gap_Analysis_F*.md
   ```
   If missing → STOP and ask user to run Part 1 first.

2. **Identify feature scope**:
   - Single feature → 3 slides
   - Multi feature review (e.g., 4-Reqs Review) → 3 × N slides, in user-specified order
   - Default ordering (current 4-Reqs deck): F4 MMIO → F1 I/D → F2 HW Profiling → F3 Vectorization

3. **Load PPTX skill**: `load_skill(name="office/pptx")` for actual rendering.

4. **Build deck programmatically** using `python-pptx`:
   - One slide layout template; reuse for all 3×N pages.
   - Read `references/01-template-spec.md` for exact dimensions, font sizes, color palette.
   - Read `references/02-build-script.md` for the python-pptx skeleton.

5. **QA self-check before share**:
   - Page count = 3 × N (exactly).
   - Every slide has title / subtitle / one body block / insight strip / 4-part footer.
   - Slide 3 of each feature contains a decision matrix AND any new `RL-X` red-line.
   - No language other than the doc's primary language (Chinese for these docs).
   - Page numbers `N / total` correct.
   - **VISUAL INSPECTION**: convert to PNG (one per slide) via `libreoffice --headless --convert-to png` or `pptx`-skill helper. Check for: text wrap mid-word, dark text on dark strip, table overflow, title truncation, mismatched font.

6. **Save and share**:
   - Save to `<delivery-dir>/03_评审材料/internal_{feature_set}_Review_Slides_v{YYYYMMDD}.pptx`
     - `{feature_set}` examples: `4Reqs`, `F4MMIO`, `F1IDC`.
   - `share_file` with asset name `review-slides` (REUSE existing asset name for version history) or feature-specific name if isolated.
   - DO NOT auto-share without QA inspection.

## Update Mode (vs Create Mode)

If the user uploads an existing PPTX for "update" (e.g., `internal_4Reqs_Review_Slides_v20260512.pptx`):

1. Read all slides into structured form (title / subtitle / tables / insight / footer).
2. Diff against the gap doc(s) — list every claim that no longer matches the doc.
3. **Do NOT modify the PPTX automatically.** Surface the diff as a markdown table and let the user decide which slides to update.
4. Only when the user gives explicit "go ahead" on specific slides, modify those slides — preserving layout/font/color exactly.

This rule is from a prior session: the user said "slide 等我明确命令" — never modify slides without explicit instruction.

---

# Critical Reminders (Whole Skill)

## Part 1 (Doc)
- ❗ **9 sections in fixed order.** §4 GitHub Cross-Check is mandatory even if "no new code".
- ❗ **Source tags on every claim.** Aim >90% T1.
- ❗ **Red-line candidates surfaced in §7**, never auto-resolved.
- ❗ **`_internal_` prefix mandatory** — these docs never ship externally.

## Part 2 (Slide)
- ❗ **Doc must exist first.** No exception.
- ❗ **3 slides per feature.** Not 6, not 9, not "as many as needed". Three.
- ❗ **No cover / no Q&A.** First page is Feature 1's Slide 1; last page is Feature N's Slide 3.
- ❗ **Decision matrix on Slide 3 always**, even if the recommendation is "do nothing".
- ❗ **Red-line IDs explicit on Slide 3** if the doc surfaced any `RL-{TOPIC}-{LETTER}`.
- ❗ Visual inspection BEFORE share — no exception.
- ❗ Reuse a stable asset name for the multi-feature deck to preserve version history.

## Cross-Part Integrity
- ❗ **Slides cite the doc, the doc cites specs/repos.** Never short-circuit: slides MUST NOT re-derive evidence.
- ❗ **When the doc updates, offer to diff the deck** — but never auto-modify slides without explicit user command.
