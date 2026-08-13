# Commit Log
- Issue: #3894
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3894
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3894
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3894
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/backend/decode/VecDecoder.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/decode/VecDecoder.scala b/src/main/scala/xiangshan/backend/decode/VecDecoder.scala
index 1ea02eff08b..1f81784aa33 100644
--- a/src/main/scala/xiangshan/backend/decode/VecDecoder.scala
+++ b/src/main/scala/xiangshan/backend/decode/VecDecoder.scala
@@ -355,7 +355,7 @@ object VecDecoder extends DecodeConstants {
     VSSRA_VI      -> OPIVI(FuType.vialuF, VialuFixType.vssra_vv, T, F, F, selImm = SelImm.IMM_OPIVIU),
 
     VNCLIPU_WI    -> OPIVI(FuType.vialuF, VialuFixType.vnclipu_wv, T, F, T, selImm = SelImm.IMM_OPIVIU, uopSplitType = UopSplitType.VEC_WXV),
-    VNCLIP_WI     -> OPIVI(FuType.vialuF, VialuFixType.vnclip_wv, T, F, T, uopSplitType = UopSplitType.VEC_WXV),
+    VNCLIP_WI     -> OPIVI(FuType.vialuF, VialuFixType.vnclip_wv, T, F, T, selImm = SelImm.IMM_OPIVIU, uopSplitType = UopSplitType.VEC_WXV),
 
     VMV1R_V       -> OPIVI(FuType.vppu, VpermType.vmv1r, T, F, F, uopSplitType = UopSplitType.VEC_MVNR, src1 = SrcType.no), // vmv1r.v vd, vs2
     VMV2R_V       -> OPIVI(FuType.vppu, VpermType.vmv2r, T, F, F, uopSplitType = UopSplitType.VEC_MVNR, src1 = SrcType.no), // vmv2r.v vd, vs2
```
