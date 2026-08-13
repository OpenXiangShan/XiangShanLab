### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug


At PC `0x800013bc`, XiangShan executes `fsw fa1,716(t6)` (`2cbfa627`) with `t6=0`. The effective address `0x2cc` is not covered by any PMA region. NEMU correctly raises **Store/AMO access fault** (`mcause=7`, `mtval=0x2cc`). XiangShan instead **deadlocks** -- no instruction commits for 15000+ cycles.

| Field | DUT (XiangShan) | REF (NEMU) |
|-------|-----------------|------------|
| **Faulting instruction** | `fsw fa1,716(t6)` @ `0x800013bc` | Same |
| **t6 (rs1)** | `0x0000000000000000` | Same |
| **Effective address** | `0x0 + 0x2cc = 0x2cc` | Same |
| **PMA** | None (below PMA19 at `0x4000000`) | Same |
| **mepc** | Stale (`0x800013a4`) | `0x800013bc` |
| **Behavior** | **Deadlock** | Precise exception |

**`fsw fa1,716(t6)`** at `0x800013bc` (`2cbfa627`). This is the first scalar store to a PMA-missing address in the execution path. The address `0x2cc` falls below the first PMA memory region (PMA19 at `0x4000000`).

Previous AMO instructions (`amoxor.w.rl` at `0x8000138c`, `sc.d.aqrl` at `0x800013a0`) also access PMA-missing addresses (t6=0), but they go through the **LoadUnit** path and handle the store access fault correctly (trap -> mret -> recovery). The `fsw` goes through the **StoreUnit** path and deadlocks.


```
PMA19: cfg=0x0b  addr=0x0000000004000000   <- first PMA region at 64MB (R=1,W=1,X=0,I=1)
PMA20: cfg=0x0f  addr=0x0000000008000000
```

Address `0x2cc` is not covered by any PMA region.


RISC-V Specification References

> ```
> XReg virtual_address = X[xs1] + $signed(imm);
> write_memory(32, virtual_address, F[fs2][31:0], $encoding);
> ```

> ```
> kind: exception_code
> name: StoreAmoAccessFault
> num: 7
> ```

> **"Precisely trapped PMA violations manifest as instruction, load, or store access-fault exceptions."**

> **"The virtual store/AMO address causing the access fault. (Even though the access fault arises on a physical address, the virtual address is reported)"**

> **"If `mtval` is written with a nonzero value when ... an access-fault ... exception occurs on ... a store, then `mtval` will contain the faulting virtual address."**


The DiffTest report is as follows：

[seeds.log](https://github.com/user-attachments/files/30229435/seeds.log)

### Expected behavior

Per the RISC-V specification, `fsw fa1,716(t6)` with `t6=0` (effective address `0x2cc`, which falls outside all PMA regions) must raise a precise **Store/AMO access fault**:

| CSR | Expected Value | Spec Reference |
|-----|---------------|----------------|
| `mcause` | `0x0000000000000007` | `StoreAmoAccessFault.yaml`: exception code 7 |
| `mepc` | `0x00000000800013bc` | `PRECISE_SYNCHRONOUS_EXCEPTIONS`: all prior instructions committed, faulting PC captured |
| `mtval` | `0x00000000000002cc` | `mtval.yaml` cause 7: "The virtual store/AMO address causing the access fault" |
| `mstatus.MPP` | `3` (M-mode) | Trap into M-mode from M-mode |


### Environment

- Repo
    - XiangShan commit id: `b90dbba40d`
    - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
    - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
    - Run command: `/***/dut/XiangShan/build/emu -b 0 -e 0 -i /***/seed.elf --diff /***/dut/XiangShan/ready-to-run/riscv64-nemu-interpreter-so`
    - Config: `DefaultConfig`

### To Reproduce

[seed.zip](https://github.com/user-attachments/files/30229420/seed.zip)

### Additional context

_No response_
