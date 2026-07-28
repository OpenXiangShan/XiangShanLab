# Issue 6264 Analysis

Issue: https://github.com/OpenXiangShan/XiangShan/issues/6264
Related fix draft: https://github.com/OpenXiangShan/XiangShan/pull/6272

## Summary

This is a frontend canonical-address bug on the sequential fetch path.
When fetch falls through from the last canonical Sv39 address into `0x0000004000000000`, XiangShan loses the overflow bit before the ITLB can do a full canonical check, so the core keeps fetching instead of raising instruction page fault.

## Evidence

- Issue body says the redirect target is already handled by backend canonical checking; the failure is the next-line sequential fetch.
- The repro log in the issue shows expected `mcause=12`, but XiangShan returns from S-mode with no prior fault and executes the alias stub.
- Commenter `ngc7331` confirmed this is a design flaw and pointed to draft fix `#6272`.
- PR `#6272` explicitly says the earlier backend fix in `#3003` covered jump/branch targets, but not straight-line fall-through across the boundary.

## Root Cause

The fetch/redirect pipeline only carried `VAddrBits` in several places, so the extra boundary bit was dropped too early:

- `frontend/IFU.scala`: fetch resend address, prediction target, and ITLB request metadata
- `frontend/BPU.scala`: fall-through and predictor PC/target widths
- `frontend/NewFtq.scala`: FTQ start/next-line/target storage
- `frontend/PreDecode.scala`: fixed target width
- `backend/fu/wrapper/BranchUnit.scala` and `backend/CtrlBlock.scala`: redirect/trap paths
- `cache/mmu/TLB.scala`: instruction high-address truncation / canonical-check handling
- `backend/fu/NewCSR/CSREvents/CSREvent.scala`: trap VA reconstruction

Key patch anchors from `#6272`:

- `src/main/scala/xiangshan/frontend/IFU.scala:100, 303, 365, 600, 873, 1103`
- `src/main/scala/xiangshan/frontend/BPU.scala:108, 206, 287, 354, 453, 603`
- `src/main/scala/xiangshan/frontend/NewFtq.scala:64, 92, 207, 649, 831, 1157, 1462, 1474`
- `src/main/scala/xiangshan/cache/mmu/TLB.scala:199, 461`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala:99, 118`
- `src/main/scala/xiangshan/backend/CtrlBlock.scala:95, 326, 373`
- `src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala:11, 35, 50, 62`

Once the straight-line PC overflows past `0x3fffffffffff`, the lost bit means the ITLB never sees that the fetch address is non-canonical. The old logic therefore lets the fetch continue and execute the aliased mapping.

## Fix Direction in #6272

The draft fix widens the relevant frontend/control bundles to `VAddrBits + 1`, preserves the guard bit through FTQ/BPU/IFU/IPrefetch, enables `checkfullva` on instruction fetch requests, and updates TLB/CSR trap generation so instruction PF/GPF paths can distinguish canonical sign-extension from overflow.

## Conclusion

This issue is not a decode or branch-prediction mistake. It is a missing canonical-boundary check on the sequential instruction-fetch path, caused by truncating the PC before ITLB validation.

I did not rerun `xs-env` or the emu repro here; this analysis is based on the issue log, the two GitHub comments, and the open draft fix diff in `#6272`.
