when(s1_fire)(s2_valid := true.B)
  .elsewhen(s2_flush)(s2_valid := false.B)
  .elsewhen(s2_fire)(s2_valid := false.B)
