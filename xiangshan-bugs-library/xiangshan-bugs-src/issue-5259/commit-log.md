# Commit Log
- Issue: #5259
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5259
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5259
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5259
- Changed files: 1
- Additions: 3
- Deletions: 3

## Files
- `src/main/scala/xiangshan/backend/issue/IssueQueue.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/issue/IssueQueue.scala b/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
index 887d761ffe4..ae6432933dc 100644
--- a/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
+++ b/src/main/scala/xiangshan/backend/issue/IssueQueue.scala
@@ -934,8 +934,8 @@ class IssueQueueImp(implicit p: Parameters, params: IssueBlockParams) extends XS
     // deqBeforeDly.ready is always true
     deq.ready := true.B
     // for int scheduler fdiv has high priority than alu
-    if (params.inIntSchd && deqFuCfgs(i).contains(DivCfg)) {
-      // wakeupFromExu only one div
+    if (params.inIntSchd && (deqFuCfgs(i).contains(DivCfg) || deqFuCfgs(i).contains(CsrCfg))) {
+      // div and csr need wakeupFromExu
       io.wakeupFromExu.foreach(x => {
         deq.ready := !x.head.valid
         deqDly.valid := deq.valid && !x.head.valid
@@ -943,7 +943,7 @@ class IssueQueueImp(implicit p: Parameters, params: IssueBlockParams) extends XS
     }
     if (params.aluDeqNeedPickJump && deqFuCfgs(i).contains(JmpCfg)) {
       io.wakeupFromExu.foreach(x => {
-        deq.ready := !(entries.io.aluDeqSelectJump.get && x.head.valid)
+        deq.ready := !entries.io.aluDeqSelectJump.get
         deqDly.valid := deq.valid && !(entries.io.aluDeqSelectJump.get && x.head.valid)
       })
     }
```
