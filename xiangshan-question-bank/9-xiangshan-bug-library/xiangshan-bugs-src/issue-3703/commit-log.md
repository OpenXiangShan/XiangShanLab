# Commit Log
- Issue: #3703
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3703
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3703
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3703
- Changed files: 2
- Additions: 5
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index 247ea9bdcfe..71ba47c0684 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -206,6 +206,8 @@ class NewCSR(implicit val p: Parameters) extends Module
     val toDecode = new CSRToDecode
 
     val fetchMalTval = Input(UInt(XLEN.W))
+
+    val distributedWenLegal = Output(Bool())
   })
 
   val toAIA   = IO(Output(new CSRToAIABundle))
@@ -1241,6 +1243,8 @@ class NewCSR(implicit val p: Parameters) extends Module
     henvcfg.regOut.CBIE === EnvCBIE.Flush && (isModeVS || isModeVU)
   )
 
+  io.distributedWenLegal := wenLegal
+
   // Always instantiate basic difftest modules.
   if (env.AlwaysBasicDiff || env.EnableDifftest) {
     // Delay trap passed to difftest until VecExcpMod is not busy
diff --git a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
index a41b906e6cd..dd38329a2a5 100644
--- a/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/wrapper/CSR.scala
@@ -339,7 +339,7 @@ class CSR(cfg: FuConfig)(implicit p: Parameters) extends FuncUnit(cfg)
       custom.wfi_enable               := csrMod.io.status.custom.wfi_enable
       // distribute csr write signal
       // write to frontend and memory
-      custom.distribute_csr.w.valid := csrWen
+      custom.distribute_csr.w.valid := csrMod.io.distributedWenLegal
       custom.distribute_csr.w.bits.addr := addr
       custom.distribute_csr.w.bits.data := wdata
       // rename single step
```
