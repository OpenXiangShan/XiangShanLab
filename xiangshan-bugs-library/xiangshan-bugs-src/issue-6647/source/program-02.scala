connectWbFuBusyTableIn(sink = ...intWbFuBusyTableIn, deqWen = out.deq.map(_.bits.gpWen), ...)
connectWbFuBusyTableIn(sink = ...fpWbFuBusyTableIn,  deqWen = out.deq.map(x => x.bits.fpWen || x.bits.gpWen), ...)
// and vpWen / v0Wen / vlWen for the three vector tables
