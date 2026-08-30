# Commit Log
- Issue: #3644
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3644
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3644
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3644
- Changed files: 2
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRModule.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRModule.scala
index b85f4756e48..c77f393ea7d 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRModule.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRModule.scala
@@ -42,7 +42,7 @@ class CSRModule[T <: CSRBundle](
 
   reconnectReg()
 
-  protected val rdataFields = Wire(bundle)
+  val rdataFields = IO(Output(bundle))
   rdataFields :|= regOut
 
   rdata := rdataFields.asUInt
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 2703a9f7d34..105a6f016b1 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -300,7 +300,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   intrMod.io.in.mstatusMIE := mstatus.regOut.MIE.asBool
   intrMod.io.in.sstatusSIE := mstatus.regOut.SIE.asBool
   intrMod.io.in.vsstatusSIE := vsstatus.regOut.SIE.asBool
-  intrMod.io.in.mip := mip.regOut
+  intrMod.io.in.mip := mip.rdataFields
   intrMod.io.in.mie := mie.regOut
   intrMod.io.in.mideleg := mideleg.regOut
   intrMod.io.in.sip := sip.regOut
```
