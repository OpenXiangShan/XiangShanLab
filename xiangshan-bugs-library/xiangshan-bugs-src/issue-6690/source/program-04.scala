// https://github.com/OpenXiangShan/XiangShan/blob/a7b9dea601f2f08dcca7d97ac544221cf02b1fe9/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala#L506-L516
      // Two-step selection to handle circular queue segments
      val s1CanForwardLow = s1AgeMaskLow & s1OverlapMask & s1VaddrMatchVec
      val s1CanForwardHigh = s1AgeMaskHigh & s1OverlapMask & s1VaddrMatchVec

      // find youngest entry, which is one-hot
      // Find youngest store (highest index = most recent)
      //   Reverse vector so we can find leftmost 1 (highest index)
      val (s1SelectLowOH, _)             = findYoungest(Reverse(s1CanForwardLow))
      val (s1ForwardHighOH, _)           = findYoungest(Reverse(s1CanForwardHigh))
      val s1SelectHighOH                 = s1ForwardHighOH & VecInit(Seq.fill(StoreQueuePhysicalSize)(!s1CanForwardLow.orR)).asUInt
      val s1SelectOH                     = Reverse(s1SelectLowOH | s1SelectHighOH) // index higher, mean it younger
