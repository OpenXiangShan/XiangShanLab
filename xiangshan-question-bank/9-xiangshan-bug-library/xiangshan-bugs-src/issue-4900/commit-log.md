# Commit Log
- Issue: #4900
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4900
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4900
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4900
- Changed files: 2
- Additions: 2
- Deletions: 2

## Files
- `src/main/scala/xiangshan/cache/mmu/L2TLB.scala`
- `src/main/scala/xiangshan/cache/mmu/MMUBundle.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/L2TLB.scala b/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
index 4b4f8f31954..a991408bca8 100644
--- a/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/L2TLB.scala
@@ -699,7 +699,7 @@ class L2TLBImp(outer: L2TLB)(implicit p: Parameters) extends PtwModule(outer) wi
       ptw_resp.ppn_low := pte_in.getPPN()(sectortlbwidth - 1, 0)
       ptw_resp.level.map(_ := 0.U)
       ptw_resp.pbmt := pte_in.pbmt
-      ptw_resp.n.map(_ := pte_in.n === true.B && ptw_resp.ppn(3, 0) === 8.U)
+      ptw_resp.n.map(_ := pte_in.n === true.B && pte_in.getPPN()(3, 0) === 8.U)
       ptw_resp.perm.map(_ := pte_in.getPerm())
       ptw_resp.tag := vpn(vpnLen - 1, sectortlbwidth)
       // LLPTW will not handle onlyS2 situations
diff --git a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
index ba2dbd9735e..0e3ccfed894 100644
--- a/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
+++ b/src/main/scala/xiangshan/cache/mmu/MMUBundle.scala
@@ -1246,7 +1246,7 @@ class PtwMergeResp(implicit p: Parameters) extends PtwBundle {
     val ptw_resp = Wire(new PtwMergeEntry(tagLen = sectorvpnLen, hasPerm = true, hasLevel = true, hasNapot = true))
     ptw_resp.ppn := resp_pte.getPPN()(ptePPNLen - 1, sectortlbwidth)
     ptw_resp.ppn_low := resp_pte.getPPN()(sectortlbwidth - 1, 0)
-    ptw_resp.n.map(_ := resp_pte.n === true.B && ptw_resp.ppn(3, 0) === 8.U && level === 0.U)
+    ptw_resp.n.map(_ := resp_pte.n === true.B && resp_pte.getPPN()(3, 0) === 8.U && level === 0.U)
     ptw_resp.pbmt := resp_pte.pbmt
     ptw_resp.level.map(_ := level)
     ptw_resp.perm.map(_ := resp_pte.getPerm())
```
