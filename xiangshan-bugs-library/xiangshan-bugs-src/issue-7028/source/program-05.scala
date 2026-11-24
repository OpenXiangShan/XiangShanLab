// src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1251
val paddrHigh = Cat(paddrModule.io.rdata(0)(paddrModule.io.rdata(0).getWidth - 1, 3), 0.U(3.W)) + 8.U
// src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1287-1300
} .otherwise {
  ...
  else {
    dataBuffer.io.enq(i).bits.addr      := paddrHigh
