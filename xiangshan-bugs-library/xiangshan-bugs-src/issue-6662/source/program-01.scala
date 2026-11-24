// https://github.com/OpenXiangShan/XiangShan/blob/09453ff8f44e9d2b526e6149c60e1dc3f08c7192/src/main/scala/xiangshan/cache/mmu/MptChecker.scala#L1130-L1132
  val rsvZeroError0 = RegEnable(mem.resp.bits(9, 2).orR, false.B, memRespAccepted)
  // max 3 level or gate on top of mem.resp，NON ZERO error of mtpe
  val rsvZeroError1 = RegEnable(mem.resp.bits(62, 58).orR, false.B, memRespAccepted)
