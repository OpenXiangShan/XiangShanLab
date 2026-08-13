# Commit Log
- Issue: #4131
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4131
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4131
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4131
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/rename/Rename.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/rename/Rename.scala b/src/main/scala/xiangshan/backend/rename/Rename.scala
index 8d4855ac611..43bce5b37c0 100644
--- a/src/main/scala/xiangshan/backend/rename/Rename.scala
+++ b/src/main/scala/xiangshan/backend/rename/Rename.scala
@@ -561,7 +561,7 @@ class Rename(implicit p: Parameters) extends XSModule with HasCircularQueuePtrHe
     // For fused-lui-load, load.src(0) is replaced by the imm.
     val last_is_lui = io.in(i - 1).bits.selImm === SelImm.IMM_U && io.in(i - 1).bits.srcType(0) =/= SrcType.pc
     val this_is_load = io.in(i).bits.fuType === FuType.ldu.U
-    val lui_to_load = io.in(i - 1).valid && io.in(i - 1).bits.ldest === io.in(i).bits.lsrc(0)
+    val lui_to_load = io.in(i - 1).valid && io.in(i - 1).bits.rfWen && io.in(i - 1).bits.ldest === io.in(i).bits.lsrc(0)
     val fused_lui_load = last_is_lui && this_is_load && lui_to_load
     when (fused_lui_load) {
       // The first LOAD operand (base address) is replaced by LUI-imm and stored in imm
```
