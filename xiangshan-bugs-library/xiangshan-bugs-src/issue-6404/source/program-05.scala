// src/main/scala/xiangshan/frontend/ifu/Ifu.scala:536-543
  // if icache respond with exception, it's marked on entire cacheline,
  // so the first enqueued instr should be marked with exception
  // otherwise, we only have rvcException, so select its offset
  io.toIBuffer.bits.exceptionMask := Mux(
    s2_icacheMeta(0).exception.hasException,
    VecInit.tabulate(IBufferEnqueueWidth)(i => if (i < IfuAlignWidth) i.U === s2_alignShiftNum else false.B),
    VecInit.tabulate(IBufferEnqueueWidth)(i => enq(i) & s2_rvcIll(i))
  )
