# Commit Log
- Issue: #4561
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4561
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4561
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4561
- Changed files: 2
- Additions: 3
- Deletions: 3

## Files
- `src/main/scala/system/SoC.scala`
- `src/main/scala/xiangshan/Parameters.scala`

## Diff
```diff
diff --git a/src/main/scala/system/SoC.scala b/src/main/scala/system/SoC.scala
index 3da57de3a65..9481aaf336a 100644
--- a/src/main/scala/system/SoC.scala
+++ b/src/main/scala/system/SoC.scala
@@ -149,7 +149,7 @@ trait HasSoCParameter {
   val TracePrivWidth              = tiles.head.traceParams.PrivWidth
   val TraceIaddrWidth             = tiles.head.traceParams.IaddrWidth
   val TraceItypeWidth             = tiles.head.traceParams.ItypeWidth
-  val TraceIretireWidthCompressed = log2Up(tiles.head.RenameWidth * tiles.head.CommitWidth * 2)
+  val TraceIretireWidthCompressed = log2Up(tiles.head.RenameWidth * tiles.head.CommitWidth * 2 + 1)
   val TraceIlastsizeWidth         = tiles.head.traceParams.IlastsizeWidth
 
   // L3 configurations
diff --git a/src/main/scala/xiangshan/Parameters.scala b/src/main/scala/xiangshan/Parameters.scala
index ca8e72db6f8..1a4e62ff36a 100644
--- a/src/main/scala/xiangshan/Parameters.scala
+++ b/src/main/scala/xiangshan/Parameters.scala
@@ -904,8 +904,8 @@ trait HasXSParameter {
   def PrivWidth              = coreParams.traceParams.PrivWidth
   def IaddrWidth             = coreParams.traceParams.IaddrWidth
   def ItypeWidth             = coreParams.traceParams.ItypeWidth
-  def IretireWidthInPipe     = log2Up(RenameWidth * 2)
-  def IretireWidthCompressed = log2Up(RenameWidth * CommitWidth * 2)
+  def IretireWidthInPipe     = log2Up(RenameWidth * 2 + 1)
+  def IretireWidthCompressed = log2Up(RenameWidth * CommitWidth * 2 + 1)
   def IlastsizeWidth         = coreParams.traceParams.IlastsizeWidth
 
   def hasMbist               = coreParams.hasMbist
```
