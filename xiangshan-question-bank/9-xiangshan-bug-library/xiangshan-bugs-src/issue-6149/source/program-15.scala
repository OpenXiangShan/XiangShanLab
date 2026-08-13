s3_prediction.target := MuxCase(
  s3_fallThroughPrediction.target,
  Seq(
    (s3_taken && s3_useRas) -> ras.io.topRetAddr,
    ...
  )
)
