# Commit Log
- Issue: #3681
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3681
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3681
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3681
- Changed files: 1
- Additions: 12
- Deletions: 2

## Files
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
index 9042320f49a..fd01ec7dc33 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
@@ -242,6 +242,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
 
   when (io.req.fire && !io.req.bits.stage1Hit){
     val req = io.req.bits
+    val gvpn_wire = Wire(UInt(ptePPNLen.W))
     if (EnableSv48) {
       when (mode === Sv48) {
         level := Mux(req.l2Hit, 1.U, Mux(req.l3Hit.get, 2.U, 3.U))
@@ -249,12 +250,14 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
         gpf_level := Mux(req.l2Hit, 2.U, Mux(req.l3Hit.get, 3.U, 0.U))
         ppn := Mux(req.l2Hit || req.l3Hit.get, io.req.bits.ppn, satp.ppn)
         l3Hit := req.l3Hit.get
+        gvpn_wire := Mux(req.l2Hit || req.l3Hit.get, io.req.bits.ppn, satp.ppn)
       } .otherwise {
         level := Mux(req.l2Hit, 1.U, 2.U)
         af_level := Mux(req.l2Hit, 1.U, 2.U)
         gpf_level := 0.U
         ppn := Mux(req.l2Hit, io.req.bits.ppn, satp.ppn)
         l3Hit := false.B
+        gvpn_wire := Mux(req.l2Hit, io.req.bits.ppn, satp.ppn)
       }
     } else {
       level := Mux(req.l2Hit, 1.U, 2.U)
@@ -262,6 +265,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
       gpf_level := 0.U
       ppn := Mux(req.l2Hit, io.req.bits.ppn, satp.ppn)
       l3Hit := false.B
+      gvpn_wire := Mux(req.l2Hit, io.req.bits.ppn, satp.ppn)
     }
     vpn := io.req.bits.req_info.vpn
     l2Hit := req.l2Hit
@@ -281,8 +285,14 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
         s_last_hptw_req := false.B
       }
     }.elsewhen(io.req.bits.req_info.s2xlate === allStage){
-      need_last_s2xlate := true.B
-      s_hptw_req := false.B
+      val allstage_gpaddr = Cat(gvpn_wire, 0.U(offLen.W))
+      val check_gpa_high_fail = Mux(io.csr.hgatp.mode === Sv39x4, allstage_gpaddr(allstage_gpaddr.getWidth - 1, GPAddrBitsSv39x4) =/= 0.U, Mux(io.csr.hgatp.mode === Sv48x4, allstage_gpaddr(allstage_gpaddr.getWidth - 1, GPAddrBitsSv48x4) =/= 0.U, false.B))
+      when(check_gpa_high_fail){
+        mem_addr_update := true.B
+      }.otherwise{
+        need_last_s2xlate := true.B
+        s_hptw_req := false.B
+      }
     }.otherwise {
       need_last_s2xlate := false.B
       s_pmp_check := false.B
```
