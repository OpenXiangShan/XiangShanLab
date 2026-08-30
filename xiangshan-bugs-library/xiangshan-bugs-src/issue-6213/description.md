When we have half rvi, the offset should be 0.U.

In cacheable path we have `s1_fixedInstrVec(0).endOffset := Mux(s1_prevEndIsHalfRvi, 0.U, s1_instrVec(0).endOffset)`, but uncacheable path does not consider this flag, giving wrong endOffset, and finally causing backend calculating wrong xpec and xtval.

- `xepc = pcMem.rdata + (offset << 1) + Mux(isRVC, 0, -2)`
- `xtval = xepc + Mux(crossPage, 2, 0)`

Fixes #6211

Also fixes a similar problem when a single RV-I instruction crossing page boundary in MMIO region, after this instruction is fetched and sent to backend, we should redirect to its `pc+2` instead of `+4`, otherwise we'll skip 2B of instruction data. (i.e. RVI on `[0xfffe, 0x10002)` will be splitted into 2 fetch blocks `[0xfffe, 0x10000)` and `[0x10000, 0x10002)`, when it's fetched, the pc is the start of second fetch block `0x10000`, we should redirect to `0x10002` instead of `0x10004`).

Tested with https://github.com/OpenXiangShan/nexus-am/pull/68 and add that to CI.

Related: #5985
