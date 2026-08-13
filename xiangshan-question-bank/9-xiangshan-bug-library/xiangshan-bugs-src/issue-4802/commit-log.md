# Commit Log
- Issue: #4802
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4802
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4802
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4802
- Changed files: 1
- Additions: 3
- Deletions: 2

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 74387f849fb..b61f54fc223 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -1091,6 +1091,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   /** Data that have been read before,and should be stored because output not fired */
   val normalCSRValid = state === s_idle && valid && !asyncAccess
   val waitIMSICValid = state === s_waitIMSIC && fromAIA.rdata.valid
+  val claimAIA = mtopei.w.wen | stopei.w.wen | vstopei.w.wen
 
   io.out.valid := (waitIMSICValid || state === s_finish) && !redirectFlush
   io.out.bits.EX_II := DataHoldBypass(Mux1H(Seq(
@@ -1106,9 +1107,9 @@ class NewCSR(implicit val p: Parameters) extends Module
   /** Prepare read data for output */
   io.out.bits.rData := DataHoldBypass(
     Mux1H(Seq(
-      io.in.fire -> rdata,
+      (io.in.fire || claimAIA) -> rdata,
       fromAIA.rdata.valid -> fromAIA.rdata.bits.data
-    )), 0.U(64.W), io.in.fire || fromAIA.rdata.valid)
+    )), 0.U(64.W), io.in.fire || fromAIA.rdata.valid || claimAIA)
   io.out.bits.regOut := regOut
   io.out.bits.targetPc := DataHoldBypass(
     Mux(trapEntryDEvent.out.targetPc.valid,
```
