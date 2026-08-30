# Commit Log
- Issue: #5540
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5540
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5540
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5540
- Changed files: 1
- Additions: 5
- Deletions: 2

## Files
- `src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbReplacer.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbReplacer.scala b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbReplacer.scala
index 04233b5f086..e416882c34a 100644
--- a/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbReplacer.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbReplacer.scala
@@ -76,8 +76,11 @@ class MainBtbReplacer(implicit p: Parameters) extends MainBtbModule {
   // compose touch way vec
   private val trainTouchWay = Wire(Valid(UInt(log2Up(NumWay).W)))
   trainTouchWay.valid := io.trainTouch.valid
-  trainTouchWay.bits  := OHToUInt(io.victim.wayMask) // MainBtbAlignBank ensures this is one-hot
-  assert(!io.trainTouch.valid || PopCount(io.victim.wayMask) <= 1.U, "victim wayMask should be at-most-one-hot")
+  trainTouchWay.bits  := OHToUInt(io.trainTouch.bits.wayMask) // MainBtbAlignBank ensures this is one-hot
+  assert(
+    !io.trainTouch.valid || PopCount(io.trainTouch.bits.wayMask) <= 1.U,
+    "victim wayMask should be at-most-one-hot"
+  )
 
   // generate next state
   trainStateGen.io.stateIn   := trainState
```
