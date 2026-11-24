### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

On XiangShan master (commit `b9638259e98c92b4c0d9e64a09d3c504faa699e6`), the `ISAExtensions` list in `src/main/scala/xiangshan/Parameters.scala` includes `zvkt` and `zvbb`, but does not include `zvknha` or `zvknhb`.

However, vector SHA-2 instruction decoding and execution logic is present in the source tree.

### Expected behavior

The advertised ISA extensions should match the implemented and supported instructions.

If vector SHA-256 is supported with SEW=32, `zvknha` may be appropriate. If SHA-512 is also supported with SEW=64, `zvknhb` may be appropriate.

Alternatively, please clarify whether these instructions are intentionally excluded from the advertised ISA extensions.

### Environment
**Hardware**
- CPU: Intel(R) Xeon(R) 6731P (64 logical CPUs, shared server)
- Memory: 251 GB
- Storage: 4398 GB (1175 GB free)

**Software**
- OS: Ubuntu 24.04 LTS (kernel 6.8.12)
- GCC: 13.3.0
- Clang: 18.1.3
- Java: OpenJDK 21.0.5
- Mill: 0.12.17
- Verilator: 5.048

**Repository versions**
- XiangShan: `b9638259e98c92b4c0d9e64a09d3c504faa699e6`
- Branch: `kunminghu-v3`
- Configuration: `DefaultConfig`
- NEMU: prebuilt `riscv64-nemu-interpreter-so` from `ready-to-run` commit `4cf9983569432035d41956c65e6a58724b5954be`, whose build record references NEMU commit `513786c4c597dd628c6126144937372d6f7fc92e`
- Spike: N/A

**Build command**

```bash
make emu CONFIG=DefaultConfig EMU_THREADS=2 -j16 CXX=clang++ LINK=clang++
```

Relevant files:

- `src/main/scala/xiangshan/Parameters.scala`
- `src/main/scala/xiangshan/backend/decode/isa/Instructions.scala`
- `src/main/scala/xiangshan/backend/decode/opcode/Opcode.scala`
- `src/main/scala/xiangshan/backend/fu/FuConfig.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/VSha256msWrapper.scala`
- `src/main/scala/xiangshan/backend/fu/wrapper/VSha256cWrapper.scala`

### Potential impact

Software relying on the advertised ISA extensions may not select the vector SHA-2 implementation, even if the corresponding instructions are supported by the hardware.
