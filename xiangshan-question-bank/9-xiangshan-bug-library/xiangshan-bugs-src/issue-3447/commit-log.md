# Commit Log
- Issue: #3447
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3447
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3447
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3447
- Changed files: 1
- Additions: 6
- Deletions: 2

## Files
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index ca128f05af8..0d4f523080b 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -694,8 +694,12 @@ class PteBundle(implicit p: Parameters) extends PtwBundle{
 
   def isPf(level: UInt) = {
     val pf = WireInit(false.B)
-    when (isNext()) {
-      pf := (perm.u || perm.a || perm.d )
+    when (reserved =/= 0.U){
+      pf := true.B
+    }.elsewhen(pbmt === 3.U){
+      pf := true.B
+    }.elsewhen (isNext()) {
+      pf := (perm.u || perm.a || perm.d || n =/= 0.U || pbmt =/= 0.U)
     }.elsewhen (!perm.v || (!perm.r && perm.w)) {
       pf := true.B
     }.otherwise{
```
