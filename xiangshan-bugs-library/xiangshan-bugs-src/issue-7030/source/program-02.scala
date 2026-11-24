// src/main/scala/xiangshan/cache/mmu/L2TLB.scala:714-715
      when (!bitmap_enable) {
        outArb(i).in(0).bits.s1 := merge_ptwResp_to_sector_ptwResp(mergeArb(i).out.bits.s1)
// src/main/scala/xiangshan/cache/mmu/L2TLB.scala:830-832
    ptw_sector_resp.entry.ppn := pte.entry(OHToUInt(pte.pteidx)).ppn
    ...
    ptw_sector_resp.entry.n.map(_ := pte.entry(OHToUInt(pte.pteidx)).n.getOrElse(0.U))
