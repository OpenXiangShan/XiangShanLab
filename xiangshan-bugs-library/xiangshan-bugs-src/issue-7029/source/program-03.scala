// src/main/scala/xiangshan/mem/lsqueue/VirtualLoadQueue.scala:251-258
val loadWbIndex = io.ldin(i).bits.uop.lqIdx.value
...
when (io.ldin(i).valid) {
  ...
  when (!need_rep && need_valid && !io.ldin(i).bits.isvec) {
    committed(loadWbIndex) := true.B
