# Commit Log
- Issue: #3510
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3510
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3510
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3510
- Changed files: 1
- Additions: 3
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
index 692c07fbd24..36b8894aca3 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableWalker.scala
@@ -283,7 +283,9 @@ class PTW()(implicit p: Parameters) extends XSModule with HasPtwConst with HasPe
     pte_valid := false.B
     req_s2xlate := io.req.bits.req_info.s2xlate
     when(io.req.bits.req_info.s2xlate =/= noS2xlate && io.req.bits.req_info.s2xlate =/= onlyStage1){
-      when(io.req.bits.req_info.s2xlate === onlyStage2 && gvpn_gpf){
+      val onlys2_gpaddr = Cat(io.req.bits.req_info.vpn, 0.U(offLen.W)) // is 50 bits, don't need to check high bits when sv48x4 is enabled
+      val check_gpa_high_fail = Mux(io.req.bits.req_info.s2xlate === onlyStage2 && io.csr.hgatp.mode === Sv39x4, onlys2_gpaddr(onlys2_gpaddr.getWidth - 1, GPAddrBitsSv39x4) =/= 0.U, false.B)
+      when(io.req.bits.req_info.s2xlate === onlyStage2 && check_gpa_high_fail){
         mem_addr_update := true.B
         last_s2xlate := false.B
       }.otherwise{
```
