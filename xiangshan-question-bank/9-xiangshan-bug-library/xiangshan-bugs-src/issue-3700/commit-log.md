# Commit Log
- Issue: #3700
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3700
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3700
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3700
- Changed files: 1
- Additions: 3
- Deletions: 3

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 056ca11360b..b02eb62433c 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -1170,9 +1170,9 @@ class NewCSR(implicit val p: Parameters) extends Module
   toAIA.vsClaim := vstopei.w.wen
 
   // tlb
-  io.tlb.satpASIDChanged  := GatedValidRegNext(wenLegal && addr === CSRs. satp.U && satp .regOut.ASID =/=  satp.w.wdataFields.ASID)
-  io.tlb.vsatpASIDChanged := GatedValidRegNext(wenLegal && addr === CSRs.vsatp.U && vsatp.regOut.ASID =/= vsatp.w.wdataFields.ASID)
-  io.tlb.hgatpVMIDChanged := GatedValidRegNext(wenLegal && addr === CSRs.hgatp.U && hgatp.regOut.VMID =/= hgatp.w.wdataFields.VMID)
+  io.tlb.satpASIDChanged  := GatedValidRegNext(satp.w.wen  && satp .regOut.ASID =/=  satp.w.wdataFields.ASID)
+  io.tlb.vsatpASIDChanged := GatedValidRegNext(vsatp.w.wen && vsatp.regOut.ASID =/= vsatp.w.wdataFields.ASID)
+  io.tlb.hgatpVMIDChanged := GatedValidRegNext(hgatp.w.wen && hgatp.regOut.VMID =/= hgatp.w.wdataFields.VMID)
   io.tlb.satp := satp.rdata
   io.tlb.vsatp := vsatp.rdata
   io.tlb.hgatp := hgatp.rdata
```
