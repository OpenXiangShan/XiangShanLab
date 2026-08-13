# Commit Log
- Issue: #4345
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4345
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4345
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4345
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
index 2e0c2aa5075..49c45cad5ce 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
@@ -498,9 +498,9 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   val s3_data_word = RegEnable(s2_data_word, s2_fire_to_s3)
   val s3_data_quad_word = RegEnable(s2_data_quad_word, s2_fire_to_s3)
   val s3_data = RegEnable(s2_data, s2_fire_to_s3)
-  val s3_l2_error = RegEnable(s2_l2_error, s2_fire_to_s3)
   val s3_idx = RegEnable(s2_idx, s2_fire_to_s3)
   // data_error will be reported by data array 1 cycle after data read resp
+  val s3_l2_error = RegEnable(s2_l2_error, s2_fire)
   val s3_data_error = Wire(Bool())
   s3_data_error := Mux(GatedValidRegNextN(s1_fire,2), // ecc check result is generated 2 cycle after read req
     io.readline_error_delayed && RegNext(s2_may_report_data_error),
@@ -508,7 +508,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   )
   // error signal for amo inst
   // s3_error = s3_flag_error || s3_tag_error || s3_l2_error || s3_data_error
-  val s3_error = RegEnable(s2_error, 0.U.asTypeOf(s2_error), s2_fire_to_s3) || s3_data_error
+  val s3_error = RegEnable(s2_error, 0.U.asTypeOf(s2_error), s2_fire) || s3_data_error
   val (_, probe_shrink_param, probe_new_coh) = s3_coh.onProbe(s3_req.probe_param)
   val (_, miss_shrink_param, _) = s3_coh.onCacheControl(M_FLUSH)
   val s3_need_replacement = RegEnable(s2_need_replacement, s2_fire_to_s3)
```
