---
name: spec-learning
version: "1.0"
updated: "2026-07-21"
description: >
  Unified RISC-V specification navigation and Tier-1 architecture mapping skill.
  Combines a 5-layer document priority hierarchy (UDB → Normative → Ratified →
  post-v0.7 → Un-ratified) with evidence-first, tier-1-source mapping methodology
  for comparing RISC-V ISA extensions and platform specs to Arm / x86 analogues.
  Activate whenever a task involves looking up RISC-V specs, mapping features
  across architectures, or producing source-backed comparison tables.
---

# Skill: spec-learning  (v1.0)

> **Merged from**: `riscv-docs` (spec document navigation) + `riscv-tier1-mapping`
> (evidence-first mapping methodology). This unified skill provides both the
> **where** (document hierarchy) and the **how** (tier-1 mapping discipline).

---

## When to Activate

Activate for any task that:

- Looks up a RISC-V specification, extension definition, CSR, or instruction semantic.
- Maps a RISC-V ISA extension, profile requirement, or SoC feature to an Arm or x86 analogue.
- Verifies a feature's ratification status, platform requirement level (MUST/SHOULD/MAY), or Linux/toolchain support.
- Produces comparison tables, timelines, or checklists for architecture analysis or competitive reviews.
- Answers whether a RISC-V feature closes a specific Arm/x86 gap.
- Drafts or reviews content that must be cited as T1-VERIFIED in any RISC-V architecture document.

Do **not** activate for purely narrative, creative, or non-architectural tasks.

---

## Part 1: RISC-V Specification Document Navigation

> **Goal**: Provide structured access to RISC-V specification documents based on
> a 5-layer priority hierarchy, enabling rapid citation and reference extraction.

**Priority Order**: UDB → Normative Rules → Ratified Specs → post-v0.7 Specs → Un-ratified Specs

### Layer 1: UDB (Unprivileged/Debug specification baseline)

**Source**: https://github.com/riscv/riscv-unified-db

**Official Deployment**: https://riscv.github.io/riscv-unified-db/

**Core Documents**:

1. **UnifiedDB Manual (HTML)**
   - URL: https://riscv.github.io/riscv-unified-db/manual/html/index.html
   - Description: Machine-readable source of truth for all RISC-V specs
   - Contains: Extensions, instruction specs, CSR specs

2. **Instruction Appendix PDF**
   - URL: https://riscv.github.io/riscv-unified-db/pdfs/instructions_appendix.pdf
   - Description: Complete instruction set reference

3. **Resolved Specification YAML**
   - URL: https://riscv.github.io/riscv-unified-db/resolved_spec/index.yaml
   - Description: Structured data for programmatic access

**Citation Priority**: HIGHEST — Canonical machine-readable baseline

**Note**: UDB-generated specs are marked "UNOFFICIAL" pending RVI clarification

---

### Layer 2: Normative Rules (RVI normative constraints)

**Source**: https://github.com/riscv/docs-resources

**Guideline**: normative-rules.md

**Core Documents**:

1. **Normative Rules Guidelines**
   - URL: https://github.com/riscv/docs-resources/blob/main/normative-rules.md
   - Description: Defines MUST/SHOULD/SHALL requirements for compliance

2. **Tagged Normative Content in ISA Manuals**
   - Pattern: `norm:Zilsd_reg_pairs`, `norm:exts:F_D:num-fregs`

3. **CHERI Normative Tagging Example**
   - URL: https://github.com/riscv/riscv-cheri/blob/main/tagging_normative_rules.adoc
   - Description: Best practices for normative rule extraction

**Citation Priority**: SECOND — Binding constraints vs informative content

**Usage**: Validate implementation compliance, extract must/should rules

---

### Layer 3: Ratified Specifications

**Source**: https://github.com/riscv/riscv-isa-manual

**Ratified Tags**: freeze-freeze, frozen

**Core Documents**:

1. **RISC-V ISA Manual (User-Level)**
   - URL: https://github.com/riscv/riscv-isa-manual/releases
   - Description: Check releases for "Ratified" tags

