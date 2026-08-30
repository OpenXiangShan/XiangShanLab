# Commit Log
- Issue: #4354
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4354
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4354
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4354
- Changed files: 1
- Additions: 1
- Deletions: 0

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSROoORead.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSROoORead.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSROoORead.scala
index 713d47be6e0..d373ae601d6 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSROoORead.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSROoORead.scala
@@ -19,5 +19,6 @@ object CSROoORead {
     CSRs.hstatus,
     CSRs.mnstatus,
     CSRs.dcsr,
+    CSRs.vtype,
   )
 }
```
