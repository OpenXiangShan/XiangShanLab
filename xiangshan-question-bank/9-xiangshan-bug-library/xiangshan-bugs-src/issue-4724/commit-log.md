# Commit Log
- Issue: #4724
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4724
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4724
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4724
- Changed files: 2
- Additions: 3
- Deletions: 7

## Files
- `src/main/scala/xiangshan/backend/fu/PMA.scala`
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/PMA.scala b/src/main/scala/xiangshan/backend/fu/PMA.scala
index 67eceeb9ce8..66426779c40 100644
--- a/src/main/scala/xiangshan/backend/fu/PMA.scala
+++ b/src/main/scala/xiangshan/backend/fu/PMA.scala
@@ -210,14 +210,11 @@ trait PMAMethod extends PMAConst {
 trait PMACheckMethod extends PMPConst {
   def pma_check(cmd: UInt, cfg: PMPConfig) = {
     val resp = Wire(new PMPRespBundle)
-    resp.ld := TlbCmd.isRead(cmd) && !TlbCmd.isAmo(cmd) && !cfg.r
-    resp.st := (TlbCmd.isWrite(cmd) || TlbCmd.isAmo(cmd) && cfg.atomic) && !cfg.w
+    resp.ld := TlbCmd.isRead(cmd) && !cfg.r
+    resp.st := Mux(TlbCmd.isAmo(cmd), !cfg.atomic || !cfg.w, Mux(TlbCmd.isWrite(cmd), !cfg.w, false.B))
     resp.instr := TlbCmd.isExec(cmd) && !cfg.x
     //TODO We require that a `PMA` can generate an mmio response only if the address has the appropriate `PMA` permissions.
-    resp.mmio := !cfg.c &&
-                 (TlbCmd.isRead(cmd) && cfg.r ||
-                 (TlbCmd.isWrite(cmd) || TlbCmd.isAmo(cmd) && cfg.atomic) && cfg.w ||
-                 TlbCmd.isExec(cmd) && cfg.x)
+    resp.mmio := !cfg.c && !(resp.ld || resp.st || resp.instr)
     resp.atomic := cfg.atomic
     resp
   }
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index 61c8a470777..8ac3c1dd2b3 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -392,7 +392,6 @@ object TlbCmd {
   def isWrite(a: UInt) = a(1,0)===write
   def isExec(a: UInt) = a(1,0)===exec
 
-  def isAtom(a: UInt) = a(2)
   def isAmo(a: UInt) = a===atom_write // NOTE: sc mixed
 }
```
