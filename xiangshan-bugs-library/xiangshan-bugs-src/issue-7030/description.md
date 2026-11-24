### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

In an Sv39 page table, a 64 KiB region has an ordinary 4 KiB leaf for page 0 (PA `P` = 0x80315000) and NAPOT leaves (N = 1, PPN = 0x80308, so `Q` = 0x80300000) for pages 1-15. The program loads from page 0, loads from page 8, then stores to page 0 and to page 3. The store to page 0 stops the emu on the PtwCache assertion `normal page and super page both hit`. With an emu built with `STOP_COND=0`, the run continues. The store to page 0 writes PA 0x80310000 and the store to page 3 writes PA 0x80313000. The page table gives page 0 either `P` or `Q`, and page 3 `Q` + 0x3000. A load variant of the program reads pages 0 and 3 from the same two wrong addresses.

The page-0 load puts page 0's leaf into l0. The page-8 load puts the NAPOT leaf into sp, and that sp entry matches all 16 pages of the region. The page-0 store misses the store DTLB, and its PtwCache lookup hits both l0 and sp. In that case the response takes the PPN and the permissions from l0, but `n` from sp:

```scala
// src/main/scala/xiangshan/cache/mmu/PageTableCache.scala:808-835
      io.resp.bits.stage1.entry(i).ppn := Mux(resp_res.l0.hit, resp_res.l0.ppn(i)(gvpnLen - 1, sectortlbwidth),
      ...
      io.resp.bits.stage1.entry(i).ppn_low := Mux(resp_res.l0.hit, resp_res.l0.ppn(i)(sectortlbwidth - 1, 0),
      ...
    io.resp.bits.stage1.entry(i).n.map(_ := Mux(resp_res.sp.hit, resp_res.sp.n, 0.U))
    io.resp.bits.stage1.entry(i).perm.map(_ := Mux(resp_res.l0.hit, resp_res.l0.perm(i),  Mux(resp_res.sp.hit, resp_res.sp.perm, 0.U.asTypeOf(new PtePermBundle))))
  ...
  XSError(stageResp.valid && resp_res.l0.hit && resp_res.sp.hit, "normal page and super page both hit")
```

With bitmap checking off, the L2TLB passes `ppn` and `n` of the requested lane to the L1 TLB:

```scala
// src/main/scala/xiangshan/cache/mmu/L2TLB.scala:714-715
      when (!bitmap_enable) {
        outArb(i).in(0).bits.s1 := merge_ptwResp_to_sector_ptwResp(mergeArb(i).out.bits.s1)
// src/main/scala/xiangshan/cache/mmu/L2TLB.scala:830-832
    ptw_sector_resp.entry.ppn := pte.entry(OHToUInt(pte.pteidx)).ppn
    ...
    ptw_sector_resp.entry.n.map(_ := pte.entry(OHToUInt(pte.pteidx)).n.getOrElse(0.U))
```

The L1 TLB treats an entry with `n` set as a 64 KiB page. The entry is valid for all lanes, its tag ignores VPN[3:0], and its PPN is PPN[high:4] of the entry followed by VPN[3:0]:

```scala
// src/main/scala/xiangshan/cache/mmu/MMUBundle.scala:371-373
    this.n := inner_n
    val isSuperPage = inner_level =/= 0.U || inner_n =/= 0.U
    this.valididx := Mux(isSuperPage, VecInit(Seq.fill(tlbcontiguous)(true.B)),
// src/main/scala/xiangshan/cache/mmu/MMUBundle.scala:227
    tag_matchs(0) := Mux(n === 0.U, tag(vpnnLen - sectortlbwidth - 1, 0) === vpn(vpnnLen - 1, sectortlbwidth), tag(vpnnLen - sectortlbwidth - 1, pteNapotBits - sectortlbwidth) === vpn(vpnnLen - 1, pteNapotBits))
// src/main/scala/xiangshan/cache/mmu/MMUBundle.scala:397-399
      Mux(inner_level >= "b01".U , vpn(vpnnLen - 1, 0),
        // inner_level == "b00".U (4KB), need to check whether n is 0
        Mux(n === 0.U, Cat(ppn(vpnnLen - sectortlbwidth - 1, 0), ppn_low(vpn(sectortlbwidth - 1, 0))), Cat(ppn(vpnnLen - sectortlbwidth - 1, pteNapotBits - sectortlbwidth), vpn(pteNapotBits - 1, 0)))))
```

The entry's PPN is page 0's own PPN 0x80315. Page 0 therefore gets PPN 0x80310 (PA 0x80310000), and page 3 hits the same entry and gets PPN 0x80313 (PA 0x80313000).


### Expected behavior

Page 0 is accessed at `P` (its own PTE) or at `Q` (the NAPOT PTE), and page 3 at `Q` + 0x3000. For a region whose PTEs are inconsistent, the Svnapot chapter says that "one of the translations will be chosen, but the choice is unpredictable".

### Environment

- XiangShan: kunminghu-v2 `9c53db086a`, KunminghuV2Config, 1 core, Verilator emu, `--no-diff`. The last column of the table below is the same emu rebuilt with `STOP_COND=0` added to the `make emu` variables (assertions print, the run does not stop).

### To Reproduce

[ptwcache-double-hit.zip](https://github.com/user-attachments/files/35228050/ptwcache-double-hit.zip)

Attachment: `ptwcache-double-hit.zip`. It contains `repro.S`, `repro_st.bin` (the store sequence above), `repro_ld.bin` (load variant), the control `control.bin` (`repro_st.bin` without the page-8 load), `build.sh` (rebuilds the images with LLVM and GNU ld and checks `SHA256SUMS`), and a short README.

```bash
./build/emu -i repro_st.bin --no-diff -C 200000
./build/emu -i repro_ld.bin --no-diff -C 200000
./build/emu -i control.bin  --no-diff -C 200000
```

The program leaves an exit code in `a0` (see README). The v2 emu prints codes other than 0, 2 and 3 as `HIT BAD TRAP`, so the code is given by the trap PC.

| Image | emu | emu built with `STOP_COND=0` |
|---|---|---|
| `repro_st.bin` | `Assertion failed at .../build/rtl/PtwCache.sv:6405.` and `[ERROR][time=6520] SimTop.cpu.l_soc.core_with_l2.core.memBlock.inner.ptw.ptw.cache: normal page and super page both hit`, then `ABORT`, `cycleCnt = 6,516`. | The same `[ERROR]` line once. `HIT BAD TRAP at pc = 0x80000450` = exit code 22 after 6,881 cycles: the page-0 store went to 0x80310000, the page-3 store to 0x80313000. |
| `repro_ld.bin` | The same assertion and `[ERROR]` line at time 6501, then `ABORT`, `cycleCnt = 6,497`. | The same `[ERROR]` line once. `HIT BAD TRAP at pc = 0x800003b4` = exit code 22 after 6,779 cycles: the loads from pages 0 and 3 returned the words at 0x80310000 and 0x80313000. |
| `control.bin` | No assertion. `HIT BAD TRAP at pc = 0x80000394` = exit code 1 after 6,660 cycles: page 0 went to `P`, page 3 to `Q` + 0x3000. | Same, 6,660 cycles. |

### Additional context

_No response_
