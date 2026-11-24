### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

Bitmap checking is on (`mbmc.BME = 1`, `CMODE = 0`). Two 8-page leaf sectors are mapped page by page: S0 is VA 0x80600000-0x80607fff -> PA 0x80300000-0x80307fff, and S1 is VA 0x80608000-0x8060ffff -> PA 0x80308000-0x8030ffff. The bitmap denies page 5 of each sector (PA 0x80305000 and 0x8030d000). The program loads from S0 page 0 and stores to S1 page 2; each walk checks only the requested page. It then issues a store to S0 page 0 and a load from S1 page 2 together, and both miss their DTLBs. After that, a store to S0 page 5 and a load from S1 page 5 should both raise access faults. In `repro_ld.bin`, the load from S1 page 5 does not fault and returns the word at PA 0x8030d000. In `repro_st.bin`, where the load address is ready one `addi` later, the store to S0 page 5 completes without a fault. With a fence between the store and the load of the pair (`control.bin`), both target accesses fault.

For an l0 hit, PtwCache computes the bitmap vector `l0cfs` (1 = the lane has not passed the bitmap check) from registers that are loaded when a request enters the check stage (`stageDelay(1).fire`):

```scala
// src/main/scala/xiangshan/cache/mmu/PageTableCache.scala:528
  val (l0Hit, l0HitData, l0Pre, l0eccError, l0HitWay, l0BitmapCheckResult, l0JmpBitmapCheck) = {
// src/main/scala/xiangshan/cache/mmu/PageTableCache.scala:553-566
    val hitVec = RegEnable(hitVec_delay, stageDelay(1).fire)
    ...
    val hitWay = ParallelPriorityMux(hitVec zip (0 until l2tlbParams.l0nWays).map(_.U(log2Up(l2tlbParams.l0nWays).W)))
    ...
      l0bitmapreg := RegEnable(RegEnable(l0BitmapReg(ridx), stageReq.fire), stageDelay(1).fire)
// src/main/scala/xiangshan/cache/mmu/PageTableCache.scala:598
    (hit, hitWayData, hitWayData.prefetch, eccError, UIntToOH(hitWay), l0bitmapreg(hitWay), jmp_bitmap_check)
// src/main/scala/xiangshan/cache/mmu/PageTableCache.scala:605-610
  val l0cfs = WireInit(VecInit(Seq.fill(tlbcontiguous)(false.B))) // L0 lavel Bitmap Check Failed Vector
  ...
      l0cfs(i) := !l0BitmapCheckResult(i)
```

`resp_res` latches this vector with the rest of the hit result when the request leaves the check stage, and the walker path (`toFsm`) reads the latched copy. The stage1 response reads `l0cfs` directly. When the next request has already entered the check stage, the stage1 response therefore carries that request's vector:

```scala
// src/main/scala/xiangshan/cache/mmu/PageTableCache.scala:692-696
  check_res.l0.apply(l0Hit, ... cfs = l0cfs)
  ...
  val resp_res = Reg(new PageCacheRespBundle)
  when (stageCheck(1).fire) { resp_res := check_res }
// src/main/scala/xiangshan/cache/mmu/PageTableCache.scala:739
    io.resp.bits.toFsm.bitmapCheck.get.cfs := resp_res.l0.bitmapCheck.get.cfs
// src/main/scala/xiangshan/cache/mmu/PageTableCache.scala:829
    io.resp.bits.stage1.entry(i).cf := Mux(resp_res.l0.hit, l0cfs(i), false.B) // L0 lavel Bitmap Check Failed Vector
```

With bitmap checking on, the L2TLB builds the sector for the L1 TLB with `ptwResp_to_sector_4kptwResp`. A lane is marked valid when it matches the requested lane, `cf` included:

```scala
// src/main/scala/xiangshan/cache/mmu/L2TLB.scala:713-719
      val bitmap_enable = mbmc.BME === 1.U && mbmc.CMODE === 0.U
      when (!bitmap_enable) {
      ...
      } .otherwise {
        when (mergeArb(i).out.bits.s2xlate === noS2xlate || mergeArb(i).out.bits.s2xlate === onlyStage1) {
          outArb(i).in(0).bits.s1 := ptwResp_to_sector_4kptwResp(mergeArb(i).out.bits.s1)
// src/main/scala/xiangshan/cache/mmu/L2TLB.scala:874-881
      val ppn_equal = pte.entry(i).ppn === pte.entry(OHToUInt(pte.pteidx)).ppn
      ...
      val cf_equal = pte.entry(i).cf === pte.entry(OHToUInt(pte.pteidx)).cf
      ptw_sector_resp.valididx(i) := (ppn_equal && pbmt_equal && perm_equal && v_equal && af_equal && pf_equal && cf_equal) && !pte.not_merge
```

