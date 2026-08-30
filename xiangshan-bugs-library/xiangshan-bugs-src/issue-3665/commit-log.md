# Commit Log
- Issue: #3665
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3665
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3665
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3665
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
index 5544a06c987..9f473a57f8b 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/MachineLevel.scala
@@ -341,10 +341,10 @@ trait MachineLevel { self: NewCSR =>
     .setAddr(CSRs.mimpid)
 
   val mhartid = Module(new CSRModule("Mhartid", new CSRBundle {
-    val ALL = RO(7, 0)
+    val ALL = RO(hartIdLen - 1, 0)
   }) {
     val hartid = IO(Input(UInt(hartIdLen.W)))
-    this.reg.ALL := RegEnable(hartid, reset.asBool)
+    this.regOut.ALL := hartid
   })
     .setAddr(CSRs.mhartid)
```
