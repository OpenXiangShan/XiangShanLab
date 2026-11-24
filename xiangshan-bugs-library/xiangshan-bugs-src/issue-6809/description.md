### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

(Reproduced at `f85a1f71d`. `src/main/scala/xiangshan/cache/mmu/TLBStorage.scala` is unchanged on `kunminghu-v3` HEAD
`b55bd03fb`; the code quoted below is at the same lines there.)

When more than one entry of the fully associative L1 TLB storage (`TLBFA`) hits the same VPN, the read port returns the
bitwise OR of the hitting entries' PPNs and permission bits. The read data is selected with `Mux1H` on the raw hit
vector, and `Mux1H` is an AND-OR, so it is only correct when the hit vector is one-hot
(`TLBStorage.scala`, lines 125 and 155-160):

```scala
    val hitVecReg = RegEnable(hitVec, req.fire)
    // Sector tlb may trigger multi-hit, see def "wbhit"
    XSPerfAccumulate(s"port${i}_multi_hit", !(!resp.valid || (PopCount(hitVecReg) === 0.U || PopCount(hitVecReg) === 1.U)))
    ...
        resp.bits.ppn(d) := Mux1H(hitVecReg zip entries.map(_.genPPN(saveLevel, resp.valid)(reqVpn)))
        resp.bits.pbmt(d) := Mux1H(hitVecReg zip pbmt)
        resp.bits.g_pbmt(d) := Mux1H(hitVecReg zip gpbmt)
        resp.bits.perm(d) := Mux1H(hitVecReg zip perm)
        resp.bits.g_perm(d) := Mux1H(hitVecReg zip gPerm)
        resp.bits.s2xlate(d) := Mux1H(hitVecReg zip s2xLate)
```

The perf counter on the line above shows that more than one hit is expected to happen; when it does, the translation
is neither entry's. In the generated Verilog (`TLBFA_1`, the load DTLB `dtlb_ld_tlb_ld` storage) this is, for example:

```verilog
wire [35:0] io_r_resp_0_bits_ppn_0_0 = _io_r_resp_0_bits_ppn_0_T | _io_r_resp_0_bits_ppn_0_T_1
                                     | _io_r_resp_0_bits_ppn_0_T_2 | _io_r_resp_0_bits_ppn_0_T_3;
// _io_r_resp_0_bits_ppn_0_T_k = hitVecReg_k ? <entry k PPN> : 0
```

One way software reaches a double hit is a global mapping that differs between address spaces. The program below
(`multihit_main3.S`, attached) does this in M-mode with `mstatus.MPRV` = 1 and `MPP` = S, so its loads are translated
with Sv39 at S privilege:

- table R1: VA `0x40003000` -> global (G = 1) 4 KiB leaf -> PA `0x8000C000` (filled with `0xAAAA...`) = mapping A
- table R2: the same VA is inside a non-global 2 MiB leaf -> PA `0x80003000` (filled with `0xBBBB...`) = mapping B
- PA `0x8000F000` (= `0x8000C000 | 0x80003000`) is filled with `0xCCCC...`; no PTE maps it

```asm
  li s0, 0x40003000               # v
  li s1, 0x40008000               # v': another 4 KiB page of the same 2 MiB region
  ld a0, 0(s0)                    # walk under R1: the load DTLB caches the global 4 KiB entry of v
  li t0, 0x800010000008000B       # satp R2: Sv39, ASID 1 (same ASID), root PPN 0x8000B
  csrw satp, t0
  li t3, 1
  sfence.vma x0, t3               # flush ASID 1, keep global entries
  ld a1, 0(s1)                    # walk under R2 for v': the DTLB caches the 2 MiB entry, which also covers v
  and t4, a1, x0
  add s2, s0, t4                  # v again, address dependent on the v' load
  ld a2, 0(s2)                    # v now hits both entries
```

| | XiangShan (`f85a1f71d`) | hit vector made one-hot (see below) |
|---|---|---|
| first `ld v` (`mtval`) | `0xAAAAAAAAAAAAAAAA` | `0xAAAAAAAAAAAAAAAA` |
| `ld v'` (`stval`) | `0x000000005A5A5A5A` | `0x000000005A5A5A5A` |
| second `ld v` (`sscratch`) | **`0xCCCCCCCCCCCCCCCC`** (PA `0x8000F000`) | `0xAAAAAAAAAAAAAAAA` (mapping A) |
| traps | none | none |

The second `ld v` reads PA `0x8000F000`: PPN `0x8000C` (the 4 KiB entry) OR PPN `0x80003` (the 2 MiB entry with VPN[0]
= 3), a page that neither page table maps. (`ld v'` reads `0x5A5A5A5A`, the word at PA `0x80008000` that R2's 2 MiB leaf
maps `v'` to, on both runs.)

In the waveform (load DTLB storage `TLBFA_1`, `dtlb_ld_tlb_ld.entries.page_ldtlb_storage_fa`):


The same `Mux1H` also ORs the permission bits (`perm`, including U, X, W, R, D, A, G), `pbmt` and `s2xlate`, so two
entries with different permissions give a union of permissions. Our run only shows the PPN; the permission path is the
same AND-OR (`io_r_resp_0_bits_perm_0_*_0` in `TLBFA_1`).

