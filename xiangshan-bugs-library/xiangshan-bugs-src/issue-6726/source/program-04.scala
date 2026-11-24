// https://github.com/OpenXiangShan/XiangShan/blob/18657f51ce1ed4acaffdceb38bb79ebae68b6af4/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala#L276-L280
  val hasTrap = io.fromRob.trap.valid
  val trapVec = io.fromRob.trap.bits.trapVec
  val trapPC = io.fromRob.trap.bits.pc
  val trapPCGPA = io.fromRob.trap.bits.pcGPA
  val trapIsInterrupt = io.fromRob.trap.bits.isInterrupt
