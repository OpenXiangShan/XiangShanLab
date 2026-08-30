# Commit Log
- Issue: #3433
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3433
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3433
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3433
- Changed files: 1
- Additions: 11
- Deletions: 21

## Files
- `src/main/scala/xiangshan/frontend/icache/IPrefetch.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala b/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
index 23cdab103bd..a7c3c5e02ed 100644
--- a/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
+++ b/src/main/scala/xiangshan/frontend/icache/IPrefetch.scala
@@ -349,9 +349,9 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
       when(s1_valid) {
         when(!itlb_finish) {
           next_state := m_itlbResend
-        }.elsewhen(!toWayLookup.fire && !s1_isSoftPrefetch) {  // itlb_finish
+        }.elsewhen(!toWayLookup.fire) {  // itlb_finish
           next_state := m_enqWay
-        }.elsewhen(!s2_ready) { // itlb_finish && (toWayLookup.fire || s1_isSoftPrefetch)
+        }.elsewhen(!s2_ready) {  // itlb_finish && toWayLookup.fire
           next_state := m_enterS2
         } // .otherwise { next_state := m_idle }
       } // .otherwise { next_state := m_idle }  // !s1_valid
@@ -360,34 +360,24 @@ class IPrefetchPipe(implicit p: Parameters) extends  IPrefetchModule
       when(itlb_finish) {
         when(!toMeta.ready) {
           next_state := m_metaResend
-        }.elsewhen(!s1_isSoftPrefetch) { // toMeta.ready
+        }.otherwise { // toMeta.ready
           next_state := m_enqWay
-        }.elsewhen(!s2_ready) { // toMeta.ready && s1_isSoftPrefetch
-          next_state := m_enterS2
-        }.otherwise { // toMeta.ready && s1_isSoftPrefetch && s2_ready
-          next_state := m_idle
         }
       } // .otherwise { next_state := m_itlbResend }  // !itlb_finish
     }
     is(m_metaResend) {
       when(toMeta.ready) {
-        when (!s1_isSoftPrefetch) {
-          next_state := m_enqWay
-        }.elsewhen(!s2_ready) { // s1_isSoftPrefetch
-          next_state := m_enterS2
-        }.otherwise { // s1_isSoftPrefetch && s2_ready
-          next_state := m_idle
-        }
+        next_state := m_enqWay
       } // .otherwise { next_state := m_metaResend }  // !toMeta.ready
     }
     is(m_enqWay) {
-      // sanity check
-      assert(!s1_isSoftPrefetch, "Soft prefetch enters m_enqWay")
-      when(toWayLookup.fire && !s2_ready) {
-        next_state := m_enterS2
-      }.elsewhen(toWayLookup.fire && s2_ready) {
-        next_state := m_idle
-      }
+      when(toWayLookup.fire || s1_isSoftPrefetch) {
+        when (!s2_ready) {
+          next_state := m_enterS2
+        }.otherwise {  // s2_ready
+          next_state := m_idle
+        }
+      } // .otherwise { next_state := m_enqWay }
     }
     is(m_enterS2) {
       when(s2_ready) {
```
