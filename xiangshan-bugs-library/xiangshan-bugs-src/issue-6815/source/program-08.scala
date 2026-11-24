  val intrBitSetReg = RegNext(io.csr.intrBitSet)
  val intrEnable = intrBitSetReg && !hasWaitForward && deqPtrEntry.interruptSafe && !deqHasFlushed
