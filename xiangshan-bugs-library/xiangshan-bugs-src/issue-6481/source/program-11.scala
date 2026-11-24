// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala#L224-L232
  // Write entry only when there's a mispredict, and if:
  private val t1_entryNeedWrite = t1_needWrite && t1_mispredictInfo.valid && (
    // 1. not hit, always write a new entry, use mbtb replacer's victim way.
    !t1_hit ||
      // 2. hit, do write only if:
      //   a. it's an OtherIndirect-type branch (to update target and play the role of Ittage's base table).
      t1_mispredictInfo.bits.attribute.needIttage ||
      //   b. attribute changed, probably indicating a software self-modification.
      t1_mispredictInfo.bits.attribute =/= Mux1H(t1_hitMask, t1_meta.map(_.attribute))
