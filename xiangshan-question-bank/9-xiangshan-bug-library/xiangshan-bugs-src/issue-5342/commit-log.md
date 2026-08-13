# Commit Log
- Issue: #5342
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/5342
- Issue state: closed
- Tested RTL commit: -
- Related PR: #5342
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/5342
- Changed files: 1
- Additions: 17
- Deletions: 0

## Files
- `.github/CODEOWNERS`

## Diff
```diff
diff --git a/.github/CODEOWNERS b/.github/CODEOWNERS
index 84795593055..f4035719c41 100644
--- a/.github/CODEOWNERS
+++ b/.github/CODEOWNERS
@@ -6,6 +6,23 @@ src/main/scala/xiangshan/frontend/icache/ @ngc7331 @Gao-Zeyu
 src/main/scala/xiangshan/frontend/ifu/ @ngc7331 @my-mayfly @Gao-Zeyu
 src/main/scala/xiangshan/frontend/instruncache/ @ngc7331 @Gao-Zeyu
 
+src/main/scala/xiangshan/backend/ @lewislzh
+src/main/scala/xiangshan/backend/Region.scala @xiaofeibao-xjtu @lewislzh
+src/main/scala/xiangshan/backend/CtrlBlock.scala @wissygh @lewislzh
+src/main/scala/xiangshan/backend/ctrlblock/ @wissygh @lewislzh
+src/main/scala/xiangshan/backend/datapath/ @xiaofeibao-xjtu @lewislzh
+src/main/scala/xiangshan/backend/decode/ @HeiHuDie @lewislzh
+src/main/scala/xiangshan/backend/dispatch/ @xiaofeibao-xjtu @lewislzh
+src/main/scala/xiangshan/backend/exu/ @sinceforYy @lewislzh
+src/main/scala/xiangshan/backend/fu/ @sinceforYy @lewislzh
+src/main/scala/xiangshan/backend/fu/NewCSR/ @huxuan0307 @lewislzh
+src/main/scala/xiangshan/backend/issue/ @xiaofeibao-xjtu @lewislzh
+src/main/scala/xiangshan/backend/regcache/ @xiaofeibao-xjtu @lewislzh
+src/main/scala/xiangshan/backend/regfile/ @xiaofeibao-xjtu @lewislzh
+src/main/scala/xiangshan/backend/rename/ @Tang-Haojin @lewislzh
+src/main/scala/xiangshan/backend/rob/ @NewPaulWalker @xiaofeibao-xjtu @lewislzh
+src/main/scala/xiangshan/backend/trace/ @wissygh @lewislzh
+
 src/main/scala/xiangshan/cache/ @linjuanZ
 src/main/scala/xiangshan/cache/dcache/ @Maxpicca-Li @linjuanZ
 src/main/scala/xiangshan/cache/mmu/ @good-circle @cebarobot @linjuanZ
```
