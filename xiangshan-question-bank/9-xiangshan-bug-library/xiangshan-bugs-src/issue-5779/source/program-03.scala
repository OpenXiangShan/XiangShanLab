// src/main/scala/xiangshan/cache/mmu/TLBStorage.scala:274
// HFENCE.GVMA with specific VMID (rs2 ≠ x0):
v.zipWithIndex.map { case (a, i) =>
  a := a && !(entries(i).s2xlate =/= noS2xlate && entries(i).vmid === sfence.bits.id)
}
