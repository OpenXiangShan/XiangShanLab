// src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtb.scala
s0_fire := io.enable && predictReqValid
s1_fire := io.enable && s1_valid && s2_ready && predictReqValid
s2_fire := io.enable && s2_valid && predictionSent
