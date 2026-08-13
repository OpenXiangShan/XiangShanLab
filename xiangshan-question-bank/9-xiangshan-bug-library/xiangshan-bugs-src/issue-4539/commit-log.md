# Commit Log
- Issue: #4539
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4539
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4539
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4539
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/mmu/PageTableCache.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala b/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
index ca4cb0d5f00..076f47e16e8 100644
--- a/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
+++ b/src/main/scala/xiangshan/cache/mmu/PageTableCache.scala
@@ -1212,7 +1212,7 @@ class PtwCache()(implicit p: Parameters) extends XSModule with HasPtwConst with
         spv := spv & ~(sphhit & VecInit(sp.map(_.hit(hfenceg_gvpn, 0.U, 0.U, sfence_dup(0).bits.id, ignoreAsid = true, s2xlate = false.B))).asUInt)
       }.otherwise {
         l0v := l0v & ~(l0hhit & l0vmidhit & l0vpnhit & l0flushMask)
-        spv := spv & ~(~spg & sphhit & VecInit(sp.map(_.hit(hfenceg_gvpn, 0.U, 0.U, sfence_dup(0).bits.id, ignoreAsid = true, s2xlate = true.B))).asUInt)
+        spv := spv & ~(sphhit & VecInit(sp.map(_.hit(hfenceg_gvpn, 0.U, 0.U, sfence_dup(0).bits.id, ignoreAsid = true, s2xlate = true.B))).asUInt)
       }
     }
   }
```
