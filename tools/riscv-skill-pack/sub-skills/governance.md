---
name: governance
description: >-
  Standardize ecosystem governance workflows: (1) tracking upstream Task Group
  (TG) spec maturity and ratification milestones, (2) transmitting requirements
  between an organization and its partners using a nine-dimension framework and
  seven-step process, and (3) maintaining a consistent bilingual (Chinese ↔ English)
  technical glossary. Use when a partner raises a technical requirement, when a
  TG spec revision may impact delivery, when acceptance fails due to requirement
  ambiguity, or when translating architecture documents. Triggers:
  'task group', 'TG tracking', 'partner requirement', 'requirement transmission',
  'requirement baseline', 'acceptance failure', 'glossary', 'translation'.
metadata:
  version: '1.0'
  scope: ecosystem-governance
  note: >-
    Generalized from internal case studies. All organization and partner names
    are redacted. Replace MEMBER-xx and ORG-xx placeholders with your actual
  note_continued: >-
    identifiers before use.
---

# Governance Skill

This skill consolidates three governance sub-competencies into one reference:

| Part | Source | Purpose |
|------|--------|---------|
| **Part 1 — Task Group Tracking** | Synthesized from upstream-tracking practice | Monitor spec maturity, ratification milestones, and delivery impact |
| **Part 2 — Partner Communication** | Derived from internal nine-dimension framework + seven-step process | Prevent requirement-transmission loss between organization and partners |
| **Part 3 — Translation Glossary** | Derived from internal bilingual terminology table | Keep Chinese ↔ English technical terms consistent across documents |

> **Redaction notice**: All real organization, partner, product, and person names
> have been replaced with generic placeholders (`ORG-xx`, `MEMBER-xx`, `RVI-xx`,
> `Internal-xx`). See the placeholder table at the end of this file.

---

# Part 1: Task Group (TG) Tracking

## When to Activate

Activate when any of the following conditions is met:

- **A TG spec is evolving** and your product roadmap cites it (even as "future compliance").
- **Ratification milestone slips or advances** — the timeline change may unblock or block a delivery window.
- **New TG chair / vice-chair appointed** — the new leadership may reset priorities or scope.
- **Public review / freeze / ratification ballot announced** — you must decide whether to comment, object, or abstain.
- **Quarterly / semi-annual roadmap sync** — fixed-cadence check of all tracked TGs.

**Quick test**: if the conversation involves "will spec X be ratified before our delivery milestone Y?", activate this part.

---

## TG Tracking Matrix

Maintain a living matrix with one row per tracked TG. Update it whenever a TG
publishes a new draft, holds a key vote, or changes leadership.

| Field | Example | Why it matters |
|-------|---------|----------------|
| **TG name** | IOPMP, Fast Interrupts, AME | Identifies the spec being tracked |
| **Latest version** | v0.8.2 (2026-02-09) | Tells you how stale your last read is |
| **Status** | Draft / Frozen / Public Review / Ratified | Drives the delivery-risk assessment |
| **Chair / ARC liaison** | Names + affiliations | Know who to approach for clarification |
| **Ratification target** | Earliest 2027 H1 | The "not before" date for compliance claims |
| **Linkage to your product** | Strong / Medium / Weak + requirement IDs | Connects spec movement to delivery commitments |
| **Delivery impact per version** | R1: no block / R2: track / R3: depends | Drives the per-release risk level |
| **Risk level** | 🟢 Low / 🟡 Medium / 🔴 High | Summarizes net exposure |
| **Mitigation** | Self-developed fallback, no spec dependency | What you do if the spec slips |

### Risk Assessment Heuristic

```
IF product version cites spec as "compliant":
    risk = 🔴 High (spec slip = delivery slip)
    → require ratified spec OR contractual waiver

ELIF product version cites spec as "tracked / future":
    risk = 🟡 Medium (spec slip = roadmap shift)
    → monitor quarterly, update roadmap if slip > 6 months

ELSE (no citation):
    risk = 🟢 Low (no formal dependency)
    → optional awareness only
```

### Self-Developed Fallback Pattern

When a spec is still in Draft and the product cannot wait:

1. **Implement an internal solution** that covers the functional need without
   claiming spec compliance.
2. **Document the deviation** — which spec clauses are met, which are deferred.
3. **Track the spec** — when it ratifies, re-evaluate whether to converge.
4. **Plan the convergence window** — usually the next major revision (R2 / R3),
   not a patch release.

> **Example (redacted)**: A product used a self-developed static I/O isolation
> component for R1 because the relevant TG spec was still in Draft (v0.8.2,
> ARC review). R2 planned to replace it with a dynamic solution once the spec
> stabilized. Risk was assessed 🟢 Low for R1 because delivery did not depend
> on spec ratification.

