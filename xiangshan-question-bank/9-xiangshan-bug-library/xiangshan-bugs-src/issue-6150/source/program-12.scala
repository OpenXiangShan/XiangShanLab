// src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
private val xretTargetUpdate =
  mnretEvent.out.targetPc.valid ||
  mretEvent.out.targetPc.valid  ||
  sretEvent.out.targetPc.valid  ||
  dretEvent.out.targetPc.valid

io.xretTargetPc.bits := DataHoldBypass(..., xretTargetUpdate)
