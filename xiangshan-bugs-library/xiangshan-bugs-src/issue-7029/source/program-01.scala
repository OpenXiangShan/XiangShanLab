// src/main/scala/xiangshan/mem/lsqueue/LoadQueueUncache.scala:364
s2_enqueue(w) := s2_valid(w) && !s2_has_exception(w) && !s2_need_replay(w) && (s2_req(w).mmio || s2_req(w).nc)
