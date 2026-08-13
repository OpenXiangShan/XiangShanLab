# Commit Log
- Issue: #4580
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4580
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4580
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4580
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
index d1d237bc54d..6382235eb81 100644
--- a/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/LoadUnit.scala
@@ -1650,7 +1650,7 @@ class LoadUnit(implicit p: Parameters) extends XSModule
   io.lsq.ldin.bits.uop := s3_out.bits.uop
 //  io.lsq.ldin.bits.uop.exceptionVec(loadAddrMisaligned) := Mux(s3_in.onlyMisalignException, false.B, s3_in.uop.exceptionVec(loadAddrMisaligned))
 
-  val s3_revoke = s3_exception || io.lsq.ldin.bits.rep_info.need_rep || s3_mis_align
+  val s3_revoke = s3_exception || io.lsq.ldin.bits.rep_info.need_rep || s3_mis_align || (s3_frm_mabuf && io.misalign_ldout.bits.rep_info.need_rep)
   io.lsq.ldld_nuke_query.revoke := s3_revoke
   io.lsq.stld_nuke_query.revoke := s3_revoke
```
