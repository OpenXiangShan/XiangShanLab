# Commit Log
- Issue: #4112
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4112
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4112
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4112
- Changed files: 1
- Additions: 8
- Deletions: 1

## Files
- `src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala b/src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala
index 030d738018a..a854aa1e140 100644
--- a/src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala
+++ b/src/main/scala/xiangshan/frontend/icache/ICacheMissUnit.scala
@@ -345,8 +345,15 @@ class ICacheMissUnit(edge: TLEdgeOut)(implicit p: Parameters) extends ICacheModu
   // if any beat is corrupt, the whole response (to mainPipe/metaArray/dataArray) is corrupt
   private val corrupt_r = RegInit(false.B)
   when(io.mem_grant.fire && edge.hasData(io.mem_grant.bits) && io.mem_grant.bits.corrupt) {
+    // Set corrupt_r when any beat is corrupt
+    // This is actually when(xxx.fire && xxx.hasData) { corrupt_r := corrupt_r || io.mem_grant.bits.corrupt }
     corrupt_r := true.B
-  }.elsewhen(io.fetch_resp.fire) {
+  }.elsewhen(last_fire_r) {
+    // Clear corrupt_r when response it sent to mainPipe
+    // This used to be io.fetch_resp.valid (last_fire_r && mshr_resp.valid) but when mshr is flushed by io.flush/fencei,
+    // mshr_resp.valid is false.B and corrupt_r will never be cleared, that's not correct
+    // so we remove mshr_resp.valid here, and the condition leftover is last_fire_r
+    // or, actually, io.fetch_resp.valid || (last_fire_r && !mshr_resp.valid)
     corrupt_r := false.B
   }
```
