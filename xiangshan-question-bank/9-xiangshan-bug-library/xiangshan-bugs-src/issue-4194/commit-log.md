# Commit Log
- Issue: #4194
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4194
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4194
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4194
- Changed files: 2
- Additions: 4
- Deletions: 0

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala
index 32446f57334..b4fff31d684 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/CSREvent.scala
@@ -34,9 +34,11 @@ trait CSREvents { self: NewCSR =>
     trapEntryMEvent,
     trapEntryHSEvent,
     trapEntryVSEvent,
+    trapEntryMNEvent,
     mretEvent,
     sretEvent,
     dretEvent,
+    mnretEvent,
   )
 
   events.foreach(x => dontTouch(x.out))
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala
index b4e8437f3f2..296dd18914a 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala
@@ -63,6 +63,8 @@ class MNretEventModule(implicit p: Parameters) extends Module with CSREventBase
 
   out.privState.valid := valid
   out.mnstatus .valid := valid
+  out.mstatus  .valid := valid
+  out.vsstatus .valid := valid
   out.targetPc .valid := valid
 
   out.privState.bits          := outPrivState
```
