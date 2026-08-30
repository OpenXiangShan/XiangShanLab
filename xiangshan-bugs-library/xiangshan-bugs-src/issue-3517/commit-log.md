# Commit Log
- Issue: #3517
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3517
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3517
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3517
- Changed files: 1
- Additions: 7
- Deletions: 3

## Files
- `src/main/scala/xiangshan/backend/rob/Rab.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/rob/Rab.scala b/src/main/scala/xiangshan/backend/rob/Rab.scala
index 03753d108f0..ae6a4151140 100644
--- a/src/main/scala/xiangshan/backend/rob/Rab.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rab.scala
@@ -206,8 +206,9 @@ class RenameBuffer(size: Int)(implicit p: Parameters) extends XSModule with HasC
   }
 
   private val walkEndNext = walkSizeNxt === 0.U
-  private val specialWalkEndNext = specialWalkSizeNext === 0.U
-
+  private val specialWalkEndNext = specialWalkSize <= RabCommitWidth.U
+  // when robWalkEndReg is 1, walkSize donot increase and decrease RabCommitWidth per Cycle
+  private val walkEndNextCycle = (robWalkEndReg || io.fromRob.walkEnd && io.fromRob.walkSize === 0.U) && (walkSize <= RabCommitWidth.U)
   // change state
   state := stateNext
   when(io.redirect.valid) {
@@ -229,7 +230,7 @@ class RenameBuffer(size: Int)(implicit p: Parameters) extends XSModule with HasC
         }
       }
       is(s_walk) {
-        when(robWalkEnd && walkEndNext) {
+        when(walkEndNextCycle) {
           stateNext := s_idle
         }
       }
@@ -259,6 +260,9 @@ class RenameBuffer(size: Int)(implicit p: Parameters) extends XSModule with HasC
   if (backendParams.debugEn) {
     dontTouch(deqPtrVec)
     dontTouch(walkPtrNext)
+    dontTouch(walkSizeNxt)
+    dontTouch(walkEndNext)
+    dontTouch(walkEndNextCycle)
   }
 
   XSPerfAccumulate("s_idle_to_idle", state === s_idle         && stateNext === s_idle)
```
