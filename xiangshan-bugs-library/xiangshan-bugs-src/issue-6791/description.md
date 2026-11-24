### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

`vmv.x.s a0, v2` returns `0xfe4832fbe5831d82` after element 0 of `v2` has been computed as 8.

The test initializes `v1` to 7, adds 1 into `v2`, and moves `v2[0]` to `a0`. It uses `SEW=64`, `LMUL=1` and VLEN=128. The requested AVL=4 produces VL=2. The preceding vector instructions commit without a difftest mismatch; the first mismatch is the scalar result of `vmv.x.s`.

Workload source:

```asm
.section .text
.globl _start
_start:
  li       t0, 0x600
  csrs     mstatus, t0
  vsetivli zero, 4, e64, m1, ta, ma
  vmv.v.i  v1, 7
  vadd.vi  v2, v1, 1
  vmv.x.s  a0, v2
1: j 1b
```

| Destination | Expected / Spike     | XiangShan DUT        |
| ----------- | -------------------- | -------------------- |
| `a0`        | `0x0000000000000008` | `0xfe4832fbe5831d82` |

Full difftest output: `run.log` in the reproducer archive.

### Expected behavior

RVV 1.0, **Integer Scalar Move Instructions**, defines `vmv.x.s rd, vs2` as copying element 0 of the source vector register into the integer destination. Here `SEW=XLEN=64`, so the entire 64-bit element must be copied to `a0`.

Specification: https://docs.riscv.org/reference/isa/v20260120/unpriv/v-st-ext.html

### Environment

- Hardware
    - CPU: AMD Ryzen 7 5800H with Radeon Graphics.
    - Memory: 15 GiB.
    - Storage: 1007G filesystem, as reported by `df -h`.
- Software (collector session)
    - OS: Ubuntu 24.04.4 LTS.
    - Kernel: `6.18.54.1-microsoft-standard-WSL2`.
    - GCC: 13.3.0.
    - Java: OpenJDK 17.0.19.
    - Clang / Mill: not found on the collector's PATH.
- Repo
    - XiangShan: `kunminghu-v3`, commit `b716aade5550b6c7ba21c199bc917e8275140bba`.
    - Spike: OpenXiangShan/riscv-isa-sim `difftest` at `5e8a1eae`, with local difftest adapter patches for tinfo.VERSION, cbo.zero store events, and 8-byte store-event splitting/data masking.
    - Reference `.so` SHA-256: `efa062302a0cb10b4546c3762582995902912b40bd881437225a4f43cbe2030b`.
- Recorded replay
    - Configuration: `MinimalConfig`, VLEN=128.
    - Emulator: Verilator 5.020; build ID `b716aade55, dirty: 1`.
    - DUT build command: not captured in the replay record.
    - Workload rebuild toolchain: GNU Binutils 2.45.
    - Reproduced with an ELF rebuilt from `test.S` using the supplied `link.ld`; run command below.

### To Reproduce

1. Extract `vmv-x-s-reproducer.zip` in the XiangShan directory. It contains `test.S`, `test.elf`, `link.ld`, and the full run log `run.log`.

2. To rebuild from source, run:

    ```bash
    riscv64-unknown-elf-as -march=rv64gcv_zvfh -mabi=lp64d \
      -o test.o test.S
    riscv64-unknown-elf-ld -T link.ld \
      -o test.elf test.o
    ```

    The included `link.ld` fixes the entry point and section addresses:

    ```ld
    OUTPUT_ARCH("riscv")
    ENTRY(_start)
    
    SECTIONS
    {
      . = 0x80000000;
      .text : { *(.text) }
    
      . = 0x80001000;
      .data : { *(.data) }
    }
    ```

3. Set `REF` to the Spike difftest shared library and run the ELF from the XiangShan directory:

    ```bash
    ./build/emu -b 0 -e 0 -C 3000000 \
      -i test.elf --diff "$REF"
    ```

4. Difftest reports an `a0` mismatch at `vmv.x.s` and terminates with `Core 0: ABORT`. In the log, `right` is Spike and `wrong` is the DUT.

[vmv-x-s-reproducer.zip](https://github.com/user-attachments/files/34030357/vmv-x-s-reproducer.zip)

### Additional context

_No response_
