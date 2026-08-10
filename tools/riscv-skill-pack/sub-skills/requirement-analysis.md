---
name: requirement-analysis
description: >
  End-to-end RISC-V CPU requirement skill covering three complementary modes:
  (1) Requirement analysis workflow — ingest external specs (RVA23, SP3, RAS,
  IOMMU, CXL, etc.), produce feature-mapping CSVs with source tags and mapping
  types, surface red-line conflicts, split deliverables into consultant-visible
  and internal versions, and package a versioned release zip.
  (2) Feature list reference — 20 SoC feature items, 66 Server Platform / SoC
  requirements, 28 Arch Freeze items, work-item tracking, previous revision→future revision delta, and
  the current revision/next revision/future revision feature horizon.
  (3) Requirement query — intelligent lookup over requirement document sets with
  structured answers, member-need cross-referencing, and spec-priority mapping.
license: MIT
metadata:
  author: riscv-skill-pack
  version: '1.0'
  domain: cpu-architecture
  merged_from:
    - kunminghu-requirement-analysis (v1.0)
    - kunminghu-v3-feature-list (v1.0)
    - kmh-v3-requirement (v1.0)
---

# Requirement Analysis Skill

> **Redacted template.** All internal codenames, member names, and person names
> have been replaced with placeholders (`MEMBER-XX-XXX`, `ORG-00`, `RVI-XXX`).
> Replace placeholders with your project's actual identifiers before use.

---

## When to Use This Skill

Use this skill when the user asks you to:

**Requirement Analysis Workflow (Part 1)**
- Build or refresh a CPU requirement matrix (mapping external specs → internal feature list).
- Run a gap analysis for a release (V2, V3, V4, …).
- Freeze a release gate's feature list (e.g., Rx Arch Freeze, Rx Full Feature, Rx Tape-out Ready).
- Surface red-line conflicts between consultant input and internal decisions.
- Package a consultant-visible release zip while keeping internal-only material intact.
- Update an existing mapping CSV without losing the frozen baseline naming.

**Feature List Query (Part 2)**
- Look up whether a specific feature is On-SoC / Hybrid / Not Now / current revision / next revision / future revision.
- Verify the previous revision → future revision delta (what is new, what carries over, what is deferred).
- Answer which work item maps to which SoC feature.
- Check MUST / SHOULD / MAY / P0 / P1 / P2 / P3 priority for any future revision item.
- Produce a structured view of the 20 SoC features, 66 platform requirements, or 28 Arch Freeze items.
- Answer competitor positioning against future revision (SiFive P870-D, NVLink Fusion, Arm Neoverse V2/V3, Intel Xeon, AMD EPYC).
- Schedule and horizon questions (current revision Arch Freeze, next revision full feature, future revision code freeze).

**Requirement Document Query (Part 3)**
- Query requirement documents by ID (e.g., "04-02-01"), by topic ("performance requirements"), or by member need.
- Compare requirement deltas (V2 → V3 increments).
- Compare spec differences (RVA23 mandatory vs Server Platform 1.0).
- Cross-reference member needs across multiple members.

**Trigger phrases**: "Vx requirement", "RVA23 mapping", "SP3 mapping", "Arch Freeze", "consultant release", "_internal_", "requirement query", "feature list", "member needs".

---

## Part 1: Requirement Analysis Workflow

### Project Conventions (HARD RULES)

These rules come from prior sessions. Follow them unless the user explicitly overrides.

1. **Project codename uses the version only**: `Xiangshan-KMH V3` (not `V3R1`, not `V3R3`). Spell the codename as configured in your project settings.
2. **Release gates** use the `Rx` suffix:
   - `Rx Arch Freeze` — locks features with deep micro-arch impact.
   - `Rx Full Feature` — implementation complete for all in-scope features.
   - `Rx Tape-out Ready` — verification + DV sign-off.
3. **CSV file names keep the gate suffix that froze them.** Example: a mapping frozen at R1 stays `Xiangshan-KMH-V3R1-requirement-with-mapping.csv` even after R2/R3 narratives are added.
4. **Internal-only files MUST be prefixed `_internal_`.** Consultant-visible files have no prefix.
5. **Never delete content** when splitting into consultant + internal versions. Crop the consultant copy from the internal master via line-range extraction; keep the full internal master intact.
6. **Preserve red-line conflicts** in the consultant Master Report (typically §1.1).
7. **Do not translate** Chinese sections unless the user explicitly asks. English-only deliverables (README, frontmatter) are the exception.
8. **Source tags** are required on every claim:
   - `[T1-VERIFIED]` — primary spec or internal authoritative source.
   - `[T2-CROSS-CHECKED]` — confirmed by >=2 secondary sources.
   - `[UNVERIFIED]` — single source or inference.
9. **Mapping types** are required on every row of the mapping CSV:
   - `[exact]` — 1:1 spec-to-feature mapping.
   - `[approximate]` — semantic match, naming differs.
   - `[composite]` — feature combines multiple spec items.
   - `[N/A]` — internal feature with no external spec counterpart.
10. **Top-level analysis** is delivered as `L1-investment-and-competition-analysis.md` and `L2-SoC-system-level-analysis.md`. Specialty research (e.g., dynamic-TSO, AME comparison) lives in `04_专题/` and is **excluded from the consultant zip** unless the user asks otherwise.

### Workflow Overview

