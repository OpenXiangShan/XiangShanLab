### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A DiffTest mismatch was observed between XiangShan and NEMU. At PC `0x80001020`, `0xc20073f3` (`csrrci t2, vl, 0`) diverges: NEMU raises an illegal-instruction exception (`mcause=2`), while XiangShan commits the instruction and reads `vl=0` into `t2`. The encoding is a pure read of `vl` (funct3=`111`, uimm=`0`, csr=`0xC20`).

The state is set up in `init_env`:

```
  li x26, 0x000000e60028ccea
  csrw 0x300, x26   # mstatus
  ...
  mret
```

`mstatus = 0x000000e60028ccea` has `MPP[12:11]=01` (S-mode), `MPV[39]=1` (V=1), `VS[10:9]=10` (Clean). After `mret` the hart runs in VS-mode. `vsstatus` is never written, so `vsstatus.VS=0` (Off).

DiffTest reports (the `pc` on the `different` lines is not meaningful in this build):

```
[31] commit pc 0000000080001020 inst c20073f3 wen 1 dst 07 data 0000000000000000 idx 079
     t2 different at pc = 0xffff3fff79d1ca54, right = 0xf1e92d9de21dfb55, wrong = 0x0000000000000000
   mode different at pc = 0xffff3fff79d1ca54, right = 0x0000000000000003, wrong = 0x0000000000000001
mstatus different at pc = 0xffff3fff79d1ca54, right = 0x0000048a00284ca2, wrong = 0x0000000a002844aa
   mepc different at pc = 0xffff3fff79d1ca54, right = 0x0000000080001020, wrong = 0x0000000080000370
  mtval different at pc = 0xffff3fff79d1ca54, right = 0x00000000c20073f3, wrong = 0x00000000f20285d3
      v different at pc = 0xffff3fff79d1ca54, right = 0x0000000000000000, wrong = 0x0000000000000001
```

NEMU (`right`) traps to M-mode (`mcause=2`, `mepc=0x80001020`, `mtval=0xc20073f3`, `V=0`). XiangShan (`wrong`) commits the instruction, writes `t2=0` (reads `vl=0`) and stays in VS-mode (`mode=1`, `V=1`).

The RISC-V V Extension specifies:

> When V=1, both csr:vsstatus[vs] and csr:mstatus[vs] are in effect: attempts to execute any vector instruction, or to access the vector CSRs, raise an illegal-instruction exception when either field is set to Off.

The vector CSRs are `vstart`, `vxsat`, `vxrm`, `vcsr`, `vtype`, `vl`, `vlenb`, so this read of `vl` must trap.

XiangShan's vector-context check is implemented in two places, and this encoding is covered by neither:

- `csrr vl` / `csrr vlenb` (CSRRS, funct3=`010`) are converted in `PseudoDecodeChannel.scala`, where the check is applied via `in.fromCSR.illegalInst.vsIsOff`. The match pattern only covers funct3=`010` with `rs1=x0`:

```scala
// PseudoDecodeChannel.scala:333
def makeCSRRBitPat(csrno: Int): BitPat = {
  new BitPat(csrno, 0xFFF, 12) ## BitPat("b00000_010_?????_1110011")
}
```

- The generic CSR path uses `csrIsVec` in `CSRPermitModule.scala`, which lists only `vstart`, `vxsat`, `vxrm`, `vcsr`, `vtype`:

```scala
// CSRPermitModule.scala:214, 224
private val csrIsVec = Seq(CSRs.vstart, CSRs.vxsat, CSRs.vxrm, CSRs.vcsr, CSRs.vtype).map(_.U === addr).reduce(_ || _)
...
private val vecOff_EX_II = csrIsVec && vsEffectiveOff
```

`vl`/`vlenb` were removed from `csrIsVec` in commit `8c5acf7371`, which moved this check to decode for the `csrr` encoding only. The other read encodings do not match `makeCSRRBitPat`, reach `CSRPermitModule`, and are not gated by `vecOff_EX_II`.

Seeds differing only in the `main` instruction:

| `main` instruction | Encoding | DiffTest |
|--------------------|----------|----------|
| `csrr t2, vl` | `0xc20023f3` | match (traps) |
| `csrrc t2, vl, x0` | `0xc20033f3` | mismatch |
| `csrrsi t2, vl, 0` | `0xc20063f3` | mismatch |
| `csrrci t2, vl, 0` | `0xc20073f3` | mismatch |
| `csrrci t2, vtype, 0` | `0xc21073f3` | match (traps) |
| `csrrci t2, vlenb, 0` | `0xc22073f3` | mismatch |

With the same CSRRCI encoding, `vtype` traps because it is in `csrIsVec`, while `vlenb` does not, and `vlenb` is read as `0x10` (VLEN/8). This isolates the difference to the `vl`/`vlenb` entries missing from `csrIsVec`.

NEMU's check includes all seven vector CSRs:

```c
// NEMU/src/isa/riscv64/system/priv.c:3026
static inline bool vec_permit_check(const word_t *dest_access) {
  if (is_access(vcsr) || is_access(vlenb) || is_access(vstart) || is_access(vxsat) || is_access(vxrm) || is_access(vl) || is_access(vtype)) {
    if (!require_vs()) { longjmp_exception(EX_II); }
  }
  return false;
}
```

The DiffTest report is as follows：

[seeds_2350_min.log](https://github.com/user-attachments/files/33531174/seeds_2350_min.log)


### Expected behavior

Reads of `vl` and `vlenb` must raise an illegal-instruction exception when `vsstatus.VS` is Off in VS-mode (or `mstatus.VS` is Off), for every read encoding. The check must also cover `csrrc rd, vl, x0`, `csrrsi rd, vl, 0` and `csrrci rd, vl, 0`.

### Environment

- Repo
    - XiangShan commit id: `18657f51ce`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seeds_2350_min.zip](https://github.com/user-attachments/files/33531212/seeds_2350_min.zip)

### Additional context

_No response_
