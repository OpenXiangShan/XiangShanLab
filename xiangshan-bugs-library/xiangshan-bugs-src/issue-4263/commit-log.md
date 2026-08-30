# Commit Log
- Issue: #4263
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4263
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4263
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4263
- Changed files: 1
- Additions: 3
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index f146b096e3f..971a0ce3e78 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -232,7 +232,9 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   val s0_tlb_fullva    = Wire(UInt(XLEN.W))
   val s0_dcache_vaddr  = Wire(UInt(VAddrBits.W))
   val s0_is128bit      = Wire(Bool())
-  val s0_misalign_wakeup_fire = s0_misalign_select && s0_can_go && io.misalign_ldin.bits.misalignNeedWakeUp
+  val s0_misalign_wakeup_fire = s0_misalign_select && s0_can_go &&
+    io.dcache.req.ready &&
+    io.misalign_ldin.bits.misalignNeedWakeUp
 
   // flow source bundle
   class FlowSource extends Bundle {
```
