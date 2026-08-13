# Commit Log
- Issue: #6074
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6074
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6074
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6074
- Changed files: 1
- Additions: 1
- Deletions: 0

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala
index 6d725d749fc..9d4bfbc8705 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MretEvent.scala
@@ -60,6 +60,7 @@ class MretEventModule(implicit p: Parameters) extends Module with CSREventBase {
 
   out.privState.valid := valid
   out.mstatus  .valid := valid
+  out.vsstatus .valid := valid
   out.targetPc .valid := valid
 
   out.privState.bits          := outPrivState
```
