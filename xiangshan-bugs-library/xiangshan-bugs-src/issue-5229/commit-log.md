# Commit Log
- Issue: #5229
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5229
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5229
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5229
- Changed files: 1
- Additions: 3
- Deletions: 3

## Files
- `src/main/scala/xiangshan/cache/mmu/TLBStorage.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala b/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala
index 8b116b5a566..1b4232d3514 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLBStorage.scala
@@ -126,9 +126,9 @@ class TLBFA(
     // Sector tlb may trigger multi-hit, see def "wbhit"
     XSPerfAccumulate(s"port${i}_multi_hit", !(!resp.valid || (PopCount(hitVecReg) === 0.U || PopCount(hitVecReg) === 1.U)))
 
-    resp.valid := GatedValidRegNext(req.valid)
+    resp.valid := RegNext(req.valid)
     resp.bits.hit := Cat(hitVecReg).orR
-    val reqVpn   = RegEnable(vpn, 0.U, req.fire)
+    val reqVpn   = RegNext(vpn)
     val pbmt     = entries.map(_.pbmt)
     val gpbmt    = entries.map(_.g_pbmt)
     val perm     = entries.map(_.perm)
@@ -325,7 +325,7 @@ class TLBFakeFA(
     val pf = helper.pf
     val level = helper.level
 
-    resp.valid := GatedValidRegNext(req.valid)
+    resp.valid := RegNext(req.valid)
     resp.bits.hit := true.B
     for (d <- 0 until nDups) {
       resp.bits.perm(d).pf := pf
```
