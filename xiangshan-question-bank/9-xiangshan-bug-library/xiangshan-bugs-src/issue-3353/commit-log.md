# Commit Log
- Issue: #3353
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3353
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3353
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3353
- Changed files: 1
- Additions: 7
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala
index 7f774905b3d..349f36e9755 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/DebugLevel.scala
@@ -22,7 +22,13 @@ import scala.collection.immutable.SeqMap
 
 
 trait DebugLevel { self: NewCSR =>
-  val tselect = Module(new CSRModule("Tselect", new TselectBundle(TriggerNum)))
+  val tselect = Module(new CSRModule("Tselect", new TselectBundle(TriggerNum)) {
+    when (this.w.wen && this.w.wdata < TriggerNum.U) {
+      reg := this.w.wdata
+    }.otherwise {
+      reg := reg
+    }
+  })
     .setAddr(CSRs.tselect)
 
   val tdata1 = Module(new CSRModule("Tdata1") with HasTdataSink {
```
