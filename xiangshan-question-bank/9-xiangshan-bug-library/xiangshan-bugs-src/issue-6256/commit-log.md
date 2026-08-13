# Commit Log
- Issue: #6256
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6256
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6256
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6256
- Changed files: 2
- Additions: 4
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 4afb4cd27b2..58ce63a32d0 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -427,6 +427,7 @@ class NewCSR(implicit val p: Parameters) extends Module
   trapHandleMod.io.in.trapInfo.bits.irToVS := irToVS
   trapHandleMod.io.in.privState := privState
   trapHandleMod.io.in.mstatus  := mstatus.regOut
+  trapHandleMod.io.in.sstatus   := mstatus.sstatus
   trapHandleMod.io.in.vsstatus := vsstatus.regOut
   trapHandleMod.io.in.mnstatus := mnstatus.regOut
   trapHandleMod.io.in.mideleg  := mideleg.regOut
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala
index 82f86d64092..87b7c1201b5 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/TrapHandleModule.scala
@@ -14,6 +14,7 @@ class TrapHandleModule extends Module {
   private val trapInfo = io.in.trapInfo
   private val privState = io.in.privState
   private val mstatus  = io.in.mstatus
+  private val sstatus  = io.in.sstatus
   private val vsstatus = io.in.vsstatus
   private val mnstatus = io.in.mnstatus
   private val mideleg = io.in.mideleg.asUInt
@@ -80,7 +81,7 @@ class TrapHandleModule extends Module {
 
   // sm/ssdbltrp
   private val m_EX_DT  = handleTrapUnderM  && mstatus.MDT.asBool  && hasTrap
-  private val s_EX_DT  = handleTrapUnderHS && mstatus.SDT.asBool  && hasTrap
+  private val s_EX_DT  = handleTrapUnderHS && sstatus.SDT.asBool  && hasTrap
   private val vs_EX_DT = handleTrapUnderVS && vsstatus.SDT.asBool && hasTrap
 
   private val dbltrpToMN = m_EX_DT && mnstatus.NMIE.asBool // NMI not allow double trap
@@ -126,6 +127,7 @@ class TrapHandleIO extends Bundle {
     })
     val privState = new PrivState
     val mstatus = new MstatusBundle
+    val sstatus = new SstatusBundle
     val vsstatus = new SstatusBundle
     val mnstatus = new MnstatusBundle
     val mideleg = new MidelegBundle
```
