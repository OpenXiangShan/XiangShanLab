# Commit Log
- Issue: #5122
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5122
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5122
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5122
- Changed files: 2
- Additions: 5
- Deletions: 3

## Files
- `src/main/scala/xiangshan/frontend/ifu/Bundles.scala`
- `src/main/scala/xiangshan/frontend/ifu/Ifu.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/ifu/Bundles.scala b/src/main/scala/xiangshan/frontend/ifu/Bundles.scala
index 197c4ec5d1a..9cb5c708ad6 100644
--- a/src/main/scala/xiangshan/frontend/ifu/Bundles.scala
+++ b/src/main/scala/xiangshan/frontend/ifu/Bundles.scala
@@ -82,12 +82,12 @@ class FetchBlockInfo(implicit p: Parameters) extends IfuBundle {
     (byteOffset - instBytes.U)(FetchBlockInstOffsetWidth, instOffsetBits)
   }
 
-  def fromFtqRequest(ftqFetch: FetchRequestBundle): FetchBlockInfo = {
+  def fromFtqRequest(ftqFetch: FetchRequestBundle, flush: Bool): FetchBlockInfo = {
     val cfiOffset      = ftqFetch.takenCfiOffset.bits
     val taken          = ftqFetch.takenCfiOffset.valid
     val calcInstrRange = Fill(FetchBlockInstNum, 1.U(1.W)) >> (~cfiOffset).asUInt
     val calcFetchSize  = cfiOffset + 1.U(log2Ceil(FetchBlockInstNum + 1).W)
-    when(ftqFetch.valid && !taken) {
+    when(ftqFetch.valid && !taken && !flush) {
       assert(
         cfiOffset === getBasicBlockIdx(ftqFetch.nextStartVAddr, ftqFetch.startVAddr),
         "when not taken, cfiOffset must match fetch block range."
diff --git a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
index f03358da0bc..c632d7fdaa9 100644
--- a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
+++ b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
@@ -158,7 +158,9 @@ class Ifu(implicit p: Parameters) extends IfuModule
   s0_flush        := s1_flush || s0_flushFromBpu(0)
 
   fromFtq.req.ready := s1_ready && io.fromICache.fetchReady
-  private val s0_fetchBlock = VecInit.tabulate(FetchPorts)(i => Wire(new FetchBlockInfo).fromFtqRequest(s0_ftqFetch(i)))
+  private val s0_fetchBlock = VecInit.tabulate(FetchPorts)(i =>
+    Wire(new FetchBlockInfo).fromFtqRequest(s0_ftqFetch(i), s0_flush || s0_flushFromBpu(i))
+  )
 
   /* *****************************************************************************
    * IFU Stage 1
```
