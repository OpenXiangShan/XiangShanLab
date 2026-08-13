# Commit Log
- Issue: #5073
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5073
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5073
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5073
- Changed files: 2
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/cache/mmu/BitmapCheck.scala`
- `src/main/scala/xiangshan/cache/mmu/PageTableCache.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/BitmapCheck.scala b/src/main/scala/xiangshan/cache/mmu/BitmapCheck.scala
index 0372f38d979..6fffc3f7136 100644
--- a/src/main/scala/xiangshan/cache/mmu/BitmapCheck.scala
+++ b/src/main/scala/xiangshan/cache/mmu/BitmapCheck.scala
@@ -323,7 +323,7 @@ class Bitmap(implicit p: Parameters) extends XSModule with HasPtwConst {
   io.req.ready := !full
 
   // io.resp.ready always ture
-  val wakeup_valid_1cycle = io.resp.valid && !entries(mem_ptr).hptw_bypassed && entries(mem_ptr).level =/= 0.U && entries(mem_ptr).n === 0.U
+  val wakeup_valid_1cycle = io.resp.valid && !entries(mem_ptr).hptw_bypassed && entries(mem_ptr).level === 0.U && entries(mem_ptr).n === 0.U
   // when wakeup is stall, block resp valid too
   val wakeup_stall = {
     val valid = RegInit(false.B)
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala b/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
index 43c77424e3d..404c0847503 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
@@ -561,7 +561,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
     val hit = WireInit(false.B)
     val l0bitmapreg = WireInit((VecInit(Seq.fill(l2tlbParams.l0nWays)(VecInit(Seq.fill(tlbcontiguous)(0.U(1.W)))))))
     if (HasBitmapCheck) {
-      l0bitmapreg := RegEnable(RegNext(l0BitmapReg(ridx)), stageDelay(1).fire)
+      l0bitmapreg := RegEnable(RegEnable(l0BitmapReg(ridx), stageReq.fire), stageDelay(1).fire)
       // cause llptw will trigger bitmapcheck
       // add a coniditonal logic
       // (s2x_info =/= allStage || ishptw)
```
