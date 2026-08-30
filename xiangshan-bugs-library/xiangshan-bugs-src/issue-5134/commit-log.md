# Commit Log
- Issue: #5134
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5134
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5134
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5134
- Changed files: 1
- Additions: 5
- Deletions: 5

## Files
- `src/main/scala/xiangshan/frontend/bpu/WriteBuffer.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/WriteBuffer.scala b/src/main/scala/xiangshan/frontend/bpu/WriteBuffer.scala
index 503692f28f5..81c92486a50 100644
--- a/src/main/scala/xiangshan/frontend/bpu/WriteBuffer.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/WriteBuffer.scala
@@ -136,11 +136,11 @@ class WriteBuffer[T <: WriteReqBundle](
       val victim       = Mux(notUseful, notUsefulIdx, replacerWay(portIdx))
       // if this wirte port !hit need to write a new entry
       when(!hit) {
-        entries(portIdx)(victim)    := io.write(portIdx).bits
-        valids(portIdx)(victim)     := true.B
-        needWrite(portIdx)(victim)  := true.B
-        writeTouchVec(victim).valid := true.B
-        writeTouchVec(victim).bits  := victim
+        entries(portIdx)(victim)     := io.write(portIdx).bits
+        valids(portIdx)(victim)      := true.B
+        needWrite(portIdx)(victim)   := true.B
+        writeTouchVec(portIdx).valid := true.B
+        writeTouchVec(portIdx).bits  := victim
       }
 
       // if hit need to update the entry
```
