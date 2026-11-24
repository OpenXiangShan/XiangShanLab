// https://github.com/OpenXiangShan/XiangShan/blob/09453ff8f44e9d2b526e6149c60e1dc3f08c7192/src/main/scala/xiangshan/mem/pipeline/AtomicsUnit.scala#L261-L269
      val addrAligned = LookupTree(amoSize, List(
        LSUOpType.AMOSize.B.U -> true.B,
        LSUOpType.AMOSize.H.U -> (vaddr(0) === 0.U),
        LSUOpType.AMOSize.W.U -> (vaddr(1,0) === 0.U),
        LSUOpType.AMOSize.D.U -> (vaddr(2,0) === 0.U),
        LSUOpType.AMOSize.Q.U -> (vaddr(3,0) === 0.U)
      ))
      exceptionVec(loadAddrMisaligned)  := !addrAligned && isLr
      exceptionVec(storeAddrMisaligned) := !addrAligned && !isLr
