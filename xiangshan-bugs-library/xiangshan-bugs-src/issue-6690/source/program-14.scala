// https://github.com/OpenXiangShan/XiangShan/blob/505315237f4079d6dafeacbab906e7e6962b41a5/src/main/scala/xiangshan/mem/lsqueue/NewStoreQueue.scala#L476-L482
      //TODO: consumer need to choise whether use paddrNoMatch or not.
      val selectIsCboZero     = s2SelectCtrlEntry.isCbo && isCboZero(s2SelectDataEntry.cboType)
      // !Paddrmatch
      val paddrNoMatch       = !(
        (s2SelectDataEntry.paddr(log2Ceil(CacheLineSize / 8) - 1, log2Ceil(VLENB)) === s2LoadPaddr(log2Ceil(CacheLineSize / 8) - log2Ceil(VLENB) - 1, 0) || selectIsCboZero) &&
        s2SelectDataEntry.paddr(PAddrBits - 1, log2Ceil(CacheLineSize / 8)) === s2LoadPaddr(s2LoadPaddr.getWidth - 1, log2Ceil(CacheLineSize / 8) - log2Ceil(VLENB))
        ) && s2Valid