2. **Privileged Architecture Manual**
   - URL: https://github.com/riscv/riscv-isa-manual/releases
   - Description: Architecture-level specifications

3. **Extension Specs (e.g., Zicond, Zicfilp)**
   - URL: https://github.com/riscv/riscv-isa-manual
   - Description: Individual ratified extension specifications

**Citation Priority**: THIRD — Official RVI ratified standards

**Identification**: Look for "Ratified" tag in GitHub releases or manual metadata

---

### Layer 4: post-v0.7 Specifications

**Source**: https://github.com/riscv/riscv-isa-manual

**Criteria**: Versions after v0.7 but not yet ratified

**Core Documents**:

1. **Draft ISA Manuals**
   - URL: https://github.com/riscv/riscv-isa-manual
   - Description: Post-v0.7 draft specifications

2. **Extension Drafts**
   - URL: https://github.com/riscv/riscv-isa-manual
   - Description: Post-v0.7 extension drafts

**Citation Priority**: FOURTH — More mature than v0.7

**Usage**: Reference for newer features not yet ratified

---

### Layer 5: Un-ratified Specifications

**Source**: https://github.com/riscv/riscv-isa-manual

**Criteria**: v0.7 or earlier, or explicitly un-ratified

**Core Documents**:

1. **Legacy v0.7 Specifications**
   - URL: https://github.com/riscv/riscv-isa-manual
   - Description: Older specification versions

2. **Experimental Extensions**
   - URL: https://github.com/riscv/riscv-isa-manual
   - Description: Un-ratified or experimental features

**Citation Priority**: FIFTH — Least authoritative

**Usage**: Last resort reference only

---

### Document Navigation Query Strategy

When querying RISC-V documentation:

1. **Start with Layer 1 (UDB)**: Check UnifiedDB for machine-readable specs
2. **Check Layer 2 (Normative)**: Extract MUST/SHOULD constraints
3. **Verify with Layer 3 (Ratified)**: Confirm with official RVI ratified specs
4. **Fallback to Layer 4 (post-v0.7)**: If no ratified spec exists
5. **Last resort Layer 5 (Un-ratified)**: Only if no other source available

### Citation Format

Always cite with layer priority:

```
[Layer 1] UDB: <URL> - <description>
[Layer 2] Normative: <URL> - <description>
[Layer 3] Ratified: <URL> - <description>
[Layer 4] Post-v0.7: <URL> - <description>
[Layer 5] Un-ratified: <URL> - <description>
```

---

## Part 2: Tier-1 Source Mapping Methodology

> **Goal**: Perform tier-1-source, evidence-first RISC-V architecture mapping
> applicable to any RISC-V server, client, or embedded platform analysis.
>
> **Never improvise the architecture. When in doubt, stop and say "UNVERIFIED".**

### 2.1 Source Hierarchy (Hard Requirement)

#### Tier-1 (Primary) — always fetch the LATEST version

For every tier-1 source listed below, you **MUST fetch or verify the current version** before grounding a claim.
Do not rely on a cached or version-pinned copy if a newer ratification or release may exist.

