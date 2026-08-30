// src/main/scala/xiangshan/frontend/bpu/Bpu.scala
private val s3_useIttage = s3_firstTakenBranch.bits.attribute.needIttage && ittage.io.prediction.hit

s3_prediction.target := MuxCase(
  s3_fallThroughPrediction.target,
  Seq(
    (s3_taken && s3_useRas)    -> ras.io.topRetAddr,
    (s3_taken && s3_useIttage) -> ittage.io.prediction.target,
    s3_taken                   -> s3_firstTakenBranch.bits.target
  )
)
