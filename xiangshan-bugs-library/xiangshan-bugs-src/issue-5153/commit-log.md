# Commit Log
- Issue: #5153
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5153
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5153
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5153
- Changed files: 1
- Additions: 1
- Deletions: 0

## Files
- `src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtbBank.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtbBank.scala b/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtbBank.scala
index 03f071c9725..192553f9449 100644
--- a/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtbBank.scala
+++ b/src/main/scala/xiangshan/frontend/bpu/abtb/AheadBtbBank.scala
@@ -40,6 +40,7 @@ class AheadBtbBank(implicit p: Parameters) extends AheadBtbModule {
     way = NumWays,
     singlePort = true,
     shouldReset = true,
+    holdRead = true,
     withClockGate = true,
     hasMbist = hasMbist,
     hasSramCtl = hasSramCtl
```