---

## TG Tracking Procedure

### Step 1: Identify Tracked TGs

Scan your product's requirement specs, gap-analysis documents, and architecture
decision records. Every cited spec → one TG to track.

### Step 2: Build the Matrix

Fill the matrix fields above. Use the TG's public mailing list, GitHub repo,
or wiki as the authoritative source.

### Step 3: Assess Per-Version Impact

For each product version (R1, R2, R3, ...), classify the TG's impact:

| Classification | Meaning | Action |
|----------------|---------|--------|
| **Blocking** | Delivery cannot ship without this spec | Escalate; require ratified spec or contractual waiver |
| **Tracked** | Roadmap cites it as "future compliance" | Monitor quarterly; update roadmap on slip |
| **Awareness** | No formal citation | Optional; revisit if linkage emerges |

### Step 4: Monitor on Cadence

- **Monthly**: check TG mailing list for new drafts, votes, or chair changes.
- **Quarterly**: full matrix review; update risk levels; publish to stakeholders.
- **On trigger**: any TG status change → immediate impact assessment for the
  affected product version.

### Step 5: Feed Back into Requirement Process

When a TG spec change impacts a partner requirement, trigger Part 2
(Partner Communication) Step 4 (Internal Review) to re-assess feasibility and
update the requirement baseline.

---

# Part 2: Partner Communication

## When to Activate This Part

Activate when any of the following conditions is met:

- **Partner raises a new technical requirement** — a partner sends a feature /
  performance / compatibility request via email, meeting, or IM.
- **Organization needs to clarify an existing requirement** — R&D finds
  ambiguity in an existing requirement doc.
- **Acceptance fails and root cause is requirement ambiguity** — partner rejects
  delivery because the implemented behavior does not match their actual intent.
- **Technical decision impacts partner commitment** — internal architecture
  choice may change what the organization can promise.
- **Quarterly / annual alignment meeting** — fixed-cadence backlog alignment
  with a partner.

**Quick test**: if the conversation involves aligning "what the partner expects"
with "what the organization can deliver", activate this part.

---

## Hard Conventions (DO NOT VIOLATE)

These rules prevent the most common failure mode: requirement-transmission loss.

1. **Never accept a one-line requirement.** A bare "support perf" or "support
   Linux perf" is not a requirement — it is a conversation starter. Push for the
   nine dimensions before any R&D work begins.
2. **Always cite the source partner and channel.** Every requirement must record
   who said it, when, and via what medium (email / meeting / IM). No anonymous
   requirements.
3. **The nine dimensions are mandatory, not optional.** Even when the partner
   pushes back on "too much paperwork", every dimension must be filled or
   explicitly marked "TBD" (to be supplemented). Skipping a dimension is the
   root cause of the canonical failure case.
4. **Acceptance steps must be third-party reproducible.** The acceptance
   procedure (dimension 7) must be executable by someone other than the
   implementer. If it cannot be reproduced, it is not a valid acceptance
   criterion.
5. **Unknowns are honest, not invented.** Dimension 9 (Unconfirmed Items) must
   list every missing piece. Do not fabricate information to make the document
   look complete.
6. **Scope is subsystem level.** This skill is not limited to CPU. It applies to
   any subsystem requirement that crosses the organization ↔ partner boundary.

---

## The Nine-Dimension Requirement Framework

When a partner requirement arrives, immediately load this framework.

| # | Dimension | Purpose |
|---|-----------|---------|
| 1 | **Requirement Scenario** | Who uses it, when, and the exact operation flow |
| 2 | **Key Parameters** | Parameters that look ordinary but are technically critical |
| 3 | **Current Pain Point** | What breaks today — functional / performance / experience |
| 4 | **Quantitative Metrics** | Reproducible, measurable acceptance numbers |
| 5 | **Competitive Reference** | Partner's experience on ARM / x86 / other RISC-V |
| 6 | **Priority & Time Window** | Priority grade, target milestone, hard deadlines |
| 7 | **Acceptance Method** | Tools, steps, pass/fail criteria, owner |
| 8 | **Linked Requirements** | Internal requirement IDs, gap-analysis doc linkage |
| 9 | **Unconfirmed Items** | Honest list of what is still missing |

**Action**: Create a draft document with all nine dimension headers. Do not skip
any header, even if the dimension will be marked "TBD".

### Critical Dimensions to Push Hardest On

- **Dimension 2 (Key Parameters)** — this is where the canonical failure case
  failed. The `-e cycles` parameter was the entire reason for the requirement,
  but it was lost in transmission. Always ask: *"Are there any parameters that
  look ordinary but are technically critical?"*
