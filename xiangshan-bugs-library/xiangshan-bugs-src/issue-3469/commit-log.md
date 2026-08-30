# Commit Log
- Issue: #3469
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3469
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3469
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3469
- Changed files: 2
- Additions: 3
- Deletions: 8

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index a9079d2bd8f..1ad3801d3bb 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -164,7 +164,7 @@ trait MachineLevel { self: NewCSR =>
     .setAddr(CSRs.mcountinhibit)
 
   val mhpmevents: Seq[CSRModule[_]] = (3 to 0x1F).map(num =>
-    Module(new CSRModule(s"Mhpmevent$num", new MhpmeventBundle) with HasPerfEventBundle {
+    Module(new CSRModule(s"Mhpmevent$num") with HasPerfEventBundle {
       regOut := this.perfEvents(num - 3)
     })
       .setAddr(CSRs.mhpmevent3 - 3 + num)
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 8072d9a8ffc..9a9ea93321f 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -925,11 +925,6 @@ class NewCSR(implicit val p: Parameters) extends Module
    * perf number: 29 (frontend 8, ctrlblock 8, memblock 8, huancun 5)
    */
   // tmp: mhpmevents is wrapper of perfEvents, read/write/update mhpmevents -> read/write/update perfEvents
-  for (i <-0 until perfCntNum) {
-    when(mhpmevents(i).w.wen) {
-      perfEvents(i) := wdata
-    }
-  }
   val csrevents = perfEvents.slice(24, 29)
 
   val hcEvents = Wire(Vec(numPCntHc * coreParams.L2NBanks, new PerfEvent))
@@ -966,8 +961,8 @@ class NewCSR(implicit val p: Parameters) extends Module
         ofFromPerfCntVec(i) := m.toMhpmeventOF
       case _ =>
     }
-    perfEvents(i)   := (perfEvents(i).head(1).asBool || ofFromPerfCntVec(i)) ## perfEvents(i).tail(1)
-    lcofiReqVec(i)  := ofFromPerfCntVec(i) && !mhpmevents(i).rdata.head(1)
+    perfEvents(i)  := Mux(mhpmevents(i).w.wen, wdata, (perfEvents(i).head(1).asBool || ofFromPerfCntVec(i)) ## perfEvents(i).tail(1))
+    lcofiReqVec(i) := ofFromPerfCntVec(i) && !mhpmevents(i).rdata.head(1)
   }
 
   val lcofiReq = lcofiReqVec.asUInt.orR
```
