# Commit Log
- Issue: #4406
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4406
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4406
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4406
- Changed files: 2
- Additions: 8
- Deletions: 5

## Files
- `src/main/scala/xiangshan/cache/mmu/MMUConst.scala`
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUConst.scala b/src/main/scala/xiangshan/cache/mmu/MMUConst.scala
index 1609a517127..e543a5a0b7c 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUConst.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUConst.scala
@@ -110,6 +110,7 @@ trait HasTlbConst extends HasXSParameter {
   val pteFlagLen = 8
   val pteRswLen = 2
   val ptePPNLen = 44
+  val ptePaddrLen = 56
   val pteResLen = 7
   val ptePbmtLen = 2
   val pteNLen = 1
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
index 5e4ea226f7f..18cc494d652 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
@@ -177,9 +177,10 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   val to_find_pte = level === 1.U && find_pte === false.B
   val source = RegEnable(io.req.bits.req_info.source, io.req.fire)
 
-  val l3addr = Wire(UInt(PAddrBits.W))
-  val l2addr = Wire(UInt(PAddrBits.W))
-  val l1addr = Wire(UInt(PAddrBits.W))
+  val l3addr = Wire(UInt(ptePaddrLen.W))
+  val l2addr = Wire(UInt(ptePaddrLen.W))
+  val l1addr = Wire(UInt(ptePaddrLen.W))
+  val hptw_addr = Wire(UInt(ptePaddrLen.W))
   val mem_addr = Wire(UInt(PAddrBits.W))
 
   l3addr := MakeAddr(satp.ppn, getVpnn(vpn, 3))
@@ -193,7 +194,8 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     l2addr := MakeAddr(satp.ppn, getVpnn(vpn, 2))
   }
   l1addr := MakeAddr(Mux(l2Hit, ppn, pte.getPPN()), getVpnn(vpn, 1))
-  mem_addr := Mux(af_level === 3.U, l3addr, Mux(af_level === 2.U, l2addr, l1addr))
+  hptw_addr := Mux(af_level === 3.U, l3addr, Mux(af_level === 2.U, l2addr, l1addr))
+  mem_addr := hptw_addr(PAddrBits - 1, 0)
 
   val hptw_resp = Reg(new HptwResp)
 
@@ -202,7 +204,7 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
   val full_gvpn_wire = pte.getPPN()
   val full_gvpn = Mux(update_full_gvpn_mem_resp, full_gvpn_wire, full_gvpn_reg)
 
-  val gpaddr = MuxCase(mem_addr, Seq(
+  val gpaddr = MuxCase(hptw_addr, Seq(
     (stage1Hit || onlyS2xlate) -> Cat(full_gvpn, 0.U(offLen.W)),
     !s_last_hptw_req -> Cat(MuxLookup(level, pte.getPPN())(Seq(
       3.U -> Cat(pte.getPPN()(ptePPNLen - 1, vpnnLen * 3), vpn(vpnnLen * 3 - 1, 0)),
```
