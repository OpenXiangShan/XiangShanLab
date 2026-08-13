# Commit Log
- Issue: #4103
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4103
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4103
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4103
- Changed files: 4
- Additions: 8
- Deletions: 5

## Files
- `src/main/scala/xiangshan/backend/MemBlock.scala`
- `src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala`
- `src/main/scala/xiangshan/mem/vector/VSplit.scala`
- `src/main/scala/xiangshan/mem/vector/VecBundle.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/MemBlock.scala b/src/main/scala/xiangshan/backend/MemBlock.scala
index 5286f971882..e65f3e4763f 100644
--- a/src/main/scala/xiangshan/backend/MemBlock.scala
+++ b/src/main/scala/xiangshan/backend/MemBlock.scala
@@ -1496,6 +1496,8 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
     vlSplit(i).io.in.valid := io.ooo_to_mem.issueVldu(i).valid &&
                               vLoadCanAccept(i) && !isSegment && !isFixVlUop(i)
     vlSplit(i).io.toMergeBuffer <> vlMergeBuffer.io.fromSplit(i)
+    vlSplit(i).io.threshold.get.valid := vlMergeBuffer.io.toSplit.get.threshold
+    vlSplit(i).io.threshold.get.bits  := lsq.io.lqDeqPtr
     NewPipelineConnect(
       vlSplit(i).io.out, loadUnits(i).io.vecldin, loadUnits(i).io.vecldin.fire,
       Mux(vlSplit(i).io.out.fire, vlSplit(i).io.out.bits.uop.robIdx.needFlush(io.redirect), loadUnits(i).io.vecldin.bits.uop.robIdx.needFlush(io.redirect)),
diff --git a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
index aac9d695535..818c0d95110 100644
--- a/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
+++ b/src/main/scala/xiangshan/mem/vector/VMergeBuffer.scala
@@ -309,8 +309,6 @@ abstract class BaseVMergeBuffer(isVStore: Boolean=false)(implicit p: Parameters)
     XSError((entries(latchWbIndex).flowNum - latchFlowNum > entries(latchWbIndex).flowNum) && latchWbValid && !latchMergeByPre, s"entry: $latchWbIndex, FlowWriteback overflow!!\n")
     XSError(!allocated(latchWbIndex) && latchWbValid, s"entry: $latchWbIndex, Writeback error flow!!\n")
   }
-  // for inorder mem asscess
-  io.toSplit := DontCare
 
   //uopwriteback(deq)
   for (i <- 0 until uopSize){
@@ -379,6 +377,7 @@ class VLMergeBufferImp(implicit p: Parameters) extends BaseVMergeBuffer(isVStore
     enablePreAlloc = false,
     moduleName = "VLoad MergeBuffer freelist"
   ))
+  io.toSplit.get.threshold := freeCount <= 6.U
 
   //merge data
   val flowWbElemIdx     = Wire(Vec(pipeWidth, UInt(elemIdxBits.W)))
diff --git a/src/main/scala/xiangshan/mem/vector/VSplit.scala b/src/main/scala/xiangshan/mem/vector/VSplit.scala
index a5222cb38ec..39f74ac7c77 100644
--- a/src/main/scala/xiangshan/mem/vector/VSplit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSplit.scala
@@ -493,8 +493,10 @@ class VLSplitImp(implicit p: Parameters) extends VLSUModule{
   val io = IO(new VSplitIO(isVStore=false))
   val splitPipeline = Module(new VLSplitPipelineImp())
   val splitBuffer = Module(new VLSplitBufferImp())
+  val mergeBufferNack = io.threshold.get.valid && io.threshold.get.bits =/= io.in.bits.uop.lqIdx
   // Split Pipeline
   splitPipeline.io.in <> io.in
+  io.in.ready := splitPipeline.io.in.ready && !mergeBufferNack
   splitPipeline.io.redirect <> io.redirect
   io.toMergeBuffer <> splitPipeline.io.toMergeBuffer
 
diff --git a/src/main/scala/xiangshan/mem/vector/VecBundle.scala b/src/main/scala/xiangshan/mem/vector/VecBundle.scala
index 168ea657ec2..b8a597fd8d5 100644
--- a/src/main/scala/xiangshan/mem/vector/VecBundle.scala
+++ b/src/main/scala/xiangshan/mem/vector/VecBundle.scala
@@ -187,7 +187,6 @@ class MergeBufferResp(isVStore: Boolean=false)(implicit p: Parameters) extends V
 class ToMergeBufferIO(isVStore: Boolean=false)(implicit p: Parameters) extends VLSUBundle{
   val req                 = DecoupledIO(new MergeBufferReq(isVStore))
   val resp                = Flipped(ValidIO(new MergeBufferResp(isVStore)))
-  // val issueInactive       = ValidIO
 }
 
 class FromSplitIO(isVStore: Boolean=false)(implicit p: Parameters) extends VLSUBundle{
@@ -197,7 +196,7 @@ class FromSplitIO(isVStore: Boolean=false)(implicit p: Parameters) extends VLSUB
 }
 
 class FeedbackToSplitIO(implicit p: Parameters) extends VLSUBundle{
-  val elemWriteback       = Bool()
+  val threshold            = Bool()
 }
 
 class FeedbackToLsqIO(implicit p: Parameters) extends VLSUBundle{
@@ -230,6 +229,7 @@ class VSplitIO(isVStore: Boolean=false)(implicit p: Parameters) extends VLSUBund
   val out                 = Decoupled(new VecPipeBundle(isVStore))// to scala pipeline
   val vstd                = OptionWrapper(isVStore, Valid(new MemExuOutput(isVector = true)))
   val vstdMisalign        = OptionWrapper(isVStore, new storeMisaignIO)
+  val threshold            = OptionWrapper(!isVStore, Flipped(ValidIO(new LqPtr)))
 }
 
 class VSplitPipelineIO(isVStore: Boolean=false)(implicit p: Parameters) extends VLSUBundle{
@@ -252,7 +252,7 @@ class VMergeBufferIO(isVStore : Boolean=false)(implicit p: Parameters) extends V
   val fromPipeline        = if(isVStore) Vec(StorePipelineWidth, Flipped(DecoupledIO(new VecPipelineFeedbackIO(isVStore)))) else Vec(LoadPipelineWidth, Flipped(DecoupledIO(new VecPipelineFeedbackIO(isVStore))))
   val fromSplit           = if(isVStore) Vec(VecStorePipelineWidth, new FromSplitIO) else Vec(VecLoadPipelineWidth, new FromSplitIO) // req mergebuffer entry, inactive elem issue
   val uopWriteback        = if(isVStore) Vec(VSUopWritebackWidth, DecoupledIO(new MemExuOutput(isVector = true))) else Vec(VLUopWritebackWidth, DecoupledIO(new MemExuOutput(isVector = true)))
-  val toSplit             = if(isVStore) Vec(VecStorePipelineWidth, ValidIO(new FeedbackToSplitIO)) else Vec(VecLoadPipelineWidth, ValidIO(new FeedbackToSplitIO)) // for inorder inst
+  val toSplit             = OptionWrapper(!isVStore, new FeedbackToSplitIO())
   val toLsq               = if(isVStore) Vec(VSUopWritebackWidth, ValidIO(new FeedbackToLsqIO)) else Vec(VLUopWritebackWidth, ValidIO(new FeedbackToLsqIO)) // for lsq deq
   val feedback            = if(isVStore) Vec(VSUopWritebackWidth, ValidIO(new RSFeedback(isVector = true))) else Vec(VLUopWritebackWidth, ValidIO(new RSFeedback(isVector = true)))//for rs replay
```
