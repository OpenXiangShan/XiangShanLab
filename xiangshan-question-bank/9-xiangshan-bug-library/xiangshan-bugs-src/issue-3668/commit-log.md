# Commit Log
- Issue: #3668
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3668
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3668
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3668
- Changed files: 1
- Additions: 1
- Deletions: 0

## Files
- `src/main/scala/device/imsic_axi_top.scala`

## Diff
```diff
diff --git a/src/main/scala/device/imsic_axi_top.scala b/src/main/scala/device/imsic_axi_top.scala
index da86f740e92..a907495fb67 100644
--- a/src/main/scala/device/imsic_axi_top.scala
+++ b/src/main/scala/device/imsic_axi_top.scala
@@ -127,6 +127,7 @@ class imsic_bus_top(
         TLToAXI4() :=
         TLWidthWidget(4) :=
         TLFIFOFixer() :=
+        TLBuffer() :=
         tlnode
     }
```