- **Dimension 4 (Quantitative Metrics)** — without numbers, acceptance is
  subjective. Push for measurable thresholds.
- **Dimension 7 (Acceptance Method)** — without reproducible steps, the partner
  cannot verify delivery independently.

---

## Structured Requirement Document Template

```markdown
# [Partner] [Feature] Requirement — [Tracking ID]

> **Version**: v0.1 (Draft)
> **Date**: YYYY-MM-DD
> **Source Partner**: [partner identifier]
> **Source Channel**: [email / meeting / IM]
> **Source Contact**: [name / role]
> **Internal Owner**: [product manager name]

## 1. Requirement Scenario
[content]

## 2. Key Parameters
[content]

## 3. Current Pain Point
[content]

## 4. Quantitative Metrics
[content]

## 5. Competitive Reference
[content]

## 6. Priority & Time Window
[content]

## 7. Acceptance Method
[content]

## 8. Linked Requirements
[content]

## 9. Unconfirmed Items
[content]
```

**Quality gate before hand-off to R&D**:

- [ ] Dimensions 1 and 2 are at least "filled" or "explicitly TBD"
- [ ] Dimension 9 lists every missing piece honestly
- [ ] Source partner and channel are recorded
- [ ] No dimension is silently skipped

---

## The Seven-Step Communication Process

Once the structured requirement document exists, follow this process to hand
off to R&D and close the loop with the partner.

| # | Step | Owner | Output |
|---|------|-------|--------|
| 1 | **Requirement Reception** | Product manager | Source/channel/contact recorded; duplicate check; temp tracking ID created |
| 2 | **Framework Filling** | Product manager | Raw input mapped to nine dimensions; fill status marked; partner question list compiled |
| 3 | **Internal Review** | Architect + R&D lead | Technical feasibility assessed; effort estimated; completeness checked |
| 4 | **Partner Confirmation** | Product manager | Question list sent to partner; alignment meeting held; requirement baseline frozen |
| 5 | **R&D Implementation** | R&D | Implemented against baseline; any deviation triggers change sub-process |
| 6 | **Acceptance Delivery** | Partner acceptance owner | Partner runs dimension-7 acceptance steps; signs off or reports deviation |
| 7 | **Closed-Loop Archiving** | Product manager | All artifacts archived; tracking IDs updated |

### RACI Awareness

- **Product manager** is Accountable for the full flow.
- **Architect** is Accountable for technical feasibility and solution choice.
- **R&D lead** is Accountable for effort estimate and milestone.
- **Partner interface** is Accountable for providing accurate requirement info.
- **Partner acceptance owner** is Accountable for running acceptance steps.

### Change Sub-Process (Step 5 deviation)

When R&D implementation deviates from the baseline:

1. **Freeze** the current implementation state.
2. **Document** the deviation — what changed, why, and the impact on dimensions
   4 (metrics) and 7 (acceptance).
3. **Re-escalate** to Step 3 (Internal Review) for feasibility re-assessment.
4. **Re-confirm** with the partner (Step 4) before proceeding.

---

## Case Capture for Reuse

After a requirement is delivered and accepted (or after a notable failure),
decide whether the case is worth capturing as a reusable asset.

**Capture when any is true**:

- The case illustrates a common transmission failure mode.
- The case produced a new dimension or filling instruction.
- The case required a new step or RACI clarification.
- The case surfaced a technical constraint that needs a memo.

**Capture template**:

1. **Background** — timeline of events, root cause classification.
2. **Original Communication (inaccurate version)** — what was actually said.
3. **Structured Rewrite** — how the nine dimensions would have prevented failure.
4. **Outcome** — what changed after the case was processed.
5. **Lessons** — what to do differently next time.

### Canonical Failure Case (Redacted Summary)

> A partner raised a requirement: "support Linux perf". The actual intent was
> `perf record -e cycles` for hardware-PMU-based sampling. Through three
> transmission hops (partner → product manager → R&D), the requirement was
> compressed to "support perf". R&D implemented `perf stat` and a default
> `perf record` (software timer-based), marked the requirement done. The
> partner's acceptance failed because `perf record -e cycles` either errored
> out or silently fell back to software sampling.
>
> **Root cause**: not technical failure, but requirement-transmission loss. The
> `-e cycles` parameter — the entire reason for the requirement — was lost.

### Reusable Lessons

1. **One-line requirements are conversation starters, not requirements.** Always
   push for the nine dimensions.
2. **The "key parameter" dimension is where transmission loss hides.** Make it
   the longest section in every requirement doc.
3. **Acceptance must be reproducible by a third party.** If only the implementer
   can verify it, the acceptance is fake.
