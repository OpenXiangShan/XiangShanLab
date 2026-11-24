// MemBlock.scala:1197-1198
val stIsEmpty = sbuffer.io.flush.empty && uncache.io.flush.empty
io.mem_to_ooo.sbIsEmpty := RegNext(stIsEmpty)