The L1 TLB entry keeps no bitmap state. A later access to any valid lane hits the entry and is not sent to the L2TLB:

```scala
// src/main/scala/xiangshan/cache/mmu/MMUBundle.scala:220
    val addr_low_hit = valididx(vpn(2, 0))
```

From the code above, take the load's l0 hit on S1 page 2 while the store's lookup of S0 page 0 is in the check stage. The latched vector of S1 has lane 2 clear and lanes 0, 1 and 3-7 set. The stage1 response carries S0's vector instead: lane 0 clear, lanes 1-7 set. The requested lane 2 then has `cf` = 1, like lanes 1 and 3-7, so the load DTLB receives S1 with lanes 1-7 valid, lane 5 included. The load from S1 page 5 hits this entry. `repro_st.bin` has the roles swapped: the store's response for S0 page 0 carries S1's vector, and the store DTLB receives S0 with lane 5 valid.

### Expected behavior

The DTLB receives only lanes whose bitmap check has passed, and both target accesses raise access faults, as they do in `control.bin`.

### Environment

- XiangShan: kunminghu-v2 `9c53db086a`, KunminghuV2Config, 1 core, Verilator emu.
- Difftest reference: NEMU master `de1cb67b`, `riscv64-xs-ref_defconfig` with `CONFIG_RV_MBMC=y` 

### To Reproduce

[ptwcache-l0-cf.zip](https://github.com/user-attachments/files/35228182/ptwcache-l0-cf.zip)

Attachment: `ptwcache-l0-cf.zip`. It contains `repro.S`, `repro_ld.bin`, `repro_st.bin`, the control `control.bin` (fence between the two accesses of the pair), `build.sh` (rebuilds the images with LLVM and GNU ld and checks `SHA256SUMS`), and a short README.

```bash
./build/emu -i repro_ld.bin --no-diff -C 200000
./build/emu -i repro_st.bin --no-diff -C 200000
./build/emu -i control.bin  --no-diff -C 200000
# with difftest, NEMU built with CONFIG_RV_MBMC=y:
./build/emu -i repro_ld.bin --diff <nemu-mbmc>/build/riscv64-nemu-interpreter-so -C 400000
```

The program leaves an exit code in `a0`: 2 * st_ok + ld_ok, where st_ok (ld_ok) = 1 means the store (load) to page 5 did not fault. The v2 emu prints codes 0, 1, 2 and 3 as `HIT GOOD TRAP`, `HIT BAD TRAP`, `ABORT` and `EXCEEDING CYCLE/INSTR LIMIT`; the trap PC identifies the code (README).

| Image | `--no-diff` |
|---|---|
| `repro_ld.bin` | `HIT BAD TRAP at pc = 0x800002ac` = exit code 1 after 7,017 cycles. The load from S1 page 5 returned the word at PA 0x8030d000 without a fault. The store to S0 page 5 faulted. |
| `repro_st.bin` | `ABORT at pc = 0x800002c8` = exit code 2 after 7,055 cycles, with no assertion message. The store to S0 page 5 completed without a fault. The load from S1 page 5 faulted. |
| `control.bin` | `HIT GOOD TRAP at pc = 0x8000029c` = exit code 0 after 7,159 cycles. Both target accesses faulted. |

With difftest (reference with the bitmap check):

| Image | With difftest |
|---|---|
| `repro_ld.bin` | The reference reports `isa mbmc check failed, vaddr=0x000000008060d000, paddr=0x000000008030d000, len=0x8, type=0x1` and takes the access fault; the DUT does not. Difftest stops after 6,935 cycles: `a3 different at pc = 0x0080000094, right = 0x0000000000000000, wrong = 0x5a5a00008030d000`, followed by `mode`, `mstatus` and `mepc` differences. |
| `repro_st.bin` | `Mismatch for store commits` after 6,796 cycles (`ABORT`): the DUT commits the store to `0x80305000` (S0 page 5) at pc `0x80000240`; the reference does not commit it. |
| `control.bin` | No mismatch; `HIT GOOD TRAP at pc = 0x8000029c` after 7,159 cycles. |

### Additional context

_No response_
