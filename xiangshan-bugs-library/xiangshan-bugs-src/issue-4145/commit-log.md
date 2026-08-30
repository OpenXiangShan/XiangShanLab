# Commit Log
- Issue: #4145
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4145
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4145
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4145
- Changed files: 1
- Additions: 3
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
index be12ac88177..0ade0e8fd56 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/HypervisorLevel.scala
@@ -54,7 +54,7 @@ trait HypervisorLevel { self: NewCSR =>
   })
     .setAddr(CSRs.hie)
 
-  val htimedelta = Module(new CSRModule("Htimedelta"))
+  val htimedelta = Module(new CSRModule("Htimedelta", new Htimedelta))
     .setAddr(CSRs.htimedelta)
 
   val hcounteren = Module(new CSRModule("Hcounteren", new Counteren))
@@ -349,6 +349,8 @@ class HEnvCfg extends EnvCfg {
   }
 }
 
+class Htimedelta extends FieldInitBundle
+
 trait HypervisorBundle { self: CSRModule[_] =>
   val hstatus = IO(Input(new HstatusBundle))
 }
```
