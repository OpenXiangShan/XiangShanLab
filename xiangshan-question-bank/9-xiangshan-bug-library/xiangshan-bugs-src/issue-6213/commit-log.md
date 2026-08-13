# Commit Log
- Issue: #6213
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/6213
- Issue state: closed
- Tested RTL commit: -
- Related PR: #6213
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/6213
- Changed files: 3
- Additions: 26
- Deletions: 5

## Files
- `.github/workflows/emu-basics.yml`
- `scripts/xiangshan.py`
- `src/main/scala/xiangshan/frontend/ifu/Ifu.scala`

## Diff
```diff
diff --git a/.github/workflows/emu-basics.yml b/.github/workflows/emu-basics.yml
index 4a1c5b15657..28b5ab3b2b8 100644
--- a/.github/workflows/emu-basics.yml
+++ b/.github/workflows/emu-basics.yml
@@ -106,6 +106,7 @@ jobs:
           # - name: rvv-bench
           - name: f16_test
           - name: zcb-test
+          - name: crosspage-fetch-test
       fail-fast: false
       max-parallel: 4
     steps:
diff --git a/scripts/xiangshan.py b/scripts/xiangshan.py
index f2e6c7a309b..12d082a7767 100644
--- a/scripts/xiangshan.py
+++ b/scripts/xiangshan.py
@@ -533,6 +533,7 @@ def __get_ci_zcbtest(self, name=None):
         ]
         zcb_test = map(lambda x: os.path.join(base_dir, x), workloads)
         return zcb_test
+
     def __get_ci_iopmptest(self, name=None):
         base_dir = "/nfs/home/share/ci-workloads/iopmp"
         workloads = [
@@ -540,6 +541,23 @@ def __get_ci_iopmptest(self, name=None):
         ]
         iopmp_test = map(lambda x: os.path.join(base_dir, x), workloads)
         return iopmp_test
+
+    def __get_ci_crosspage_fetch_test(self, name=None):
+        base_dir = "/nfs/home/share/ci-workloads/crosspage-fetch"
+        workloads = [
+            "crosspage_pad2b_page1_exec_page2_exec-riscv64-xs.bin",       # normal
+            "crosspage_pad2b_page1_exec_page2_exec_io-riscv64-xs.bin",    # normal -> mmio
+            "crosspage_pad2b_page1_exec_page2_none-riscv64-xs.bin",       # normal -> page fault
+            "crosspage_pad2b_page1_exec_page2_io-riscv64-xs.bin",         # normal -> mmio page fault
+            "crosspage_pad2b_page1_exec_io_page2_exec-riscv64-xs.bin",    # mmio -> normal
+            "crosspage_pad2b_page1_exec_io_page2_exec_io-riscv64-xs.bin", # mmio
+            "crosspage_pad2b_page1_exec_io_page2_none-riscv64-xs.bin",    # mmio -> page fault
+            "crosspage_pad2b_page1_exec_io_page2_io-riscv64-xs.bin",      # mmio -> mmio page fault
+            "crosspage_pad2b_page1_none_page2_exec-riscv64-xs.bin",       # page fault
+            "crosspage_pad2b_page1_none_page2_exec_io-riscv64-xs.bin",    # page fault -> mmio
+        ]
+        return map(lambda x: os.path.join(base_dir, x), workloads)
+
     def __get_ci_mc(self, name=None):
         base_dir = "/nfs/home/share/ci-workloads"
         workloads = [
@@ -626,7 +644,8 @@ def run_ci(self, test):
             # "rvv-test": self.__get_ci_rvvtest,
             "f16_test": self.__get_ci_F16test,
             "zcb-test": self.__get_ci_zcbtest,
-            "iopmp-test": self.__get_ci_iopmptest
+            "iopmp-test": self.__get_ci_iopmptest,
+            "crosspage-fetch-test": self.__get_ci_crosspage_fetch_test,
         }
         for target in all_tests.get(test, self.__get_ci_workloads)(test):
             print(target)
@@ -658,7 +677,8 @@ def run_ci_vcs(self, test):
             # "rvv-test": self.__get_ci_rvvtest,
             "f16_test": self.__get_ci_F16test,
             "zcb-test": self.__get_ci_zcbtest,
-            "iopmp-test": self.__get_ci_iopmptest
+            "iopmp-test": self.__get_ci_iopmptest,
+            "crosspage-fetch-test": self.__get_ci_crosspage_fetch_test,
         }
         for target in all_tests.get(test, self.__get_ci_workloads)(test):
             print(target)
diff --git a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
index adee41abd2c..fa10af94681 100644
--- a/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
+++ b/src/main/scala/xiangshan/frontend/ifu/Ifu.scala
@@ -610,14 +610,14 @@ class Ifu(implicit p: Parameters) extends IfuModule
   private val uncachePd           = 0.U.asTypeOf(Vec(FetchBlockInstNum, new PreDecodeInfo))
   private val uncacheMisEndOffset = Wire(Valid(UInt(FetchBlockInstOffsetWidth.W)))
   uncacheMisEndOffset.valid := s2_reqIsUncache
-  uncacheMisEndOffset.bits  := Mux(uncacheIsRvc || uncacheNeedResend, 0.U, 1.U)
+  uncacheMisEndOffset.bits  := Mux(uncacheIsRvc || s2_prevEndIsHalfRvi || uncacheNeedResend, 0.U, 1.U)
 
   // Send mmioFlushWb back to FTQ 1 cycle after uncache fetch return
   // When backend redirect, mmioState reset after 1 cycle.
   // In this case, mask .valid to avoid overriding backend redirect
   private val uncacheTarget =
     Mux(
-      uncacheIsRvc || uncacheNeedResend,
+      uncacheIsRvc || s2_prevEndIsHalfRvi || uncacheNeedResend,
       s2_fetchBlock(0).startVAddr + 2.U,
       s2_fetchBlock(0).startVAddr + 4.U
     )
@@ -646,7 +646,7 @@ class Ifu(implicit p: Parameters) extends IfuModule
 
     io.toIBuffer.bits.pc(s2_alignShiftNum)                    := uncachePc
     io.toIBuffer.bits.isRvc(s2_alignShiftNum)                 := uncacheIsRvc
-    io.toIBuffer.bits.instrEndOffset(s2_alignShiftNum).offset := Mux(uncacheIsRvc, 0.U, 1.U)
+    io.toIBuffer.bits.instrEndOffset(s2_alignShiftNum).offset := Mux(uncacheIsRvc || s2_prevEndIsHalfRvi, 0.U, 1.U)
 
     io.toIBuffer.bits.exceptionType := s2_icacheMeta(0).exception || uncacheException || uncacheRvcException
     // execption can happen in next page only when cross page.
```
