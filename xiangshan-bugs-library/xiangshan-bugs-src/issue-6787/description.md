### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

With `SEW=16`, `LMUL=1`, `VL=6` and VLEN=128, `vfwsub.vv v10, v4, v6` writes four packed FP16 subtraction results into `v10[63:0]` instead of two widened FP32 elements.

Workload source:

```asm
.section .text
.globl _start
_start:
  li      t0, 0x6600
  csrw    mstatus, t0
  vsetivli zero, 8, e16, m1, tu, mu
  la      a0, imgA16
  vle16.v v4, (a0)
  la      a0, imgB16
  vle16.v v6, (a0)
  vsetivli zero, 6, e16, m1, tu, mu
  vfwsub.vv v10, v4, v6
1: j 1b
  .section .data
imgA16: .word 0x40003c00, 0xbc003800, 0x7bffc000, 0x7c000001
imgB16: .word 0x38003800, 0x3c003400, 0x00003c00, 0x00000000
```

| Destination bits | Expected / Spike     | XiangShan DUT        |
| ---------------- | -------------------- | -------------------- |
| `v10[63:0]`      | `0x3fc000003f000000` | `0xc00034003e003800` |
| `v10[127:64]`    | `0xc00000003e800000` | `0xdf2465457bffc200` |

The low DUT word consists of halfwords `0x3800, 0x3e00, 0x3400, 0xc000`, listed from the least-significant halfword upward. These encode FP16 values `0.5, 1.5, 0.25, -2.0`: the first four subtraction results in narrow rather than widened form.

Full difftest output: `run.log` in the reproducer archive.

### Expected behavior

RVV 1.0, **Vector Widening Floating-Point Add/Subtract Instructions**, defines `vfwsub.vv` as `2*SEW = SEW - SEW`. For SEW=16, each active result is FP32.

The first four source elements are:

| Element | `v4` (FP16) | `v6` (FP16) | Difference | Required FP32 encoding |
| ------- | ----------- | ----------- | ---------- | ---------------------- |
| 0       | 1.0         | 0.5         | 0.5        | `0x3f000000`           |
| 1       | 2.0         | 0.5         | 1.5        | `0x3fc00000`           |
| 2       | 0.5         | 0.25        | 0.25       | `0x3e800000`           |
| 3       | -1.0        | 1.0         | -2.0       | `0xc0000000`           |

These results are exactly representable. All four elements are active at VL=6, and the widened destination group is `v10-v11`.

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

1. Extract `vfwsub-reproducer.zip` in the XiangShan directory. It contains `test.S`, `test.elf`, `link.ld`, and the full run log `run.log`.

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

4. Difftest reports `v10_low` and `v10_high` mismatches at `vfwsub.vv` and terminates with `Core 0: ABORT`. In the log, `right` is Spike and `wrong` is the DUT.

[vfwsub-reproducer.zip](https://github.com/user-attachments/files/34029866/vfwsub-reproducer.zip)

### Additional context

_No response_