| Category | Canonical URL / location | What to use |
|---|---|---|
| **RISC-V Ratified Extensions** | https://riscv.atlassian.net/wiki/spaces/HOME/pages/16154769/RISC-V+Technical+Specifications (primary) + https://docs.riscv.org/reference/ (live specs) | Check these pages first for ratification dates and current extension status. The old lf-riscv domain now redirects to riscv.atlassian.net. |
| **RISC-V Technical Specs Archive** | https://riscv.atlassian.net/wiki/spaces/HOME/pages/16154899/RISC-V+Technical+Specifications+Archive | Use for spec PDFs and version history. |
| **RISC-V Unified Database (live specs)** | https://docs.riscv.org/reference/ | Privileged spec, Unprivileged spec, Svpbmt, Ssqosid, Pointer Masking v1.0, Zicfiss/Zicfilp, all ratified extensions. Always link to the specific section (e.g., Priv §3.1.6.5, Unpriv Appendix A, Unpriv §2.1.7). |
| **RVA23 Profile** | https://github.com/riscv/riscv-profiles/blob/main/src/rva23-profile.adoc (repo archived Apr 2026 — read-only) | Mandatory/optional/expansion sets; ratified Oct 2024. Repository archived 2026-04-22; current profile content at docs.riscv.org/reference/. |
| **RISC-V Server Platform Spec** | https://github.com/riscv-non-isa/riscv-server-platform | v1.0 ratified May 2026. Always fetch the latest release tag. |
| **RISC-V Server SoC Spec** | https://github.com/riscv-non-isa/riscv-server-soc | v1.0 ratified Feb 2025. Check for any errata or point releases. |
| **RISC-V BRS** | https://github.com/riscv-non-isa/riscv-brs | v1.0 ratified Aug 2025. |
| **RPMI** | https://github.com/riscv-non-isa/riscv-rpmi | v1.0 ratified Jul 2025. Fetch the latest. |
| **CBQRI** | https://github.com/riscv-non-isa/riscv-cbqri | v1.0 ratified Jun 2024. |
| **RERI** | https://github.com/riscv-non-isa/riscv-ras-eri | v1.0 ratified May 2024. |
| **Self-hosted Trace** | https://github.com/riscv/self-hosted-trace | Draft v0.6.1 Mar 2026; target ratification 2026-2027. Always fetch latest tag. |
| **E-Trace / N-Trace / TCI** | https://github.com/riscv-non-isa/riscv-trace-spec | TCI v1.0 ratified Nov 2024. Check latest for N-Trace errata. |
| **Smmtt / Smmpt** | https://github.com/riscv/riscv-smmtt | v0.49 draft (Jan 2026); ARC freeze ongoing. Fetch latest release. |
| **CoVE / AP-TEE** | https://github.com/riscv-non-isa/riscv-ap-tee | Development draft. Always fetch latest. |
| **Memory Tagging TG (Zimt/Svatag)** | https://github.com/riscv-admin/riscv-memory-tagging | v0.2 draft Sep 2025; spec freeze target 2028. |
| **IME (Integrated Matrix Extension)** | RISC-V Ratified Extensions page above + IME TG GitHub | Ratification plan 2026 H2. Fetch current draft from TG repo. |
| **AME (Attached Matrix Extension)** | https://github.com/riscv/riscv-ame (spec) + https://github.com/riscv-admin/attached-matrix-extension (admin) + UDB `Zvame` | **Draft v0.2** (2026-06-15), ~188 pp. Freeze target end 2026; Public Review 2027-02-07; BoD 2027-04-29. TG RVG-135, Chair: Zhong Su (Alibaba), VC: Derek Hower (Qualcomm), 233 members. **2026-07-01: elementwise instructions added** (PR #11, scope expansion). Software: RACE-org/riscv-ame-{llvm,triton,gem5,matrix-core} (early-stage). Dashboard: https://riscv.github.io/adm-spec-dashboard. |
| **Arm ARM (DDI0487)** | https://developer.arm.com/documentation/ddi0487 | Memory model B2.10.2, Device types, MPAM — comparator only. Fetch the latest issue. |
| **Arm Neoverse TRMs** | https://developer.arm.com/documentation (search Neoverse V2/V3 TRM) | Comparator only. |
| **Arm CMN TRMs** | CMN-700: https://developer.arm.com/documentation/102337; CMN S3: https://developer.arm.com/documentation/107858 | Comparator only for NoC/QoS analysis. |
| **Intel/AMD x86 manuals** | Intel SDM, AMD APM (official sites) | Comparator only for TSO, DDIO, RAS, PMU. |
| **Linux mainline** | https://lwn.net (for RFCs and deep analysis) + https://www.cnx-software.com (release summaries) | Kernel version support per RISC-V feature; LWN CBQRI RFC (lwn.net/Articles/1055163), LWN RAS RFC (lwn.net/Articles/1053519). |
| **LLVM / GCC / glibc** | Official upstream repos and changelogs | ABI/codegen evidence for RISC-V features. |

