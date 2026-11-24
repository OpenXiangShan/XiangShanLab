# Commit Log
- Issue: #6373
- Issue URL: https://github.com/OpenXiangShan/XiangShan/issues/6373
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6449
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6449
- Changed files: 2
- Additions: 2
- Deletions: 1

## Files
- `ChiselIOPMP`
- `src/test/scala/top/SimMMIO.scala`

## Diff
```diff
diff --git a/ChiselIOPMP b/ChiselIOPMP
index 639bd341abd..cc1113c30a2 160000
--- a/ChiselIOPMP
+++ b/ChiselIOPMP
@@ -1 +1 @@
-Subproject commit 639bd341abd59c0adefc54207ba3389310576633
+Subproject commit cc1113c30a2469e27a233795a4e5705c43c84726
diff --git a/src/test/scala/top/SimMMIO.scala b/src/test/scala/top/SimMMIO.scala
index a96668be99d..94af6d796c1 100644
--- a/src/test/scala/top/SimMMIO.scala
+++ b/src/test/scala/top/SimMMIO.scala
@@ -184,5 +184,6 @@ class SimMMIO(edge: AXI4EdgeParameters)(implicit p: Parameters) extends LazyModu
     iopmp.module.apb_s.pwdata   <> iopmp_apb.pwdata
     iopmp.module.apb_s.pready   <> iopmp_apb.pready
     iopmp.module.apb_s.prdata   <> iopmp_apb.prdata
+    iopmp.module.apb_s.pslverr  <> iopmp_apb.pslverr
   }
 }
```
