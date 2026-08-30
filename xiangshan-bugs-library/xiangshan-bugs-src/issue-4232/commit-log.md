# Commit Log
- Issue: #4232
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4232
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4232
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4232
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/frontend/newRAS.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/newRAS.scala b/src/main/scala/xiangshan/frontend/newRAS.scala
index ec9184d2f74..0fbd5ccd332 100644
--- a/src/main/scala/xiangshan/frontend/newRAS.scala
+++ b/src/main/scala/xiangshan/frontend/newRAS.scala
@@ -710,8 +710,8 @@ class RAS(implicit p: Parameters) extends BasePredictor {
   val do_recover  = redirect.valid
   val recover_cfi = redirect.bits.cfiUpdate
 
-  val retMissPred  = do_recover && redirect.bits.level === 0.U && recover_cfi.pd.isRet
-  val callMissPred = do_recover && redirect.bits.level === 0.U && recover_cfi.pd.isCall
+  val retMissPred  = do_recover && redirect.bits.level === 0.U && recover_cfi.pd.isRet && recover_cfi.pd.valid
+  val callMissPred = do_recover && redirect.bits.level === 0.U && recover_cfi.pd.isCall && recover_cfi.pd.valid
   // when we mispredict a call, we must redo a push operation
   // similarly, when we mispredict a return, we should redo a pop
   stack.redirect_valid     := do_recover
```
