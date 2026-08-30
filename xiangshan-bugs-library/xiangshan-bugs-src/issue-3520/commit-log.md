# Commit Log
- Issue: #3520
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3520
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3520
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3520
- Changed files: 6
- Additions: 10
- Deletions: 9

## Files
- `src/main/scala/xiangshan/Bundle.scala`
- `src/main/scala/xiangshan/backend/Bundles.scala`
- `src/main/scala/xiangshan/backend/dispatch/Dispatch.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala`
- `src/main/scala/xiangshan/backend/rename/Rename.scala`
- `src/main/scala/xiangshan/backend/rename/freelist/MEFreeList.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/Bundle.scala b/src/main/scala/xiangshan/Bundle.scala
index e7283430a9c..b0ef9f43a71 100644
--- a/src/main/scala/xiangshan/Bundle.scala
+++ b/src/main/scala/xiangshan/Bundle.scala
@@ -229,7 +229,7 @@ class CtrlSignals(implicit p: Parameters) extends XSBundle {
   def isSoftPrefetch: Bool = {
     fuType === FuType.alu.U && fuOpType === ALUOpType.or && selImm === SelImm.IMM_I && ldest === 0.U
   }
-  def needWriteRf: Bool = (rfWen && ldest =/= 0.U) || fpWen || vecWen
+  def needWriteRf: Bool = rfWen || fpWen || vecWen
   def isHyperInst: Bool = {
     fuType === FuType.ldu.U && LSUOpType.isHlv(fuOpType) || fuType === FuType.stu.U && LSUOpType.isHsv(fuOpType)
   }
diff --git a/src/main/scala/xiangshan/backend/Bundles.scala b/src/main/scala/xiangshan/backend/Bundles.scala
index 00a1be87400..52d26e6e898 100644
--- a/src/main/scala/xiangshan/backend/Bundles.scala
+++ b/src/main/scala/xiangshan/backend/Bundles.scala
@@ -278,7 +278,7 @@ object Bundles {
       this
     }
 
-    def needWriteRf: Bool = (rfWen && ldest =/= 0.U) || fpWen || vecWen || v0Wen || vlWen
+    def needWriteRf: Bool = rfWen || fpWen || vecWen || v0Wen || vlWen
   }
 
   trait BundleSource {
diff --git a/src/main/scala/xiangshan/backend/dispatch/Dispatch.scala b/src/main/scala/xiangshan/backend/dispatch/Dispatch.scala
index ec82c2c8b24..5088b802d49 100644
--- a/src/main/scala/xiangshan/backend/dispatch/Dispatch.scala
+++ b/src/main/scala/xiangshan/backend/dispatch/Dispatch.scala
@@ -409,7 +409,7 @@ class Dispatch(implicit p: Parameters) extends XSModule with HasPerfEvents {
   for (i <- 0 until RenameWidth) {
     io.fromRename(i).ready := thisCanActualOut(i) && io.enqRob.canAccept && dqCanAccept
 
-    io.allocPregs(i).isInt := io.fromRename(i).valid && io.fromRename(i).bits.rfWen && (io.fromRename(i).bits.ldest =/= 0.U) && !io.fromRename(i).bits.eliminatedMove
+    io.allocPregs(i).isInt := io.fromRename(i).valid && io.fromRename(i).bits.rfWen && !io.fromRename(i).bits.eliminatedMove
     io.allocPregs(i).isFp := io.fromRename(i).valid && io.fromRename(i).bits.fpWen
     io.allocPregs(i).isVec := io.fromRename(i).valid && io.fromRename(i).bits.vecWen
     io.allocPregs(i).isV0 := io.fromRename(i).valid && io.fromRename(i).bits.v0Wen
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala b/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala
index 57e48b34e39..ad6f4153dfd 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/BranchUnit.scala
@@ -16,9 +16,10 @@ class AddrAddModule(implicit p: Parameters) extends XSModule {
     val offset = Input(UInt(12.W)) // branch inst only support 12 bits immediate num
     val target = Output(UInt(XLEN.W))
   })
-  io.target := SignExt(SignExt(io.pc, VAddrBits + 1) + Mux(io.taken,
-    SignExt(ImmUnion.B.toImm32(io.offset), VAddrBits + 1),
-    Mux(io.isRVC, 2.U, 4.U)
+  val pcExtend = SignExt(io.pc, VAddrBits + 1)
+  io.target := SignExt(Mux(io.taken,
+  pcExtend + SignExt(ImmUnion.B.toImm32(io.offset), VAddrBits + 1),
+  pcExtend + Mux(io.isRVC, 2.U, 4.U)
   ), XLEN)
 }
 
diff --git a/src/main/scala/xiangshan/backend/rename/Rename.scala b/src/main/scala/xiangshan/backend/rename/Rename.scala
index 90d4e13d7c9..eee9cd51791 100644
--- a/src/main/scala/xiangshan/backend/rename/Rename.scala
+++ b/src/main/scala/xiangshan/backend/rename/Rename.scala
@@ -119,7 +119,7 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
 
   // decide if given instruction needs allocating a new physical register (CfCtrl: from decode; RobCommitInfo: from rob)
   def needDestReg[T <: DecodedInst](reg_t: RegType, x: T): Bool = reg_t match {
-    case Reg_I => x.rfWen && x.ldest =/= 0.U
+    case Reg_I => x.rfWen
     case Reg_F => x.fpWen
     case Reg_V => x.vecWen
     case Reg_V0 => x.v0Wen
@@ -136,7 +136,7 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
   }
   def needDestRegWalk[T <: RabCommitInfo](reg_t: RegType, x: T): Bool = {
     reg_t match {
-      case Reg_I => x.rfWen && x.ldest =/= 0.U
+      case Reg_I => x.rfWen
       case Reg_F => x.fpWen
       case Reg_V => x.vecWen
       case Reg_V0 => x.v0Wen
diff --git a/src/main/scala/xiangshan/backend/rename/freelist/MEFreeList.scala b/src/main/scala/xiangshan/backend/rename/freelist/MEFreeList.scala
index 1be4ab52ec4..08f29fc42ce 100644
--- a/src/main/scala/xiangshan/backend/rename/freelist/MEFreeList.scala
+++ b/src/main/scala/xiangshan/backend/rename/freelist/MEFreeList.scala
@@ -46,7 +46,7 @@ class MEFreeList(size: Int)(implicit p: Parameters) extends BaseFreeList(size) w
   }
   // update arch head pointer
   val archAlloc = io.commit.commitValid zip io.commit.info map {
-    case (valid, info) => valid && info.rfWen && !info.isMove && info.ldest =/= 0.U
+    case (valid, info) => valid && info.rfWen && !info.isMove
   }
   val numArchAllocate = PopCount(archAlloc)
   val archHeadPtrNew  = archHeadPtr + numArchAllocate
```
