// src/main/scala/xiangshan/backend/decode/VTypeGen.scala:91-93
private val keepVlMask = VecInit((0 until DecodeWidth).map { i =>
  isVsetiVec(i) && instFieldVec(i).RD === 0.U && instFieldVec(i).RS1 === 0.U
})
