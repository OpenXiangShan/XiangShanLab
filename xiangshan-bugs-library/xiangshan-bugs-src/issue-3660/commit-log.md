# Commit Log
- Issue: #3660
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3660
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3660
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3660
- Changed files: 1
- Additions: 6
- Deletions: 2

## Files
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index 691f07f89d7..2f3d1a45fc3 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -720,6 +720,8 @@ class PteBundle(implicit p: Parameters) extends PtwBundle{
     pf
   }
 
+  // G-stage which for supporting VS-stage is LOAD type, only need to check A bit
+  // The check of D bit is in L1TLB
   def isGpf(level: UInt, pbmte: Bool) = {
     val gpf = WireInit(false.B)
     when (reserved =/= 0.U){
@@ -734,8 +736,10 @@ class PteBundle(implicit p: Parameters) extends PtwBundle{
       gpf := true.B
     }.elsewhen (n =/= 0.U && ppn(3, 0) =/= 8.U) {
       gpf := true.B
-    }.otherwise{
-      gpf := unaligned(level)
+    }.elsewhen (unaligned(level)) {
+      gpf := true.B
+    }.elsewhen (!perm.a) {
+      gpf := true.B
     }
     gpf
   }
```
