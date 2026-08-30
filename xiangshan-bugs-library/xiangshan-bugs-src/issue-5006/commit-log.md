# Commit Log
- Issue: #5006
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5006
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5006
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5006
- Changed files: 1
- Additions: 7
- Deletions: 7

## Files
- `src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
index 9ff254de018..039171ae83f 100644
--- a/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
+++ b/src/main/scala/xiangshan/mem/vector/VSegmentUnit.scala
@@ -428,12 +428,12 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
 
   val vaddr                           = nextBaseVaddr + realSegmentOffset
 
-  val misalignLowVaddr                = Cat(latchVaddr(VAddrBits - 1, 3), 0.U(3.W))
-  val misalignLowVaddrDup             = Cat(latchVaddrDup(VAddrBits - 1, 3), 0.U(3.W))
-  val misalignHighVaddr               = Cat(latchVaddr(VAddrBits - 1, 3) + 1.U, 0.U(3.W))
-  val misalignHighVaddrDup            = Cat(latchVaddrDup(VAddrBits - 1, 3) + 1.U, 0.U(3.W))
-  val notCross16ByteVaddr             = Cat(latchVaddr(VAddrBits - 1, 4), 0.U(4.W))
-  val notCross16ByteVaddrDup          = Cat(latchVaddrDup(VAddrBits - 1, 4), 0.U(4.W))
+  val misalignLowVaddr                = Cat(latchVaddr(XLEN - 1, 3), 0.U(3.W))
+  val misalignLowVaddrDup             = Cat(latchVaddrDup(XLEN - 1, 3), 0.U(3.W))
+  val misalignHighVaddr               = Cat(latchVaddr(XLEN - 1, 3) + 1.U, 0.U(3.W))
+  val misalignHighVaddrDup            = Cat(latchVaddrDup(XLEN - 1, 3) + 1.U, 0.U(3.W))
+  val notCross16ByteVaddr             = Cat(latchVaddr(XLEN - 1, 4), 0.U(4.W))
+  val notCross16ByteVaddrDup          = Cat(latchVaddrDup(XLEN - 1, 4), 0.U(4.W))
  //  val misalignVaddr                   = Mux(notCross16ByteReg, notCross16ByteVaddr, Mux(isFirstSplit, misalignLowVaddr, misalignHighVaddr))
   val misalignVaddr                   = Mux(isFirstSplit, misalignLowVaddr, misalignHighVaddr)
   val misalignVaddrDup                = Mux(isFirstSplit, misalignLowVaddrDup, misalignHighVaddrDup)
@@ -579,7 +579,7 @@ class VSegmentUnit (implicit p: Parameters) extends VLSUModule
         curPtr := true.B
       }
     } .otherwise {
-      when(isMisalignReg && !notCross16ByteReg && state === s_pm) {
+      when(isMisalignWire && !notCross16ByteReg && state === s_pm) {
         curPtr := !curPtr
       } .elsewhen(isMisalignReg && !notCross16ByteReg && state === s_pm && stateNext === s_send_data) {
         curPtr := false.B
```
