// https://github.com/OpenXiangShan/XiangShan/blob/a7b9dea601f2f08dcca7d97ac544221cf02b1fe9/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala#L796-L805
    val vtag_matches = VecInit(widthMap(w => vtag(w) === getVTag(s1Req.vaddr)))
    // ptag_matches uses paddr from dtlb, which is far from sbuffer
    val ptag_matches = VecInit(widthMap(w => RegEnable(ptag(w), s1ReqValid) === RegEnable(getPTag(s1Paddr), s1ReqValid)))
    val tag_matches = vtag_matches
    val tag_mismatch = RegNext(s1ReqValid) && VecInit(widthMap(w =>
      GatedValidRegNext(vtag_matches(w)) =/= ptag_matches(w) && GatedValidRegNext((activeMask(w) || inflightMask(w)))
    )).asUInt.orR && !RegEnable(s1Kill, s1ReqValid)
    mismatch(i) := tag_mismatch
    when (tag_mismatch) {
      forward_need_uarch_drain := true.B
