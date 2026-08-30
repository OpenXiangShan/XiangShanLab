# Commit Log
- Issue: #3953
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3953
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3953
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3953
- Changed files: 2
- Additions: 20
- Deletions: 1

## Files
- `src/main/scala/xiangshan/Parameters.scala`
- `src/main/scala/xiangshan/XSDts.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/Parameters.scala b/src/main/scala/xiangshan/Parameters.scala
index f5d7f53f617..c3376b2a130 100644
--- a/src/main/scala/xiangshan/Parameters.scala
+++ b/src/main/scala/xiangshan/Parameters.scala
@@ -359,6 +359,21 @@ case class XSCoreParameters
   softPTW: Boolean = false, // dpi-c l2tlb debug only
   softPTWDelay: Int = 1
 ){
+  def ISABase = "rv64i"
+  def ISAExtensions = Seq(
+    // single letter extensions, in canonical order
+    "i", "m", "a", "f", "d", "c", /* "b", */ "v", "h",
+    // multi-letter extensions, sorted alphanumerically
+    "sdtrig", "sha", "shcounterenw", "shgatpa", "shtvala", "shvsatpa", "shvstvala", "shvstvecd",
+    "smaia", "smstateen", "ss1p13", "ssaia", "ssccptr", "sscofpmf", "sscounterenw", "ssstateen",
+    "sstc", "sstvala", "sstvecd", "ssu64xl", "sv39", "sv48", "svade", "svbare", "svinval",
+    "svpbmt", "za64rs", "zba", "zbb", "zbc", "zbkb", "zbkc", "zbkx", "zbs", "zcb", "zcmop",
+    "zfa", "zfh", "zfhmin", "zic64b", "zicbom", "zicbop", "zicboz", "ziccif", "zicclsm",
+    "ziccrse", "zicntr", "zicond", "zicsr", "zifencei", "zihintpause", "zihpm", "zimop", "zkn",
+    "zknd", "zkne", "zknh", "zksed", "zksh", "zkt", "zvbb", "zvfh", "zvfhmin", "zvkt",
+    "zvl128b", "zvl32b", "zvl64b"
+  )
+
   def vlWidth = log2Up(VLEN) + 1
 
   /**
@@ -577,6 +592,8 @@ trait HasXSParameter {
   def coreParams = p(XSCoreParamsKey)
   def env = p(DebugOptionsKey)
 
+  def ISABase = coreParams.ISABase
+  def ISAExtensions = coreParams.ISAExtensions
   def XLEN = coreParams.XLEN
   def VLEN = coreParams.VLEN
   def ELEN = coreParams.ELEN
diff --git a/src/main/scala/xiangshan/XSDts.scala b/src/main/scala/xiangshan/XSDts.scala
index 9036fed2dfa..5502785d259 100644
--- a/src/main/scala/xiangshan/XSDts.scala
+++ b/src/main/scala/xiangshan/XSDts.scala
@@ -30,7 +30,9 @@ trait HasXSDts {
       "device_type" -> "cpu".asProperty,
       "status" -> "okay".asProperty,
       "clock-frequency" -> 0.asProperty,
-      "riscv,isa" -> "rv64imafdch".asProperty,
+      "riscv,isa" -> "rv64imafdcvh".asProperty, // deprecated
+      "riscv,isa-base" -> ISABase.asProperty,
+      "riscv,isa-extensions" -> ISAExtensions.map(ResourceString),
       "timebase-frequency" -> 1000000.asProperty
     )
```