> **AME vs IME**: AME = attached co-processor with tile registers; IME = integrated into pipeline. Both target ML/HPC. AME tracked as `Zvame` in UDB.

**Always-fetch-latest rule**: Before stating a ratification status, spec version, or Linux support milestone, query the canonical URL above. Do not use a stale version. If the URL is inaccessible, state "COULD NOT VERIFY — last known: [version/date]" and flag it for human review.

#### Tier-2 (Cross-check) — corroborate only, never contradict tier-1

- Reputable vendor blogs: Arm, SiFive, Cadence, Synopsys, AMD, NVIDIA …
- Conference papers: XiangShan Summit Europe 2024, CARRV, LPC 2025 QoS slides (lpc.events/…/RISC-V%20QoS%20LPC%202025.pdf), RISC-V Summit …
- High-quality third-party: Chips and Cheese, kernel deep dives, Canonical RISC-V blog …

Rules:
- Never contradict tier-1; if tier-2 disagrees, state it and side with tier-1.
- Tag as **T2-CROSS-CHECKED** and cite the specific blog/paper.
- May suggest implementation strategies — mark them **INTERPRETIVE**.

#### Tier-3 (Disallowed for ground truth)

Do not use: unvetted forums, unsourced GitHub gists, marketing slides, rumors, leaks.
If unavoidable, label "UNVERIFIED" and explain why no tier-1/tier-2 source was available.

---

### 2.2 Canonical Mapping Dimensions

Apply all six dimensions for every comparison. Skip none.

| # | Dimension | Key questions |
|---|---|---|
| 1 | Ratification & maturity | Ratified? Draft? Charter-only? Profile status (RVA23 mandatory/optional/expansion)? Platform spec (Server Platform / Server SoC / BRS / RPMI) status? What is the ETA? |
| 2 | ISA semantics | Instruction/CSR level behavior? Interaction with RVWMO, RVTSO, IO ordering channels (0/1/N), PMAs (Priv §3.1.6)? |
| 3 | Platform requirements | MUST / SHOULD / MAY / guidelines-only in Server Platform / Server SoC specs? Quantitative constraints (HPM counts, RCID/MCID ranges, timer precision, interrupt file counts, VLEN floors)? |
| 4 | Software evidence | Linux version? Role (KVM, IOMMU, resctrl, APEI, PMEM, CFI)? Selftests, perf events, kconfig flags? Toolchain (LLVM/GCC/glibc)? |
| 5 | Competitive anchor | Arm analogue (version-tagged), x86 analogue, CXL/UCIe if relevant. Is RISC-V stronger / weaker / just different? |
| 6 | Deployment intent | Server / client / embedded? Agentic AI / matrix / confidential computing / PMEM? Near-term / mid-term / long-term horizon? |

---

### 2.3 Required Behaviors by Theme

#### Memory Model and IO Ordering
- **Ground truth**: Priv spec §3.1.6.5 (PMAs, IO ordering channels), Unpriv spec Appendix A (RVWMO PPO rules 3–5), Unpriv §2.1.7 (FENCE IO bits), Svpbmt v1.0.
- **Channel semantics** (Priv §3.1.6.5): Channel 0 = point-to-point strong ordering within one IO region only; Channel 1 = global strong ordering across all IO regions (equivalent to FENCE io,io before and after); Channel N ≥ 2 = grouped strong ordering across same-channel regions.
- **Map to Arm Device types** (DDI0487 B2.10.2): nGnRnE (no-Gather, no-Reorder, no-EarlyWriteAck) ↔ Channel 1; nGnRE ↔ Channel 0 with endpoint ACK required; nGRE ↔ relaxed-ordered IO with barriers; GRE ↔ relaxed IO RVWMO. State Gathering, Reordering, and Early Write Ack dimensions explicitly.
- For Svpbmt PBMT=IO: state interaction with IO channel 0 strong ordering.
- **PCIe caveat**: RVWMO ordering may not extend beyond the RISC-V core boundary onto PCIe; platform-specific rules apply (Unpriv Appendix A platform-specific boundary note).
- Do not weaken or strengthen ordering guarantees beyond spec language. Tag: T1-VERIFIED with the specific section URL.

