# Commit Log
- Issue: #3667
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/3667
- Issue state: closed
- Tested RTL commit: -
- Related PR: #3667
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/3667
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `Makefile`

## Diff
```diff
diff --git a/Makefile b/Makefile
index 238b8069980..0abfbbf5115 100644
--- a/Makefile
+++ b/Makefile
@@ -113,7 +113,7 @@ override SIM_ARGS += --with-constantin
 endif
 
 # emu for the release version
-RELEASE_ARGS += --fpga-platform --disable-all --remove-assert --reset-gen
+RELEASE_ARGS += --fpga-platform --disable-all --remove-assert --reset-gen --firtool-opt --ignore-read-enable-mem
 DEBUG_ARGS   += --enable-difftest
 PLDM_ARGS    += --fpga-platform --enable-difftest
 ifeq ($(RELEASE),1)
```
