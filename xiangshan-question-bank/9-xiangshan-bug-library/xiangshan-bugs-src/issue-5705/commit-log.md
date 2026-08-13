# Commit Log
- Issue: #5705
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5705
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5705
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5705
- Changed files: 2
- Additions: 14
- Deletions: 14

## Files
- `src/main/scala/xiangshan/backend/rob/Rob.scala`
- `src/main/scala/xiangshan/backend/trace/Interface.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index c050cbfe5ce..1573887af80 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -131,7 +131,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
       val robHeadLqIdx = Valid(new LqPtr)
     }
     val debugRolling = new RobDebugRollingIO
-    val debugInstrAddrTransType = Input(new AddrTransType) 
+    val debugInstrAddrTransType = Input(new AddrTransType)
 
     // store event difftest information
     val storeDebugInfo = Vec(EnsbufferWidth, new Bundle {
@@ -363,9 +363,9 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   val fflagsDataRead = Wire(Vec(CommitWidth, UInt(5.W)))
   val vxsatDataRead = Wire(Vec(CommitWidth, Bool()))
   io.robDeqPtr := deqPtr
-  
+
   io.debugRobHeadFuType := robEntries(deqPtr.value).debug_fuType.getOrElse(0.U.asTypeOf(FuType()))
-  
+
   /**
    * connection of [[rab]]
    */
@@ -1075,7 +1075,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
 
     // trace
     val taken = branchWBs.map(writeback => writeback.valid && writeback.bits.robIdx.value === i.U && writeback.bits.redirect.get.bits.taken).reduce(_ || _)
-    when(robEntries(i).valid && Itype.isBranchType(robEntries(i).traceBlockInPipe.itype) && taken){
+    when(robEntries(i).valid && Itype.isNonTaken(robEntries(i).traceBlockInPipe.itype) && taken){
       // BranchType code(notaken itype = 4) must be correctly replaced!
       robEntries(i).traceBlockInPipe.itype := Itype.Taken
     }
@@ -1133,7 +1133,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
 
     // trace
     val taken = branchWBs.map(writeback => writeback.valid && writeback.bits.robIdx.value === needUpdateRobIdx(i) && writeback.bits.redirect.get.bits.taken).reduce(_ || _)
-    when(robBanksRdata(i).valid && Itype.isBranchType(robBanksRdata(i).traceBlockInPipe.itype) && taken){
+    when(robBanksRdata(i).valid && Itype.isNonTaken(robBanksRdata(i).traceBlockInPipe.itype) && taken){
       // BranchType code(notaken itype = 4) must be correctly replaced!
       needUpdate(i).traceBlockInPipe.itype := Itype.Taken
     }
@@ -1324,7 +1324,7 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   val commitIsLoad = io.commits.info.map(_.commitType).map(_ === CommitType.LOAD)
   val commitLoadValid = io.commits.commitValid.zip(commitIsLoad).map { case (v, t) => v && t }
   XSPerfAccumulate("commitInstrLoad", ifCommit(PopCount(commitLoadValid)))
-  val commitIsBranch = io.commits.info.map(_.commitType).map(_ === CommitType.BRANCH)
+  val commitIsBranch = io.commits.info.map(info => Itype.isBranch(info.traceBlockInPipe.itype))
   val commitBranchValid = io.commits.commitValid.zip(commitIsBranch).map { case (v, t) => v && t }
   XSPerfAccumulate("commitInstrBranch", ifCommit(PopCount(commitBranchValid)))
   val commitIsStore = io.commits.info.map(_.commitType).map(_ === CommitType.STORE)
@@ -1374,10 +1374,10 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
   }
 
   XSPerfAccumulate("waitNormalCycle", deqNotWritebacked && deqUopCommitType === CommitType.NORMAL)
-  XSPerfAccumulate("waitBranchCycle", deqNotWritebacked && deqUopCommitType === CommitType.BRANCH)
+  XSPerfAccumulate("waitBranchCycle", deqNotWritebacked && Itype.isBranch(debug_deqUop.traceBlockInPipe.itype))
   XSPerfAccumulate("waitLoadCycle", deqNotWritebacked   && deqUopCommitType === CommitType.LOAD)
   XSPerfAccumulate("waitStoreCycle", deqNotWritebacked  && deqUopCommitType === CommitType.STORE)
-  XSPerfReference("robHeadPC", 
+  XSPerfReference("robHeadPC",
     RegEnable(io.commits.info(0).debug_pc.getOrElse(0.U),
               0.U,
               io.commits.isCommit && io.commits.commitValid(0)))
@@ -1644,10 +1644,11 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
       port.pc := robEntries(port.robidx.value).debug_pc.getOrElse(0.U)
     }
   }
- 
+
   val misPred = io.redirect.valid && io.redirect.bits.isMisPred
   val brhJump = PopCount(isBrhOrJmpWBs.map(wb => wb.valid))
 
+  XSPerfAccumulate("branch_jump", brhJump)
   XSPerfAccumulate("br_mis_pred", misPred)
   XSPerfAccumulate("total_flush", io.redirect.valid)
 
diff --git a/src/main/scala/xiangshan/backend/trace/Interface.scala b/src/main/scala/xiangshan/backend/trace/Interface.scala
index f2ccbd5b851..b094309d20b 100644
--- a/src/main/scala/xiangshan/backend/trace/Interface.scala
+++ b/src/main/scala/xiangshan/backend/trace/Interface.scala
@@ -79,9 +79,6 @@ object Itype extends NamedUInt(4) {
   def OtherUninferableJump = 14.U   //rename
   def OtherInferableJump   = 15.U   //rename
 
-  // Assuming the branchType is NonTaken here, it will be correctly modified after writeBack.
-  def Branch = NonTaken
-
   def jumpTypeGen(fuType: UInt, fuoptype: UInt, rd: OpRegType, rs: OpRegType): UInt = {
 
     val isEqualRdRs = rd === rs
@@ -112,7 +109,7 @@ object Itype extends NamedUInt(4) {
         isOtherInferableJump,
       ),
       Seq(
-        Branch,
+        NonTaken, // Assuming the branchType is NonTaken here, it will be correctly modified after writeBack.
         UninferableCall,
         InferableCall,
         UninferableTailCall,
@@ -133,7 +130,9 @@ object Itype extends NamedUInt(4) {
 
   def isNotNone(itype: UInt) = itype =/= None
 
-  def isBranchType(itype: UInt) = itype === Branch
+  def isNonTaken(itype: UInt) = itype === NonTaken
+
+  def isBranch(itype: UInt) = Seq(NonTaken, Taken).map(_ === itype).reduce(_ || _)
 
   def isPush(itype: UInt) = Seq(UninferableCall, InferableCall, CoRoutineSwap).map(_ === itype).reduce(_ || _)
```
