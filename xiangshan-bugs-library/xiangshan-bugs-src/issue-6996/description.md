### Summary

When the same physical page is mapped at two virtual addresses that fall into different DCache alias sets, a store through one VA can be lost. This happens when an older store to the same physical line through the other VA is still in the MissQueue.

Version: `kunminghu-v3` e716c765. The code involved is unchanged at HEAD 9df38660.

### Root cause

1. The line is cached under the alias set of the primary VA. The older store (synonym VA) looks it up in the alias set of its own VA ([`get_alias`](https://github.com/OpenXiangShan/XiangShan/blob/9df386608e590e9d2e1e80d10ed5710de21f9d08/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala#L307-L318)), misses, and is accepted by an MSHR. MainPipe then sends `store_hit_resp` with `miss = 1` ([MainPipe.scala L950-L959](https://github.com/OpenXiangShan/XiangShan/blob/9df386608e590e9d2e1e80d10ed5710de21f9d08/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala#L950-L959)).
2. On that response the Sbuffer frees the entry and releases the younger same-block entry from `w_sameblock_inflight` ([Sbuffer.scala L721-L743](https://github.com/OpenXiangShan/XiangShan/blob/9df386608e590e9d2e1e80d10ed5710de21f9d08/src/main/scala/xiangshan/mem/sbuffer/Sbuffer.scala#L721-L743)).
3. The younger store (primary VA) hits the copy in the primary alias set and is written. MainPipe checks the MissQueue only through `io.replace`, which requires the same block **and** the same alias ([MissQueue.scala L345](https://github.com/OpenXiangShan/XiangShan/blob/9df386608e590e9d2e1e80d10ed5710de21f9d08/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L345), [L2056-L2060](https://github.com/OpenXiangShan/XiangShan/blob/9df386608e590e9d2e1e80d10ed5710de21f9d08/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala#L2056-L2060)). Nothing blocks this hit.
4. The MSHR's acquire probes out the copy that holds the younger data. The refill then installs the line with the old contents plus the older store, so the younger store is lost. We did not trace why the refill data does not include the probed-out younger data (L2 side). Regardless, L1 should not let the younger store complete before the older same-block MSHR.

Trace of reproducer 1 (L = PA 0x80100040; `SB DREQ` = Sbuffer request to the DCache, `MP S2` = MainPipe stage 2):

```
c=9040 SB DREQ id=0 pa=80100040 va=80200040   older sw, synonym VA
c=9042 MP S2   va=80200040 hit=0 -> MissQueue  store_hit_resp miss=1, sbuffer frees id 0
c=9046 SB DREQ id=1 pa=80100040 va=80100040   younger cbo.zero, primary VA
c=9048 MP S2   va=80100040 hit=1 -> s3 writes zeros
c=9053 MP S2   probe pa=80100040 hit=1
c=9066 MP S2   va=80200040 miss=1 refill
RESULT FAIL    L+0x0c = 0x33fbb8ce, other bytes 0xff (expected all 0)
```

### Reproducer 1: `cbo.zero`

`cboz_syn.S` + `link.ld`. VA 0x80100000 and VA 0x80200000 both map PA 0x80100000. Line L = PA 0x80100040 starts as all-ones. `s0` = primary VA of L (0x80100040), `s1` = synonym VA of L (0x80200040). VARIANT 19:

```asm
  sw    t1, 0x0c(s1)          # older store, synonym VA (t1 = 0x33fbb8ce)
  sh    x0, 0x22(s0)          # store to L, primary VA
  cbo.zero (s0)               # younger: zero L, primary VA
  fence
  # every byte of L must be 0
```

Build: `riscv64-unknown-elf-gcc -march=rv64gc -mabi=lp64d -nostdlib -nostartfiles -static -T link.ld -DVARIANT=19 cboz_syn.S -o v19.elf`. Pass: tohost (0x80001000) = 1.

| VARIANT | Sequence | XiangShan | With fix | Spike |
|---|---|---|---|---|
| 9 | `amoadd.d` L; `sw` syn; `sh` pri; `cbo.zero` pri | **FAIL** | PASS | PASS |
| 19 | `sw` syn; `sh` pri; `cbo.zero` pri | **FAIL** | PASS | PASS |
| 35 | `sb` syn ×2; `sh` pri; `cbo.zero` pri | **FAIL** | PASS | PASS |
| 12 | as 9, `sw` through the primary VA | PASS | PASS | PASS |
| 3 | `fence` before `cbo.zero` | PASS | PASS | PASS |

### Reproducer 2: plain store

`synx_50005_best.S` + `synx_link.ld`. VA 0x8007f000 and VA 0x80081000 both map PA 0x8007f000.

```asm
  # x2 = 0x80080000, x9 = 0xfffffffffffffffe, low half of f25 = 0
  addi  x15, x2, 56
  amomax.d.aq x24, x27, (x15)
  li    x12, 0x2000
  add   x12, x12, x2
  sw    x9, -169(x12)         # older, synonym VA 0x80081f57 -> PA 0x8007ff57
  fsh   f25, -168(x2)         # younger, primary VA 0x8007ff58 -> PA 0x8007ff58
```

| | dword at PA 0x8007ff58 |
|---|---|
| Spike | `54cf0f2894ff0000` |
| XiangShan | `54cf0f2894ffffff` (`fsh` lost) |
| With fix | `54cf0f2894ff0000` |

### Possible fix

In MainPipe s2, replay a store hit if an MSHR, or a request entering the MissQueue, holds the same physical block in any alias. With this change both reproducers pass, and 12 random programs that failed this way all match Spike.

<details><summary>fix.diff</summary>

```diff
diff --git a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
--- a/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
+++ b/src/main/scala/xiangshan/cache/dcache/DCacheWrapper.scala
@@ -1704,6 +1704,7 @@ class DCacheImp(outer: DCache) extends LazyModuleImp(outer) with HasDCacheParame
   missQueue.io.evict_set := mainPipe.io.evict_set
   missQueue.io.btot_ways_for_set <> mainPipe.io.btot_ways_for_set
   missQueue.io.replace <> mainPipe.io.replace
+  missQueue.io.store_block <> mainPipe.io.store_block
   val probeArb = Wire(Decoupled(new TLBundleB(edge.bundle)))
   TLArbiter.lowest(edge, probeArb, buses.map(_.b):_*)
   missQueue.io.probe.req.valid := probeArb.valid
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MainPipe.scala
@@ -211,6 +211,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
 
     // writeback addr to be replaced
     val replace = new MissQueueBlockIO
+    val store_block = new MissQueueBlockIO
 
     // sms prefetch
     val sms_agt_evict_req = DecoupledIO(new AGTEvictReq)
@@ -535,6 +536,9 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
 
   // For a store req, it either hits and goes to s3, or miss and enter miss queue immediately
   val s2_replace_block = io.replace.block && io.replace.req.valid
+  // A store from the sbuffer that hits while an MSHR holds an older miss to the same physical block (possibly in
+  // another alias set) must not complete before it: replay it.
+  val s2_store_hit_block = s2_valid && !s2_req.probe && !s2_req.miss && !s2_req.replace && s2_req.isStore && s2_hit && io.store_block.block
   val s2_req_miss_without_data = Mux(s2_valid, s2_req.miss && !io.refill_info.valid, false.B)
   val s2_can_go_to_mq_no_data = (s2_req_miss_without_data && RegEnable(s2_req_miss_without_data && !io.mainpipe_info.s2_replay_to_mq, false.B, s2_valid)) // miss_req in s2 but refill data is invalid, can block 1 cycle
   val s2_can_go_to_mq_evict_fail = s2_replace_block // refill eviction conflicts with in-flight MSHR (BtoT); replay to MQ for another evict way
@@ -544,11 +548,11 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
     Mux(
       s2_req.miss,
       io.refill_info.valid && !s2_replace_block,
-      (s2_req.isStore || s2_req.isAMO || s2_req.isPrefetch) && s2_hit
+      (s2_req.isStore || s2_req.isAMO || s2_req.isPrefetch) && s2_hit && !s2_store_hit_block
     )
   ) && s3_ready
   assert(RegNext(!(s2_valid && s2_can_go_to_s3 && s2_can_go_to_mq && s2_can_go_to_mq_replay)))
-  val s2_can_go = s2_can_go_to_s3 || s2_can_go_to_mq || s2_can_go_to_mq_replay
+  val s2_can_go = s2_can_go_to_s3 || s2_can_go_to_mq || s2_can_go_to_mq_replay || s2_store_hit_block
   val s2_fire = s2_valid && s2_can_go
   val s2_fire_to_s3 = s2_valid && s2_can_go_to_s3
   when (s1_fire) {
@@ -941,7 +945,7 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
     */
   val s2_isStore = RegEnable(s1_isStore, s1_fire)
   val s2_isAMO = RegEnable(s1_isAMO, s1_fire)
-  io.store_replay_resp.valid := s2_valid && (s2_can_go_to_mq && replay && s2_req.isStore || s2_grow_perm_fail && s2_isStore)
+  io.store_replay_resp.valid := s2_valid && (s2_can_go_to_mq && replay && s2_req.isStore || s2_grow_perm_fail && s2_isStore) || s2_store_hit_block
   io.store_replay_resp.bits.data := DontCare
   io.store_replay_resp.bits.miss := true.B // s2_can_go_to_mq && replay
   io.store_replay_resp.bits.replay := true.B // s2_grow_perm_fail
@@ -1069,6 +1073,10 @@ class MainPipe(implicit p: Parameters) extends DCacheModule with HasPerfEvents w
   io.replace.req.bits.addr := get_block_addr(Cat(s2_tag, get_untag(s2_req.vaddr)))
   io.replace.req.bits.vaddr := s2_req.vaddr
 
+  io.store_block.req.valid := s2_valid && s2_req.isStore && !s2_req.miss && !s2_req.probe
+  io.store_block.req.bits.addr := get_block_addr(s2_req.addr)
+  io.store_block.req.bits.vaddr := s2_req.vaddr
+
   io.evict_set := addr_to_dcache_set(s2_req.vaddr) // only use set index
 
   assert(!RegNext(io.tag_write.valid && !io.tag_write_intend))
diff --git a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
--- a/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
+++ b/src/main/scala/xiangshan/cache/dcache/mainpipe/MissQueue.scala
@@ -342,6 +342,10 @@ class MissReqPipeRegBundle(edge: TLEdgeOut)(implicit p: Parameters) extends DCac
     acquire
   }
 
+  def block_match_any_alias(q: MissQueueBlockReqBundle): Bool = {
+    reg_valid() && get_block(req.addr) === get_block(q.addr)
+  }
+
   def block_and_alias_match(releaseReq: MissQueueBlockReqBundle): Bool = {
     reg_valid() && get_block(req.addr) === get_block(releaseReq.addr) && req.vaddrAlias === get_alias(releaseReq.vaddr)
   }
@@ -507,6 +511,9 @@ class MissEntry(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
     // block replace when release an addr valid in mshr
     val replace = Flipped(new MissQueueBlockIO)
 
+    // block a store hit to the same physical block in any alias
+    val store_block = Flipped(new MissQueueBlockIO)
+
     val req_addr = ValidIO(UInt(PAddrBits.W))
     val req_vaddr = ValidIO(UInt(VAddrBits.W))
     val req_isBtoT = Output(Bool())
@@ -1073,6 +1080,8 @@ for(i <- 0 until reqNum) {
     get_block_addr(req.addr) === get_block_addr(io.replace.req.bits.addr) &&
     req.vaddrAlias === get_alias(io.replace.req.bits.vaddr)
 
+  io.store_block.block := req_valid && get_block_addr(req.addr) === get_block_addr(io.store_block.req.bits.addr)
+
   io.req_addr.valid := req_valid
   io.req_addr.bits:= req.addr
   io.req_vaddr.valid := req_valid
@@ -1233,6 +1242,7 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
 
     // block replace when release an addr valid in mshr
     val replace = Flipped(new MissQueueBlockIO)
+    val store_block = Flipped(new MissQueueBlockIO)
 
     // block all way for set to BtoT
     val evict_set = Input(UInt())
@@ -2059,6 +2069,12 @@ class MissQueue(edge: TLEdgeOut, reqNum: Int)(implicit p: Parameters) extends DC
       e.io.replace.block
     } ++ active_pipe_regs.map(_.block_and_alias_match(io.replace.req.bits))
   ).orR
+  io.store_block.block := Cat(
+    entries.map { e =>
+      e.io.store_block.req <> io.store_block.req
+      e.io.store_block.block
+    } ++ active_pipe_regs.map(_.block_match_any_alias(io.store_block.req.bits))
+  ).orR
 
   val btot_evict_set_hit = entries.map(e => e.io.req_isBtoT && e.io.req_vaddr.valid && addr_to_dcache_set(e.io.req_vaddr.bits) === io.evict_set) ++
     active_pipe_regs.map(_.evict_set_match(io.evict_set))
```
</details>

Reproduced with our own Verilator testbench of `XSTop` (not emu/difftest), with Spike as reference. The programs end by writing tohost and do not use the XiangShan good-trap. Under `emu` with difftest, we expect a mismatch at the checking load, but we have not run it. Files: https://gist.github.com/ttkxclr/b388ffbf586a6a30722c20b4ea793fbc.

This report was prepared with AI assistance (opencode, Claude Opus 5.5).
