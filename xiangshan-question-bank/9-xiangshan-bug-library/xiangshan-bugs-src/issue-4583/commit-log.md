# Commit Log
- Issue: #4583
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4583
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4583
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4583
- Changed files: 1
- Additions: 2
- Deletions: 1

## Files
- `src/main/scala/top/XSNoCTop.scala`

## Diff
```diff
diff --git a/src/main/scala/top/XSNoCTop.scala b/src/main/scala/top/XSNoCTop.scala
index d6eb7cd878e..ce510c15fbc 100644
--- a/src/main/scala/top/XSNoCTop.scala
+++ b/src/main/scala/top/XSNoCTop.scala
@@ -222,8 +222,9 @@ class XSNoCTop()(implicit p: Parameters) extends BaseXSSoc with HasSoCParameter
     val seip  = plic.last(0)
     val nmi_31 = nmi.head(0)
     val nmi_43 = nmi.head(1)
+    val debugIntr = debug.head(0)
     val msi_info_vld = core_with_l2.module.io.msiInfo.valid
-    val intSrc = Cat(msip, mtip, meip, seip, nmi_31, nmi_43, msi_info_vld)
+    val intSrc = Cat(msip, mtip, meip, seip, nmi_31, nmi_43, debugIntr, msi_info_vld)
 
     /*
      * CPU Low Power State:
```