Controls, same program with one change, same result on both builds:

| program | change | second `ld v` |
|---|---|---|
| `multihit_ctl3.S` | `sfence.vma x0, x0` after the `satp` write (drops the global entry too) | `0xBBBB...` (mapping B only) |
| `multihit_ctl4.S` | no `satp` write (only R1) | `0xAAAA...` (mapping A only) |


### Expected behavior

The RISC-V Privileged Specification, Sv32 "Addressing and Memory Protection" (the G bit, used by Sv39 as well):

> The G bit designates a _global_ mapping. Global mappings are those that exist in all address spaces. For non-leaf
> PTEs, the global setting implies that all mappings in the subsequent levels of the page table are global. Note that
> failing to mark a global mapping as global merely reduces performance, whereas marking a non-global mapping as
> global is a software bug that, after switching to an address space with a different non-global mapping for that
> address range, can unpredictably result in either mapping being used.

and "Supervisor Memory-Management Fence Instruction", for `sfence.vma x0, rs2`:

> The fence also invalidates all address-translation cache entries matching the address space identified by integer
> register _rs2_, except for entries containing global mappings.

So keeping the global 4 KiB entry after `sfence.vma x0, t3` is allowed.

So when software uses the G bit wrongly, as this program does, the load may read through mapping A (`0xAAAA...`) or
mapping B (`0xBBBB...`). It must not read a third physical page that no PTE names. The same applies to permissions:
either entry's permissions, not their union.

The program needs a misuse of the G bit (or a missing fence) to get two entries for one VA, so this is not reachable by
correct software through this path. The perf counter comment ("Sector tlb may trigger multi-hit, see def wbhit")
suggests other ways to reach a double hit; we did not try those.


### Environment

- Hardware
  - CPU: x86_64, 256 threads
  - Memory (GB): 1133
- Software
  - Operating system: Red Hat Enterprise Linux 8.10
  - gcc version: gcc (GCC) 8.5.0 20210514 (Red Hat 8.5.0-29)
  - Verilator 5.044; LLVM 21.1.8 (`llvm-mc`, `llvm-objcopy`) to assemble the program
- Repo
  - XiangShan commit id: `f85a1f71da8c1522035744c71e2cb266bfed1a7d`
- Build & Run
  - Verilog generated with firtool 1.162.0 from `MinimalConfig` with the L2 removed (load DTLB `NWays = 4`), top a
    small wrapper of `XSTile` with a 64 KiB memory model at `0x80000000`. The DefaultConfig load DTLB (`NWays = 48`) uses
    the same `TLBFA` code.
  - Run: Verilator `--binary` testbench, `inst.pat` = the 64 KiB program image; it prints `mtval`, `stval`, `sscratch`
    400 cycles after the program writes `mscratch` = `0x5eed5eed`.


### To Reproduce


1. Assemble `multihit_main3.S` (attached) into a 64 KiB image linked at `0x80000000`:
   `llvm-mc -triple=riscv64 -mattr=+m,+a,+zicsr -filetype=obj -o prog.o multihit_main3.S` and
   `llvm-objcopy -O binary -j .text prog.o prog.bin`. The page tables are part of the image.
2. Run it from reset at `0x80000000` in M-mode. Read `sscratch` at the end (or watch the third load's data).
3. `sscratch` = `0xCCCCCCCCCCCCCCCC` shows the OR; `0xAAAA...` or `0xBBBB...` would be a single entry.

The program writes `mnstatus.NMIE` = 1 first (`csrs 0x744, 8`), because with Smrnmi MPRV only takes effect when NMIE = 1.
It touches no device and ends in a self-loop.

[multihit_main3.zip](https://github.com/user-attachments/files/34164146/multihit_main3.zip)

### Additional context

Suggested fix: make the selection one-hot before the `Mux1H` (any fixed priority returns one entry's translation),
and keep the perf counter on the raw hit vector:

```diff
-    val hitVecReg = RegEnable(hitVec, req.fire)
+    val hitVecReg = RegEnable(VecInit(PriorityEncoderOH(hitVec.asUInt).asBools), req.fire)
+    val multiHitReg = RegEnable(PopCount(hitVec) > 1.U, req.fire)
     // Sector tlb may trigger multi-hit, see def "wbhit"
-    XSPerfAccumulate(s"port${i}_multi_hit", !(!resp.valid || (PopCount(hitVecReg) === 0.U || PopCount(hitVecReg) === 1.U)))
+    XSPerfAccumulate(s"port${i}_multi_hit", resp.valid && multiHitReg)
```

The run in the right-hand column of the table used the equivalent change in the generated `TLBFA_1.sv` (lowest hitting
entry wins: `hitVecReg_k <= hitVec_k & ~hitVec_0 & ... & ~hitVec_{k-1}` for every port). Only the second `ld v`
changed; the other loads, the controls and the trap behavior were the same. Another option is to invalidate, on
refill, any valid entry that overlaps the new one, so that the storage never holds two hits.

Earlier related work: #930 ("TLB: prevent multi-hit", closed without merge), #656 (removed a multi-hit check),
#1665 (re-added a multi-hit assert). We did not find a report of the OR result itself.
