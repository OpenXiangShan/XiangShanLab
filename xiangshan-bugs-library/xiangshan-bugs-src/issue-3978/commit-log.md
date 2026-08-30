# Commit Log
- Issue: #3978
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3978
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3978
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3978
- Changed files: 2
- Additions: 7
- Deletions: 7

## Files
- `scripts/xiangshan.py`
- `src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala`

## Diff
```diff
diff --git a/scripts/xiangshan.py b/scripts/xiangshan.py
index 79e6cc5f2c2..fc7354f8305 100644
--- a/scripts/xiangshan.py
+++ b/scripts/xiangshan.py
@@ -351,7 +351,8 @@ def __get_ci_misc(self, name=None):
             "asid/asid.bin",
             "isa_misc/xret_clear_mprv.bin",
             "isa_misc/satp_ppn.bin",
-            "cache-management/softprefetchtest-riscv64-xs.bin"
+            "cache-management/softprefetchtest-riscv64-xs.bin",
+            "smstateen/rvh_test.bin"
         ]
         misc_tests = map(lambda x: os.path.join(base_dir, x), workloads)
         return misc_tests
diff --git a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala
index 028a9b0eef6..cee170a38f8 100644
--- a/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala
+++ b/src/main/scala/xiangshan/backend/fu/NewCSR/CSRPermitModule.scala
@@ -141,8 +141,6 @@ class CSRPermitModule extends Module {
   private val rwSatp_EX_II = csrAccess && privState.isModeHS &&  tvm && (addr === CSRs.satp.U || addr === CSRs.hgatp.U)
   private val rwSatp_EX_VI = csrAccess && privState.isModeVS && vtvm && (addr === CSRs.satp.U)
 
-  private val rwCustom_EX_II = csrAccess && privState.isModeVS && csrIsCustom
-
   private val accessHPM = ren && csrIsHPM
   private val accessHPM_EX_II = accessHPM && (
     !privState.isModeM && !mcounteren(counterAddr) ||
@@ -184,9 +182,10 @@ class CSRPermitModule extends Module {
   // AIA bit 59
   private val ssAiaHaddr = Seq(CSRs.hvien.U, CSRs.hvictl.U, CSRs.hviprio1.U, CSRs.hviprio2.U)
   private val ssAiaVSaddr = addr === CSRs.vstopi.U
-  private val csrIsAIA = ssAiaHaddr.map(_ === addr).reduce(_ || _) || ssAiaVSaddr
+  private val ssAiaSaddr = addr === CSRs.stopi.U
+  private val csrIsAIA = ssAiaHaddr.map(_ === addr).reduce(_ || _) || ssAiaVSaddr || ssAiaSaddr
   private val accessAIA_EX_II = csrIsAIA && !privState.isModeM && !mstateen0.AIA.asBool
-  private val accessAIA_EX_VI = csrIsAIA && mstateen0.AIA.asBool && privState.isVirtual
+  private val accessAIA_EX_VI = ssAiaSaddr && privState.isVirtual && !hstateen0.AIA.asBool
 
   // IMSIC bit 58 (Ssaia extension)
   private val csrIsStopei = addr === CSRs.stopei.U
@@ -212,7 +211,7 @@ class CSRPermitModule extends Module {
   // [0x5c0, 0x5ff], [0x9c0, 0x9ff], [0xdc0, 0xdff]
   private val csrIsSCustom   = (addr(11, 10) =/= "b00".U) && (addr(9, 8) === "b01".U) && (addr(7, 6) === "b11".U)
   // [0x800, 0x8ff], [0xcc0, 0xcff]
-  private val csrIsUCustom   = (addr(11, 8) =/= "b1000".U) || (addr(11, 6) =/= "b100011".U)
+  private val csrIsUCustom   = (addr(11, 8) === "b1000".U) || (addr(11, 6) === "b110011".U)
   private val allCustom      = csrIsHVSCustom || csrIsSCustom || csrIsUCustom
   private val accessCustom_EX_II = allCustom && (
     !privState.isModeM && !mstateen0.C.asBool ||
@@ -266,7 +265,7 @@ class CSRPermitModule extends Module {
   // Todo: check correct
   io.out.EX_II :=  csrAccess && !privilegeLegal && (!privState.isVirtual || privState.isVirtual && csrIsM) ||
     rwIllegal || mnret_EX_II || mret_EX_II || sret_EX_II || rwSatp_EX_II || accessHPM_EX_II ||
-    rwStimecmp_EX_II || rwCustom_EX_II || fpVec_EX_II || dret_EX_II || xstateControlAccess_EX_II || rwStopei_EX_II ||
+    rwStimecmp_EX_II || fpVec_EX_II || dret_EX_II || xstateControlAccess_EX_II || rwStopei_EX_II ||
     rwMireg_EX_II || rwSireg_EX_II || rwVSireg_EX_II
   io.out.EX_VI := (csrAccess && !privilegeLegal && privState.isVirtual && !csrIsM ||
     mnret_EX_VI || mret_EX_VI || sret_EX_VI || rwSatp_EX_VI || accessHPM_EX_VI || rwStimecmp_EX_VI || rwSireg_EX_VI || rwSip_Sie_EX_VI) && !rwIllegal || xstateControlAccess_EX_VI
```
