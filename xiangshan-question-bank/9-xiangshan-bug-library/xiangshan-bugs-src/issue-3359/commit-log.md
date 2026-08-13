# Commit Log
- Issue: #3359
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3359
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3359
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3359
- Changed files: 2
- Additions: 15
- Deletions: 2

## Files
- `difftest`
- `src/main/scala/xiangshan/backend/datapath/DataConfig.scala`

## Diff
```diff
diff --git a/difftest b/difftest
index df3e227a59a..790756c4017 160000
--- a/difftest
+++ b/difftest
@@ -1 +1 @@
-Subproject commit df3e227a59ae754b0e6480d37ccf55782313688d
+Subproject commit 790756c4017eae996ee3ecc8fa1d66eb2fb97a94
diff --git a/src/main/scala/xiangshan/backend/datapath/DataConfig.scala b/src/main/scala/xiangshan/backend/datapath/DataConfig.scala
index e7fab5690dd..83d8cd44e9a 100644
--- a/src/main/scala/xiangshan/backend/datapath/DataConfig.scala
+++ b/src/main/scala/xiangshan/backend/datapath/DataConfig.scala
@@ -1,6 +1,8 @@
 package xiangshan.backend.datapath
 
 import chisel3.util.log2Up
+import org.chipsalliance.cde.config.Parameters
+import xiangshan.XSCoreParamsKey
 
 object DataConfig {
   sealed abstract class DataConfig (
@@ -14,7 +16,7 @@ object DataConfig {
   case class FpData() extends DataConfig("fp", 64)
   case class VecData() extends DataConfig("vec", 128)
   case class ImmData(len: Int) extends DataConfig("int", len)
-  case class VAddrData() extends DataConfig("vaddr", 39) // Todo: associate it with the width of vaddr
+  case class VAddrData()(implicit p: Parameters) extends DataConfig("vaddr", VAddrBits)
   case class V0Data() extends DataConfig("v0", 128)
   case class VlData() extends DataConfig("vl", log2Up(VecData().dataWidth) + 1 ) // 8
   case class FakeIntData() extends DataConfig("fakeint", 64)
@@ -29,4 +31,15 @@ object DataConfig {
 
 
   def RegDataMaxWidth : Int = RegSrcDataSet.map(_.dataWidth).max
+
+  def VAddrBits(implicit p: Parameters): Int = {
+    def coreParams = p(XSCoreParamsKey)
+    def HasHExtension = coreParams.HasHExtension
+    if(HasHExtension){
+      coreParams.GPAddrBits
+    }else{
+      coreParams.VAddrBits
+    }
+    // VAddrBits is Virtual Memory addr bits
+  }
 }
```
