// https://github.com/OpenXiangShan/XiangShan/blob/e6228891e42f63bb262e323a8a3005dbe664612c/src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala#L693-L702
    // vaddr cam result does not equal to paddr cam result
    // replay needed
    // val vpmaskNotEqual = ((paddrModule.io.forwardMmask(i).asUInt ^ vaddrModule.io.forwardMmask(i).asUInt) & needForward) =/= 0.U
    // val vaddrMatchFailed = vpmaskNotEqual && io.forward(i).valid
    val vpmaskNotEqual = (
      (RegEnable(paddrModule.io.forwardMmask(i).asUInt, io.forward(i).valid) ^ RegEnable(vaddrModule.io.forwardMmask(i).asUInt, io.forward(i).valid)) &
      RegNext(needForward) &
      GatedRegNext(addrRealValidVec.asUInt)
    ) =/= 0.U
    val vaddrMatchFailed = vpmaskNotEqual && RegNext(io.forward(i).valid)
