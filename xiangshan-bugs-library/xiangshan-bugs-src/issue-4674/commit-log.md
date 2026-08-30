# Commit Log
- Issue: #4674
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4674
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4674
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4674
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index 772f8b04bbd..dd0774829ed 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -1635,7 +1635,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
 
   val s3_frm_mis_flush     = s3_frm_mabuf &&
     (io.misalign_ldout.bits.rep_info.fwd_fail || io.misalign_ldout.bits.rep_info.mem_amb || io.misalign_ldout.bits.rep_info.nuke
-      || io.misalign_ldout.bits.rep_info.rar_nack)
+      || io.misalign_ldout.bits.rep_info.rar_nack || io.misalign_ldout.bits.rep_info.raw_nack)
 
   io.rollback.valid := s3_valid && (s3_rep_frm_fetch || s3_flushPipe || s3_frm_mis_flush) && !s3_exception
   io.rollback.bits             := DontCare
```
