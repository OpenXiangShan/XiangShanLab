// src/main/scala/xiangshan/frontend/bpu/ittage/Ittage.scala
private val s1_isIndirect = true.B // (!s1_uftbHit && !io.fromFtb.s1_ftbCloseReq) || s1_uftbHasIndirect

tables.foreach { t =>
  t.io.req.valid := s1_fire && s1_isIndirect // TODO: s1_isIndirect for low power
}
