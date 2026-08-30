# Commit Log
- Issue: #3472
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3472
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3472
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3472
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala
index 33134f04946..48d65bd9a3f 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/Debug.scala
@@ -47,14 +47,14 @@ class Debug(implicit val p: Parameters) extends Module with HasXSParameter {
   // debug_exception_ebreak
   val hasExp = hasTrap && !trapIsInterrupt
   val breakPoint = trapVec(ExceptionNO.breakPoint).asBool
-  val hasBreakPoint = hasExp && breakPoint
+  val isEbreak = hasExp && breakPoint && !TriggerAction.isExp(trigger)
   val ebreakEnterDebugMode =
     (privState.isModeM && dcsr.EBREAKM.asBool) ||
       (privState.isModeHS && dcsr.EBREAKS.asBool) ||
       (privState.isModeHU && dcsr.EBREAKU.asBool) ||
       (privState.isModeVS && dcsr.EBREAKVS.asBool) ||
       (privState.isModeVU && dcsr.EBREAKVU.asBool)
-  val hasDebugEbreakException = hasBreakPoint && ebreakEnterDebugMode
+  val hasDebugEbreakException = isEbreak && ebreakEnterDebugMode
 
   // debug_exception_trigger
   val mcontrolWireVec = tdata1Vec.map{ mod => {
```
