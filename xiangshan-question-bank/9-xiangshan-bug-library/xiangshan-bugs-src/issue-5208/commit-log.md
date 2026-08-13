# Commit Log
- Issue: #5208
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5208
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5208
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5208
- Changed files: 1
- Additions: 9
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/vector/VSplit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/vector/VSplit.scala b/src/main/scala/xiangshan/mem/vector/VSplit.scala
index 4ac5ffaa82c..5a1b39986b6 100644
--- a/src/main/scala/xiangshan/mem/vector/VSplit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSplit.scala
@@ -408,6 +408,11 @@ abstract class VSplitBuffer(isVStore: Boolean = false)(implicit p: Parameters) e
   lazy val misalignedCanGo = true.B
   val allowIssue = (addrAligned || misalignedCanGo) && io.out.ready
   val issueCount = Mux(usNoSplit, 2.U, (PopCount(inActiveIssue) + PopCount(activeIssue))) // for dont need split unit-stride, issue two flow
+  val dataIssued = RegInit(false.B)
+
+  // Only Vec Store
+  if (io.vstd.nonEmpty) when (io.vstd.get.fire) {dataIssued := true.B}
+
   splitFinish := splitIdx >= (issueFlowNum - issueCount)
 
   // handshake
@@ -418,17 +423,20 @@ abstract class VSplitBuffer(isVStore: Boolean = false)(implicit p: Parameters) e
       when (activeIssue || inActiveIssue) {
         // The uop has not been entirly splited yet
         splitIdx := splitIdx + issueCount
+        if (io.vstd.nonEmpty) dataIssued := false.B
         strideOffsetReg := Mux(!issuePreIsSplit, 0.U, strideOffsetReg + issueEntry.stride) // when normal unit-stride, don't use strideOffsetReg
       }
     }.otherwise {
       when (activeIssue || inActiveIssue) {
         // The uop is done spliting
         splitIdx := 0.U(flowIdxBits.W) // initialize flowIdx
+        if (io.vstd.nonEmpty) dataIssued := false.B
         strideOffsetReg := 0.U
       }
     }
   }.otherwise {
     splitIdx := 0.U(flowIdxBits.W) // initialize flowIdx
+    if (io.vstd.nonEmpty) dataIssued := false.B
     strideOffsetReg := 0.U
   }
   // allocated
@@ -472,7 +480,7 @@ class VSSplitBufferImp(implicit p: Parameters) extends VSplitBuffer(isVStore = t
 
   // send data to sq
   val vstd = io.vstd.get
-  vstd.valid := io.out.valid
+  vstd.valid := issueValid && vecActive && !dataIssued
   vstd.bits.uop := issueUop
   vstd.bits.uop.sqIdx := sqIdx
   vstd.bits.uop.fuType := FuType.vstu.U
```
