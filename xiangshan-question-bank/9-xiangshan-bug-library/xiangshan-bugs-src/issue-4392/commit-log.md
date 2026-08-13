# Commit Log
- Issue: #4392
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4392
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4392
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4392
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/CtrlBlock.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/CtrlBlock.scala b/src/main/scala/xiangshan/backend/CtrlBlock.scala
index 7e4ca5bd7da..637cab3a1cc 100644
--- a/src/main/scala/xiangshan/backend/CtrlBlock.scala
+++ b/src/main/scala/xiangshan/backend/CtrlBlock.scala
@@ -190,7 +190,7 @@ class CtrlBlockImp(
   private val exuRedirects: Seq[ValidIO[Redirect]] = io.fromWB.wbData.filter(_.bits.redirect.nonEmpty).map(x => {
     val hasCSR = x.bits.params.hasCSR
     val out = Wire(Valid(new Redirect()))
-    out.valid := x.valid && x.bits.redirect.get.valid && x.bits.redirect.get.bits.cfiUpdate.isMisPred && !x.bits.robIdx.needFlush(Seq(s1_s3_redirect, s2_s4_redirect))
+    out.valid := x.valid && x.bits.redirect.get.valid && (x.bits.redirect.get.bits.cfiUpdate.isMisPred || x.bits.redirect.get.bits.cfiUpdate.hasBackendFault) && !x.bits.robIdx.needFlush(Seq(s1_s3_redirect, s2_s4_redirect))
     out.bits := x.bits.redirect.get.bits
     out.bits.debugIsCtrl := true.B
     out.bits.debugIsMemVio := false.B
```
