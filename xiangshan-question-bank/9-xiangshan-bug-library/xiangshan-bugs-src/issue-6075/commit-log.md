# Commit Log
- Issue: #6075
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6075
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6075
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6075
- Changed files: 1
- Additions: 1
- Deletions: 3

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala
index 296dd18914a..4b6697a9f70 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSREvents/MNretEvent.scala
@@ -14,7 +14,7 @@ import xiangshan.AddrTransType
 
 
 class MNretEventOutput extends Bundle with EventUpdatePrivStateOutput with EventOutputBase {
-  val mnstatus  = ValidIO((new MnstatusBundle).addInEvent(_.MNPP, _.MNPV, _.NMIE))
+  val mnstatus  = ValidIO((new MnstatusBundle).addInEvent(_.NMIE))
   val mstatus   = ValidIO((new MstatusBundle).addInEvent(_.MPRV, _.MDT, _.SDT))
   val vsstatus  = ValidIO((new SstatusBundle).addInEvent(_.SDT))
   val targetPc  = ValidIO(new TargetPCBundle)
@@ -68,8 +68,6 @@ class MNretEventModule(implicit p: Parameters) extends Module with CSREventBase
   out.targetPc .valid := valid
 
   out.privState.bits          := outPrivState
-  out.mnstatus.bits.MNPP      := PrivMode.U
-  out.mnstatus.bits.MNPV      := VirtMode.Off.asUInt
   out.mnstatus.bits.NMIE      := 1.U
   out.mstatus.bits.MPRV       := Mux(in.mnstatus.MNPP =/= PrivMode.M, 0.U, in.mstatus.MPRV.asUInt)
   // clear MDT when mnret to below M
```
