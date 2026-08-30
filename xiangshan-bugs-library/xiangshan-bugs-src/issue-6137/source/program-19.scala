// src/main/scala/xiangshan/frontend/bpu/Bpu.scala
when(io.toFtq.prediction.fire && abtb.io.prediction.map(_.valid).reduce(_ || _)) {
  assert(abtb.io.debug_startPc === s1_startPc)
}
