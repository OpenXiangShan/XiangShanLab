# Commit Log
- Issue: #3395
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3395
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3395
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3395
- Changed files: 3
- Additions: 5
- Deletions: 2

## Files
- `src/main/scala/xiangshan/backend/CtrlBlock.scala`
- `src/main/scala/xiangshan/backend/dispatch/Dispatch.scala`
- `src/main/scala/xiangshan/backend/rename/Rename.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/CtrlBlock.scala b/src/main/scala/xiangshan/backend/CtrlBlock.scala
index 5f2e0179573..6fc664f150f 100644
--- a/src/main/scala/xiangshan/backend/CtrlBlock.scala
+++ b/src/main/scala/xiangshan/backend/CtrlBlock.scala
@@ -487,6 +487,7 @@ class CtrlBlockImp(
 
   rename.io.redirect := s1_s3_redirect
   rename.io.rabCommits := rob.io.rabCommits
+  rename.io.singleStep := GatedValidRegNext(io.csrCtrl.singlestep)
   rename.io.waittable := (memCtrl.io.waitTable2Rename zip decode.io.out).map{ case(waittable2rename, decodeOut) =>
     RegEnable(waittable2rename, decodeOut.fire)
   }
diff --git a/src/main/scala/xiangshan/backend/dispatch/Dispatch.scala b/src/main/scala/xiangshan/backend/dispatch/Dispatch.scala
index 290ac8a4de8..92c2b57461b 100644
--- a/src/main/scala/xiangshan/backend/dispatch/Dispatch.scala
+++ b/src/main/scala/xiangshan/backend/dispatch/Dispatch.scala
@@ -198,7 +198,7 @@ class Dispatch(implicit p: Parameters) extends XSModule with HasPerfEvents {
 
   val singleStepStatus = RegInit(false.B)
   val inst0actualOut = io.enqRob.req(0).valid
-  when(io.redirect.valid) {
+  when(!io.singleStep) {
     singleStepStatus := false.B
   }.elsewhen(io.singleStep && io.fromRename(0).fire && inst0actualOut) {
     singleStepStatus := true.B
diff --git a/src/main/scala/xiangshan/backend/rename/Rename.scala b/src/main/scala/xiangshan/backend/rename/Rename.scala
index e675f2a1ce7..f2c247d25c8 100644
--- a/src/main/scala/xiangshan/backend/rename/Rename.scala
+++ b/src/main/scala/xiangshan/backend/rename/Rename.scala
@@ -45,6 +45,8 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
   val io = IO(new Bundle() {
     val redirect = Flipped(ValidIO(new Redirect))
     val rabCommits = Input(new RabCommitIO)
+    // from csr
+    val singleStep = Input(Bool())
     // from decode
     val in = Vec(RenameWidth, Flipped(DecoupledIO(new DecodedInst)))
     val fusionInfo = Vec(DecodeWidth - 1, Flipped(new FusionDecodeInfo))
@@ -156,7 +158,7 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
   val canOut = dispatchCanAcc && fpFreeList.io.canAllocate && intFreeList.io.canAllocate && vecFreeList.io.canAllocate && v0FreeList.io.canAllocate && vlFreeList.io.canAllocate && !io.rabCommits.isWalk
 
   compressUnit.io.in.zip(io.in).foreach{ case(sink, source) =>
-    sink.valid := source.valid
+    sink.valid := source.valid && !io.singleStep
     sink.bits := source.bits
   }
   val needRobFlags = compressUnit.io.out.needRobFlags
```
