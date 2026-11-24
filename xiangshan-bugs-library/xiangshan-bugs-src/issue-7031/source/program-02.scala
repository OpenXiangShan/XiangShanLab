// src/main/scala/xiangshan/cache/mmu/PageTableCache.scala:692-696
  check_res.l0.apply(l0Hit, ... cfs = l0cfs)
  ...
  val resp_res = Reg(new PageCacheRespBundle)
  when (stageCheck(1).fire) { resp_res := check_res }
// src/main/scala/xiangshan/cache/mmu/PageTableCache.scala:739
    io.resp.bits.toFsm.bitmapCheck.get.cfs := resp_res.l0.bitmapCheck.get.cfs
// src/main/scala/xiangshan/cache/mmu/PageTableCache.scala:829
    io.resp.bits.stage1.entry(i).cf := Mux(resp_res.l0.hit, l0cfs(i), false.B) // L0 lavel Bitmap Check Failed Vector
