---
name: xiangshan-mpt-dut
description: Generate or update a compact Chisel/Scala DUT that isolates XiangShan MPT checking logic from the full core. Use when working on XiangShan MPT, MptChecker.scala, L2TLB MPT wiring, backend/decode mfence support, or the custom smmpt/mmpt CSR path, especially when asked to regenerate a simple DUT around MPT upstream/downstream logic for simulation or verification.
---

# XiangShan MPT DUT

## Workflow

Use this skill to rebuild a small DUT around XiangShan's MPT checker without dragging in the whole core.

1. Inspect the local XiangShan source before editing. Prefer `rg` and targeted `sed` reads.
2. Load [references/mpt-dut-generation.md](references/mpt-dut-generation.md) before implementing the DUT.
3. Keep the DUT focused on `MptChecker` and its real upstream/downstream contract:
   - upstream request/response: `MptReqBundle` and `MptRespBundle`
   - CSR input: `TlbCsrBundle.mmpt`
   - flush input: `SfenceBundle`, including `mfence` when `HasMptCheck`
   - memory side: `L2TlbMemReqBundle` request plus XLEN response
   - PMP side: `PMPReqBundle` and `PMPRespBundle`
4. Preserve the XiangShan parameter gate: the DUT should be meaningful only with `HasMptCheck = true`.
5. Do not model the full backend, ROB, LSQ, PTW cluster, or complete L2TLB unless the user explicitly asks. Replace them with small drivers, arbiters, memories, or stubs that preserve handshake semantics.

## Output Shape

Prefer one of these forms, matching the repository style:

- A new Chisel module such as `SimpleMptDUT`, `MptCheckerDUT`, or a project-specific name requested by the user.
- A minimal test/harness module that instantiates `MptChecker`, drives CSR/mfence/PMP/memory, and exposes decoupled request/response IO.
- A generator target only if the repository already has a clear pattern for standalone generators.

Keep source changes narrow. Avoid unrelated CSR, decode, or L2TLB refactors.

## Validation

After generating the DUT:

- Run the narrowest available Scala/Chisel compile or generator command for the touched module.
- If no compile target exists, run a syntax-oriented check available in the repo and state the limitation.
- Recheck all `Option.when(HasMptCheck)` fields so the DUT does not dereference optional `mfence` or MPT fields when disabled.
