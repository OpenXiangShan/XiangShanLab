# Commit Log
- Issue: #3961
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3961
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3961
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3961
- Changed files: 3
- Additions: 13
- Deletions: 13

## Files
- `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala`
- `src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala`
- `src/main/scala/xiangshan/backend/decode/UopInfoGen.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
index c410385a44a..476472317aa 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
@@ -856,13 +856,6 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
   decodedInst.v0Wen := false.B
   decodedInst.vlWen := false.B
 
-  // fill in exception vector
-  val vecException = Module(new VecExceptionGen)
-  vecException.io.inst := io.enq.ctrlFlow.instr
-  vecException.io.decodedInst := decodedInst
-  vecException.io.vtype := decodedInst.vpu.vtype
-  vecException.io.vstart := decodedInst.vpu.vstart
-
   private val isCboClean = CBO_CLEAN === io.enq.ctrlFlow.instr
   private val isCboFlush = CBO_FLUSH === io.enq.ctrlFlow.instr
   private val isCboInval = CBO_INVAL === io.enq.ctrlFlow.instr
@@ -874,7 +867,6 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
 
   private val exceptionII =
     decodedInst.selImm === SelImm.INVALID_INSTR ||
-    vecException.io.illegalInst ||
     io.fromCSR.illegalInst.sfenceVMA  && FuType.FuTypeOrR(decodedInst.fuType, FuType.fence) && decodedInst.fuOpType === FenceOpType.sfence  ||
     io.fromCSR.illegalInst.sfencePart && FuType.FuTypeOrR(decodedInst.fuType, FuType.fence) && decodedInst.fuOpType === FenceOpType.nofence ||
     io.fromCSR.illegalInst.hfenceGVMA && FuType.FuTypeOrR(decodedInst.fuType, FuType.fence) && decodedInst.fuOpType === FenceOpType.hfence_g ||
@@ -1074,8 +1066,7 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
   uopInfoGen.io.in.preInfo.isVlsr := decodedInst.fuOpType === VlduType.vlr || decodedInst.fuOpType === VstuType.vsr
   uopInfoGen.io.in.preInfo.isVlsm := decodedInst.fuOpType === VlduType.vlm || decodedInst.fuOpType === VstuType.vsm
   io.deq.isComplex := uopInfoGen.io.out.isComplex
-  // numOfUop should be 1 when vector instruction is illegalInst
-  io.deq.uopInfo.numOfUop := Mux(vecException.io.illegalInst, 1.U, uopInfoGen.io.out.uopInfo.numOfUop)
+  io.deq.uopInfo.numOfUop := uopInfoGen.io.out.uopInfo.numOfUop
   io.deq.uopInfo.numOfWB := uopInfoGen.io.out.uopInfo.numOfWB
   io.deq.uopInfo.lmul := uopInfoGen.io.out.uopInfo.lmul
 
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala b/src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala
index 405b51c0976..ca7becc8c9f 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeUnitComp.scala
@@ -31,7 +31,7 @@ import xiangshan.backend.fu.FuType
 import freechips.rocketchip.rocket.Instructions._
 import xiangshan.backend.Bundles.{DecodedInst, StaticInst}
 import xiangshan.backend.decode.isa.bitfield.XSInstBitFields
-import xiangshan.backend.fu.vector.Bundles.{VSew, VType, VLmul}
+import xiangshan.backend.fu.vector.Bundles.{VSew, VType, VLmul, Vl}
 import yunsuan.VpermType
 import chisel3.util.experimental.decode.{QMCMinimizer, TruthTable, decoder}
 
@@ -160,6 +160,14 @@ class DecodeUnitComp()(implicit p : Parameters) extends XSModule with DecodeUnit
 
   val isVstore = FuType.isVStore(latchedInst.fuType)
 
+  // exception generator
+  val vecException = Module(new VecExceptionGen)
+  vecException.io.inst := latchedInst.instr
+  vecException.io.decodedInst := latchedInst
+  vecException.io.vtype := latchedInst.vpu.vtype
+  vecException.io.vstart := latchedInst.vpu.vstart
+  val illegalInst = vecException.io.illegalInst
+
   numOfUop := latchedUopInfo.numOfUop
   numOfWB := latchedUopInfo.numOfWB
 
@@ -183,6 +191,7 @@ class DecodeUnitComp()(implicit p : Parameters) extends XSModule with DecodeUnit
     dst := latchedInst
     dst.numUops := latchedUopInfo.numOfUop
     dst.numWB := latchedUopInfo.numOfWB
+    dst.exceptionVec(ExceptionNO.EX_II) := latchedInst.exceptionVec(ExceptionNO.EX_II) || illegalInst
     dst.firstUop := false.B
     dst.lastUop := false.B
     dst.vlsInstr := false.B
diff --git a/src/main/scala/xiangshan/backend/decode/UopInfoGen.scala b/src/main/scala/xiangshan/backend/decode/UopInfoGen.scala
index 4a5a231dffa..acd3bf8b155 100644
--- a/src/main/scala/xiangshan/backend/decode/UopInfoGen.scala
+++ b/src/main/scala/xiangshan/backend/decode/UopInfoGen.scala
@@ -159,7 +159,7 @@ class UopInfoGen (implicit p: Parameters) extends XSModule {
     // lmul < 1, foldTime = vlmul - foldFastVlmul
     // lmul >= 1, foldTime = 0.U - foldFastVlmul
     val foldTime = Mux(vlmul(2), vlmul, 0.U) - foldLastVlmul
-    addTime + foldTime
+    Mux((addTime + foldTime).orR, addTime + foldTime, 1.U)
   }
   val numOfUopVFREDOSUM = {
     val uvlMax = MuxLookup(vsew, 1.U)(Seq(
@@ -169,7 +169,7 @@ class UopInfoGen (implicit p: Parameters) extends XSModule {
     ))
     val vlMax = Wire(UInt(7.W))
     vlMax := Mux(vlmul(2), uvlMax >> (-vlmul)(1,0), uvlMax << vlmul(1,0)).asUInt
-    vlMax
+    Mux(vlMax.orR, vlMax, 1.U)
   }
   /*
    * when 1 <= lmul <= 4, numOfUopWV = 2 * lmul, otherwise numOfUopWV = 1
```
