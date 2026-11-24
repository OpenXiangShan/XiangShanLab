### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

(Reproduced at `f85a1f71d`)
Hello I believe I have found a bug in this core.
The FIOM bit (bit 0) of `menvcfg`, `senvcfg` and `henvcfg` is hardwired to 0. Writing 1 has no effect and the bit
always reads back as 0.

The privileged spec allows FIOM to be read-only zero only in these cases:

- `menvcfg`: "If S-mode is not supported, or if `satp`.MODE is read-only zero (always Bare), the implementation may
  make FIOM read-only zero."
- `senvcfg`: "If `satp`.MODE is read-only zero (always Bare), the implementation may make FIOM read-only zero."
- `henvcfg`: no exception is given.

XiangShan implements S-mode, Sv39/Sv48 and the H extension, so none of these exceptions applies, and FIOM should be
writable in all three registers.

From M-mode:

```asm
  csrsi  menvcfg, 1      # FIOM = 1
  csrr   s4, menvcfg     # expected bit 0 = 1
  csrsi  senvcfg, 1
  csrr   s2, senvcfg     # expected bit 0 = 1
  csrsi  henvcfg, 1
  csrr   s5, henvcfg     # expected bit 0 = 1
```

On XiangShan all three read-backs have bit 0 = 0:

| read-back | XiangShan | with FIOM writable (one-bit change, see below) |
|---|---|---|
| `menvcfg` (copied to `stval`) | `0x80000000000000f0` | `0x80000000000000f1` |
| `senvcfg` (copied to `sscratch`) | `0xf0` | `0xf1` |
| `henvcfg` (copied to `vsscratch`) | `0x80000000000000f0` | `0x80000000000000f1` |
| traps (`mcause`) | none | none |
| cycles to the end marker | 780 | 780 |

Both runs use the same generated Verilog, simulator and program. The only difference is a `+define+` that makes bit 0 of the three generated `*envcfgModule`s a plain writable register. That edit only shows what software would observe; the proposed fix is below. Only bit 0 changes: same cycle count, no traps.

Waveforms (GTKWave, 2475-3055 ns). Markers A/B/C are the `menvcfg`/`senvcfg`/`henvcfg` write cycles (2495/2735/2975 ns), and the signal pane shows the values at 2995 ns.

**A: XiangShan as is.** FIOM stays 0 after each write.

<img width="1800" height="950" alt="Image" src="https://github.com/user-attachments/assets/1a2341ab-8ff2-4571-b166-898443d59725" />

**B: FIOM writable.** FIOM goes to 1 after each write.

<img width="1800" height="950" alt="Image" src="https://github.com/user-attachments/assets/fa965498-37ae-4251-9f9b-1d788f5c1c58" />

### Expected behavior

FIOM (bit 0) of `menvcfg`, `senvcfg` and `henvcfg` should be writable, since S-mode and Sv39/Sv48 are implemented. Writing 1 should set the bit, and a read-back should return it, for example `0x80000000000000f1` for `menvcfg` in the program above. Writing 0 should clear it again. Only bit 0 should change, as in arm B.

### Environment


- Repo
  - XiangShan commit id: `f85a1f71da8c1522035744c71e2cb266bfed1a7d` (kunminghu-v3). The FIOM declaration is
    identical on `b716aade5` (latest kunminghu-v3).
- Build & Run
  - Generated Verilog for `FormalXSTop`, simulated with Verilator 5.044 on a bare-metal program image at
    `0x80000000` (no difftest).

### To Reproduce

1. Run the program above from M-mode on any configuration with S-mode enabled (for example `DefaultConfig`).
2. Read back `menvcfg`, `senvcfg` and `henvcfg`: bit 0 is 0 each time.

### Additional context


The bit is declared read-only in the common base class
(`src/main/scala/xiangshan/backend/fu/NewCSR/CSRBundles.scala:67-80`):

```scala
  abstract class EnvCfg extends CSRBundle {
    // Set all fields not supported as RO in base class
    ...
    val FIOM  =      RO(     0)           .withReset(0.U).withDescription("Fence of I/O implies memory ordering.")
```

and `MEnvCfg` (`MachineLevel.scala:764`), `SEnvCfg` (`SupervisorLevel.scala:270`) and `HEnvCfg`
(`HypervisorLevel.scala:364`) never set it to RW.

Impact: as far as we can tell this is a CSR conformance issue, not a memory-ordering bug. `Fence.scala:80-83`
waits for the store buffer to drain on every FENCE whatever its predecessor/successor bits are, so the ordering FIOM=1
asks for may already be provided. What breaks is software that sets FIOM and reads it back, for example an M-mode
hypervisor emulator or an SBI that emulates a device for S/U-mode (the use case the spec gives for this bit).
It will see the bit as unsupported.
