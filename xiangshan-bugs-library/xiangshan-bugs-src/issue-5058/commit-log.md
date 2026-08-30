# Commit Log
- Issue: #5058
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5058
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5058
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5058
- Changed files: 1
- Additions: 38
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/bpu/FallThroughPredictor.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/FallThroughPredictor.scala b/src/main/scala/xiangshan/frontend/bpu/FallThroughPredictor.scala
index 68f3a886ffd..abb97055f65 100644
--- a/src/main/scala/xiangshan/frontend/bpu/FallThroughPredictor.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/FallThroughPredictor.scala
@@ -42,6 +42,26 @@ class FallThroughPredictor(implicit p: Parameters) extends BasePredictor
   private val s1_fire       = io.stageCtrl.s1_fire
   private val s1_startVAddr = RegEnable(s0_startVAddr, s0_fire)
 
+  // e.g. FetchBlockSize = 64B, FetchBlockAlignSize = 32B, we have CfiPositionWidth = 5
+  // start | nextBlock | crossPage | nextPage | cfiPc | cfiPosition
+  // 0x000 |   0x0040  |   false   |  0x0000  | 0x03e | 0x1f // (0x03e-0x000) >> 1
+  // 0x010 |   0x0040  |   false   |  0x0000  | 0x03e | 0x1f // (0x03e-0x000) >> 1
+  // 0x020 |   0x0060  |   false   |  0x0000  | 0x05e | 0x1f // (0x05e-0x020) >> 1
+  // 0xfc0 |   0x1000  |    true   |  0x1000  | 0xffe | 0x1f // (0xffe-0xfc0) >> 1
+  // 0xfd0 |   0x1000  |    true   |  0x1000  | 0xffe | 0x1f // (0xffe-0xfc0) >> 1
+  // 0xfe0 |   0x1020  |    true   |  0x1000  | 0xffe | 0x0f // (0xffe-0xfe0) >> 1
+  // 0xff0 |   0x1020  |    true   |  0x1000  | 0xffe | 0x0f // (0xffe-0xfe0) >> 1
+
+  // e.g. FetchBlockSize = 64B, FetchBlockAlignSize = 16B, we have CfiPositionWidth = 5
+  // start | nextBlock | crossPage | nextPage | cfiPc | cfiPosition
+  // 0x000 |   0x0040  |   false   |  0x0000  | 0x03e | 0x1f // (0x03e-0x000) >> 1
+  // 0x010 |   0x0050  |   false   |  0x0000  | 0x04e | 0x1f // (0x04e-0x010) >> 1
+  // 0x020 |   0x0060  |   false   |  0x0000  | 0x05e | 0x1f // (0x05e-0x020) >> 1
+  // 0xfc0 |   0x1000  |    true   |  0x1000  | 0xffe | 0x1f // (0xffe-0xfc0) >> 1
+  // 0xfd0 |   0x1010  |    true   |  0x1000  | 0xffe | 0x17 // (0xffe-0xfd0) >> 1
+  // 0xfe0 |   0x1020  |    true   |  0x1000  | 0xffe | 0x0f // (0xffe-0xfe0) >> 1
+  // 0xff0 |   0x1030  |    true   |  0x1000  | 0xffe | 0x07 // (0xffe-0xff0) >> 1
+
   // fall-through address = startVAddr + FetchBlockSize(64B), aligned to FetchBlockAlign(32B)
   private val s1_nextBlockAlignedAddr = getAlignedAddr(s1_startVAddr + FetchBlockSize.U)
 
@@ -49,6 +69,23 @@ class FallThroughPredictor(implicit p: Parameters) extends BasePredictor
   private val s1_crossPage           = isCrossPage(s1_startVAddr, s1_nextBlockAlignedAddr) // compare LSB of Vpn
   private val s1_nextPageAlignedAddr = getPageAlignedAddr(s1_nextBlockAlignedAddr)         // clear page offset
 
+  // cfiPosition is the offset between alignedStart and last 2B before nextStart (nextBlock or nextPage), in instr
+  // i.e. if not crossPage:
+  //    cfiPosition = ((nextBlock - alignedStart) >> instOffsetBits) - 1
+  //                = (FetchBlockSize >> instOffsetBits) - 1
+  //                = FetchBlockInstNum - 1 // constant
+  // otherwise:
+  //    cfiPosition = ((nextPage - alignedStart) >> instOffsetBits) - 1
+  //                = ((-(alignedStart - nextPage)) >> instOffsetBits) - 1
+  //                // ignore the higher bits, alignedStart(CfiPositionWidth, 1) === nextBlock(CfiPositionWidth, 1)
+  //                = ((-(nextBlock - nextPage)) >> instOffsetBits) - 1
+  //                = (~(nextBlock - nextPage)) >> instOffsetBits
+  private val s1_cfiPosition = Mux(
+    s1_crossPage,
+    ~(s1_nextBlockAlignedAddr - s1_nextPageAlignedAddr)(CfiPositionWidth + instOffsetBits - 1, instOffsetBits),
+    (FetchBlockInstNum - 1).U
+  )
+
   private val s1_fallThroughAddr = Mux(
     s1_crossPage,
     s1_nextPageAlignedAddr,
@@ -56,7 +93,7 @@ class FallThroughPredictor(implicit p: Parameters) extends BasePredictor
   )
 
   io.prediction.taken       := false.B
-  io.prediction.cfiPosition := (FetchBlockInstNum - 1).U
+  io.prediction.cfiPosition := s1_cfiPosition
   io.prediction.target      := s1_fallThroughAddr
   io.prediction.attribute   := BranchAttribute.None
```
