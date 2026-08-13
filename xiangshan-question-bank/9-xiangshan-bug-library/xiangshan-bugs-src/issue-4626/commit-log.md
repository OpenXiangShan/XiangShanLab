# Commit Log
- Issue: #4626
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4626
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4626
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4626
- Changed files: 3
- Additions: 38
- Deletions: 3

## Files
- `.github/workflows/check_verilog.py`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CommitIDModule.scala`
- `src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala`

## Diff
```diff
diff --git a/.github/workflows/check_verilog.py b/.github/workflows/check_verilog.py
index 66bfff4532b..463df408160 100644
--- a/.github/workflows/check_verilog.py
+++ b/.github/workflows/check_verilog.py
@@ -31,7 +31,8 @@ def err(file, line, loc, msg):
             line_number = 1
             for line in f:
                 if "$fatal" in line or "$fwrite" in line:
-                    err(file, line, line_number, "'fatal' or 'fwrite' statement was found!")
+                    if "Commit SHA" not in line:
+                        err(file, line, line_number, "'fatal' or 'fwrite' statement was found!")
                 if "module Decode" in line:
                     in_decode = True
                 elif "module Dispatch" in line:
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CommitIDModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CommitIDModule.scala
index e916ff5f542..efabe181024 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CommitIDModule.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CommitIDModule.scala
@@ -1,11 +1,39 @@
 package xiangshan.backend.fu.NewCSR
 
 import chisel3._
+import chisel3.util.HasBlackBoxInline
 
 import java.util.Properties
 
-class CommitIDModule(shaWidth: Int) extends Module {
+class PrintCommitIDModule(shaWidth: Int, hartIdlen: Int) extends BlackBox with HasBlackBoxInline {
+  val io = IO(new Bundle{
+    val hartID = Input(UInt(hartIdlen.W))
+    val commitID = Input(UInt(shaWidth.W))
+    val dirty = Input(Bool())
+  })
+
+  setInline("PrintCommitIDModule.v",
+    s"""
+      |module PrintCommitIDModule(
+      |  input [${hartIdlen-1}:0] hartID,
+      |  input [${shaWidth-1}:0] commitID,
+      |  input dirty
+      |);
+      |
+      |`ifndef SYNTHESIS
+      |  initial begin
+      |    $$fwrite(32'h80000001, "Core %d's Commit SHA is: %h, dirty: %d\\n", hartID, commitID, dirty);
+      |  end
+      |`endif
+      |
+      |endmodule
+      |""".stripMargin
+  )
+}
+
+class CommitIDModule(shaWidth: Int, hartIdlen: Int) extends Module {
   val io = IO(new Bundle {
+    val hartId = Input(UInt(hartIdlen.W))
     val commitID = Output(UInt(shaWidth.W))
     val dirty    = Output(Bool())
   })
@@ -21,4 +49,9 @@ class CommitIDModule(shaWidth: Int) extends Module {
 
   io.commitID := BigInt(sha, 16).U(shaWidth.W)
   io.dirty := dirty.U
+
+  val printCommitIDMod = Module(new PrintCommitIDModule(shaWidth, hartIdlen))
+  printCommitIDMod.io.hartID := io.hartId
+  printCommitIDMod.io.commitID := io.commitID
+  printCommitIDMod.io.dirty := io.dirty
 }
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
index ebfdb8451f3..4912ce1bb1d 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/NewCSR.scala
@@ -291,8 +291,9 @@ class NewCSR(implicit val p: Parameters) extends Module
 
   val permitMod = Module(new CSRPermitModule)
   val sstcIRGen = Module(new SstcInterruptGen)
-  val commidIdMod = Module(new CommitIDModule(40))
+  val commidIdMod = Module(new CommitIDModule(40, hartIdLen))
 
+  commidIdMod.io.hartId := io.fromTop.hartId
   val gitCommitSHA = WireInit(commidIdMod.io.commitID)
   val gitDirty     = WireInit(commidIdMod.io.dirty)
   dontTouch(gitCommitSHA)
```
