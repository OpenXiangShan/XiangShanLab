# Commit Log
- Issue: #4412
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4412
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4412
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4412
- Changed files: 2
- Additions: 7
- Deletions: 2

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index ffad1cc0dbe..f106aa5600b 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -352,7 +352,9 @@ trait MachineLevel { self: NewCSR =>
     }).setAddr(CSRs.mhpmcounter3 - 3 + num)
   )
 
-  val mvendorid = Module(new CSRModule("Mvendorid") { rdata := 0.U })
+  val mvendorid = Module(new CSRModule("Mvendorid", new CSRBundle {
+    val ALL = RO(63, 0)
+  }))
     .setAddr(CSRs.mvendorid)
 
   // architecture id for XiangShan is 25
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index de3d5c3ce57..c9309661132 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -969,7 +969,10 @@ class NewCSR(implicit val p: Parameters) extends Module
     }
   })
 
-  private val regOut = Mux1H(csrOutMap.map { case (id, regOut) =>
+  private val rwMask = 0xc00
+  private val csrOutMapFilter = csrOutMap.filter { case (id, _) => (id & rwMask) != rwMask }
+
+  private val regOut = Mux1H(csrOutMapFilter.map { case (id, regOut) =>
     if (vsMapS.contains(id)) {
       ((isModeVS && addr === vsMapS(id).U) || !isModeVS && addr === id.U) -> regOut
     } else if (sMapVS.contains(id)) {
```