```
Input baselines (CSVs + specs)
  |
  |-- Phase 1: Ingest & normalize    -> references/01-mapping.md
  |-- Phase 2: Build mapping CSV     -> references/01-mapping.md + scripts/build_mapping.py
  |-- Phase 3: Red-line review        -> references/02-red-lines.md
  |-- Phase 4: Master Report split   -> references/03-delivery-packaging.md
  |-- Phase 5: CSV cleanup            -> scripts/clean_csv.py
  `-- Phase 6: Consultant zip         -> references/03-delivery-packaging.md
```

Read each `references/*.md` file when you reach that phase — they hold the detailed instructions and decision rules.

### Phase 1 — Ingest & Normalize Inputs

Inputs the user typically provides for a new Vx round:

- Top-level requirement CSV (internal): `_internal_Xiangshan-KMH-Vx-top-level-requirement.csv`
- ISA / non-ISA spec extract: `_internal_Xiangshan-KMH-Vx-ISA-non-ISA-spec-requirement.csv`
- External spec references: RVA23 profile, RVB23, SP3 server platform, RAS APEI, IOMMU, CXL 3.0/3.1, MPAM/CBQRI.
- Prior version baseline (previous version checklists, previous revision SP2 feature list).

Steps:

1. Confirm the version label (V3, V4, …) and the active gate (R1/R2/R3).
2. Place all internal inputs under `01_输入基线/` with `_internal_` prefix.
3. Identify the consultant-visible competition table (e.g., `Xiangshan-KMH-Vx-requirement-competition-table-vN.csv`) — this is shipped without the prefix.

Output of this phase: a clean `01_输入基线/` directory and a documented input list in the Master Report §0.

### Phase 2 — Build the Mapping CSV

Read `references/01-mapping.md` for the full column spec and tag rules.

The mapping CSV must have these 12 columns (header carries a UTF-8 BOM so Excel renders Chinese correctly):

```
Feature ID, Feature Name, Category, Spec Source, Spec Section,
Mapping Type, Source Tag, Internal Owner, Status (R1/R2/R3), Priority, Notes, Red-Line Flag
```

Each row gets a `[mapping type]` and `[source tag]` from the conventions above. Rows with red-line conflicts get `Red-Line Flag = Y`.

### Phase 3 — Red-Line Review

Read `references/02-red-lines.md` for examples and resolution patterns.

A red-line conflict is any case where:

- Consultant input contradicts an internal decision (frozen at a prior gate).
- Two external specs give incompatible requirements (e.g., RVA23 vs SP3 vs CXL).
- A feature's gate assignment (R1 vs R2 vs R3) is disputed.

For every red-line:

1. Record both positions verbatim.
2. Mark the conflict in Master Report §1.1.
3. Do not auto-resolve — surface for the user's decision.
4. If the user defers, mark the row `[讨论中]` (under discussion) in the gate column.

### Phase 4 — Master Report Split

Read `references/03-delivery-packaging.md` for the full file layout and line-range extraction approach.

Two outputs:

- `_internal_Vx_Master_Report.md` — full internal master (kept intact).
- `Vx_Master_Report.md` — consultant version, cropped via line-range extraction. Always keeps §0 (overview + milestone table + Rx micro-arch list) and §1.1 (red-lines).

Build via `scripts/build_consultant_report.py`. Specify keep-ranges as `(start, end)` line tuples. Never edit the consultant report by hand-typing — always re-run the script so changes are reproducible.

The §0 milestone table for any Vx looks like:

| Milestone | Date | Scope | Goal |
|---|---|---|---|
| VxR1 — Arch Freeze | YYYY-MM | Lock features with deep micro-arch impact | e.g., SPECINT2006 >= N/GHz |
| VxR2 — Full Feature | YYYY-MM | All N features implemented | Functionally complete |
| VxR3 — Tape-out Ready | YYYY-MM | Verification + DV sign-off | Tape-out gate |

R1 micro-arch list (typical, mark each `[讨论中]` until user signs off): V/RVV, Zvfhmin/Zvbb/Zvkt/Zfa/Zfh, Zacas/Zabha/Zawrs, Dynamic TSO, Svadu/Sv57/PA52/Svvptc/Svnapot/Svinval, Sstc/Sscofpmf/AIA/Sha, Zicfilp/Zicfiss/Zimop/Zcmop, Zicbom/Zicboz/Zicbop/Zic64b, B umbrella/Zicond/Zihintntl/Zihintpause/Zkt.

R2 list (typical): Zvkn/Zvks/Zvfh/Zvfbf*, IOMMU, RAS APEI, CXL 3.0/3.1, NUMA, MPAM/CBQRI, trace.

### Phase 5 — CSV Cleanup

Run `scripts/clean_csv.py` on every consultant-visible CSV before packaging. It:

- Removes fully empty rows.
- Removes fully empty columns.
- Preserves the UTF-8 BOM on the header line.
- Leaves cell content untouched.

### Phase 6 — Consultant Release Zip

Read `references/03-delivery-packaging.md` for the canonical layout. Summary:

```
Xiangshan-KMH-V{N}-Consultant-Release-{YYYYMMDD}/
|-- README_Consultant_EN.md
|-- 00_Overview/
|   `-- V{N}_Master_Report.md
|-- 01_Requirement/
|   |-- Xiangshan-KMH-V{N}R{gate}-requirement-with-mapping.csv
|   |-- RVA23-ServerPlatform-ISA-NonISA-Mapping.csv
|   `-- Xiangshan-KMH-V{N}-requirement-competition-table-v{ver}.csv
`-- 02_Top-level-analysis/
    |-- L1-investment-and-competition-analysis.md
    `-- L2-SoC-system-level-analysis.md
```

Exclude from consultant zip:

- All `_internal_*` files.
- `04_专题/dynamic-TSO-research.md`, `04_专题/AME-comparison-research.md` (and any other specialty research not explicitly approved).

After packaging, share via the asset name `Xiangshan-KMH_V{N}_Consultant_Release` so version history accumulates on the same asset.

### Quick-Start: New Vx Round

1. Confirm version + active gate with the user.
2. Drop input CSVs into `01_输入基线/` with `_internal_` prefix where applicable.
3. Run `scripts/build_mapping.py` -> consultant mapping CSV.
4. Identify red-lines -> update Master Report §1.1.
5. Run `scripts/build_consultant_report.py` -> consultant Master Report.
6. Run `scripts/clean_csv.py` on every consultant-visible CSV.
7. Assemble zip per the Phase 6 layout.
8. Share with name `Xiangshan-KMH_V{N}_Consultant_Release`.

### Anti-Patterns (Do Not Do)

- Translating Chinese content without explicit instruction.
- Renaming a frozen mapping CSV (keep the gate suffix forever).
- Deleting `_internal_*` files when splitting deliverables.
- Including dynamic-TSO or AME research in a consultant zip.
- Auto-resolving red-line conflicts.
- Hand-editing the consultant Master Report instead of re-running the build script.
- Using `V3R1` or `V3R3` as a project codename — it's `Xiangshan-KMH V3`.
- Using the words "scrape" or "crawl" in any user-facing text.

---

## Part 2: Feature List Reference

> This section provides a complete reference of the SoC feature set organized
> across four planes. Replace placeholder version numbers with your project's
> actual release versions.

### Feature List Overview

The target RISC-V server CPU is defined by the future revision Master Report, L2 macro-architecture analysis, and Unified Decision Matrix. The feature set is organized across four planes:

| Plane | Count | Scope |
|---|---|---|
| SoC feature items (On-SoC / Hybrid / Not Now) | 20 | New vs previous revision, carries over, or deferred |
| Server Platform / SoC requirements | 66 | MUST / SHOULD / MAY per specs |
| Arch Freeze new items (vs baseline) | 28 | Added at arch freeze review |
| Open / risk items | ~22 ORG-00 + 13 MC | Tracked in issue tracker / Master Report |

**Source authority**: SoC Next Features 20 (Unified Decision Matrix v3), Master Report v2.2, L2 macro-architecture analysis v1.1 Part D/F, previous revision CSV files.

### 20 SoC Feature Items (future revision Baseline)

These are the 20 new-or-upgraded SoC-level features targeted for future revision, each tagged with implementation scope and previous revision heritage.

#### SoC Core (Top-level)

| ID | Feature | future revision Scope | previous revision Heritage | Key Spec | Priority |
|---|---|---|---|---|---|
| SoC-1 | NoC Trace Support | On-SoC | V2: CPU E-trace only | Server SoC SPM010-030 SHOULD; NoC HPM, DTM/DTC, A | P1 |
| SoC-2 | NoC Management Interface | On-SoC | New | System management IF for NoC | P1 |
| SoC-3 | QoS (CBQRI) | On-SoC | New | CBQRI v1.0; Ssqosid | P0 |
| SoC-4 | CXL 3.0/3.1 | On-SoC | New | CXL BI, Poison, G-FAM | P0 |
| SoC-5 | Always-on Region | On-SoC | New | Low-power always-on domain | P1 |
| SoC-6 | Multi-key Encryption | On-SoC | New | CoVE per-TVM key; AES-XTS-256 | P0 |
| SoC-7 | HW RNG | On-SoC | New | Zkr ISA + HPER090 | P0 |
| SoC-8 | Dynamic TSO | On-SoC | New | Ssdtso; memory model | P1 |
| SoC-9 | PCIe 7.0 | Not Now | — | Deferred to future | — |
| SoC-10 | Shared Unit | On-SoC | Carry from V2 | Enhanced | — |
| SoC-11 | 10ns Timer | On-SoC | Carry from V2 | CTI010 MUST | — |
| SoC-12 | System Power | On-SoC | Carry from V2 | Enhanced | — |
| SoC-13 | Multi-power Domain | On-SoC | Carry from V2 | Enhanced | — |
| SoC-14 | RoT (Root of Trust) | On-SoC | Carry from V2 | SEC010; primary RoT on-die | — |
| SoC-15 | Trace | On-SoC | Carry from V2 | Enhanced | — |
| SoC-16 | PMU | On-SoC | Carry from V2 | Enhanced | — |
| SoC-17 | AXI-Stream | On-SoC | Carry minor delta | Mostly carry | — |
| SoC-18 | DDR Self-refresh | On-SoC | Carry minor delta | Mostly carry | — |
| SoC-19 | TPM | On-SoC | Carry minor delta | Mostly carry | — |
| SoC-20 | BMC | On-SoC | Carry minor delta | Mostly carry | — |

**Category summary:**

| Category | previous revision | future revision New | Deferred |
|---|---|---|---|
| New in V3 (9) | — | NoC Trace, NoC Mgmt IF, QoS (CBQRI), CXL, Always-on Region, Multi-key Enc, HW RNG, Dynamic TSO | PCIe 7.0 Not Now |
| Carry from V2 (7) | Shared Unit, 10ns Timer, Sys Power, Multi-power Domain, RoT, Trace, PMU | Enhanced / upgraded | — |
| Carry minor delta (4) | AXI-Stream, DDR Self-refresh, TPM, BMC | Mostly carry | — |

### 28 Arch Freeze Items (Added at Review)

These items were added on top of the original SoC features at the architecture freeze review. They are organized by category and priority.

#### IO / NoC / C2C (P0 MUST)

| # | Feature | Work Item ID | future revision Scope | Notes |
|---|---|---|---|---|
| 20 | C2C / UCIe Die-to-Die Interface | MC-CORE-001/002 | On-SoC | 128-core baseline (MC-CORE-001, P0), 256-core C2C (MC-CORE-002, P0); UCIe PHY |
| 21 | CXL 3.0 BI + CXL 3.1 Poison | MC-CXL-001/002 | On-SoC | Back-Invalidate (P0 MUST), Poison propagation (P0 MUST) |
| 22 | Cache Stashing (PCIe -> CPU cache) | MC-PCIE-001/002 | On-SoC | ARM CHI Stash / Intel DDIO equivalent; RISC-V Platform Specs Issue #83; P0 MUST |
| 23 | Die-to-Die IO Coherency | MC-IOC-005 | On-SoC | CHI C2C die-snoop; P0 MUST |

#### Security / Confidential Computing (P0 MUST)

| # | Feature | Work Item ID | future revision Scope | Notes |
|---|---|---|---|---|
| 24 | Confidential Computing: CoVE + Memory Integrity Engine | M3 / 08 Security | On-SoC | CoVE (riscv-ap-tee), AES-XTS encryption + AES-GCM/HMAC-SHA256 MAC on DDR; ORG-00-603/615; Smmtt CSR slot reservation |

#### Power Management

| # | Feature | Work Item ID | future revision Scope | Notes |
|---|---|---|---|---|
| 25 | DVFS per-core/cluster | M10 / ORG-00-607 | On-SoC | RPMI DVFS service group stub; Full DVFS future revision; 50us wake-up target |

#### CPU Core

| # | Feature | Work Item ID | future revision Scope | Notes |
|---|---|---|---|---|
| 26 | AME Matrix Extension (pre-reservation) | M4 / 01 | On-SoC | IME v1.0 ratification plan 2026 H2 (Aug 27); opcode pre-reserved future revision; full FU next revision; P1 SHOULD |

#### IO — PCIe Gen6

| # | Feature | Work Item ID | future revision Scope | Notes |
|---|---|---|---|---|
| 27 | PCIe Gen6 Architecture Readiness (PHY slot) | M6 / MC-PCIE-003 | Hybrid (Gen5 On-SoC, Gen6 slot reserved) | Server SoC RCI010 MUST for PCIe 6.0 SW-visible rules |

#### Memory

| # | Feature | Work Item ID | future revision Scope | Notes |
|---|---|---|---|---|
| 28 | Persistent Memory (PMEM) | M16 / MC-PMEM-001 | On-SoC | CBO.FLUSH-to-PoP, ACPI NFIT/MC-PMEM-001; P1 |
| 29 | CXL G-FAM (Global Fabric Attached Memory) | M13 / MC-CXL-003 | On-SoC | CXL 3.1 fabric-attached memory; P1; next revision for hot-plug |

#### Additional SoC Next Items (Items A-F from Decision Matrix)

| Letter | Feature | Scope | Notes |
|---|---|---|---|
| A | Top-level RAS (RERI + APEI + SBI SSE) | On-SoC | Per-IP RERI registers, error aggregator, firmware-first; ORG-00-594/597 |
| B | PCIe Reset Functions (FLR, SBR, PERST) | Hybrid | FLR/SBR on-SoC root complex; PERST via BCSCP GPIO; Server SoC v1.0 §2.4 |
| C | PMU per-hart CPU Performance Monitoring | On-SoC | Zicntr/Zihpm; per-category/chip-or-board SoC HPM; RVA070 MUST |
| D | Dynamic TSO (Memory Model) | On-SoC | MM-1 above; current revision/next revision/future revision phased; ORG-00-584 |
| E | PCIe Reset Functions additional (PTM MAY) | Hybrid/MAY | PTM MAY per Server SoC PTM010 |
| F | PCIe PTM (Precision Time Measurement) | Hybrid/MAY | Root port PTM responder on-SoC; MAY |

### 66 Server Platform / SoC Requirements (future revision Compliance)

Organized by category. Source: Master Report §4.1-4.4, Server Platform v1.0, Server SoC v1.0, BRS v1.0.

#### ISA (5 items)

| # | Requirement | Level | ID | Notes |
|---|---|---|---|---|
| 1 | Ssaia (AIA local interrupt CSRs) | MUST | RVA020 | previous revision carry; IIC010 |
| 2 | Sscsrind (CSR indirect access) | MUST | RVA020 | PMU counter delegation |
| 3 | Ssstrict (opcode strict mode) | MUST | RVA020 | Illegal-instruction exception enforcement |
| 4 | Svvptc (PTE virt TLB invalidation coherency) | SHOULD | BRS/Linux | ORG-00-584; RVA23 optional; Linux TLB |
| 5 | Zacas (compare-and-swap) | MAY/NC | Dev. Option | ORG-00-649; 8 NC items next revision; RVA23 development optional |

#### Hart (4 items)

| # | Requirement | Level | ID | Notes |
|---|---|---|---|---|
| 6 | Hart Homogeneous ISA | MUST | RVA030 | ASID/VLEN/PA width uniform across harts |
| 7 | Hart Software-indistinguishable | MUST | RVA040 | All harts identical from OS perspective |
| 8 | 6 HPM programmable counters (Zihpm) | MUST | RVA070 | ORG-00-614; Intel 8 GP, AMD 6 GP, ARM PMU 6 |
| 9 | Debug trigger (4i+4l+1ic+1irq+1exc, VMID/ASID filtering) | MUST | RVA060 | ORG-00-605; Debug Spec 1.0 ratified Feb 2025 |

#### SoC (16 items)

| # | Requirement | Level | ID | Notes |
|---|---|---|---|---|
| 10 | time CSR 1ns / 100MHz | MUST | CTI010 | ORG-00-604; Always-on, TSC analog |
| 11 | time CSR survives hart power-off | MUST | CTI020 | — |
| 12-14 | IOMMU (RV-ATS, stage 2, DDT) | MUST | IOM010-230 | ORG-00-601; Intel VT-d / AMD IOMMU / ARM SMMU |
| 15 | IOMMU PA width = hart PA width | MUST | IOM230 | ORG-00-601; Intel VT-d / AMD IOMMU / ARM SMMU |
| 16 | IOMMU reset default Off | MUST | IOM240 | Default bypass until OS enables |
| 17 | DMA-capable peripherals use IOMMU | MUST | IOM020 | ORG-00-601; Non-PCIe DMA must go through IOMMU |
| 18 | IOMMU MRIF (MSI translation) | SHOULD | IOM070/080 | RISC-V-specific MSI remapping |
| 19 | PCIe root port AER / DPC / RP PIO | MUST | AER010-030 | ORG-00-616; Intel/AMD/ARM AER/DPC |
| 20 | PCIe ACS (Source Validation, Translation Blocking) | MUST | ACS010 | Intel VT-d / AMD IOMMU ACS |
| 21 | PCIe INTx support | MUST | MSI020 | — |
| 22 | ECAM (PMA, non-posted write, contiguous) | MUST | ECM010-110 | x86/ARM ECAM standard |
| 23 | Hardware RoT (primary RoT on-die) | MUST | SEC010 | ORG-00-603; Intel PFR / AMD PSP / ARM CCA RoT |
| 24 | RERI (severity, persistence, corrected counter) | MUST/SHOULD | RAS040-080 | ORG-00-594; RERI v1.0 (May 2024); Intel eMCA Gen2 |
| 25 | Data Poisoning (CHI Poison propagation) | SHOULD | RAS020 | ORG-00-597; AMD/Intel CDCpoison |

#### Firmware / Platform (8 items)

| # | Requirement | Level | ID | Notes |
|---|---|---|---|---|
| 26 | BRS-I: UEFI/ACPI/SMBIOS | MUST | FIRM010 | BRS v1.0; ACPI MADT RINTC, SPCR |
| 27 | SBI v2.0 (HSM, TIME, PMU) | MUST | SBI010-070 | ARM PSCI / Intel ACPI runtime analog |
| 28 | UEFI Secure Boot key management | MUST | SEC010-060 | ORG-00-603; PK/KEK/db/dbx |
| 29 | TPM 2.0 | MUST | HPER080 | fTPM primary, dTPM fallback |
| 30 | Hardware RNG | MUST | HPER090 | ORG-00-584; Zkr ISA + HPER090 platform |
| 31 | 16550 UART (interrupt, flow, 115200 baud) | MUST | HPER010/020 | — |
| 32 | Battery-backed RTC | MUST | HPER070 | — |
| 33 | RPMI 2.0 | Plan | — | ORG-00-649; RPMI v1.0 ratified Jul 2025; v2.0 plan 2026 |

#### Security — CoVE (5 items)

| # | Requirement | Level | ID | Notes |
|---|---|---|---|---|
| 34 | Smmtt 4KiB MPT | MUST (with CoVE) | — | ORG-00-603/615; 4KiB mandatory; Intel SEPT / AMD RMP / ARM GPT |
| 35 | IO-MPT DMA enforcement | MUST | — | ORG-00-603; Per-device IO-MPT; Intel TDX VT-d / ARM SMMU |
| 36 | DRAM transient key 256-bit | SHOULD | SEC030 | ORG-00-603; AES-XTS-256; CoVE per-TVM key |
| 37 | PCIe IDE | SHOULD | SEC020 | PCIe root port IDE capability; ARM/Intel no mandatory equivalent |
| 38 | Interrupt isolation for confidential VMs (COVI) | MUST (with CoVE) | — | ORG-00-603; COVI SBI extension; TDX SEAM / SEV-SNP / CCA RMM analog |

#### RAS — Competitive/Optional (5 items)

| # | Feature | Level | Notes |
|---|---|---|---|
| 39 | Patrol Scrub | RECOMMENDS | Server SoC RAS010 |
| 40 | Firmware-First Error Handling (FFH) | T2 | RERI SBI SSE; Intel eMCA Gen2 / ARM HEST/SDEI |
| 41 | CPER / BERT / HEST | T2 | BRS ACPI |
| 42 | Post Package Repair (PPR) | T2 | Intel / AMD dynamic PPR |
| 43 | Error Injection (EINJ) | MAY | Server SoC RAS070 |

#### QoS (4 items)

| # | Feature | Level | ID | Notes |
|---|---|---|---|---|
| 47 | L3 Cache Capacity Allocation (CBQRI CAT) | SHOULD | QOS050 | Intel CAT / AMD L3 CAT / ARM MPAM CPBM; ORG-00-651 |
| 48 | Cache Occupancy Monitoring (CBQRI CMT) | SHOULD | QOS060 | — |
| 49 | Memory Bandwidth Allocation (CBQRI MBA) | SHOULD | QOS070 | Intel MBA 2.0 / AMD MBA / ARM MPAM |
| 50 | CBQRI 16 RCID / 32 MCID | MUST | QOS030 | ORG-00-651; Ssqosid + CBQRI v1.0 Jun 2024 |

#### Debug / Trace / PMU (5 items)

| # | Feature | Level | Notes |
|---|---|---|---|
| 51 | Statistical Profiling / Instruction Sampling | T2 | ORG-00-614; ARM SPE / Intel PEBS / AMD IBS |
| 52 | Last Branch Record / CTR depth >=32 | MUST | ORG-00-584; Server Platform RVA021 MUST; Ssctr v1.0 Nov 2024 |
| 53 | Activity Monitor Unit (AMU) for DVFS | T2/next revision | ORG-00-614 item 05-A |
| 54 | SoC HPM (Cache/Memory/PCIe counters) | SHOULD | Server SoC SPM010-060 |
| 55 | Trace Control Interface | V3 | TCI v1.0 ratified Nov 2024; ORG-00-608 |

#### Power Management (3 items)

| # | Feature | Level | ID | Notes |
|---|---|---|---|---|
| 56 | ACPI CPPC OS-directed perf control | MUST (if supported) | BRS AML040 | Intel HWPSST/AMD CPPC/ARM SCP |
| 57 | ACPI LPI low-power idle states | MUST | BRS AML050 | — |
| 58 | RPMI service groups (CPPC, HSM, Reset/Suspend) | T1/Plan | RPMI v1.0 | ORG-00-649 |

#### IO / PCIe (5 items)

| # | Feature | Level | ID | Notes |
|---|---|---|---|---|
| 59 | PCIe 6.0 root complex compliance (SW-visible rules) | MUST | RCI010 | MC-PCIE-003; FLIT mode is hardware-only |
| 60 | PCIe PTM | MAY | PTM010 | — |
| 61 | PCIe VDM P2P routing (MCTP) | SHOULD | IDR020 | BMC MCTP |
| 62 | SoC-integrated PCIe device | SHOULD | SID010-100 | — |
| 63 | PCIe Gen6 Flit PMU | SHOULD | SPM060 | — |

#### Management (3 items)

| # | Feature | Level | ID | Notes |
|---|---|---|---|---|
| 64 | BMC PCIe x1 Gen3 root port | SHOULD | MNG010 | — |
| 65 | IPMI SSIF / I2C | SHOULD | MNG020 | — |
| 66 | UART to BMC | SHOULD | MNG030 | HPER010 |

### Open Items and Risk Register

#### Red-line Issues

| Work Item ID | Feature | Risk | future revision Status |
|---|---|---|---|
| ORG-00-584 | Ssdtso (Dynamic TSO) | P1 — IMPDEF only; Ssdtso not an ISA standard | future revision scope; needs monitoring |
| ORG-00-651/MC-PERF-001 | Ssqosid + CBQRI Linux resctrl | P0 — RFC only (Jan 2026 LWN) | Expected merge Linux 6.15-6.16 |
| ORG-00-608 | E-Trace 2.0 (trace encoder) | P0 — open-source encoder maturity UNVERIFIED | V3 risk; PM 2 months |
| ORG-00-592 | MMIO Outstanding IO ordering | P0 — future revision no OT count equivalent to ARM | See riscv-tier1-mapping §4.7 |
| ORG-00-650a | Pointer Masking (Zpm, RVA23 Mandatory) | previous revision done | Linux 6.13 merged |
| ORG-00-650b | Memory Tagging (Zimt/Svatag) | future version | Charter 2024; v0.2 draft Sep 2025; spec freeze 2028 |
| ORG-00-653 | SMT | P3 / model needed | Not in future revision scope |
| ORG-00-603/615 | Smmtt/Smmpt CoVE | P0 — ARC freeze ongoing | v0.3.3 Apr 2025; CSR slot reservation future revision Arch Freeze |

#### MC-xxx Items Status

| MC ID | Feature | Priority | Status |
|---|---|---|---|
| MC-CORE-001 | 128-core coherent NoC | P0 MUST | Arch Freeze current revision |
| MC-CORE-002 | 256-core C2C chiplet | P0 MUST | Arch Freeze current revision |
| MC-CORE-003 | Additional NoC/die features | P2 | Open |
| MC-IOC-001-006 | IO coherency / MMIO / sideband | P0 MUST | Partially open; ORG-00-592 risk |
| MC-PCIE-001/002 | Cache Stashing | P0 MUST | Arch Freeze item 22 |
| MC-PCIE-003 | PCIe Gen6 FLIT mode | P2 PHY | next revision PHY; SW rules current revision MUST |
| MC-CXL-001/002/003 | CXL 3.0 BI, 3.1 Poison, G-FAM | P0/P0/P1 | Arch Freeze items 21+29 |
| MC-DBG-001/002/003 | Debug/trace | Partially P1 | Trace open (ORG-00-608) |
| MC-INT-001/002 | Interrupt | P0 | AIA/IMSIC carry |
| MC-PERF-001 | QoS / CBQRI | P0 MUST | ORG-00-651 Linux RFC |
| MC-PMEM-001 | Persistent Memory | P1 | Arch Freeze item 28 |

#### future revision vs future version Deferred Items (under discussion)

| Item | current revision | future version |
|---|---|---|
| Memory Tagging (Zimt/Svatag) | Pointer Masking only (ORG-00-650a) | Full hardware tag (ORG-00-650b) |
| SMT | ORG-00-653, not in scope | future version consideration
| Zvkng/Zvksg (GHASH/SM3) | next revision | — |
| NC items (8) | next revision | — |
| IME full matrix FU | next revision (opcode reserved current revision) | — |
| PCIe Gen6 FLIT full | next revision | — |
| Full DVFS per-core | future revision | — |

### Schedule Horizon

current revision Arch Freeze 2026.9, next revision full feature 2027.3, future revision code freeze 2027.9.

> **Template note:** Replace these dates with your project's actual milestone schedule.

### Competitor Feature Mapping (current revision vs Peers)

| Feature | current revision | SiFive P870-D | ARM Neoverse V2 / Graviton4 | Intel Xeon GNR | AMD EPYC Turin |
|---|---|---|---|---|---|
| Core count (die) | 128 (current revision), 256 (C2C next revision) | 128 (C2C) | 96 (Graviton4, CMN-700 256 max-die) | ~64 | ~192 (CCD) |
| ISA Profile | RVA23 | RVA23.1 | AArch64 ARMv9 | x86-64 | x86-64 |
| Coherent NoC | CHI / TileLink-C | CHI E-series C2C | ARM CMN-700/S3 | Intel mesh/CHA | AMD Infinity Fabric |
| QoS | Ssqosid + CBQRI v1.0 | MPAM | MPAM v1.1 (511 PARTID, 32 PMG) | Intel RDT (CAT/MBA/CMT) | AMD L3 CAT/MBA |
| Vector | RVV 1.0 VLEN 256 | RVV 1.0 VLEN 512 | SVE2 4x128b, FEAT_SVEAES | AVX-512 | AVX-512 |
| Matrix | IME (opcode reserved, next revision FU) | — | SME2 | AMX (BF16/INT8) | — |
| Confidential | CoVE + Memory Integrity + Smmtt | WorldGuard TEE | ARM CCA (Realm/RMM) | Intel TDX | AMD SEV-SNP |
| CXL | CXL 3.0 BI + 3.1 Poison On-SoC | CXL 2.0 | CMN S3 CXL 3.0; Graviton4 CXL 1.1 | CXL 3.0 (Granite Rapids) | CXL 3.0 (Turin) |
| PCIe | Gen5 (current revision), Gen6 slot (next revision) | Gen5 | Gen6 (Neoverse V3/CMN S3) | Gen5 | Gen5 |
| NVLink | CXL or NVLink (TBD, M2) | NVLink Fusion (coherent GPU) | — | — | — |

---

## Part 3: Requirement Query Interface

> This section provides structured query patterns for looking up requirements
> from a requirement document set. Adapt the document paths and IDs to your
> project's actual document structure.

### Data Source Location

```
your-project/RISC-V_CPU_requirement/
|-- 04-02-00-Delivery-configurable-CPU-subsystem.md      # CPU subsystem overview (RAS, QoS, BMC)
|-- 04-02-01-Compliance-with-RVA23.1-server-platform-v1.0.md  # RVA23 compliance
|-- 04-02-02-Single-core-SPEC06-65point.md               # Single-core performance
|-- 04-02-03-Scale-up-no-less-then-128-cores.md         # Scale-up capability
|-- 04-02-04-multi-core-performance-linear-more-than-0.7.md  # Multi-core scalability
|-- 04-02-08-Support-server-level-confidential-security.md    # Security
|-- 04-02-11-Support-vector-matrix-acceleration-for-LLM.md    # LLM acceleration
|-- 04-02-12-IP-delivery-documentation-requirements.md       # Delivery documentation
`-- 04-02-99-archived-requirements.md                   # Archived requirements
```

### Core Capabilities

#### 1. Requirement Document Query
- **By requirement ID**: "query 04-02-01" or "member need analysis for 04-02-01"
- **By topic**: "performance requirements", "RAS requirements", "security requirements"
- **By member need**: "SoC Partner B requirements", "SoC Partner A requirements", "which members need Zvkng?"

#### 2. Structured Answer
For each query, extract:
- Requirement source (member name, competitive analysis, foundation spec)
- Associated RVA23 Server Platform 1.0 spec entry
- Precise document reference (filename, section number, work item ID)
- Member requirement status (priority A/B/C or 1/2/3, not mentioned)

#### 3. Delta Comparison
- **V2 -> V3 increment comparison**: list added/modified requirement entries
- **RVA23 mandatory vs Server Platform 1.0**: compare spec differences, mark choices
- **Multi-member cross-reference**: e.g., "which members need Zvkng?"

#### 4. Spec Priority Mapping
Return references in this priority order:
1. UDB (user database record, if exists)
2. Nomination rules
3. RISC-V Foundation spec (RVA23.1 Server Platform 1.0)

### Output Format

#### Standard Answer Structure (for complex queries)
```markdown
## Question: [user's original question]

### Core Answer
[Direct answer, 1-3 sentences]

### Requirement Document Reference
- **Document**: 04-02-01-Compliance-with-RVA23.1-server-platform-v1.0.md
- **Section**: §Member Need Analysis · SoC Partner B
- **Work Item ID**: ORG-00-XXX (if any)
- **Associated Spec**: RVA23.1 Server Platform 1.0 §Zvksg

### Member Requirement Status
- **SoC Partner B**: Priority A (confirmed requirement)
- **SoC Partner A**: Priority 1 (mandatory)
- **SoC Partner D**: Not mentioned
- **SoC Partner C**: Not mentioned
- **SoC Partner E**: Not mentioned

### Previous Version -> Current Version Increment
- **previous revision**: Not implemented
- **current version**: Mandatory

### Spec Priority
1. UDB: [reference record]
2. Nomination rules: [reference record]
3. RISC-V Foundation: RVA23.1 Server Platform 1.0

### Notes
[If conflicts or ambiguities exist, mark "needs manual confirmation" here]
```

#### Compact Answer Mode (for simple queries)
```markdown
04-02-02 requires single-core SPEC06 >= 65 points.
Reference: `your-project/RISC-V_CPU_requirement/04-02-02-Single-core-SPEC06-65point.md`
```

### Constraints (MUST NOT)

1. **Do not modify requirements**
   - Do not create, edit, or delete requirement documents
   - Do not suggest requirement changes (handled by requirement change process)

2. **Do not make technical decisions**
   - Do not evaluate implementation feasibility
   - Do not provide architecture design suggestions
   - Do not recommend technical solutions

3. **Do not execute database operations**
   - Do not execute SQL queries (avoid SQL execution errors)
   - Do not modify database records
   - All queries based on filesystem reads

4. **Do not override spec authority**
   - Do not modify RISC-V Foundation spec text
   - Do not re-interpret spec semantics (avoid ambiguity)
   - When spec conflicts exist, mark "needs manual confirmation"

### Typical Scenarios

#### Scenario 1: RVA23 Server Platform Requirement Query
**User question**: "What are the RVA23 server platform requirements for Q4?"
**Expected output**:
- List all RVA23 compliance entries in 04-02-01
- Classify by priority (A/B/C/under discussion/not needed)
- Mark implementation status

#### Scenario 2: Previous Version -> Current Version Increment Query
**User question**: "What new requirements does current version add compared to previous version?"
**Expected output**:
- List added entries by requirement ID
- Associate member sources
- Mark implementation priority

#### Scenario 3: Requirement vs Spec Difference Query
**User question**: "RVA23 mandatory vs Server Platform 1.0, which does V3 choose?"
**Expected output**:
- Compare RVA23 Profile and Server Platform 1.0 differences
- Mark V3's choice (e.g., "force-merge RVA23.1 key features")
- Reference 04-02-01 relevant sections

#### Scenario 4: Multi-Member Cross-Reference Query
**User question**: "Which members need vector crypto instruction sets?"
**Expected output**:
- List all members needing vector crypto (SoC Partner B, SoC Partner A)
- Specific entries (Zvkng, Zvksg, Zvbc)
- previous revision not implemented, current version mandatory

#### Scenario 5: Requirement Document Precise Location
**User question**: "Where are the RAS requirements in 04-02-00?"
**Expected output**:
- Filename and section number (`04-02-00.md §RAS·QoS·BMC`)
- Cross-reference 04-02-01 ISA-native detailed analysis
- Related member requirement links

### Member Priority System

> **Methodology Example:** This section illustrates how different stakeholders may use different priority systems. The identifiers shown are placeholder examples for methodology demonstration purposes only.

In a typical RISC-V CPU requirement project:

- **SoC Partner A**: Uses A/B/C priority (A=confirmed requirement, B=needed, C=optional)
- **SoC Partner B**: Uses 1/2/3 priority (1=mandatory, 2=needed, 3=optional)
- Other stakeholders (SoC Partner C/D/E): Not mentioned = no declared requirement
- Member need analysis is located in the `## Member Need Analysis` section of each document

> **Template note:** Replace stakeholder identifiers with your actual project's member/partner names and their specific priority systems before use.

### Supported Query Modes

1. **Direct Q&A**: "What are the performance requirements for current version?"
2. **Document reference**: "What is the single-core SPEC06 target in 04-02-02?"
3. **Comparison query**: "What are the RAS differences between previous version and current version?"
4. **Spec mapping**: "What is the status of RVA23 mandatory Zvksg in current version?"

---

## Interaction Protocol

For every request activating this skill:

1. **Identify the specific feature or requirement** by its ID: SoC-N, IO-N, PM-N, Sec-N, DT-N, MM-N, Arch Freeze item 20-29, or Server Platform req #1-66, or work item ID.
2. **State future revision scope** (On-SoC / Hybrid / Not Now) and priority (P0/P1/P2/P3).
3. **State previous revision heritage** (new in current version, carry, or enhanced).
4. **Map to spec requirement** (MUST/SHOULD/MAY/RECOMMENDS) and cite the Server Platform, Server SoC, or BRS requirement ID.
5. **Cross-reference open items**: if the feature has an open work item, state the current risk level and open action.
6. **Cross-reference the current revision/next revision/future revision/future version horizon** if the feature is partially in scope.
7. **Invoke riscv-tier1-mapping** for any deep architectural mapping question (e.g., "how does Ssqosid compare to MPAM?" -> hand off to riscv-tier1-mapping).
8. **Evidence-tag all factual claims**: T1-VERIFIED / T2-CROSS-CHECKED / UNVERIFIED per the source hierarchy.

If asked about a feature not in this list, say so explicitly and suggest whether it belongs to next revision / future revision / future version or is out of scope.
