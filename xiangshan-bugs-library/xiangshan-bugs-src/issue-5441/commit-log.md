# Commit Log
- Issue: #5441
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5441
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5441
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5441
- Changed files: 1
- Additions: 27
- Deletions: 14

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 1c5d81ca14d..18a131a7147 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -1621,19 +1621,31 @@ class NewCSR(implicit val p: Parameters) extends Module
     diffHCSRState.vsatp       := vsatp.rdata.asUInt
     diffHCSRState.vsscratch   := vsscratch.rdata.asUInt
 
-    val platformIRPMeipChange = !platformIRP.MEIP &&  RegNext(platformIRP.MEIP) || platformIRP.MEIP && !RegNext(platformIRP.MEIP)
-    val platformIRPMtipChange = !platformIRP.MTIP &&  RegNext(platformIRP.MTIP) || platformIRP.MTIP && !RegNext(platformIRP.MTIP)
-    val platformIRPMsipChange = !platformIRP.MSIP &&  RegNext(platformIRP.MSIP) || platformIRP.MSIP && !RegNext(platformIRP.MSIP)
-    val platformIRPSeipChange = !platformIRP.SEIP &&  RegNext(platformIRP.SEIP) || platformIRP.SEIP && !RegNext(platformIRP.SEIP)
-    val platformIRPStipChange = !sstcIRGen.o.STIP &&  RegNext(sstcIRGen.o.STIP) || sstcIRGen.o.STIP && !RegNext(sstcIRGen.o.STIP)
-    val platformIRPVseipChange = !platformIRP.VSEIP &&  RegNext(platformIRP.VSEIP) ||
-                                  platformIRP.VSEIP && !RegNext(platformIRP.VSEIP) ||
-                                 !hgeip.rdata.asUInt(hstatus.regOut.VGEIN.asUInt) &&  RegNext(hgeip.rdata.asUInt(hstatus.regOut.VGEIN.asUInt)) ||
-                                  hgeip.rdata.asUInt(hstatus.regOut.VGEIN.asUInt) && !RegNext(hgeip.rdata.asUInt(hstatus.regOut.VGEIN.asUInt))
-    val platformIRPVstipChange = !sstcIRGen.o.VSTIP && RegNext(sstcIRGen.o.VSTIP) || sstcIRGen.o.VSTIP && !RegNext(sstcIRGen.o.VSTIP)
-    val fromAIAMeipChange = !fromAIA.meip && RegNext(fromAIA.meip) || fromAIA.meip && !RegNext(fromAIA.meip)
-    val fromAIASeipChange = !fromAIA.seip && RegNext(fromAIA.seip) || fromAIA.seip && !RegNext(fromAIA.seip)
-    val lcofiReqChange = !lcofiReq && RegNext(lcofiReq) || lcofiReq && !RegNext(lcofiReq)
+    val meipReg = RegNext(platformIRP.MEIP, false.B)
+    val mtipReg = RegNext(platformIRP.MTIP, false.B)
+    val msipReg = RegNext(platformIRP.MSIP, false.B)
+    val seipReg = RegNext(platformIRP.SEIP, false.B)
+    val stipReg = RegNext(sstcIRGen.o.STIP, false.B)
+    val vseipReg = RegNext(platformIRP.VSEIP, false.B)
+    val hgeipVgeinReg = RegNext(hgeip.rdata.asUInt(hstatus.regOut.VGEIN.asUInt), false.B)
+    val vstipReg = RegNext(sstcIRGen.o.VSTIP, false.B)
+    val aiaMeipReg = RegNext(fromAIA.meip, false.B)
+    val aiaSeipReg = RegNext(fromAIA.seip, false.B)
+    val lcofiReqReg = RegNext(lcofiReq, false.B)
+
+    val platformIRPMeipChange = !platformIRP.MEIP && meipReg || platformIRP.MEIP && !meipReg
+    val platformIRPMtipChange = !platformIRP.MTIP && mtipReg || platformIRP.MTIP && !mtipReg
+    val platformIRPMsipChange = !platformIRP.MSIP && msipReg || platformIRP.MSIP && !msipReg
+    val platformIRPSeipChange = !platformIRP.SEIP && seipReg || platformIRP.SEIP && !seipReg
+    val platformIRPStipChange = !sstcIRGen.o.STIP && stipReg || sstcIRGen.o.STIP && !stipReg
+    val platformIRPVseipChange = !platformIRP.VSEIP &&  vseipReg ||
+                                  platformIRP.VSEIP && !vseipReg ||
+                                 !hgeip.rdata.asUInt(hstatus.regOut.VGEIN.asUInt) &&  hgeipVgeinReg ||
+                                  hgeip.rdata.asUInt(hstatus.regOut.VGEIN.asUInt) && !hgeipVgeinReg
+    val platformIRPVstipChange = !sstcIRGen.o.VSTIP && vstipReg || sstcIRGen.o.VSTIP && !vstipReg
+    val fromAIAMeipChange = !fromAIA.meip && aiaMeipReg || fromAIA.meip && !aiaMeipReg
+    val fromAIASeipChange = !fromAIA.seip && aiaSeipReg || fromAIA.seip && !aiaSeipReg
+    val lcofiReqChange = !lcofiReq && lcofiReqReg || lcofiReq && !lcofiReqReg
 
     val diffNonRegInterruptPendingEvent = DifftestModule(new DiffNonRegInterruptPendingEvent)
     diffNonRegInterruptPendingEvent.coreid           := hartId
@@ -1656,7 +1668,8 @@ class NewCSR(implicit val p: Parameters) extends Module
     val diffMhpmeventOverflowEvent = DifftestModule(new DiffMhpmeventOverflowEvent)
     diffMhpmeventOverflowEvent.coreid := hartId
     diffMhpmeventOverflowEvent.valid  := Cat(mhpmevents.zipWithIndex.map{ case (event, i) =>
-      !ofFromPerfCntVec(i) && RegNext(ofFromPerfCntVec(i)) || ofFromPerfCntVec(i) && !RegNext(ofFromPerfCntVec(i))
+      val ofFromPerfCntVecReg = RegNext(ofFromPerfCntVec(i), false.B)
+      !ofFromPerfCntVec(i) && ofFromPerfCntVecReg || ofFromPerfCntVec(i) && !ofFromPerfCntVecReg
     }).orR
     diffMhpmeventOverflowEvent.mhpmeventOverflow := VecInit(mhpmevents.map(_.regOut.asInstanceOf[MhpmeventBundle].OF.asBool)).asUInt
```
