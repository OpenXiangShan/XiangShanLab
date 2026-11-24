// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/bpu/Bpu.scala#L408-L416
  s3_prediction.target :=
    MuxCase(
      s3_fallThroughPrediction.target,
      Seq(
        (s3_taken && s3_useRas)    -> ras.io.topRetAddr,
        (s3_taken && s3_useIttage) -> ittage.io.prediction.target,
        s3_taken                   -> s3_firstTakenBranch.bits.target
      )
    )
