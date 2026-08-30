// src/main/scala/xiangshan/cache/mmu/PageTableCache.scala:1203-1208
val l0hashVmid = XORFold(sfence_dup(0).bits.id, l2tlbParams.hashAsidWidth) // 16-bit input
val l2vmidhit = VecInit(l2vmids.map(_.getOrElse(0.U) === sfence_dup(2).bits.id)).asUInt
