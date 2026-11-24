// src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:304-305
} .elsewhen(writeBack && isCrossPage) {
  bufferState := s_block
// src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:311-314
is (s_block) {
  when (io.sqControl.toStoreMisalignBuffer.doDeq) {
    bufferState := s_idle
    req_valid := false.B
