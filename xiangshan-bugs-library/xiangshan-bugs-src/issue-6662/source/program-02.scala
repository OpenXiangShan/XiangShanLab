// https://github.com/OpenXiangShan/XiangShan/blob/09453ff8f44e9d2b526e6149c60e1dc3f08c7192/src/main/scala/xiangshan/cache/mmu/MptChecker.scala#L1158
  val entryError = mpteInvalid || rsvZeroError0 || rsvZeroError1 || rsvZeroError2 || ((!isLeafMpte) && level === 1.U)
