# Commit Log
- Issue: #4541
- Issue URL: https://github.com/OpenXiangShan/XiangShan/pull/4541
- Issue state: closed
- Tested RTL commit: -
- Related PR: #4541
- PR URL: https://github.com/OpenXiangShan/XiangShan/pull/4541
- Changed files: 1
- Additions: 1
- Deletions: 1

## Files
- `src/main/scala/xiangshan/cache/mmu/L2TlbPrefetch.scala`

## Diff
```diff
diff --git a/src/main/scala/xiangshan/cache/mmu/L2TlbPrefetch.scala b/src/main/scala/xiangshan/cache/mmu/L2TlbPrefetch.scala
index fa4d34c6a69..7cf1a214147 100644
--- a/src/main/scala/xiangshan/cache/mmu/L2TlbPrefetch.scala
+++ b/src/main/scala/xiangshan/cache/mmu/L2TlbPrefetch.scala
@@ -41,7 +41,7 @@ class L2TlbPrefetch(implicit p: Parameters) extends XSModule with HasPtwConst {
     Cat(old_reqs.zip(old_v).map{ case (o,v) => dup(o,vpn) && v}).orR
   }
 
-  val flush = io.sfence.valid || (io.csr.priv.virt && io.csr.vsatp.changed)
+  val flush = io.sfence.valid || io.csr.satp.changed || io.csr.vsatp.changed || io.csr.hgatp.changed
   val next_line = get_next_line(io.in.bits.vpn)
   val next_req = RegEnable(next_line, io.in.valid)
   val input_valid = io.in.valid && !flush && !already_have(next_line)
```
