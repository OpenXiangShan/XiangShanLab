// src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
// Enqueue logic uses req.valid && !cancel && !wbq_block_miss_req
// - LoadPipe: io.lsu.s2_kill (...), plus s2_tag_error and s2_btot_occupy_fail
val cancel = Bool()
