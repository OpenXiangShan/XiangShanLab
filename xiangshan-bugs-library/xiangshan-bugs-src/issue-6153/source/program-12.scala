// src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
parallel_pipe_regs(i).alloc := ((analysis.strategy(i) & 1.U) =/= 0.U) &&
                                (analysis.compress_group(i) === i.U) &&
                                !io.queryMQ(i).req.bits.cancel &&
                                !io.wbq_block_miss_req(i)

parallel_pipe_regs(i).merge := ((analysis.strategy(i) & 2.U) =/= 0.U) &&
                                (analysis.compress_group(i) === i.U) &&
                                !io.queryMQ(i).req.bits.cancel &&
                                !io.wbq_block_miss_req(i)
