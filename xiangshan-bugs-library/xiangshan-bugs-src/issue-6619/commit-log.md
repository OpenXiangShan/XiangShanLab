# Commit Log
- Issue: #6619
- Issue URL: https://github.com/OpenXiangShan/XiangShan/issues/6619
- Issue state: closed
- Tested RTL commit: 4e0288460d483e6048000a13fa7825f0f81d8e38
- Related PR: #6016
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6016
- Changed files: 5
- Additions: 11
- Deletions: 7

## Files
- `src/main/scala/xiangshan/backend/fu/PMA.scala`
- `src/main/scala/xiangshan/backend/fu/PMP.scala`
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`
- `src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/fu/PMA.scala b/src/main/scala/xiangshan/backend/fu/PMA.scala
index ae7d37575eb..8aa536ee456 100644
--- a/src/main/scala/xiangshan/backend/fu/PMA.scala
+++ b/src/main/scala/xiangshan/backend/fu/PMA.scala
@@ -210,9 +210,9 @@ trait PMAMethod extends PMAConst {
 trait PMACheckMethod extends PMPConst {
   def pma_check(cmd: UInt, cfg: PMPConfig) = {
     val resp = Wire(new PMPRespBundle)
-    resp.ld := TlbCmd.isRead(cmd) && !cfg.r
+    resp.ld := (TlbCmd.isRead(cmd) || TlbCmd.isReadExec(cmd)) && !cfg.r
     resp.st := Mux(TlbCmd.isAmo(cmd), !cfg.atomic || !cfg.w, Mux(TlbCmd.isWrite(cmd), !cfg.w, false.B))
-    resp.instr := TlbCmd.isExec(cmd) && !cfg.x
+    resp.instr := (TlbCmd.isExec(cmd) || TlbCmd.isReadExec(cmd)) && !cfg.x
     resp.mmio := !cfg.c
     resp.atomic := cfg.atomic
     resp
diff --git a/src/main/scala/xiangshan/backend/fu/PMP.scala b/src/main/scala/xiangshan/backend/fu/PMP.scala
index e7159a9839e..596efe41ac0 100644
--- a/src/main/scala/xiangshan/backend/fu/PMP.scala
+++ b/src/main/scala/xiangshan/backend/fu/PMP.scala
@@ -403,9 +403,9 @@ class PMPRespBundle(implicit p: Parameters) extends PMPBundle {
 trait PMPCheckMethod extends PMPConst {
   def pmp_check(cmd: UInt, cfg: PMPConfig) = {
     val resp = Wire(new PMPRespBundle)
-    resp.ld := TlbCmd.isRead(cmd) && !TlbCmd.isAmo(cmd) && !cfg.r
+    resp.ld := (TlbCmd.isRead(cmd) || TlbCmd.isReadExec(cmd)) && !TlbCmd.isAmo(cmd) && !cfg.r
     resp.st := (TlbCmd.isWrite(cmd) || TlbCmd.isAmo(cmd)) && !cfg.w
-    resp.instr := TlbCmd.isExec(cmd) && !cfg.x
+    resp.instr := (TlbCmd.isExec(cmd) || TlbCmd.isReadExec(cmd)) && !cfg.x
     resp.mmio := false.B
     resp.atomic := false.B
     resp
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index 503984760e5..5db7014b56f 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -420,11 +420,13 @@ object TlbCmd {
 
   def atom_read  = "b100".U // lr
   def atom_write = "b101".U // sc / amo
+  def read_exec  = "b111".U // HLVX final physical permission check
 
   def apply() = UInt(3.W)
   def isRead(a: UInt) = a(1,0)===read
   def isWrite(a: UInt) = a(1,0)===write
   def isExec(a: UInt) = a(1,0)===exec
+  def isReadExec(a: UInt) = a===read_exec
 
   def isAmo(a: UInt) = a===atom_write // NOTE: sc mixed
 }
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index 63122c8d7c8..429377fafba 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -255,7 +255,8 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
   (0 until Width).foreach{i =>
     val noTranslateReg = RegNext(req(i).bits.no_translate)
     val addr = Mux(noTranslateReg, req(i).bits.pmp_addr, pmp_addr(i))
-    pmp_check(addr, req_out(i).size, req_out(i).cmd, noTranslateReg, i)
+    val pmpCmd = Mux(req_out(i).hlvx, TlbCmd.read_exec, req_out(i).cmd)
+    pmp_check(addr, req_out(i).size, pmpCmd, noTranslateReg, i)
     for (d <- 0 until nRespDups) {
       pbmt_check(i, d, pbmt(i)(d), g_pbmt(i)(d), req_out_s2xlate(i))
       perm_check(perm(i)(d), req_out(i).cmd, i, d, g_perm(i)(d), req_out(i).hlvx, req_out_s2xlate(i), prepf(i), pregpf(i), preaf(i))
@@ -605,7 +606,8 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
         pbmt_check(idx, d, io.ptw.resp.bits.s1.entry.pbmt, io.ptw.resp.bits.s2.entry.pbmt, s2xlate)
         perm_check(stage1, req_out(idx).cmd, idx, d, stage2, req_out(idx).hlvx, s2xlate)
       }
-      pmp_check(resp(idx).bits.paddr(0), req_out(idx).size, req_out(idx).cmd, false.B, idx)
+      val pmpCmd = Mux(req_out(idx).hlvx, TlbCmd.read_exec, req_out(idx).cmd)
+      pmp_check(resp(idx).bits.paddr(0), req_out(idx).size, pmpCmd, false.B, idx)
 
       // NOTE: the unfiltered req would be handled by Repeater
     }
diff --git a/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala b/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
index 01e1f3e02af..bd3112f66f1 100644
--- a/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/NewLoadUnit.scala
@@ -907,7 +907,7 @@ class LoadUnitS2(param: ExeUnitParams)(
     uop.exceptionVec(loadPageFault) ||
     uop.exceptionVec(loadGuestPageFault)
   val tlbAccessable = !tlbUnaccessable
-  val pmpUnaccessable = pmp.ld && tlbHit
+  val pmpUnaccessable = (pmp.ld || (pmp.instr && LSUOpType.isHlvx(uop.fuOpType))) && tlbHit
 
   val isNC = tlbHit && tlbAccessable && Pbmt.isNC(pbmt)
   val isMMIO = tlbHit && tlbAccessable && (Pbmt.isIO(pbmt) || Pbmt.isPMA(pbmt) && pmp.mmio)
```
