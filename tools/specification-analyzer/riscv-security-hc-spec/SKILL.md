---
name: riscv-security-hc-spec
description: >
  Look up RISC-V Security Horizontal Committee related specifications, including
  ratified and unratified/development specs. Activate for questions about RISC-V
  security specifications, memory isolation, memory encryption or confidential
  computing, I/O isolation, secure interrupts, trusted debug, and control-flow
  integrity including Zicfilp and Zicfiss.
---

# RISC-V Security HC Specification Lookup

## Rule

Do not answer RISC-V Security HC specification-status or source-link questions from memory. First check the bundled specification index and, for current status, verify against the official sources listed there.

## Primary Reference

Read `references/security-hc-specifications.md` when the user asks about:

- memory isolation: PMP, Smepmp, SPMP, Smmtt, Worlds, Smpmpmt
- memory encryption or confidential computing: Security Model, CoVE/AP-TEE, HAC, crypto
- I/O isolation: IOMMU, IOPMP, CoVE with I/O
- secure interrupts: AIA, PLIC, CLIC, NMI/double-trap related privileged extensions
- trusted debug: Debug, external debug security, trigger delegation
- control-flow integrity: Zicfilp, Zicfiss, Smcfilp, Smcfiss, shadow stacks

## Status Sources

- Ratified specs: `https://riscv.org/specifications/ratified/` and the official RISC-V docs library.
- Development specs: `https://riscv.org/specifications/` -> "VIEW DEVELOPMENT SPECS", backed by `https://riscv.github.io/adm-spec-dashboard/latest.csv`.
- If GitHub access is blocked in this environment, run network commands through `bosc-ipv6`, for example:
  ```bash
  bosc-ipv6 curl -L https://riscv.github.io/adm-spec-dashboard/latest.csv
  ```

## Answering Guidance

When answering, report:

1. specification name and extension names where applicable;
2. status: Ratified, Development, Stabilization, Freeze, Ratification-Ready, or Planning;
3. whether it is ISA, NON-ISA, or both;
4. official source link;
5. the security category.

If a requested category has no direct public RISC-V spec entry, say so and name the closest related specs instead of inventing one.
