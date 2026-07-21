# RISC-V Security HC Specifications

Snapshot date: 2026-07-14.

Sources:
- RISC-V specifications landing page: https://riscv.org/specifications/
- Ratified specifications library: https://riscv.org/specifications/ratified/
- Development dashboard CSV: https://riscv.github.io/adm-spec-dashboard/latest.csv
- RISC-V docs library: https://docs.riscv.org/reference/

Use the development dashboard for current unratified status. The rows below capture the dashboard data available on 2026-07-14.

## Category Map

| Category | Main specs |
|---|---|
| Memory isolation | Privileged Architecture PMP, Smepmp, SPMP, SPMP for Hypervisor, Smmtt, RISC-V Worlds, Smpmpmt |
| Memory encryption / confidential computing | Security Model, CoVE/AP-TEE, CoVE with I/O, scalar/vector crypto, PQC, HAC |
| I/O isolation | IOMMU, IOPMP, CoVE with I/O |
| Secure interrupts | AIA, PLIC, CLIC, Smrnmi, Smdbltrp |
| Trusted debug | Debug Specification, External Debug Security (Sdsec), Trigger Delegation |
| Control-flow integrity | Zicfilp, Zicfiss, Smcfilp, Smcfiss, shadow stacks |

## Ratified Specifications

### Memory Isolation

| Specification | ISA? | Status | Source | Notes |
|---|---:|---|---|---|
| RISC-V Privileged Architecture | ISA | Ratified | https://docs.riscv.org/reference/isa/priv/priv-index.html | Includes PMP, privilege modes, virtual memory, traps, and related security mechanisms. |
| Smepmp | ISA | Ratified | https://docs.riscv.org/reference/isa/priv/smepmp.html | Enhanced PMP, including machine-mode lockdown behavior. |
| Hypervisor Extension | ISA | Ratified | https://docs.riscv.org/reference/isa/priv/hypervisor.html | Virtualization isolation substrate used by confidential VM work. |

### Memory Encryption / Confidential Computing

| Specification | ISA? | Status | Source | Notes |
|---|---:|---|---|---|
| RISC-V Cryptography Extensions, Volume I: Scalar & Entropy Source Instructions | ISA | Ratified | https://docs.riscv.org/reference/isa/unpriv/scalar-crypto.html | Scalar crypto building blocks; not itself a memory-encryption architecture. |
| RISC-V Cryptography Extensions, Volume II: Vector Instructions | ISA | Ratified | https://docs.riscv.org/reference/isa/unpriv/vector-crypto.html | Vector crypto building blocks. |

There is no standalone ratified "memory encryption" ISA spec in the public ratified library. Public work closest to that topic is confidential computing/TEE work listed in the development section.

### I/O Isolation

| Specification | ISA? | Status | Source | Notes |
|---|---:|---|---|---|
| RISC-V IOMMU Architecture | NON-ISA | Ratified | https://docs.riscv.org/reference/platform/iommu.html | DMA address translation and device isolation. |

### Secure Interrupts

| Specification | ISA? | Status | Source | Notes |
|---|---:|---|---|---|
| Advanced Interrupt Architecture (AIA) | ISA/platform | Ratified | https://docs.riscv.org/reference/isa/priv/aia.html | IMSIC/APLIC interrupt architecture. |
| Platform-Level Interrupt Controller (PLIC) | NON-ISA | Ratified | https://docs.riscv.org/reference/platform/plic.html | Platform interrupt controller. |
| Smrnmi | ISA | Ratified | https://docs.riscv.org/reference/isa/priv/smrnmi.html | Resumable non-maskable interrupt support. |
| Smdbltrp | ISA | Ratified | https://docs.riscv.org/reference/isa/priv/smdbltrp.html | Double-trap handling. |

### Trusted Debug

| Specification | ISA? | Status | Source | Notes |
|---|---:|---|---|---|
| RISC-V Debug Specification | NON-ISA | Ratified | https://docs.riscv.org/reference/debug/ | Base external debug architecture; use with Sdsec for security-specific debug policy. |

### Control-Flow Integrity

| Specification | ISA? | Status | Source | Notes |
|---|---:|---|---|---|
| Zicfilp | ISA | Ratified | https://docs.riscv.org/reference/isa/unpriv/cfi.html | Forward-edge CFI using landing pads. The common typo `ziciflp` should be treated as `Zicfilp`. |
| Zicfiss | ISA | Ratified | https://docs.riscv.org/reference/isa/unpriv/cfi.html | Backward-edge CFI using shadow stacks. |
| Smcfilp / Smcfiss | ISA | Ratified | https://docs.riscv.org/reference/isa/priv/priv-cfi.html | Privileged architecture support for landing pads and shadow stacks. |

## Development / Unratified Specifications

These entries come from the official development dashboard CSV on 2026-07-14.

### Memory Isolation

