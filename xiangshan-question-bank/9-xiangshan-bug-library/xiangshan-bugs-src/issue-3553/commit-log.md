# Commit Log
- Issue: #3553
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3553
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3553
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3553
- Changed files: 5
- Additions: 13
- Deletions: 9

## Files
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`
- `src/main/scala/xiangshan/cache/mmu/MMUConst.scala`
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala`
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`
- `src/main/scala/xiangshan/cache/mmu/TLBStorage.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index ddc23295943..2754fa5f211 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -69,6 +69,7 @@ class TlbPMBundle(implicit p: Parameters) extends TlbBundle {
 class TlbPermBundle(implicit p: Parameters) extends TlbBundle {
   val pf = Bool() // NOTE: if this is true, just raise pf
   val af = Bool() // NOTE: if this is true, just raise af
+  val v = Bool() // if stage1 pte is fake_pte, v is false
   // pagetable perm (software defined)
   val d = Bool()
   val a = Bool()
@@ -82,6 +83,7 @@ class TlbPermBundle(implicit p: Parameters) extends TlbBundle {
     val ptePerm = item.entry.perm.get.asTypeOf(new PtePermBundle().cloneType)
     this.pf := item.pf
     this.af := item.af
+    this.v := item.v
     this.d := ptePerm.d
     this.a := ptePerm.a
     this.g := ptePerm.g
@@ -97,6 +99,7 @@ class TlbPermBundle(implicit p: Parameters) extends TlbBundle {
     val ptePerm = item.entry.perm.get.asTypeOf(new PtePermBundle().cloneType)
     this.pf := item.gpf
     this.af := item.gaf
+    this.v := DontCare
     this.d := ptePerm.d
     this.a := ptePerm.a
     this.g := ptePerm.g
@@ -116,6 +119,7 @@ class TlbPermBundle(implicit p: Parameters) extends TlbBundle {
 class TlbSectorPermBundle(implicit p: Parameters) extends TlbBundle {
   val pf = Bool() // NOTE: if this is true, just raise pf
   val af = Bool() // NOTE: if this is true, just raise af
+  val v = Bool() // if stage1 pte is fake_pte, v is false
   // pagetable perm (software defined)
   val d = Bool()
   val a = Bool()
@@ -129,6 +133,7 @@ class TlbSectorPermBundle(implicit p: Parameters) extends TlbBundle {
     val ptePerm = item.entry.perm.get.asTypeOf(new PtePermBundle().cloneType)
     this.pf := item.pf
     this.af := item.af
+    this.v := item.v
     this.d := ptePerm.d
     this.a := ptePerm.a
     this.g := ptePerm.g
@@ -1109,7 +1114,7 @@ class PtwSectorResp(implicit p: Parameters) extends PtwBundle {
   }
 
   def isFakePte() = {
-    !pf && !entry.v
+    !pf && !entry.v && !af
   }
 
   def hit(vpn: UInt, asid: UInt, vmid: UInt, allType: Boolean = false, ignoreAsid: Boolean = false, s2xlate: Bool): Bool = {
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUConst.scala b/src/main/scala/xiangshan/cache/mmu/MMUConst.scala
index 9163f57895e..e851b8f80d0 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUConst.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUConst.scala
@@ -186,6 +186,7 @@ trait HasTlbConst extends HasXSParameter {
     val ptePerm = hptwResp.entry.perm.get.asTypeOf(new PtePermBundle().cloneType)
     tp.pf := hptwResp.gpf
     tp.af := hptwResp.gaf
+    tp.v := DontCare
     tp.d := ptePerm.d
     tp.a := ptePerm.a
     tp.g := ptePerm.g
@@ -201,6 +202,7 @@ trait HasTlbConst extends HasXSParameter {
     val ptePerm = ptwResp.entry.perm.get.asTypeOf(new PtePermBundle().cloneType)
     tp.pf := ptwResp.pf
     tp.af := ptwResp.af
+    tp.v := ptwResp.entry.v
     tp.d := ptePerm.d
     tp.a := ptePerm.a
     tp.g := ptePerm.g
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
index c83a971c4da..96769fbc506 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
@@ -185,11 +185,6 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   val pte_valid = RegInit(false.B)  // avoid l1tlb pf from stage1 when gpf happens in the first s2xlate in PTW
   val fake_pte = 0.U.asTypeOf(new PteBundle())
   fake_pte.perm.v := false.B // tell L1TLB this is fake pte
-  fake_pte.perm.r := true.B
-  fake_pte.perm.w := true.B
-  fake_pte.perm.x := true.B
-  fake_pte.perm.a := true.B
-  fake_pte.perm.d := true.B
   fake_pte.ppn := ppn(ppnLen - 1, 0)
   fake_pte.ppn_high := ppn(ptePPNLen - 1, ppnLen)
 
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index 9b86f470479..82240ff7ca6 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -149,7 +149,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       pbmt_check(i, d, pbmt(i)(d), g_pbmt(i)(d), req_out_s2xlate(i))
       perm_check(perm(i)(d), req_out(i).cmd, i, d, g_perm(i)(d), req_out(i).hlvx, req_out_s2xlate(i))
     }
-    hasGpf(i) := resp(i).bits.excp(0).gpf.ld || resp(i).bits.excp(0).gpf.st || resp(i).bits.excp(0).gpf.instr
+    hasGpf(i) := hitVec(i) && (resp(i).bits.excp(0).gpf.ld || resp(i).bits.excp(0).gpf.st || resp(i).bits.excp(0).gpf.instr)
   }
 
   // handle block or non-block io
@@ -295,7 +295,8 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     val ldPf = (ldPermFail || pf) && (TlbCmd.isRead(cmd) && !TlbCmd.isAmo(cmd))
     val stPf = (stPermFail || pf) && (TlbCmd.isWrite(cmd) || TlbCmd.isAmo(cmd))
     val instrPf = (instrPermFail || pf) && TlbCmd.isExec(cmd)
-    val s1_valid = portTranslateEnable(idx) && !onlyS2
+    val isFakePte = !perm.v && !pf && !perm.af
+    val s1_valid = portTranslateEnable(idx) && !onlyS2 && !isFakePte
 
     // Stage 2 perm check
     val gpf = g_perm.pf
@@ -308,7 +309,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     val ldGpf = (g_ldPermFail || gpf) && (TlbCmd.isRead(cmd) && !TlbCmd.isAmo(cmd))
     val stGpf = (g_stPermFail || gpf) && (TlbCmd.isWrite(cmd) || TlbCmd.isAmo(cmd))
     val instrGpf = (g_instrPermFail || gpf) && TlbCmd.isExec(cmd)
-    val s2_valid = hasS2xlate && !onlyS1 && portTranslateEnable(idx)
+    val s2_valid =  portTranslateEnable(idx) && hasS2xlate && !onlyS1
 
     val fault_valid = s1_valid || s2_valid
 
diff --git a/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala b/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala
index 5ffe8688396..a5ceb9a3607 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala
@@ -384,6 +384,7 @@ class TlbStorageWrapper(ports: Int, q: TLBParameters, nDups: Int = 1)(implicit p
       rp.bits.ppn(d) := p.bits.ppn(d)
       rp.bits.perm(d).pf := p.bits.perm(d).pf
       rp.bits.perm(d).af := p.bits.perm(d).af
+      rp.bits.perm(d).v := p.bits.perm(d).v
       rp.bits.perm(d).d := p.bits.perm(d).d
       rp.bits.perm(d).a := p.bits.perm(d).a
       rp.bits.perm(d).g := p.bits.perm(d).g
```