4. **Unknowns are not failures.** Dimension 9 is a feature, not a bug — it makes
   the gaps visible before they become failures.

---

# Part 3: Translation Glossary

## When to Use

Use this glossary when translating architecture, requirement, or specification
documents between Chinese and English. Consistent terminology prevents the
ambiguity that leads to requirement-transmission loss (see Part 2).

> **Generalization note**: Product-specific codenames below are shown as generic
> placeholders (`PRODUCT-Vn`). Replace them with your actual product identifiers.
> Standard RISC-V ISA extensions and architecture terms are kept as-is (they are
> universal and should not be translated).

---

## Product / Project Codenames

| Chinese | English | Note |
|---------|---------|------|
| 产品 V3 | PRODUCT V3 / PROD V3 | Use full name + abbreviation on first occurrence |
| 产品 V3 R1/R2/R3 | PRODUCT V3 R1 / R2 / R3 | Do not translate revision numbers |
| 产品 V3 核心 | PRODUCT V3 core | CPU core name |
| 智算 | AI compute / Intelligent compute | Business direction |
| 通算 | general-purpose compute | Business direction |
| 双核一桥 | dual-core-plus-bridge architecture | Architecture feature |

> Replace `PRODUCT` / `PROD` with your actual product codename. Internal
> project codenames (lakes, rivers, etc.) should be replaced with your own
> identifiers or left as placeholders.

---

## Business Terms

| Chinese | English |
|---------|---------|
| 需求 | requirement |
| 父需求 / 子需求 | parent requirement / sub-requirement |
| 优先级 | priority |
| 验证 | verification |
| 交付 | delivery |
| 集成 | integration |
| 文档基线 | document baseline |
| 业务验收 | business acceptance |
| 业务测试用例 | business test case |
| 红线 / 考核红线 | bottom line / KPI threshold |
| 异步桥 | asynchronous bridge |
| 顾问 | (technical) advisor / consultant |
| 合作方 | partner |
| 联盟 | alliance / consortium |
| 基金会 | foundation |
| 任务组 | Task Group (TG) |
| 标准 | standard / specification |
| 批准 | ratify / ratification |
| 公开评审 | public review |
| 冻结 | freeze |

---

## Technical Terms (Keep Original, Do Not Translate)

These are universal RISC-V / computer-architecture terms. Keep them in English
in both Chinese and English documents to avoid ambiguity.

```
ARM Neoverse
RVA23 / RVA22 / RVB23
H-extension / H-ext
AIA / IMSIC / APLIC
IOMMU / CoVE / Smmtt
CBQRI / CHI / NUMA
Sscofpmf / Sstc / SMT
SR-IOV / PASID
RAS / RERI
CXL / DMA / TLB / LLC
KVM / VM / GIC
SMMUv3 / VT-d / AMD-Vi
Zacas / CMO
Zicbom / Zicboz / Zicbop
V-ext / RVV
Zk / Zkne / Zknd / Zknh
Zvbb / Zvbc / Zvkn
Zvkng / Zvksg
PMU / PMC
perf / perf stat / perf record
```

> When a new ISA extension appears in a TG draft (see Part 1), add it to this
> list so all translators use the same spelling.

---

## Translation Conventions

1. **First occurrence**: full name + abbreviation in parentheses.
   - Example: "Advanced Interrupt Architecture (AIA)"
2. **Subsequent occurrences**: abbreviation only.
3. **Revision numbers**: never translate. Revision identifiers (e.g., R1, R2, R3) stay as-is.
4. **Chinese-only business terms**: translate to the closest English equivalent
   and record it in the glossary. If no clear equivalent exists, keep the pinyin
   + a footnote explanation.
5. **ISA extension names**: always keep original spelling. Never translate
   "Zicbom" as anything other than "Zicbom".

---

## Placeholder Reference Table

This file uses the following redaction placeholders. Replace them with your
actual identifiers before use in a production context.

 | Placeholder | Replaces | Example replacement |
 |-------------|----------|---------------------|
 | `RVI-Liaison` | Foundation liaison | (real person name) |
 | `Internal-Advisor` | Internal advisor | (real person name) |
 | `Internal-Director` | Internal director | (real person name) |

> **XiangShan** is kept as-is because it is a public open-source RISC-V CPU
> project. It is not an internal codename.

---

## Revision Log

| Version | Date | Change |
|---------|------|--------|
| v1.0 | 2026-07-21 | Initial release. Merged and generalized from three internal source skills: (1) TG tracking practice, (2) partner communication nine-dimension framework + seven-step process, (3) bilingual translation glossary. All organization, partner, product, and person names redacted. |
