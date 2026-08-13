# Commit Log
- Issue: #4915
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4915
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4915
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4915
- Changed files: 3
- Additions: 3
- Deletions: 3

## Files
- `ready-to-run`
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala`

## Diff
```diff
diff --git a/ready-to-run b/ready-to-run
index dec66a09340..2ea9d84709f 160000
--- a/ready-to-run
+++ b/ready-to-run
@@ -1 +1 @@
-Subproject commit dec66a09340ccf720063601dddb856b246a91285
+Subproject commit 2ea9d84709fdafe27c421d10d7e065c9e0a1b20f
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index 868ce07cc35..f882638a7d8 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -756,7 +756,7 @@ object OPTYPE extends CSREnum with WARLApply {
 
 class McontextBundle extends CSRBundle {
   override val len = 14
-  val HCONTEXT = RW(13, 0)
+  val HCONTEXT = RW(13, 0).withReset(0.U)
 }
 
 trait HasOfFromPerfCntBundle { self: CSRModule[_] =>
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala
index b6cd2a685b4..7660d2379ec 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/SupervisorLevel.scala
@@ -253,7 +253,7 @@ class SatpBundle extends CSRBundle {
 
 class ScontextBundle extends CSRBundle {
   override val len = 32
-  val ALL = RW(31, 0)
+  val ALL = RW(31, 0).withReset(0.U)
 }
 
 class SEnvCfg extends EnvCfg
```
