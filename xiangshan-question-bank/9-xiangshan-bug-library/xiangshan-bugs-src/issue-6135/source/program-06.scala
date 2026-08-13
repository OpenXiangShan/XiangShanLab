// src/main/scala/xiangshan/backend/fu/NewCSR/CSRCustom.scala
val RAS_ENABLE = RW(6).withReset(true.B).withDescription("Enable the return-address stack predictor.")

// src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
io.status.custom.bp_ctrl.rasEnable := sbpctl.regOut.RAS_ENABLE.asBool

// src/main/scala/xiangshan/frontend/bpu/Bpu.scala
uras.io.enable := true.B
ras.io.enable  := ctrl.rasEnable
