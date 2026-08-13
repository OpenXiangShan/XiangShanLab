# Commit Log
- Issue: #4856
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4856
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4856
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4856
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
index fea0f69edeb..a562f4c5963 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
@@ -587,7 +587,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   val s3_tag_error_wb = RegEnable(s2_tag_error, s2_fire_to_s3)
 
   // data_error will be reported by data array 1 cycle after data read resp
-  val s3_data_error_beu = io.readline_error && RegEnable(s2_may_report_data_error, s2_fire)
+  val s3_data_error_beu = io.readline_error && GatedValidRegNext(s2_fire_to_s3) && RegEnable(s2_may_report_data_error, s2_fire)
   val s3_data_error_wb = io.readline_error_delayed && RegEnable(s2_may_report_data_error, s2_fire_to_s3)
 
   val s3_l2_error_beu = RegEnable(s2_l2_error, s2_fire)
```
