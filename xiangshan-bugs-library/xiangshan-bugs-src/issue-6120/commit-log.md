# Commit Log
- Issue: #6120
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6120
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6120
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6120
- Changed files: 1
- Additions: 28
- Deletions: 14

## Files
- `src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala
index 9c876939441..d9d34f24944 100644
--- a/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala
+++ b/src/main/scala/xiangshan/mem/lsqueue/LoadQueueRAW.scala
@@ -273,30 +273,44 @@ class LoadQueueRAW(implicit p: Parameters) extends XSModule
     assert(valid.length == bits.length)
     val numSelectGroups = scala.math.ceil(valid.length.toFloat / SelectGroupSize).toInt
 
+    def selectOldestByAgeMatrix(valid: Seq[Bool], bits: Seq[T]): (Bool, T) = {
+      assert(valid.nonEmpty)
+      val validAgeMatrixUpper = Seq.tabulate(valid.length) { i =>
+        Seq.tabulate(valid.length - i - 1) { offset =>
+          val j = i + offset + 1
+          valid(i) && (!valid(j) || isOlderFu(bits(i), bits(j)))
+        }
+      }
+      def ageMatrixOffset(row: Int, col: Int): Int = col - row - 1
+      def validAgeMatrixLookup(row: Int, col: Int): Bool = {
+        if (row == col) {
+          valid(row)
+        } else if (row < col) {
+          validAgeMatrixUpper(row)(ageMatrixOffset(row, col))
+        } else {
+          !validAgeMatrixUpper(col)(ageMatrixOffset(col, row))
+        }
+      }
+      val winnerOH = valid.indices.map { i =>
+        VecInit(valid.indices.map(j => validAgeMatrixLookup(i, j))).asUInt.andR
+      }
+      val selValid = VecInit(valid).asUInt.orR
+      val selBits = Mux1H(winnerOH, bits)
+      (selValid, selBits)
+    }
+
     // group info
     val selectValidGroups = valid.grouped(SelectGroupSize).toList
     val selectBitsGroups = bits.grouped(SelectGroupSize).toList
     // select logic
     if (valid.length <= SelectGroupSize) {
-      val selectModule = Module(new SelectOldest(bits.head.cloneType, bits.length, isOlderFu).suggestName(s"selectModule_level_${level}"))
-      selectModule.io.in.zipWithIndex.map{case (sink, i) =>
-        sink.valid := valid(i)
-        sink.bits := bits(i)
-      }
-      val selValid = selectModule.io.out.valid
-      val selBits = selectModule.io.out.bits
+      val (selValid, selBits) = selectOldestByAgeMatrix(valid, bits)
       val selValidNext = GatedValidRegNext(selValid)
       val selBitsNext = RegEnable(selBits, selValid)
       (Seq(selValidNext && !selBitsNext.robIdx.needFlush(RegNext(io.redirect))), Seq(selBitsNext))
     } else {
       val select = (0 until numSelectGroups).map(g => {
-        val selectModule = Module(new SelectOldest(bits.head.cloneType, selectValidGroups(g).length, isOlderFu).suggestName(s"selectModule_level_${level}_group_${g}"))
-        selectModule.io.in.zipWithIndex.map{case (sink, i) =>
-          sink.valid := selectValidGroups(g)(i)
-          sink.bits := selectBitsGroups(g)(i)
-        }
-        val selValid = selectModule.io.out.valid
-        val selBits = selectModule.io.out.bits
+        val (selValid, selBits) = selectOldestByAgeMatrix(selectValidGroups(g), selectBitsGroups(g))
         val selValidNext = RegNext(selValid)
         val selBitsNext = RegEnable(selBits, selValid)
         (selValidNext && !selBitsNext.robIdx.needFlush(io.redirect) && !selBitsNext.robIdx.needFlush(RegNext(io.redirect)), selBitsNext)
```
