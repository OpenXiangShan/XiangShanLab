# Commit Log
- Issue: #4842
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4842
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4842
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4842
- Changed files: 1
- Additions: 7
- Deletions: 2

## Files
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
index 104de7cf262..fea0f69edeb 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
@@ -356,8 +356,13 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
 
   val s1_hit_tag = get_tag(s1_req.addr)
   val s1_hit_coh = ClientMetadata(ParallelMux(s1_tag_ecc_match_way.asBools, (0 until nWays).map(w => meta_resp(w))))
-  val s1_flag_error = ParallelMux(s1_tag_ecc_match_way.asBools, (0 until nWays).map(w => io.extra_meta_resp(w).error))
-  val s1_extra_meta = ParallelMux(s1_tag_ecc_match_way.asBools, (0 until nWays).map(w => io.extra_meta_resp(w)))
+  val s1_extra_meta = Wire(io.extra_meta_resp.head.cloneType)
+  s1_extra_meta := Mux(
+    GatedValidRegNext(s0_fire),
+    ParallelMux(s1_tag_ecc_match_way.asBools, (0 until nWays).map(w => io.extra_meta_resp(w))),
+    RegEnable(s1_extra_meta, s1_valid)
+  )
+  val s1_flag_error = s1_extra_meta.error
   io.pseudo_tag_error_inj_done := s1_fire && s1_meta_valids.orR
 
   XSPerfAccumulate("probe_unused_prefetch", s1_req.probe && isFromL1Prefetch(s1_extra_meta.prefetch) && !s1_extra_meta.access) // may not be accurate
```
