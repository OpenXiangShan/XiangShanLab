// https://github.com/OpenXiangShan/XiangShan/blob/92cc48d2cb6af52a1fb2922f9eb99a37918654b3/src/main/scala/xiangshan/frontend/bpu/Bpu.scala#L469-L475
  io.toFtq.prediction.valid := s1_valid && s2_ready || s3_override
  when(s3_override) {
    io.toFtq.prediction.bits.fromStage(s3_startPc.get, s3_prediction)
  }.otherwise {
    io.toFtq.prediction.bits.fromStage(s1_startPc.get, s1_prediction)
  }
  io.toFtq.prediction.bits.s3Override := s3_override
