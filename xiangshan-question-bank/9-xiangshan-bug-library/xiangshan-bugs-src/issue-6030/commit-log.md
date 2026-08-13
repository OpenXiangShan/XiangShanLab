# Commit Log
- Issue: #6030
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6030
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6030
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6030
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
index 630b8836c08..edbf55fc4a9 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/InterruptFilter.scala
@@ -297,7 +297,7 @@ class InterruptFilter extends Module {
   // Candidate2,Candidate5 不可能同时成立
   val onlyC1Enable = Candidate1 & !Candidate45
   val onlyC2Enable = Candidate2 & !Candidate45
-  val onlyC3Enable = Candidate3 & !Candidate123
+  val onlyC3Enable = Candidate3 & !Candidate45
   val onlyC4Enable = Candidate4 & !Candidate123
   val onlyC5Enable = Candidate5 & !Candidate123
   val C1C4Enable   = Candidate1 & Candidate4
```
