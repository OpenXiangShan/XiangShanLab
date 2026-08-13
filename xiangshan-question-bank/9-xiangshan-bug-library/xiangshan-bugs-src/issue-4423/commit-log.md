# Commit Log
- Issue: #4423
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4423
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4423
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4423
- Changed files: 1
- Additions: 20
- Deletions: 5

## Files
- `src/main/scala/xiangshan/frontend/icache/IPrefetch.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala b/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
index d0c3dd2144f..71984b6ed8a 100644
--- a/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
+++ b/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
@@ -209,7 +209,7 @@ class IPrefetchPipe(implicit p: Parameters) extends IPrefetchModule with HasICac
       data = fromITLB(i).bits.isForVSnonLeafPTE
     )
   })
-  private val s1_itlb_exception = VecInit((0 until PortNumber).map { i =>
+  private val s1_itlb_exception_tmp = VecInit((0 until PortNumber).map { i =>
     ResultHoldBypass(
       valid = tlb_valid_pulse(i),
       init = 0.U(ExceptionType.width.W),
@@ -223,6 +223,18 @@ class IPrefetchPipe(implicit p: Parameters) extends IPrefetchModule with HasICac
       data = fromITLB(i).bits.pbmt(0)
     )
   })
+
+  // merge backend exception and itlb exception
+  // for area concern, we don't have 64 bits vaddr in frontend, but spec asks page fault when high bits are not all 0/1
+  // this check is finished in backend, and passed to frontend with redirect, we see it as a part of itlb exception
+  private val s1_itlb_exception = ExceptionType.merge(
+    s1_backendException,
+    s1_itlb_exception_tmp
+  )
+  // debug
+  dontTouch(s1_itlb_exception_tmp)
+  dontTouch(s1_itlb_exception)
+
   private val s1_itlb_exception_gpf = VecInit(s1_itlb_exception.map(_ === ExceptionType.gpf))
 
   /* Select gpaddr with the first gpf
@@ -359,8 +371,12 @@ class IPrefetchPipe(implicit p: Parameters) extends IPrefetchModule with HasICac
     val excpValid = if (i == 0) true.B else s1_doubleline
     // Send s1_itlb_exception to WayLookup (instead of s1_exception_out) for better timing.
     // Will check pmp again in mainPipe
-    toWayLookup.bits.itlb_exception(i) := Mux(excpValid, s1_itlb_exception(i), ExceptionType.none)
-    toWayLookup.bits.itlb_pbmt(i)      := Mux(excpValid, s1_itlb_pbmt(i), Pbmt.pma)
+    toWayLookup.bits.itlb_exception(i) := Mux(
+      excpValid,
+      s1_itlb_exception(i), // includes backend exception
+      ExceptionType.none
+    )
+    toWayLookup.bits.itlb_pbmt(i) := Mux(excpValid, s1_itlb_pbmt(i), Pbmt.pma)
   }
 
   private val s1_waymasks_vec = s1_waymasks.map(_.asTypeOf(Vec(nWays, Bool())))
@@ -397,8 +413,7 @@ class IPrefetchPipe(implicit p: Parameters) extends IPrefetchModule with HasICac
   // merge s1 itlb/pmp exceptions, itlb has the highest priority, pmp next
   // for timing consideration, meta_corrupt is not merged, and it will NOT cancel prefetch
   private val s1_exception_out = ExceptionType.merge(
-    s1_backendException,
-    s1_itlb_exception,
+    s1_itlb_exception, // includes backend exception
     s1_pmp_exception
   )
```
