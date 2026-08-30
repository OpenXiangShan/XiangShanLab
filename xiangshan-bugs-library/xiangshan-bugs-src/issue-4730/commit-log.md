# Commit Log
- Issue: #4730
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4730
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4730
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4730
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
index 2812775172d..d9a91e2eb51 100644
--- a/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
+++ b/src/main/scala/xiangshan/mem/pipeline/StoreUnit.scala
@@ -192,7 +192,7 @@ class StoreUnit(implicit p: Parameters) extends XSModule
     io.misalign_stin.bits.mask,
     Mux(
       s0_use_flow_rs,
-      genVWmask128(s0_saddr, s0_uop.fuOpType(2,0)),
+      Mux(s0_isCbo, Fill(VLEN/8, 1.U(1.W)), genVWmask128(s0_saddr, s0_uop.fuOpType(2,0))),
       Mux(
         s0_use_flow_vec,
         s0_vecstin.mask,
```