#### QoS: MPAM vs Ssqosid + CBQRI
- **Core evidence (T1)**: Ssqosid v1.0 (ISA, Jun 2024), CBQRI v1.0 (non-ISA, Jun 2024), ACPI RQSC, Server Platform QoS requirements, LWN CBQRI RFC Jan 2026 (lwn.net/Articles/1055163).
- **Arm comparator**: MPAM v1.1 (ARM Neoverse V2 TRM §A.13 — up to 511 PARTID, 32 PMG, per-EL CSR, resctrl 10 partitions).
- Distinguish: ISA-visible QoS CSR (Ssqosid srmcfg) | platform-level spec requirements | OS/user-space control via Linux resctrl RFC.
- Flag: Linux resctrl RFC patch series Jan 2026; estimated merge Linux 6.15–6.16 (T1-VERIFIED: LWN).

#### Security, CFI, and Memory Tagging
- **RISC-V anchors (T1)**:
  - CFI: Zicfiss (shadow stack, Jun 2024, RVA23 Expansion Optional), Zicfilp (landing pad, Jun 2024, RVA23 Expansion Optional).
  - Pointer Masking: Zpm family (RVA23 Mandatory) — tag bits in pointer, hardware ignores during address resolution; does NOT provide tag mismatch detection by itself. Fetch Pointer Masking v1.0 spec (docs.riscv.org/reference/isa/priv/zpm.html) for latest.
  - Memory Tagging: Zimt/Svatag, charter May 2024, v0.2 draft Sep 2025, spec freeze 2028. Pointer Masking does not substitute for full hardware memory tagging.
  - CoVE / AP-TEE: riscv-ap-tee dev draft. Smmtt v0.49 draft (Jan 2026). Fetch latest.
- **Arm comparators**: PAC+BTI (CFI) vs Zicfiss/Zicfilp; MTE3 (ARM Neoverse V2, FEAT_MTE3, 4-bit tags, 16 B granules, tag mismatch → Data Abort/SError) vs Pointer Masking + Zimt TG.
- **x86**: CET shadow stack vs Zicfiss; ENDBRANCH vs Zicfilp.
- Separate: mandatory in RVA23 profile | optional/expansion | future horizon.
- State ratified vs draft status for every cited RISC-V item.

#### Trace, PMU, and Observability
- **RISC-V baseline (T1)**:
  - Self-hosted Trace: Smstrc/Ssamb, draft v0.6.1 Mar 2026, target ratification 2026–2027. Fetch latest from github.com/riscv/self-hosted-trace.
  - E-Trace 2.0 (Jun 2025), N-Trace 1.0 (Nov 2024): github.com/riscv-non-isa/riscv-trace-spec.
  - TCI v1.0 (Nov 2024), Ssctr/Smctr CTR (Nov 2024, RVA23 Optional/ARC Aug 2025). Sdtrig / Sdext Debug Spec 1.0 (ratified Feb 2025).
  - Server SoC HPM requirements: SPM010–060. Per-hart PMU: Zicntr/Zihpm CSRs + SBI PMU extension.
- **Arm comparators**: ETM / N-Trace / BRBE (ISA) / CMN DTM/DTC (fabric) — version-tag each. CMN-700 vs CMN-S3 PMU differences.
- **x86**: Intel Processor Trace, AMD uProf, Uncore PMU.
- Emphasize: per-hart vs fabric/NoC trace; on-die vs off-die; required vs optional in Server Platform/SoC spec.
- SoC design pattern: E-Trace encoder per-hart → ATB/AXI-Stream → Trace sink DRAM; NoC DTM/DTC trace.

#### Confidential Computing and CoVE
- **RISC-V anchors (T1)**:
  - CoVE (riscv-ap-tee) dev draft: TSM/TVM model, Memory Integrity Engine (replay/splicing protection via AES-GCM / HMAC-SHA256 on DDR controller).
  - Smmtt v0.49 / Smmpt: supervisor domains, memory protection table, ARC freeze ongoing. Fetch latest release.
  - Server SoC SEC030 (DRAM encryption), SEC020 (PCIe IDE), COVI interrupt isolation.
