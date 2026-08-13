# Commit Log
- Issue: #5867
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5867
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5867
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5867
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala b/src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala
index 24e3bdebe04..c5b64dceae4 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/JumpUnit.scala
@@ -35,7 +35,7 @@ class JumpUnit(cfg: FuConfig)(implicit p: Parameters) extends PipedFuncUnit(cfg)
 
   val redirect = io.out.bits.res.redirect.get.bits
   val redirectValid = io.out.bits.res.redirect.get.valid
-  redirectValid := io.in.valid && !jumpDataModule.io.isAuipc
+  redirectValid := io.in.valid && !jumpDataModule.io.isAuipc && (redirect.cfiUpdate.isMisPred || redirect.cfiUpdate.hasBackendFault)
   redirect := 0.U.asTypeOf(redirect)
   redirect.level := RedirectLevel.flushAfter
   redirect.robIdx := io.in.bits.ctrl.robIdx
```