| Specification | Dashboard status | ISA? | Target | Progress | Source | Latest PDF |
|---|---|---:|---|---|---|---|
| PMP-based Memory Types Extension (Smpmpmt) | Specification in Freeze | ISA | 26Q4 | On Track | https://github.com/riscv/riscv-isa-manual/blob/smpmpmt/src/smpmpmt.adoc | https://github.com/riscv/riscv-isa-manual/releases/download/riscv-isa-release-79b241c-2026-07-10/riscv-spec.pdf |
| S-mode Physical Memory Protection (SPMP) | Specification in Ratification-Ready | ISA | 26Q3 | On Track | https://github.com/riscv/riscv-spmp | https://github.com/riscv/riscv-spmp/releases/download/v0.9.2/rv-spmp-spec.pdf |
| Supervisor Domains Access Protection (Smmtt) | Specification in Freeze | ISA | 26Q4 | On Track | https://github.com/riscv/riscv-smmtt | https://github.com/riscv/riscv-smmtt/releases/download/v0.49/smmtt-spec.pdf |
| RISC-V Worlds (Smwid, Smlwid, Smlwidlist, Smwiddeleg, Sswid) | Specification in Freeze | ISA | 26Q4 | On Track | https://github.com/riscv/wordguard | https://github.com/riscv/riscv-worlds/releases/download/riscv-isa-release-4c81a3f-2026-04-14/riscv-privileged.pdf |
| RV Worlds H-extension | Specification in Planning | ISA | Not Set Yet | Not Set Yet | https://github.com/riscv/riscv-worlds | https://github.com/riscv/riscv-worlds/releases/download/riscv-isa-release-4c81a3f-2026-04-14/riscv-privileged.pdf |
| SPMP for Hypervisor | Specification in Planning | ISA | Not Set Yet | Not Set Yet | https://github.com/riscv/riscv-spmp/tree/main/spmp-for-hyp | https://github.com/riscv/riscv-spmp/releases/download/v0.9.2/rv-spmp-spec.pdf |

### Memory Encryption / Confidential Computing

| Specification | Dashboard status | ISA? | Target | Progress | Source | Latest PDF |
|---|---|---:|---|---|---|---|
| Security Model | Specification in Freeze | NON-ISA | 26Q4 | On Track | https://github.com/riscv-non-isa/riscv-security-model | https://github.com/riscv/riscv-security-model/releases/download/v0.6/riscv-platform-security-model.pdf |
| Confidential VM Extension (CoVE) | Specification in Freeze | NON-ISA | 26Q4 | On Track | https://github.com/riscv-non-isa/riscv-ap-tee | https://github.com/riscv-non-isa/riscv-ap-tee/releases/download/v0.7/riscv-cove.pdf |
| Confidential VM Extension (CoVE) with I/O | Specification in Freeze | NON-ISA | 26Q4 | Exposed | https://github.com/riscv-non-isa/riscv-ap-tee-io | https://github.com/riscv-non-isa/riscv-ap-tee-io/releases/download/v0.3.0/riscv-cove-io.pdf |
| High Assurance Cryptography (HAC) | Specification Under Development | ISA | 27Q1 | On Track | https://github.com/riscv/riscv-high-assurance-cryptography | Not published in dashboard |
| Post-Quantum Cryptography (Keccak) | Specification Under Development | ISA | 27Q1 | On Track | https://github.com/riscv/riscv-pqc | Not published in dashboard |

### I/O Isolation

| Specification | Dashboard status | ISA? | Target | Progress | Source | Latest PDF |
|---|---|---:|---|---|---|---|
| Input/Output Physical Memory Protection (IOPMP) | Specification in Freeze | NON-ISA | 26Q4 | On Track | https://github.com/riscv-non-isa/iopmp-spec | https://github.com/riscv-non-isa/riscv-iopmp/releases/download/v0.8.2/2026-0209-iopmp.pdf |
| Confidential VM Extension (CoVE) with I/O | Specification in Freeze | NON-ISA | 26Q4 | Exposed | https://github.com/riscv-non-isa/riscv-ap-tee-io | https://github.com/riscv-non-isa/riscv-ap-tee-io/releases/download/v0.3.0/riscv-cove-io.pdf |

### Secure Interrupts

| Specification | Dashboard status | ISA? | Target | Progress | Source | Latest PDF |
|---|---|---:|---|---|---|---|
| Fast Interrupts (CLIC) | Specification in Freeze | ISA | 26Q4 | On Track | https://github.com/riscv/riscv-fast-interrupt | https://github.com/riscv/riscv-fast-interrupt/releases/download/v0.19/aclic-0.19.pdf |

### Trusted Debug

| Specification | Dashboard status | ISA? | Target | Progress | Source | Latest PDF |
|---|---|---:|---|---|---|---|
| External Debug Security (Sdsec) | Specification in Freeze | ISA + NON-ISA | 26Q4 | On Track | https://github.com/riscv-non-isa/riscv-external-debug-security | https://github.com/riscv-non-isa/riscv-external-debug-security/releases/download/v0.7.4/external-debug-security.pdf |
| Trigger Delegation | Specification in Planning | ISA | Not Set Yet | Not Set Yet | https://github.com/riscv/ft-trigger-delegation | https://github.com/riscv/ft-trigger-delegation/releases/download/riscv-isa-release-804db27-2026-03-07/riscv-privileged.pdf |

### Control-Flow Integrity

| Specification | Dashboard status | ISA? | Target | Progress | Source | Latest PDF |
|---|---|---:|---|---|---|---|
| Shadow Stacks for M-mode, M+U & SPMP | Specification Under Stabilization | ISA | 26Q4 | On Track | https://github.com/ved-rivos/riscv-isa-manual/blob/smpmpss/src/smcfiss.adoc | https://github.com/ved-rivos/riscv-isa-manual/releases/download/riscv-isa-release-3b63260-2026-01-20/riscv-privileged.pdf |

## Refresh Procedure

Fetch dashboard data through IPv6 proxy when direct GitHub/GitHub Pages access fails:

```bash
bosc-ipv6 curl -L -o /tmp/riscv-latest.csv https://riscv.github.io/adm-spec-dashboard/latest.csv
```

Then filter likely Security HC entries:

```bash
rg -n "IOPMP|PMP|SPMP|Smmtt|Worlds|CoVE|TEE|Security Model|Sdsec|CLIC|Cryptography|HAC|PQC|Shadow Stack|Zicfilp|Zicfiss|Trigger Delegation" /tmp/riscv-latest.csv
```

The dashboard page is reached from https://riscv.org/specifications/ by selecting "VIEW DEVELOPMENT SPECS".
