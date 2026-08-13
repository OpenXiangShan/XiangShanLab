* When inject tag ecc error `io.pseudo_error.valid  == 1` and mainpipe request valid (for example `io.miss_req.valid == 1`
```
  val s1_need_replacement = s1_req.miss && !s1_tag_match
  val s1_need_eviction = s1_req.miss && !s1_tag_match && s1_repl_coh.state =/= ClientStates.Nothing
  val s1_way_en = Mux(s1_need_replacement, s1_repl_way_en, s1_tag_match_way)
```
when generate `s1_way_en`,  `s1_tag_match == 0` cause inject tag ecc error, so `s1_need_replacement == 1`, but may be target cacheline already in dcache (for example `prefetch.w` miss), finally there is 2 same paddr cacheline in dcache, obviously this is illegal.

* Determine whether it is a pseudo error. If it's pseudo error, use no-toggled-tag (which no toggled by cacheCtrl) for generate `s1_way_en`, otherwise use toggled-tag