- **Competitive map**:
  - Intel TDX: SEAM/TSM role, SEPT page table, TDCALL/SEAMCALL ISA.
  - AMD SEV-SNP: RMP (Reverse Map Table), SNP page validation, PSP secure processor.
  - Arm CCA: RMM/Realm, GPT (Granule Protection Table), S-EL2.
- Map axes: trust anchor, MPT/page-table structure, interrupt/IO isolation mechanism.
- State ratified vs draft for each RISC-V item.

#### Matrix / Vector / Crypto Extensions
- **RISC-V (T1)**:
  - RVV 1.0: RVA23 Mandatory, VLEN ≥ 128. Fetch latest VLEN floor for any specific platform profile.
  - IME: Ratification plan 2026 H2 (Aug 27, 2026 target). Fetch TG repo for current draft.
  - Vector crypto: Zvkn (Jun 2023, RVA23 Mandatory), Zvkb (Mandatory), Zvbc (Development Option), Zvkng/Zvksg (post-RVA23).
  - BF16: ZvfbfminZvfbfwma (Jun 2024, RVA23 Expansion Options).
- **Arm**: SVE2 (Neoverse V2 — 4× 128-bit pipes, FEAT_SVEAES/SHA3/SM4); SME2 (matrix tiles).
- **x86**: AVX-512 (Intel Xeon); AMX (Intel, matrix tiles, 2D register).
- Map: element types/precisions (fp64/fp32/bf16/fp16/fp8/int8), register organization (vector length vs matrix tiles vs LMUL), profile integration level.

#### MMIO Outstanding and IO Ordering
- Outstanding MMIO loads/stores: RISC-V does not define a per-channel outstanding count the way Arm CMN tracks OT. State this gap explicitly for any IO coherency analysis.
- When mapping RISC-V IO channel N to Arm Device types, note that Arm nGnRnE maps closest to Channel 1 (global strong ordering), but does not have an explicit per-region "channel number" concept.
- The Svpbmt PBMT=NC (non-cacheable, non-idempotent) interaction with FENCE and outstanding loads is architecturally distinct from PBMT=IO. State this clearly.
- Reference: Priv §3.1.6.5, Unpriv Appendix A, DDI0487 B2.10.2.

---

### 2.4 Output Requirements

1. **Tag every factual claim** as:
   - `T1-VERIFIED <spec name + section or URL>`
   - `T2-CROSS-CHECKED <source name + URL>`
   - `UNVERIFIED` (explain why no tier-1/tier-2 source was available)

2. **Always-fetch-latest**: Before finalizing any claim about ratification status, platform spec version, or Linux support, verify against the canonical URL in §2.1. If you detect a version newer than what was previously cited, flag it as **SPEC-UPDATE-ALERT**.

3. **Produce explicit mapping tables** per theme. Minimum columns:
   `Feature | RISC-V (spec + status) | Arm/x86 analogue | Platform requirement | Software evidence | Tag`

4. **Include timelines with status flags**:
   - `RATIFIED <date>` / `PUBLIC REVIEW <date>` / `DRAFT <date>` / `RATIFICATION PLAN <target date>`

5. **Scope every statement** to a named profile (e.g., RVA23) and platform spec version (e.g., Server Platform v1.0, Server SoC v1.0). Never say "all RISC-V".

6. **Separate the concern** per claim:
   - Programmer-visible semantics
   - Microarchitectural implementation pattern
   - Platform integrator requirement (MUST/SHOULD/MAY)
   - OS / toolchain support expectation

7. **End with a verification checklist** the reader can use to validate the mapping, including:
   - Which spec URLs to re-check before the next milestone review.
   - Any open architectural gaps or unresolved questions identified during the analysis.

---

### 2.5 Agent Roles (Internal Coordination)

