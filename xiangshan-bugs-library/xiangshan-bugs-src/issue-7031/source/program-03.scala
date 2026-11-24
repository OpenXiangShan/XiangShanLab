// src/main/scala/xiangshan/cache/mmu/L2TLB.scala:713-719
      val bitmap_enable = mbmc.BME === 1.U && mbmc.CMODE === 0.U
      when (!bitmap_enable) {
      ...
      } .otherwise {
        when (mergeArb(i).out.bits.s2xlate === noS2xlate || mergeArb(i).out.bits.s2xlate === onlyStage1) {
          outArb(i).in(0).bits.s1 := ptwResp_to_sector_4kptwResp(mergeArb(i).out.bits.s1)
// src/main/scala/xiangshan/cache/mmu/L2TLB.scala:874-881
      val ppn_equal = pte.entry(i).ppn === pte.entry(OHToUInt(pte.pteidx)).ppn
      ...
      val cf_equal = pte.entry(i).cf === pte.entry(OHToUInt(pte.pteidx)).cf
      ptw_sector_resp.valididx(i) := (ppn_equal && pbmt_equal && perm_equal && v_equal && af_equal && pf_equal && cf_equal) && !pte.not_merge
