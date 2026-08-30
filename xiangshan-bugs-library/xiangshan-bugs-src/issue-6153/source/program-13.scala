// src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
acquire_from_pipereg_vec(i).valid := parallel_pipe_regs(i).alloc &&
                                     !can_merge_store_from_pipe(i) &&
                                     !io.wfi.wfiReq