| Role | Owns |
|---|---|
| ISA & Memory Model Lawyer | Priv/Unpriv spec, PMAs, RVWMO, IO channels 0/1/N, Svpbmt, FENCE semantics; prevents over-claims; enforces always-fetch-latest for ISA specs |
| Platform & QoS Architect | Server Platform Spec, Server SoC Spec, BRS, RPMI, Ssqosid+CBQRI+RQSC, AIA, Arm MPAM/CMN, x86 Uncore; version-checks platform spec before citing requirements |
| Security & Confidential Computing Analyst | Zicfiss/Zicfilp, Pointer Masking Zpm, Zimt/Svatag, CoVE/Smmtt, DRAM encryption, PCIe IDE, RoT |
| Observability & Software Integrator | Linux kernel version evidence, perf/PMU/trace, IOMMU/resctrl/APEI, toolchain support; checks LWN for latest RFC merge status |
| Competitive Systems Analyst | Arm Neoverse V2/V3, Intel Xeon SPR/GNR, AMD EPYC Genoa/Turin, NVIDIA Grace, CXL/UCIe-based systems, RISC-V vendor SoCs (SiFive P870, Ventana Veyron C1, XiangShan) — always tier-1 grounded, version-tagged |

---

## Unified Workflow

For every request activating this skill:

1. **Identify scope**: which features, specs, and profiles are in scope.
2. **Navigate the document hierarchy** (Part 1): locate the authoritative source starting from UDB (Layer 1) down.
3. **Fetch-or-check-latest**: for each tier-1 source you will cite, verify the current version against §2.1 canonical URLs before proceeding.
4. **Map along all six canonical dimensions** (§2.2).
5. Use tables and dense paragraphs. Avoid narrative bloat.
6. **State explicitly**:
   - Where RISC-V is stronger, weaker, or different vs Arm/x86.
   - Where ecosystem/software gaps are the bottleneck vs ISA/platform gaps.
7. **Emit SPEC-UPDATE-ALERT** if you discover a spec version newer than what was previously cited.
8. **End with the verification checklist** including spec URLs to re-check and open architectural gaps.

If a request requires speculation beyond tier-1 plus disciplined tier-2 cross-checks — **stop, explain the limitation, and do not guess**.

---

## Update Strategy

### When to Update

1. **Monthly Check**: Review RVI GitHub releases for new ratified specs
2. **UDB Updates**: Track riscv-unified-db repository changes
3. **Normative Rules**: Monitor docs-resources repository updates
4. **User Request**: Update when specific document links break

### Update Procedure

1. Check GitHub releases: https://github.com/riscv/riscv-isa-manual/releases
2. Verify UDB deployment: https://riscv.github.io/riscv-unified-db/
3. Review normative rules: https://github.com/riscv/docs-resources
4. Update document URLs and descriptions accordingly

---

## Quality Assurance

### Verification Checklist

- All URLs are valid and accessible
- Priority order is maintained (UDB → Normative → Ratified → post-v0.7 → Un-ratified)
- Document descriptions are accurate
- Ratified tags are correctly identified
- Normative rule patterns are current

### Common Issues

- **Broken Links**: Verify RVI repository structure changes
- **Stale Specs**: Check for newer ratified versions
- **Conflicting Information**: Prioritize higher layers over lower layers

---

## Resources

### Primary RVI Repositories

- riscv-unified-db: https://github.com/riscv/riscv-unified-db
- riscv-isa-manual: https://github.com/riscv/riscv-isa-manual
- docs-resources: https://github.com/riscv/docs-resources
- riscv-cheri: https://github.com/riscv/riscv-cheri

### Official Documentation

- RVI Documentation: https://riscv.org/technical/specifications/
- UDB Deployment: https://riscv.github.io/riscv-unified-db/

---

## Notes

- UDB-generated specs marked "UNOFFICIAL" pending RVI clarification
- Normative rules extraction is critical for compliance validation
- Always prioritize ratified specs over draft versions
- Layer priority hierarchy MUST be strictly followed
- Never improvise the architecture — when in doubt, say "UNVERIFIED"
