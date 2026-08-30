# Commit Log
- Issue: #4877
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4877
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4877
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4877
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/mem/MemBlock.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/MemBlock.scala b/src/main/scala/xiangshan/mem/MemBlock.scala
index 29d13f23b8e..d7362b74bbd 100644
--- a/src/main/scala/xiangshan/mem/MemBlock.scala
+++ b/src/main/scala/xiangshan/mem/MemBlock.scala
@@ -1375,10 +1375,10 @@ class MemBlockInlinedImp(outer: MemBlockInlined) extends LazyModuleImp(outer)
   lsq.io.vecmmioStout.ready := false.B
 
   // miss align buffer will overwrite stOut(0)
-  val storeMisalignCanWriteBack = !otherStout.valid && !storeUnits(0).io.stout.valid && !storeUnits(0).io.vecstout.valid
+  val storeMisalignCanWriteBack = !otherStout.valid && !storeUnits(0).io.stout.valid && !storeUnits(0).io.vecstout.valid && !storeUnits(1).io.vecstout.valid
   storeMisalignBuffer.io.writeBack.ready := storeMisalignCanWriteBack
   storeMisalignBuffer.io.storeOutValid := storeUnits(0).io.stout.valid
-  storeMisalignBuffer.io.storeVecOutValid := storeUnits(0).io.vecstout.valid
+  storeMisalignBuffer.io.storeVecOutValid := storeUnits(0).io.vecstout.valid || storeUnits(1).io.vecstout.valid
   when (storeMisalignBuffer.io.writeBack.valid && storeMisalignCanWriteBack) {
     stOut(0).valid := true.B
     stOut(0).bits  := storeMisalignBuffer.io.writeBack.bits
```
