### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

A DiffTest mismatch was observed between XiangShan and NEMU. A `vcpop.m t0, v30` instruction at `0x80001034` is executed after `vsetvl zero, t0, t1` at `0x80001030`, which writes `vtype=0x83` (`vma=1, vta=0, vsew=e8, vlmul=m8`) and `vl=31`.

The instruction encoding `0x43e822d7` decodes as `vcpop.m t0, v30` (unmasked, `vm=1`). Per the RISC-V V Extension, the `inst[19:15]` field of `vcpop.m` is fixed to `10000` and is not a source register, so this encoding is legal. The source operand is a single mask register, independent of the current `LMUL`.

After the instruction, DiffTest reports:

```
     t0 different at pc = 0xfffee4db93ee74c9, right = 0x0000000000000010, wrong = 0x000000000000001f
mstatus different at pc = 0xfffee4db93ee74c9, right = 0x8000004a000866a2, wrong = 0x8000040a00087e22
   mepc different at pc = 0xfffee4db93ee74c9, right = 0x000000008000008e, wrong = 0x0000000080001034
  mtval different at pc = 0xfffee4db93ee74c9, right = 0x0000000000000000, wrong = 0x0000000043e822d7
 mcause different at pc = 0xfffee4db93ee74c9, right = 0x0000000000000000, wrong = 0x0000000000000002
```

- **NEMU**: `vcpop.m` executes normally, counting the set mask bits among the first `vl=31` elements of `v30` and writing `t0=16` (`0x10`). No exception is taken.
- **XiangShan**: raises an illegal-instruction exception (`mcause=2`) on the instruction, leaving `t0` at its previous value `31` (`0x1f`), and writing `mtval=0x43e822d7` and `mepc=0x80001034`.

The root cause is that `VCPOP.M` (and likewise `VFIRST.M`) is declared with the `V2_VecNormal` source-operand trait in `src/main/scala/xiangshan/backend/vector/Decoder/InstPattern/VecArithInstPattern.scala`. This makes the decoder treat `vs2` as an `LMUL`-sized register group. With `vlmul=m8`, `VecExceptionGen` then requires `vs2` to be aligned to an 8-register group:

```
vs2NotAlign = useVs2 && vregNotAlign(emulVs2=m8, vs2=v30) = true   // v30[2:0] = 110 != 000
```

Since `vs2=v30` is not aligned to 8, the whole instruction is wrongly flagged as illegal.

As specified in the RISC-V V Extension:

> A vector mask occupies only one vector register regardless of SEW and LMUL.

> The source operand is a single vector register holding mask register values as described in 《sec-mask-register-layout》.

Therefore the group-alignment check must not apply to the mask source of `vcpop.m`. `v30` is a perfectly valid source regardless of `LMUL`.

The DiffTest report is as follows：

[seeds_4_.log](https://github.com/user-attachments/files/32048211/seeds_4_.log)

### Expected behavior

`vcpop.m t0, v30` must be decoded and executed as a legal instruction. With `vl=31` and `v30`'s lower 31 mask bits having 16 ones, `t0` must be written with `16` and no exception must be raised. The decoder must model the source operand of `vcpop.m` / `vfirst.m` as a single mask register (e.g. the `V2_VecMask` trait, as already used by `vmsbf.m` / `vmsof.m` / `vmsif.m`), rather than an `LMUL`-sized register group.

### Environment

- Repo
    - XiangShan commit id: `eedc9aa511`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seeds_4_.zip](https://github.com/user-attachments/files/32048228/seeds_4_.zip)

### Additional context

_No response_
