# Commit Log
- Issue: #3989
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3989
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3989
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3989
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 5fb5192ae7e..10336fba0a1 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -1224,7 +1224,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   io.status.instrAddrTransType.sv48x4 := privState.isVirtual && vsatp.regOut.MODE === SatpMode.Bare && hgatp.regOut.MODE === HgatpMode.Sv48x4
   assert(PopCount(io.status.instrAddrTransType.asUInt) === 1.U, "Exactly one inst trans type should be asserted")
 
-  private val csrAccess = wenLegal || ren
+  private val csrAccess = wenLegalReg || RegNext(ren)
 
   private val imsicAddrValid =
     csrAccess &&  addr === CSRs.mireg.U &&  miselect.inIMSICRange ||
```
