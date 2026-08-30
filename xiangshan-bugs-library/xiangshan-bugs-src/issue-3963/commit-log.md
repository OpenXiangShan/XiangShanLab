# Commit Log
- Issue: #3963
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3963
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3963
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3963
- Changed files: 1
- Additions: 13
- Deletions: 10

## Files
- `src/main/scala/xiangshan/frontend/IFU.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/IFU.scala b/src/main/scala/xiangshan/frontend/IFU.scala
index 5fa6978536f..7175703af79 100644
--- a/src/main/scala/xiangshan/frontend/IFU.scala
+++ b/src/main/scala/xiangshan/frontend/IFU.scala
@@ -394,16 +394,19 @@ class NewIFU(implicit p: Parameters) extends XSModule
   val f2_isForVSnonLeafPTE = fromICache.bits.isForVSnonLeafPTE
 
   // FIXME: raise af if one fetch block crosses the cacheable-noncacheable boundary, might not correct
-  val f2_mmio_mismatch_exception = VecInit(Seq.fill(2)(Mux(
-    // not double-line, skip check
-    !fromICache.bits.doubleline || (
-      // is double-line, ask for consistent pmp_mmio and itlb_pbmt value
-      fromICache.bits.pmp_mmio(0) === fromICache.bits.pmp_mmio(1) &&
-        fromICache.bits.itlb_pbmt(0) === fromICache.bits.itlb_pbmt(1)
-    ),
-    ExceptionType.none,
-    ExceptionType.af
-  )))
+  val f2_mmio_mismatch_exception = VecInit(Seq(
+    ExceptionType.none, // mark the exception only on the second line
+    Mux(
+      // not double-line, skip check
+      !fromICache.bits.doubleline || (
+        // is double-line, ask for consistent pmp_mmio and itlb_pbmt value
+        fromICache.bits.pmp_mmio(0) === fromICache.bits.pmp_mmio(1) &&
+          fromICache.bits.itlb_pbmt(0) === fromICache.bits.itlb_pbmt(1)
+      ),
+      ExceptionType.none,
+      ExceptionType.af
+    )
+  ))
 
   // merge exceptions
   val f2_exception = ExceptionType.merge(f2_exception_in, f2_mmio_mismatch_exception)
```
