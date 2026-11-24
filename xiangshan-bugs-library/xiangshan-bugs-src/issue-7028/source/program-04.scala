// src/main/scala/xiangshan/mem/lsqueue/StoreMisalignBuffer.scala:227
io.sqControl.toStoreQueue.crossPageWithHit := sameSqPtr && isCrossPage && req_valid
// src/main/scala/xiangshan/mem/lsqueue/StoreQueue.scala:1205
val isCross4KPage = io.maControl.toStoreQueue.crossPageWithHit
