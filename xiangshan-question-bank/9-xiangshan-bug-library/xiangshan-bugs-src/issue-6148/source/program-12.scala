// src/main/scala/xiangshan/frontend/bpu/ittage/IttageTable.scala
val writeValid = readPort.valid && !bank.io.r.req.valid
bank.io.w.apply(writeValid, writeEntry, writeSetIdx, true.B, writeBitMask)
readPort.ready := bank.io.w.req.ready && !bank.io.r.req.valid

XSPerfAccumulate(
  "ittage_table_read_write_conflict",
  VecInit(tables.zip(writeBuffers).map { case (bank, buffer) =>
    bank.io.r.req.valid && buffer.io.read.head.valid
  }).asUInt.orR
)
