# MPT DUT Generation Reference

## Source Map

Read these files first when regenerating the DUT:

- `src/main/scala/xiangshan/cache/mmu/MptChecker.scala`: MPT request/response bundles, output switch box, `MptCheckerIO`, and the checker/cache/table-walker/miss-queue integration.
- `src/main/scala/xiangshan/cache/mmu/L2TLB.scala`: real upstream/downstream wiring for MPT checker, MPT request arbitration, memory arbitration, and response demux.
- `src/main/scala/xiangshan/Bundle.scala`: `MmptStruct`, `TlbMmptBundle`, `TlbCsrBundle`, and `SfenceBundle.bits.mfence`.
- `src/main/scala/xiangshan/Parameters.scala`: `HasMptCheck`, `HasMptCheckDefault`, `HasMptCheckDefault4k`, and `HasMptInodeOpt`.
- `src/main/scala/xiangshan/backend/fu/Fence.scala`: backend `mfence` path into `SfenceBundle`.
- `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala`: decode entry for `MFENCE` and legality check from CSR.
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`: `MmptBundle` and `Mmpt` CSR implementation.
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`: `mmptSDIDChanged`, TLB CSR export, and decode illegal flag for `mfence`.
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRDefines.scala`: legal `MmptMode` values.
- `src/main/scala/xiangshan/backend/fu/util/CSRConst.scala`: custom CSR address `Mmpt = 0x382`.

## Real MPT Checker Contract

`MptCheckerIO` extends the MMU base bundle, so the DUT must provide:

- `io.csr`: `TlbCsrBundle`
- `io.sfence`: `SfenceBundle`
- `io.req`: flipped `DecoupledIO[MptReqBundle]`
- `io.resp`: `ValidIO[MptRespBundle]`
- `io.mem.req`: `DecoupledIO[L2TlbMemReqBundle]`
- `io.mem.resp`: flipped `ValidIO[UInt(XLEN.W)]`
- `io.mem.mask`: input mask bit
- `io.pmp.req`: `ValidIO[PMPReqBundle]`
- `io.pmp.resp`: flipped `PMPRespBundle`

`MptReqBundle` fields:

- `reqPA`: PPN-width physical-page request
- `id`: source ID, used to route the response
- `mptOnly`: request bypasses normal translation and only needs MPT checking

`MptRespBundle` fields:

- `id`, `reqPA`, `mptOnly`
- `accessFault`
- `mptPerm`
- `mptLevel`
- `contigousPerm`
- `permIsNAPOT`

## Minimal DUT Architecture

Use this structure unless the user asks for a different one:

1. Instantiate `MptChecker`.
2. Expose a simple top-level request port compatible with `MptReqBundle`.
3. Expose the checker response directly as `ValidIO[MptRespBundle]`.
4. Provide a small CSR control input or register bank for `mmpt`:
   - `mode`: 4 bits
   - `sdid`: 6 bits
   - `optOutInNode`: 1 bit
   - `ppn`: 44 bits in this branch
   - `changed`: explicit pulse for flush testing
5. Provide a simple `sfence` input. For MPT tests, drive `sfence.bits.mfence.get` when `HasMptCheck`.
6. Stub PMP as permissive unless testing PMP faults:
   - accept `pmp.req`
   - drive `pmp.resp` as pass/no-fault according to the local `PMPRespBundle` fields
7. Model memory with a small synchronous or decoupled table:
   - accept `io.mem.req`
   - return XLEN data through `io.mem.resp`
   - preserve the Decoupled valid/ready timing enough to test misses

Avoid pulling in the full `L2TLB`, `PTW`, `HPTW`, `LLPTW`, dcache, backend, or core top unless the task specifically requires integration verification.

## Upstream/Downstream Behavior To Preserve

From `L2TLB.scala`:

- MPT checker receives requests from an arbiter over PTW, HPTW, LLPTW, final L1TLB merge path, and `mptOnly` L1TLB path.
- `mptOnly` requests use the original request VPN/PA as `reqPA` and route back to L1TLB through the MPT response path.
- Non-`mptOnly` translation responses must wait for the MPT response before returning to L1TLB.
- MPT memory requests share the L2TLB memory arbiter with PTW/HPTW/LLPTW.
- Memory responses are selected by source ID and returned to `mptc.io.mem.resp`.
- Flush is asserted on `sfence.valid`, satp/vsatp/hgatp changes, virtual privilege changes, or `mmpt.changed`.

For a simple DUT, this can be reduced to one request source and one memory port, but preserve:

- request `valid/ready`
- response `valid`
- source `id` round trip
- `mptOnly` round trip
- CSR mode behavior
- flush/mfence visibility

## CSR And mfence Details

The branch uses `Mmpt` as the custom MPT CSR at address `0x382`.

`MmptBundle` fields:

- `MODE[63:60]`: legal values are Bare(0), Smmpt43(1), Smmpt52(2); `smmpt64` is declared but not legal in this branch.
- `optOutInNode[59]`: skip non-leaf node checks.
- `SDID`: security domain ID.
- `PPN`: MPT root table address.

`NewCSR.scala` exports:

- `io.tlb.mmpt`
- `io.tlb.mmptSDIDChanged`
- decode illegal flag: `io.toDecode.illegalInst.mfence := !isModeM`

`Fence.scala` maps `FenceOpType.mfence` to the same TLB flush path as sfence/hfence and sets `sfence.bits.mfence.get`.

When generating a simple DUT, do not reimplement full CSR decode unless requested. A compact control bundle or test registers are enough, but use the same field names and widths as `TlbMmptBundle` so tests can be lifted back into XiangShan.

## Common Pitfalls

- Do not use `HasBitmapCheck` paths for MPT-only DUT work; bitmap and MPT are mutually specialized in several CSR/module list branches.
- Do not dereference `.get` on optional MPT or `mfence` fields unless guarded by `HasMptCheck`.
- Do not drop `mmpt.mode === 0.U` behavior. `MptChecker` fakes a permissive response when MPT is disabled.
- Do not confuse `reqPA` with a byte address. In `MptReqBundle` it is PPN-width.
- Keep source IDs stable through memory and MPT response paths; L2TLB uses IDs to demux responses.
