### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

A misaligned scalar load/store that starts at the last bytes of the canonical lower half (Sv39: `0x0000_003F_FFFF_FFFC`; Sv48: `0x0000_7FFF_FFFF_FFFC`) and crosses into the non-canonical hole is executed without a page fault. The high half is translated as if its address were the *canonical upper-half alias* (`0xFFFF_FFC0_0000_0000` / `0xFFFF_8000_0000_0000`), so data is read from / written to whatever page is mapped there.

Root cause: the Sv39/Sv48 canonical check in the DTLB runs only when `checkfullva` is set, and the split pieces re-issued from the misalign buffers never set it:

- `StoreUnit.scala:218` `io.tlb.req.bits.checkfullva := s0_use_flow_rs || s0_use_flow_vec` — false for `s0_use_flow_ma`; `:190-197` the ma-flow `fullva` is just the 50-bit `s0_vaddr`.
- `LoadUnit.scala:392` `checkfullva := s0_src_select_vec(vec_iss_idx) || s0_src_select_vec(int_iss_idx)` — false for the `mab_idx` source; `:780` `s0_out.fullva := Mux(frm_mabuf, s0_out.vaddr, ...)`.
- `StoreMisalignBuffer.scala:385/427/...`, `LoadMisalignBuffer.scala:341/...`: `highAddr*.vaddr := req.vaddr + N` computed in `VAddrBits` (50 bits), so `0x3F_FFFF_FFFC + 4` becomes `0x40_0000_0000` with no upper bits to compare.
- `TLB.scala:223` `when (req(i).valid && req(i).bits.checkfullva) { prepf := pf39/pf48 }` — skipped; the PTW then indexes each level with 9-bit `vpn` slices only, so `0x40_0000_0000` walks the same PTEs as `0xFFFF_FFC0_0000_0000`.

The first issue of the instruction is checked (its start address is canonical, so it passes); only the continuation piece is non-canonical, and that piece is exactly the one exempted from the check.

### Expected behavior

`sd` at `0x3F_FFFF_FFFC` (Sv39, both pages mapped) raises store page fault with `stval = 0x0000_0040_0000_0000`; nothing is written. Same for `ld` (load page fault) and `sh` at `0x3F_FFFF_FFFF`.

Actual (XiangShan): no exception; the low 4 bytes go to `0x3F_FFFF_FFFC..FFFF`, the high 4 bytes are written to the physical page mapped at `0xFFFF_FFC0_0000_0000`. `ld` returns the concatenation of both pages. In S-mode this silently accesses the kernel-half mapping instead of faulting (from U-mode the aliased PTE has U=0, so it still faults, but for the wrong reason).

### Environment

- XiangShan branch: kunminghu-v2
- Reproduced on commit `2e148b41d5` (emu, `--no-diff` and `--diff riscv64-nemu-interpreter-so`); all cited lines are unchanged on kunminghu-v2 HEAD `5d393413` (2026-09-02).
- Reference model: NEMU (ready-to-run `riscv64-nemu-interpreter-so`) — difftest aborts at the offending store: `NEMU does not commit any store instruction` (NEMU raised the page fault, DUT committed the store).

### To Reproduce

Bare-metal nexus-am app (`misalign-canon-poc`, attached), runs in S-mode with a hand-built Sv39 page table:

- `0x0000_003F_FFFF_F000` -> P_LOW, `0x0000_003F_FFFF_E000` -> P_CTL, `0xFFFF_FFC0_0000_0000` -> P_HI (all RW), identity gigapages for code/uart.
- T1 `sd @0x40_0000_0000` (non-canonical *start*): correctly traps, cause 15, tval `0x4000000000` — shows the first-issue check works.
- T2 `sd @0x3F_FFFF_EFFC` (canonical cross-page control): OK.
- T3 `sd @0x3F_FFFF_FFFC`: **no trap**, `P_HI[0..3] = 55 66 77 88`.
- T4 `ld @0x3F_FFFF_FFFC`: **no trap**, returns `0xA3A2A1A0_B3B2B1B0` (P_LOW tail || P_HI head).
- T5 `sh @0x3F_FFFF_FFFF`: **no trap**, `P_HI[0] = 0xCD`.

```
make ARCH=riscv64-xs
./build/emu -i misalign-canon-poc-riscv64-xs.bin --no-diff                                   # -> "BUG CONFIRMED ... (mask 7)"
./build/emu -i misalign-canon-poc-riscv64-xs.bin --diff ready-to-run/riscv64-nemu-interpreter-so  # -> ABORT at T3
```

Log excerpt (`--no-diff`):

```
T1 sd @4000000000 (non-canonical start): traps=1 cause=15 tval=4000000000
T3 sd @3ffffffffc (crosses into non-canonical): traps=0 cause=0 tval=0
  P_HI [0..7]    = 55 66 77 88 22 22 22 22
  -> BUG: no page fault; high half written to the page mapped at ffffffc000000000
T4 ld @3ffffffffc (crosses into non-canonical): traps=0 cause=0 tval=0 data=a3a2a1a0b3b2b1b0
T5 sh @3fffffffff: traps=0 cause=0 tval=0 P_HI[0]=cd
```

[misalign-canon-poc-results.zip](https://github.com/user-attachments/files/31872060/misalign-canon-poc-results.zip)

### Additional context

Related (instruction-fetch side of the same failure class): #6264 (sequential fetch fall-through from `0x3F_FFFF_FFFC` into `0x40_0000_0000`, same alias to root[256]) fixed by #6340 (v3) / #6272 (v2, open); #6210 / #6235. Those fixes only add `checkfullva`/`pf.instr` for the ITLB path; the LSU misalign re-issue path (`s0_use_flow_ma` / `mab_idx`) still requests translation with `checkfullva = false`, so this data-side case is not covered by them.

Open PR #6309 (PMM only-stage-2, `fix(lsu): preserve MABuf fragment fullva`) makes the misalign-buffer fragments carry a proper XLEN `fullva` (`Cat(req.fullva(XLEN-1, VAddrBits), highAddrStore.vaddr)`), but keeps `checkfullva := s0_use_flow_rs || s0_use_flow_vec`, so the canonical check is still skipped for the fragments. On top of #6309 the fix for this issue would be to also assert `checkfullva` for the `s0_use_flow_ma` / `mab_idx` flows (and for `LoadEntrance.unalignTail` in `NewLoadUnit` on v3), so that the TLB's `pf39/pf48` check runs on the fragment's own effective address.
