# Commit Log
- Issue: #5143
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5143
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5143
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5143
- Changed files: 1
- Additions: 9
- Deletions: 7

## Files
- `src/main/scala/xiangshan/frontend/bpu/WriteBuffer.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/WriteBuffer.scala b/src/main/scala/xiangshan/frontend/bpu/WriteBuffer.scala
index 81c92486a50..8a2fbf885dc 100644
--- a/src/main/scala/xiangshan/frontend/bpu/WriteBuffer.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/WriteBuffer.scala
@@ -63,6 +63,7 @@ class WriteBuffer[T <: WriteReqBundle](
   private val valids  = RegInit(VecInit(Seq.fill(numPorts)(VecInit(Seq.fill(numEntries)(false.B)))))
 
   private val writePortValid = VecInit(Seq.fill(numPorts)(false.B))
+  private val writePortBits  = VecInit(Seq.fill(numPorts)(0.U.asTypeOf(gen.cloneType)))
   // Record the hitTouch situation for each row of all write requests
   private val hitTouchVec =
     WireInit(VecInit.fill(numPorts)(VecInit.fill(numEntries)(0.U.asTypeOf(Valid(UInt(log2Ceil(numEntries).W))))))
@@ -75,8 +76,8 @@ class WriteBuffer[T <: WriteReqBundle](
   private val fullVec      = WireInit(VecInit(Seq.fill(numPorts)(false.B)))
   private val writeFlowVec = WireInit(VecInit(Seq.fill(numPorts)(false.B)))
 
-  private val writePortVec = io.write
-  dontTouch(writePortVec)
+  writePortValid := io.write.map(_.valid)
+  writePortBits  := io.write.map(_.bits)
   dontTouch(readValidVec)
   dontTouch(replacerWay)
   dontTouch(emptyVec)
@@ -86,10 +87,11 @@ class WriteBuffer[T <: WriteReqBundle](
     i <- 0 until numPorts
     j <- i + 1 until numPorts
   } {
-    writePortValid(i) := writePortVec(i).valid
+    val writeSameEntry =
+      writePortValid(i) && writePortValid(j) && writePortBits(i).setIdx === writePortBits(j).setIdx &&
+        writePortBits(i).tag.getOrElse(0.U) === writePortBits(j).tag.getOrElse(0.U)
     XSError(
-      writePortVec(i).valid && writePortVec(j).valid && writePortVec(i).bits.setIdx === writePortVec(j).bits.setIdx &&
-        writePortVec(i).bits.tag.getOrElse(0.U) === writePortVec(j).bits.tag.getOrElse(0.U),
+      writeSameEntry,
       f"WriteBuffer can not support write same data on the same cycle, port${i} and port${j} write the same entry "
     )
   }
@@ -108,8 +110,8 @@ class WriteBuffer[T <: WriteReqBundle](
     val hitMask = VecInit.fill(numPorts)(VecInit.fill(numEntries)(false.B))
     for (p <- 0 until numPorts; e <- 0 until numEntries) {
       hitMask(p)(e) := writeValid && valids(p)(e) &&
-        writePortVec(portIdx).bits.setIdx === entries(p)(e).setIdx &&
-        writePortVec(portIdx).bits.tag.getOrElse(0.U) === entries(p)(e).tag.getOrElse(0.U)
+        writePortBits(portIdx).setIdx === entries(p)(e).setIdx &&
+        writePortBits(portIdx).tag.getOrElse(0.U) === entries(p)(e).tag.getOrElse(0.U)
     }
     // each write port can only hit at most one entry
     XSError(
```
