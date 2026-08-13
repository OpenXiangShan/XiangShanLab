# Commit Log
- Issue: #3284
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3284
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3284
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3284
- Changed files: 3
- Additions: 23
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/Bundles.scala`
- `src/main/scala/xiangshan/backend/decode/DecodeUnit.scala`
- `src/main/scala/xiangshan/backend/rob/VTypeBuffer.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/Bundles.scala b/src/main/scala/xiangshan/backend/Bundles.scala
index fd4e76842d7..93424446d3e 100644
--- a/src/main/scala/xiangshan/backend/Bundles.scala
+++ b/src/main/scala/xiangshan/backend/Bundles.scala
@@ -388,6 +388,13 @@ object Bundles {
     val vsew      = VSew()
     val vlmul     = VLmul()   // 1/8~8      --> -3~3
 
+    // spec vtype
+    val specVill  = Bool()
+    val specVma   = Bool()    // 1: agnostic, 0: undisturbed
+    val specVta   = Bool()    // 1: agnostic, 0: undisturbed
+    val specVsew  = VSew()
+    val specVlmul = VLmul()   // 1/8~8      --> -3~3
+
     val vm        = Bool()    // 0: need v0.t
     val vstart    = Vl()
 
@@ -428,6 +435,16 @@ object Bundles {
       res
     }
 
+    def specVType: VType = {
+      val res = Wire(VType())
+      res.illegal := this.specVill
+      res.vma     := this.specVma
+      res.vta     := this.specVta
+      res.vsew    := this.specVsew
+      res.vlmul   := this.specVlmul
+      res
+    }
+
     def vconfig: VConfig = {
       val res = Wire(VConfig())
       res.vtype := this.vtype
diff --git a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
index 43af16c569b..af02a61e92d 100644
--- a/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
+++ b/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala
@@ -920,6 +920,11 @@ class DecodeUnit(implicit p: Parameters) extends XSModule with DecodeUnitConstan
     decodedInst.vpu.isWritePartVd := isWritePartVd || isVlm || isVle && emulIsFrac
     decodedInst.vpu.vstart := io.enq.vstart
   }
+  decodedInst.vpu.specVill := io.enq.vtype.illegal
+  decodedInst.vpu.specVma := io.enq.vtype.vma
+  decodedInst.vpu.specVta := io.enq.vtype.vta
+  decodedInst.vpu.specVsew := io.enq.vtype.vsew
+  decodedInst.vpu.specVlmul := io.enq.vtype.vlmul
 
   decodedInst.vlsInstr := isVls
 
diff --git a/src/main/scala/xiangshan/backend/rob/VTypeBuffer.scala b/src/main/scala/xiangshan/backend/rob/VTypeBuffer.scala
index 1e795e8390d..da3325d76ee 100644
--- a/src/main/scala/xiangshan/backend/rob/VTypeBuffer.scala
+++ b/src/main/scala/xiangshan/backend/rob/VTypeBuffer.scala
@@ -104,7 +104,7 @@ class VTypeBuffer(size: Int)(implicit p: Parameters) extends XSModule with HasCi
   private val walkPtrVecNext = VecInit((0 until CommitWidth).map(x => walkPtrNext + x.U))
 
   // get enque vtypes in io.req
-  private val enqVTypes = VecInit(io.req.map(req => req.bits.vpu.vtype))
+  private val enqVTypes = VecInit(io.req.map(req => req.bits.vpu.specVType))
   private val enqValids = VecInit(io.req.map(_.valid))
   private val enqVType = PriorityMux(enqValids.zip(enqVTypes).map { case (valid, vtype) => valid -> vtype })
```
