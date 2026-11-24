// https://github.com/OpenXiangShan/XiangShan/blob/a7b9dea601f2f08dcca7d97ac544221cf02b1fe9/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala#L616-L621
      // two situation need to trigger paddr not match :
      // [1]. vaddr match, but paddr not match.
      // [2]. vaddr not match, but paddr match.
      val s2PaddrNoMatch       = Mux(s2ForwardValid,
        !(s2PaddrMatchVec & s2CanForward & s2SelectOH).orR, // if forward valid, select entry's paddr must match
        (s2PaddrMatchVec & s2CanForward).orR) // if forward invalid, must no paddr match
