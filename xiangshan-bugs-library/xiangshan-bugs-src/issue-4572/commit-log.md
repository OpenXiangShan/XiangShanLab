# Commit Log
- Issue: #4572
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4572
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4572
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4572
- Changed files: 1
- Additions: 3
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
index 4a4ad529b11..42874716d13 100644
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
@@ -427,8 +427,10 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
 
   val s2_hit = s2_tag_match && s2_has_permission
   val s2_sc = s2_req.cmd === M_XSC
+  val s2_lr = s2_req.cmd === M_XLR
   val s2_amo_hit = s2_hit && !s2_req.probe && !s2_req.miss && s2_req.isAMO
   val s2_store_hit = s2_hit && !s2_req.probe && !s2_req.miss && s2_req.isStore
+  val s2_should_not_report_ecc_error = !s2_req.miss && (s2_req.isAMO && !s2_lr || s2_req.isStore)
 
   if(EnableTagEcc) {
     s2_tag_error := s2_tag_errors.orR && s2_need_tag
@@ -995,7 +997,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   // report error to beu and csr, 1 cycle after read data resp
   io.error := 0.U.asTypeOf(ValidIO(new L1CacheErrorInfo))
   // report error, update error csr
-  io.error.valid := s3_error && GatedValidRegNext(s2_fire && !(s2_req.isAMO || s2_req.isStore))
+  io.error.valid := s3_error && GatedValidRegNext(s2_fire && !s2_should_not_report_ecc_error)
   // only tag_error and data_error will be reported to beu
   // l2_error should not be reported (l2 will report that)
   io.error.bits.report_to_beu := (s3_tag_error || s3_data_error) && RegNext(s2_fire)
```
