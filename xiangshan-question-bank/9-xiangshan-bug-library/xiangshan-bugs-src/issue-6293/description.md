### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A DiffTest mismatch was observed between XiangShan and NEMU. After an illegal-instruction exception handler returns into U-mode via `mret`, a `vle64ff.v v17, (s6)` instruction at `0x800012a4` with `s6=0x4eb9b459` executes differently:

- **NEMU** raises a Load Access Fault (`mcause=5`, `mtval=0x4eb9b459`), leaves `v17` unchanged, and traps back to M-mode.
- **XiangShan** commits the load successfully, writes `0xf8f8f8f8f8f8f8ac` into `v17`, and stays in U-mode.

Because XiangShan does not raise the fault, the handler is never entered, and full architectural state diverges:

```
   mode different: right(NEMU)=3(M), wrong(DUT)=0(U)
 mcause different: right=5(LoadAF), wrong=2(Illegal)
  mtval different: right=0x4eb9b459, wrong=0xff484a77
   mepc different: right=0x800012a4, wrong=0x8000129c
```

The processor is running in U-mode with satp. MODE=Bare, so `s6=0x4eb9b459` is the physical address directly. With `vtype=0xd8` (SEW=64, LMUL=m1, vl=2), `vle64ff.v` performs a 64-bit load from `0x4eb9b459`. The address is **not 8-byte aligned** (`0x4eb9b459 & 0x7 = 1`), and it lies inside PMA region 29 (`[0x20000000, 0x20000000000)`, cfg `0x0b`), which is an **I/O** region (I=1).

As specified in the RISC-V Privileged Specification, Section "Physical Memory Attributes":

> PMAs are checked for any access to physical memory, including accesses that have undergone virtual to physical memory translation.
>
> Precisely trapped PMA violations manifest as instruction, load, or store access-fault exceptions, distinct from virtual-memory page-fault exceptions.

Regarding I/O regions and supported access types:

> Access types specify which access widths, from 8-bit byte to long multi-word burst, are supported, and also whether misaligned accesses are supported for each access width.
>
> Main memory regions always support read and write of all access widths required by the attached devices.
>
> I/O regions can specify which combinations of read, write, or execute accesses to which data widths are supported.

The most directly applicable specification is Section "Idempotency PMAs":

> Non-idempotent regions might not support misaligned accesses. Misaligned accesses to such regions should raise access-fault exceptions rather than address-misaligned exceptions, indicating that software should not emulate the misaligned access using multiple smaller accesses, which could cause unexpected side effects.

An I/O region marked non-idempotent falls squarely under this rule: misaligned accesses to such regions must raise an access-fault exception, not an address-misaligned exception, and certainly must not silently complete.

NEMU correctly enforces the I/O PMA restrictions: the misaligned 64-bit vector load triggers an access fault (`mcause=5`). XiangShan appears to skip the PMA check and silently completes the load, returning data from the I/O region.

The DiffTest report is as follows：

[seeds.log](https://github.com/user-attachments/files/30442262/seeds.log)

### Expected behavior

A `vle64ff.v` to a misaligned address inside an I/O PMA region must raise a Load Access Fault (`mcause=5`), matching NEMU. XiangShan must check PMAs for vector load instructions and must not silently complete loads that violate I/O region access restrictions.

### Environment

- Repo
    - XiangShan commit id: `4cf8b8d4ae`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seeds_105.zip](https://github.com/user-attachments/files/30442301/seeds_105.zip)

### Additional context

_No response_
