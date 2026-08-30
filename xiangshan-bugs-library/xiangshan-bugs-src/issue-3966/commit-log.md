# Commit Log
- Issue: #3966
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3966
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3966
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3966
- Changed files: 1
- Additions: 3
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index fac24ee8825..5fb5192ae7e 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -925,6 +925,8 @@ class NewCSR(implicit val p: Parameters) extends Module
 
   private val noCSRIllegal = (ren || wen) && Cat(csrRwMap.keys.toSeq.sorted.map(csrAddr => !(addr === csrAddr.U))).andR
 
+  private val noCSRIllegalReg = RegEnable(noCSRIllegal, ren || wen)
+
   private val s_idle :: s_waitIMSIC :: s_finish :: Nil = Enum(3)
 
   /** the state machine of newCSR module */
@@ -1325,7 +1327,7 @@ class NewCSR(implicit val p: Parameters) extends Module
     henvcfg.regOut.CBIE === EnvCBIE.Flush && (isModeVS || isModeVU)
   )
 
-  io.distributedWenLegal := wenLegalReg
+  io.distributedWenLegal := wenLegalReg && !noCSRIllegalReg
   io.status.criticalErrorState := criticalErrorState && !dcsr.regOut.CETRIG.asBool
 
   val criticalErrors = Seq(
```
