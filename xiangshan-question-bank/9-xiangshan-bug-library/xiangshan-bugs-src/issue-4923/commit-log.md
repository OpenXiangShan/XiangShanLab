# Commit Log
- Issue: #4923
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4923
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4923
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4923
- Changed files: 1
- Additions: 3
- Deletions: 0

## Files
- `src/main/scala/xiangshan/package.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/package.scala b/src/main/scala/xiangshan/package.scala
index cd9d6a50bf8..0d1c5a9d611 100644
--- a/src/main/scala/xiangshan/package.scala
+++ b/src/main/scala/xiangshan/package.scala
@@ -839,6 +839,7 @@ package object xiangshan {
     // def singleStep          = 14
     def storePageFault      = 15
     def doubleTrap          = 16
+    def softwareCheck       = 18
     def hardwareError       = 19
     def instrGuestPageFault = 20
     def loadGuestPageFault  = 21
@@ -862,6 +863,7 @@ package object xiangshan {
     def EX_LPF    = loadPageFault
     def EX_SPF    = storePageFault
     def EX_DT     = doubleTrap
+    def EX_SWC    = softwareCheck
     def EX_HWE    = hardwareError
     def EX_IGPF   = instrGuestPageFault
     def EX_LGPF   = loadGuestPageFault
@@ -890,6 +892,7 @@ package object xiangshan {
       instrPageFault,
       instrGuestPageFault,
       instrAccessFault,
+      softwareCheck,
       illegalInstr,
       virtualInstr,
       instrAddrMisaligned,
```
