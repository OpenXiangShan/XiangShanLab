# Commit Log
- Issue: #5526
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5526
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5526
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5526
- Changed files: 1
- Additions: 19
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/bpu/WriteBuffer.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/WriteBuffer.scala b/src/main/scala/xiangshan/frontend/bpu/WriteBuffer.scala
index 17d89ee8fde..1c81f695a34 100644
--- a/src/main/scala/xiangshan/frontend/bpu/WriteBuffer.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/WriteBuffer.scala
@@ -80,6 +80,8 @@ class WriteBuffer[T <: WriteReqBundle](
   private val emptyVec     = WireInit(VecInit(Seq.fill(numPorts)(false.B)))
   private val fullVec      = WireInit(VecInit(Seq.fill(numPorts)(false.B)))
   private val writeFlowVec = WireInit(VecInit(Seq.fill(numPorts)(false.B)))
+  // Replace is to prioritize replacing the entry of the same port as setIdx
+  private val victimSameSetIdx = WireInit(VecInit.fill(numPorts)(0.U.asTypeOf(Valid(UInt(log2Ceil(numEntries).W)))))
 
   writePortValid := io.write.map(_.valid)
   writePortBits  := io.write.map(_.bits)
@@ -87,6 +89,18 @@ class WriteBuffer[T <: WriteReqBundle](
   dontTouch(replacerWay)
   dontTouch(emptyVec)
 
+  writePortValid.zipWithIndex.foreach { case (writeValid, portIdx) =>
+    val setIdxHitVec = entries(portIdx).zip(valids(portIdx)).map { case (entry, valid) =>
+      writeValid && valid && writePortBits(portIdx).setIdx === entry.setIdx
+    }
+    XSError(
+      writeValid && PopCount(setIdxHitVec) > 1.U,
+      f"WriteBuffer_$nameSuffix port${portIdx}_hitMask should be no more than 1"
+    )
+    victimSameSetIdx(portIdx).valid := setIdxHitVec.reduce(_ || _)
+    victimSameSetIdx(portIdx).bits  := OHToUInt(VecInit(setIdxHitVec))
+  }
+
   // not allowed to write entries with same setIdx and tag
   for {
     i <- 0 until numPorts
@@ -140,7 +154,11 @@ class WriteBuffer[T <: WriteReqBundle](
       val notUsefulVec = needWrite(portIdx).map(!_)
       val notUseful    = notUsefulVec.reduce(_ || _)
       val notUsefulIdx = PriorityEncoder(notUsefulVec)
-      val victim       = Mux(notUseful, notUsefulIdx, replacerWay(portIdx))
+      val victim = Mux(
+        victimSameSetIdx(portIdx).valid,
+        victimSameSetIdx(portIdx).bits,
+        Mux(notUseful, notUsefulIdx, replacerWay(portIdx))
+      )
       // if this write port !hit need to write a new entry
       when(!hit) {
         entries(portIdx)(victim)     := io.write(portIdx).bits
```
