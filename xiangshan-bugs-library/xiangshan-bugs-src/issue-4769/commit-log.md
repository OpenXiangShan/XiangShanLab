# Commit Log
- Issue: #4769
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4769
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4769
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4769
- Changed files: 1
- Additions: 3
- Deletions: 2

## Files
- `src/main/scala/xiangshan/backend/fu/PMP.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/PMP.scala b/src/main/scala/xiangshan/backend/fu/PMP.scala
index 7ee98f3b041..00c9cc2aeea 100644
--- a/src/main/scala/xiangshan/backend/fu/PMP.scala
+++ b/src/main/scala/xiangshan/backend/fu/PMP.scala
@@ -569,8 +569,9 @@ class PMPChecker
   val res_pmp = pmp_match_res(leaveHitMux, io.req.valid)(req.addr(PMPAddrBits-PMPKeyIDBits-1, 0), req.size, io.check_env.pmp, io.check_env.mode, lgMaxSize)
   val res_pma = pma_match_res(leaveHitMux, io.req.valid)(req.addr(PMPAddrBits-PMPKeyIDBits-1, 0), req.size, io.check_env.pma, io.check_env.mode, lgMaxSize)
 
-  val resp_pmp = pmp_check(req.cmd, res_pmp.cfg)
-  val resp_pma = pma_check(req.cmd, res_pma.cfg)
+  val cmd = if(leaveHitMux) RegEnable(req.cmd, io.req.valid) else req.cmd
+  val resp_pmp = pmp_check(cmd, res_pmp.cfg)
+  val resp_pma = pma_check(cmd, res_pma.cfg)
   
   def keyid_check(leaveHitMux: Boolean = false, valid: Bool = true.B, addr: UInt) = {
     val resp = Wire(new PMPRespBundle)
```
