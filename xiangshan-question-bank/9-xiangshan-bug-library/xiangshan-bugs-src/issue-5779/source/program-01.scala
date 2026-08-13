// src/main/scala/xiangshan/Bundle.scala
class SfenceBundle(implicit p: Parameters) extends XSBundle {
  val bits = new Bundle {
    val id = UInt((AsidLength).W) // asid or vmid   ← AsidLength = 16
  }
}

// src/main/scala/xiangshan/Parameters.scala
AsidLength: Int = 16,
VmidLength: Int = 14,   // ← VMID is only 14 bits
