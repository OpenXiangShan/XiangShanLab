io.prediction.zipWithIndex.foreach { case (pred, i) =>
  pred.valid := s2_valid && s2_hitMask(i)
  ...
}

io.meta.valid := s2_valid
