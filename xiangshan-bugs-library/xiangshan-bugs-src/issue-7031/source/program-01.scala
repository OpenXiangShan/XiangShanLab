// src/main/scala/xiangshan/cache/mmu/PageTableCache.scala:528
  val (l0Hit, l0HitData, l0Pre, l0eccError, l0HitWay, l0BitmapCheckResult, l0JmpBitmapCheck) = {
// src/main/scala/xiangshan/cache/mmu/PageTableCache.scala:553-566
    val hitVec = RegEnable(hitVec_delay, stageDelay(1).fire)
    ...
    val hitWay = ParallelPriorityMux(hitVec zip (0 until l2tlbParams.l0nWays).map(_.U(log2Up(l2tlbParams.l0nWays).W)))
    ...
      l0bitmapreg := RegEnable(RegEnable(l0BitmapReg(ridx), stageReq.fire), stageDelay(1).fire)
// src/main/scala/xiangshan/cache/mmu/PageTableCache.scala:598
    (hit, hitWayData, hitWayData.prefetch, eccError, UIntToOH(hitWay), l0bitmapreg(hitWay), jmp_bitmap_check)
// src/main/scala/xiangshan/cache/mmu/PageTableCache.scala:605-610
  val l0cfs = WireInit(VecInit(Seq.fill(tlbcontiguous)(false.B))) // L0 lavel Bitmap Check Failed Vector
  ...
      l0cfs(i) := !l0BitmapCheckResult(i)
