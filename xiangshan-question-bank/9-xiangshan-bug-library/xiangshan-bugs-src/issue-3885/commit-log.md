# Commit Log
- Issue: #3885
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3885
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3885
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3885
- Changed files: 3
- Additions: 6
- Deletions: 6

## Files
- `difftest`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/rob/Rob.scala`

## Diff
```diff
diff --git a/difftest b/difftest
index 5d945a3a283..14117b14a78 160000
--- a/difftest
+++ b/difftest
@@ -1 +1 @@
-Subproject commit 5d945a3a2835d2c5f05bc633e97cf5732a11ae68
+Subproject commit 14117b14a78a14e3a79a228891f765fac7718435
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index b8f23efc9cc..02c885c6dc3 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -1372,6 +1372,11 @@ class NewCSR(implicit val p: Parameters) extends Module
       diffArchEvent.exceptionInst := RegEnable(io.fromRob.trap.bits.instr, hasTrap)
     }
 
+    val diffCriticalErrorEvent = DifftestModule(new DiffCriticalErrorEvent, delay = 4, dontCare = true)
+    diffCriticalErrorEvent.valid := io.status.criticalErrorState && trapValid
+    diffCriticalErrorEvent.coreid := hartId
+    diffCriticalErrorEvent.criticalError := io.status.criticalErrorState
+
     val diffCSRState = DifftestModule(new DiffCSRState)
     diffCSRState.coreid         := hartId
     diffCSRState.privilegeMode  := privState.PRVM.asUInt
diff --git a/src/main/scala/xiangshan/backend/rob/Rob.scala b/src/main/scala/xiangshan/backend/rob/Rob.scala
index 6143cddd588..b6f2a517185 100644
--- a/src/main/scala/xiangshan/backend/rob/Rob.scala
+++ b/src/main/scala/xiangshan/backend/rob/Rob.scala
@@ -1489,11 +1489,6 @@ class RobImp(override val wrapper: Rob)(implicit p: Parameters, params: BackendP
       difftest.code := trapCode
       difftest.pc := trapPC
     }
-
-    val diffCriticalErrorEvent = DifftestModule(new DiffCriticalErrorEvent)
-    diffCriticalErrorEvent.valid := criticalErrorState && !RegNext(criticalErrorState)
-    diffCriticalErrorEvent.coreid := io.hartId
-    diffCriticalErrorEvent.criticalError := criticalErrorState
   }
 
   //store evetn difftest information
```
