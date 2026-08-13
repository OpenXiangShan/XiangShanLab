# Commit Log
- Issue: #4024
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4024
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4024
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4024
- Changed files: 2
- Additions: 12
- Deletions: 13

## Files
- `src/main/scala/xiangshan/backend/decode/VecDecoder.scala`
- `src/main/scala/xiangshan/package.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/backend/decode/VecDecoder.scala b/src/main/scala/xiangshan/backend/decode/VecDecoder.scala
index 1f81784aa33..37641910797 100644
--- a/src/main/scala/xiangshan/backend/decode/VecDecoder.scala
+++ b/src/main/scala/xiangshan/backend/decode/VecDecoder.scala
@@ -382,17 +382,17 @@ object VecDecoder extends DecodeConstants {
 
     VMACC_VV     -> OPMVV(T, FuType.vimac, VimacType.vmacc, F, T, F, UopSplitType.VEC_VVV),
     VMADD_VV     -> OPMVV(T, FuType.vimac, VimacType.vmadd, F, T, F, UopSplitType.VEC_VVV),
-    VMAND_MM     -> OPMVV(T, FuType.vialuF, VialuFixType.vmand_mm, F, T, F, UopSplitType.VEC_MMM),
-    VMANDN_MM    -> OPMVV(T, FuType.vialuF, VialuFixType.vmandn_mm, F, T, F, UopSplitType.VEC_MMM),
-    VMNAND_MM    -> OPMVV(T, FuType.vialuF, VialuFixType.vmnand_mm, F, T, F, UopSplitType.VEC_MMM),
-    VMNOR_MM     -> OPMVV(T, FuType.vialuF, VialuFixType.vmnor_mm, F, T, F, UopSplitType.VEC_MMM),
-    VMOR_MM      -> OPMVV(T, FuType.vialuF, VialuFixType.vmor_mm, F, T, F, UopSplitType.VEC_MMM),
-    VMORN_MM     -> OPMVV(T, FuType.vialuF, VialuFixType.vmorn_mm, F, T, F, UopSplitType.VEC_MMM),
-    VMXNOR_MM    -> OPMVV(T, FuType.vialuF, VialuFixType.vmxnor_mm, F, T, F, UopSplitType.VEC_MMM),
-    VMXOR_MM     -> OPMVV(T, FuType.vialuF, VialuFixType.vmxor_mm, F, T, F, UopSplitType.VEC_MMM),
-    VMSBF_M      -> OPMVV(T, FuType.vipu, VipuType.vmsbf_m, F, T, F, UopSplitType.VEC_M0M, src1 = SrcType.no), // vmsbf.m vd, vs2, vm
-    VMSIF_M      -> OPMVV(T, FuType.vipu, VipuType.vmsif_m, F, T, F, UopSplitType.VEC_M0M, src1 = SrcType.no), // vmsif.m vd, vs2, vm
-    VMSOF_M      -> OPMVV(T, FuType.vipu, VipuType.vmsof_m, F, T, F, UopSplitType.VEC_M0M, src1 = SrcType.no), // vmsof.m vd, vs2, vm
+    VMAND_MM     -> OPMVV(T, FuType.vialuF, VialuFixType.vmand_mm, F, T, F, UopSplitType.dummy),
+    VMANDN_MM    -> OPMVV(T, FuType.vialuF, VialuFixType.vmandn_mm, F, T, F, UopSplitType.dummy),
+    VMNAND_MM    -> OPMVV(T, FuType.vialuF, VialuFixType.vmnand_mm, F, T, F, UopSplitType.dummy),
+    VMNOR_MM     -> OPMVV(T, FuType.vialuF, VialuFixType.vmnor_mm, F, T, F, UopSplitType.dummy),
+    VMOR_MM      -> OPMVV(T, FuType.vialuF, VialuFixType.vmor_mm, F, T, F, UopSplitType.dummy),
+    VMORN_MM     -> OPMVV(T, FuType.vialuF, VialuFixType.vmorn_mm, F, T, F, UopSplitType.dummy),
+    VMXNOR_MM    -> OPMVV(T, FuType.vialuF, VialuFixType.vmxnor_mm, F, T, F, UopSplitType.dummy),
+    VMXOR_MM     -> OPMVV(T, FuType.vialuF, VialuFixType.vmxor_mm, F, T, F, UopSplitType.dummy),
+    VMSBF_M      -> OPMVV(T, FuType.vipu, VipuType.vmsbf_m, F, T, F, UopSplitType.dummy, src1 = SrcType.no), // vmsbf.m vd, vs2, vm
+    VMSIF_M      -> OPMVV(T, FuType.vipu, VipuType.vmsif_m, F, T, F, UopSplitType.dummy, src1 = SrcType.no), // vmsif.m vd, vs2, vm
+    VMSOF_M      -> OPMVV(T, FuType.vipu, VipuType.vmsof_m, F, T, F, UopSplitType.dummy, src1 = SrcType.no), // vmsof.m vd, vs2, vm
     VMUL_VV      -> OPMVV(T, FuType.vimac, VimacType.vmul, F, T, F, UopSplitType.VEC_VVV),
     VMULH_VV     -> OPMVV(T, FuType.vimac, VimacType.vmulh, F, T, F, UopSplitType.VEC_VVV),
     VMULHSU_VV   -> OPMVV(T, FuType.vimac, VimacType.vmulhsu, F, T, F, UopSplitType.VEC_VVV),
diff --git a/src/main/scala/xiangshan/package.scala b/src/main/scala/xiangshan/package.scala
index 69e1127ee70..0d28a1e8042 100644
--- a/src/main/scala/xiangshan/package.scala
+++ b/src/main/scala/xiangshan/package.scala
@@ -799,13 +799,12 @@ package object xiangshan {
     def VEC_VFM          = "b111011".U // VEC_VFM
     def VEC_VFRED        = "b111100".U // VEC_VFRED
     def VEC_VFREDOSUM    = "b111101".U // VEC_VFREDOSUM
-    def VEC_M0M          = "b000000".U // VEC_M0M
-    def VEC_MMM          = "b000000".U // VEC_MMM
     def VEC_MVNR         = "b000100".U // vmvnr
     
     def AMO_CAS_W        = "b110101".U // amocas_w
     def AMO_CAS_D        = "b110110".U // amocas_d
     def AMO_CAS_Q        = "b110111".U // amocas_q
+    // dummy means that the instruction is a complex instruction but uop number is 1
     def dummy     = "b111111".U
 
     def X = BitPat("b000000")
```
