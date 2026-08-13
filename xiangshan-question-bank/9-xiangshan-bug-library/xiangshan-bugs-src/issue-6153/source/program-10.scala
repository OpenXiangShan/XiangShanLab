// src/main/scala/xiangshan/cache/dcache/loadpipe/LoadPipe.scala
io.miss_req.bits.cancel := io.lsu.s2_kill || s2_tag_error || s2_btot_occupy_fail
