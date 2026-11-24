### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

When a refill arrives in an L2TLB flush cycle (`sfence.vma`, `satp` change, ...), the clock gate blocks the SRAM write, yet `bcv` still marks the way valid. The code is in `BitmapCheck.scala` at v3 `8b15f06520`:

```scala
// L452
val bc_masked_clock = ClockGate(te.cgen, stageReq.fire | (!flush && io.refill.valid) | mbistBC.map(_.mbist.req).getOrElse(false.B), clock)
// L503-L515
when (io.refill.valid) {
  bitmapcache.io.w.apply(...)
  bitmapReplace.access(bcRefillIdx, bcVictimWay)
  bcv := bcv | bcRfvOH
}
when (bitmap_cache_clear === 1.U) {
  bcv := 0.U
}
```

The way thus becomes valid with whatever it held before. BCLEAR clears only `bcv`, and after a BCLEAR the victim is the first empty way. That old content is therefore typically a word cached before the BCLEAR.

The register version refilled only under `when (!flush && rf_vd)`. #5346 converted the cache to SRAM and kept `!flush` only in the clock gate. The change reached v3 as `9cc79e33e` and v2 as `12d8be7ed`.

**Impact:** Bit 1 marks a secure page. Software revokes a page in the order the UnityChip Shield-XS page [Bitmap 与L2TLB交互](https://open-verify.cc/UnityChipForXiangShan/docs/98_ut/03_memblock/02_bitmap/modules/02_bm_l2tlb/) (Bitmap and L2TLB interaction) prescribes: set the bit, `sfence` and `hfence` everything, then write BCLEAR. After that, any later L2TLB flush that lands in the same cycle as a refill into an empty way revives the pre-BCLEAR word. That flush can be an `sfence.vma` for an unrelated address. The page then stays accessible in non-secure mode (CMODE = 0) without an access fault.

### Expected behavior

The XiangShan user and design guides do not cover bitmap. The UnityChip Shield-XS pages define the behavior:

- [机器模式Bitmap控制状态寄存器](https://open-verify.cc/UnityChipForXiangShan/docs/98_ut/03_memblock/02_bitmap/feature/00_bm_csr/) (machine-mode bitmap CSR):
  - With BME = 1 and CMODE = 0, an access to a page whose bit is 1 raises an access fault.
  - BCLEAR = 1 refreshes all Shield-bit copies.
- [Bitmap 与L2TLB交互](https://open-verify.cc/UnityChipForXiangShan/docs/98_ut/03_memblock/02_bitmap/modules/02_bm_l2tlb/) (Bitmap and L2TLB interaction), refresh section: software must `sfence` and `hfence` all L1/L2TLB entries first, then raise BCLEAR.

After this revocation, a non-secure load from the page must therefore raise a load access fault. In addition, a refill in a flush cycle must update both the data and `bcv`, or neither of them.

### Environment

- Repo
  - v3, DefaultConfig: `e716c76582`. 
  - v2, KunminghuV2Config: `f86f50b834`. 

### To Reproduce

The attachment contains the images and a README.

[bcv-e716c76.tar.gz](https://github.com/user-attachments/files/34851538/bcv-e716c76.tar.gz)
The test runs in M-mode and makes translated accesses through MPRV with MPP = S, using Sv39, with CMODE = 0. Pages P and Q share the bitmap word W. Each iteration k = 0..639 runs these steps:

1. Set W to allow P, write BCLEAR, execute `sfence.vma`, then `ld P`. The load caches W.
2. Revoke P in the documented refresh order: set W to deny P, `fence rw,rw`, `sfence.vma x0,x0`, `hfence.vvma x0,x0`, `hfence.gvma x0,x0`, then write BCLEAR.
3. Run `prefetch.r Q` (its page walk reads W), then k `addi`, then one more `sfence.vma`. In `bcv-u1` this `sfence.vma` targets an unrelated address.
4. `ld P` must raise an access fault. If it does not, the iteration counts as a stale hit.

Core code (the main loop of `bcv.S`, expanded for d1; this part is identical in d0 and d1, and `check_ld` is at `0x80000188` in both):

```asm
# s0 = VA_P (0x40000000)   s1 = VA_Q (0x40200000)   s2 = &W (PA 0x100010020)
# s3 = k   s4 = 640   s5 = stale hits   s8 = MPRV (1 << 17)
# MBMC_CLR = 0x100000006: BMA = TestBMA, BME = 1, CMODE = 0, plus BCLEAR
loop:
    # 1. P is a non-secure page; clear the bitmap cache and the translations
    sd    zero, 0(s2)
    fence rw, rw
    li    t0, MBMC_CLR
    csrw  0xbc2, t0                 # mbmc
    sfence.vma
    # 2. ld P through MPRV (MPP = S): the bitmap check misses and caches W (P allowed) in way 0
    li    t0, 3 << 11
    csrc  mstatus, t0
    li    t0, (1 << 11) | MPRV
    csrs  mstatus, t0
    ld    t1, 0(s0)
    csrc  mstatus, s8
    li    t2, 0x1111
    bne   t1, t2, end_setup
    # 3. revoke P in the documented refresh order
    li    t0, 1
    sd    t0, 0(s2)                 # W = 1: P becomes a secure page
    fence rw, rw
    sfence.vma x0, x0
    hfence.vvma x0, x0
    hfence.gvma x0, x0
    li    t0, MBMC_CLR
    csrw  0xbc2, t0                 # BCLEAR
    # 4. after the revocation: the walk for Q reads W; one more sfence.vma after k addi
    csrs  mstatus, s8
    prefetch.r 0(s1)                # retires on the TLB miss, the walk continues
    mv    t3, s3
    beqz  t3, 3f
2:  addi  t3, t3, -1
    bnez  t3, 2b
3:  sfence.vma                      # u1: li t4, VA_U; sfence.vma t4, x0
    # 5. ld P must raise a load access fault (the handler sets s7 and skips the load)
    li    s7, 0
check_ld:
    ld    t1, 0(s0)
    csrc  mstatus, s8
    bnez  s7, next
    addi  s5, s5, 1                 # stale hit: the secure page was read (d0 exits, d1 prints H<k>)
    ...
next:
    addi  s3, s3, 1
    bltu  s3, s4, loop

mtrap:                              # accepts only a load access fault on check_ld
    csrr  t0, mcause
    li    t1, 5
    bne   t0, t1, end_other
    csrr  t0, mepc
    lla   t1, check_ld
    bne   t0, t1, end_other
    li    s7, 1
    addi  t0, t0, 4
    csrw  mepc, t0
    mret
```

| Image | Result on v3 `e716c76582` |
|---|---|
| `bcv-d0.bin` (stops at the first hit) | stale hit |
| `bcv-d1.bin` (counts hits) | 1 stale hit, at k = 68 |
| `bcv-d2.bin` (no prefetch) | 0 / 640 |
| `bcv-u1.bin` (unrelated-address `sfence.vma`) | 1 stale hit, at k = 60 |
| `bcv-d0.bin`, difftest | diverges at `ld P` in iteration k = 68: the reference raises a load access fault (mcause 5), the DUT does not |
| `bcv-d2.bin`, difftest | all 446,119 instructions match |


### Additional context

_No response_
