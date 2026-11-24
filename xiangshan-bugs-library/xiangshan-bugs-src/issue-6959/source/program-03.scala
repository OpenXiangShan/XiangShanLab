// Sbuffer.scala:539-544
val sbuffer_empty = Cat(invalidMask).andR
val sbuffer_mshr_empty = sbuffer_empty && io.mshr_store_empty
val inReq_empty = !Cat(io.in.req.map(_.valid)).orR
val cmo_empty = sbuffer_mshr_empty && inReq_empty
val all_empty = cmo_empty && io.sqempty

// Sbuffer.scala:559
io.flush.empty := GatedValidRegNext(all_empty)
