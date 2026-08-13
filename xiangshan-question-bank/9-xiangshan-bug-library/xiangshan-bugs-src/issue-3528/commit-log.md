# Commit Log
- Issue: #3528
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3528
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3528
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3528
- Changed files: 1
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index 8b45379665e..14e5cf0f7a4 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -347,8 +347,8 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
     val ptw_resp_bits_reg = RegEnable(ptw.resp.bits, ptw.resp.valid)
     val ptw_already_back = GatedValidRegNext(ptw.resp.fire) && req_s2xlate === ptw_resp_bits_reg.s2xlate && ptw_resp_bits_reg.hit(get_pn(req_out(idx).vaddr), io.csr.satp.asid, io.csr.vsatp.asid, io.csr.hgatp.vmid, allType = true)
     val ptw_getGpa = req_need_gpa && hitVec(idx)
-    io.ptw.req(idx).valid := req_out_v(idx) && missVec(idx) && !(ptw_just_back || ptw_already_back) && !(req_out_v(idx) && need_gpa && !resp_gpa_refill && ptw_getGpa) // TODO: remove the regnext, timing
-    io.tlbreplay(idx) := req_out_v(idx) && missVec(idx) && (ptw_just_back || ptw_already_back)
+    io.ptw.req(idx).valid := req_out_v(idx) && missVec(idx) && !(ptw_just_back || ptw_already_back || (req_out_v(idx) && need_gpa && !resp_gpa_refill && ptw_getGpa)) // TODO: remove the regnext, timing
+    io.tlbreplay(idx) := req_out_v(idx) && missVec(idx) && (ptw_just_back || ptw_already_back || (req_out_v(idx) && need_gpa && !resp_gpa_refill && ptw_getGpa))
     when (io.requestor(idx).req_kill && GatedValidRegNext(io.requestor(idx).req.fire)) {
       io.ptw.req(idx).valid := false.B
       io.tlbreplay(idx) := true.B
```
