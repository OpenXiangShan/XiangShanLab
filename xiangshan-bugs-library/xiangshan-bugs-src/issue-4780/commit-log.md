# Commit Log
- Issue: #4780
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4780
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4780
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4780
- Changed files: 1
- Additions: 1
- Deletions: 5

## Files
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index 391d24bf484..8c07899bde8 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -550,11 +550,7 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       (csr.hgatp.mode === 0.U) -> onlyStage1
     ))
     val miss_req_s2xlate_reg = RegEnable(miss_req_s2xlate, io.ptw.req(idx).fire)
-    val hasS2xlate = miss_req_s2xlate_reg =/= noS2xlate
-    val onlyS2 = miss_req_s2xlate_reg === onlyStage2
-    val hit_s1 = io.ptw.resp.bits.s1.hit(miss_req_vpn, Mux(hasS2xlate, csr.vsatp.asid, csr.satp.asid), csr.hgatp.vmid, allType = true, false, hasS2xlate)
-    val hit_s2 = io.ptw.resp.bits.s2.hit(miss_req_vpn, csr.hgatp.vmid)
-    val hit = Mux(onlyS2, hit_s2, hit_s1) && io.ptw.resp.valid && miss_req_s2xlate_reg === io.ptw.resp.bits.s2xlate
+    val hit = io.ptw.resp.bits.hit(miss_req_vpn, csr.satp.asid, csr.vsatp.asid, csr.hgatp.vmid, allType = true, ignoreAsid = false) && io.ptw.resp.valid && miss_req_s2xlate_reg === io.ptw.resp.bits.s2xlate
 
     val new_coming_valid = WireInit(false.B)
     new_coming_valid := req_in(idx).fire && !req_in(idx).bits.kill && !flush_pipe(idx)
```
