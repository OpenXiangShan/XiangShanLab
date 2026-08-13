# Commit Log
- Issue: #4997
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4997
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4997
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4997
- Changed files: 1
- Additions: 2
- Deletions: 0

## Files
- `src/main/scala/xiangshan/cache/mmu/TLB.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/TLB.scala b/src/main/scala/xiangshan/cache/mmu/TLB.scala
index e91acea3142..b923897ea48 100644
--- a/src/main/scala/xiangshan/cache/mmu/TLB.scala
+++ b/src/main/scala/xiangshan/cache/mmu/TLB.scala
@@ -160,6 +160,8 @@ class TLB(Width: Int, nRespDups: Int = 1, Block: Seq[Boolean], q: TLBParameters)
       pmm := 0.U
     } .elsewhen (premode(i) === ModeM) {
       pmm := csr.pmm.mseccfg
+    } .elsewhen (Mux(virt_in || req_in(i).bits.hyperinst, csr.priv.vmxr || csr.priv.mxr, csr.priv.mxr)) {
+      pmm := 0.U
     } .elsewhen (!(virt_in || req_in(i).bits.hyperinst) && premode(i) === ModeS) {
       pmm := csr.pmm.menvcfg
     } .elsewhen ((virt_in || req_in(i).bits.hyperinst) && premode(i) === ModeS) {
```
