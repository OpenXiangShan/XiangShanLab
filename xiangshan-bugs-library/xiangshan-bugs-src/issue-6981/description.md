### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

Commit: XiangShan KMHv3 `6705b63`.
The program sets `t0 = 5` and executes `vmv.v.x v8,t0`, which must splat the scalar register into every active element of `v8`. It stores `v8`, then computes `vadd.vi v16,v8,5` as an immediate-source control, then executes `vadd.vx v24,v16,t0` and stores `v24`. It finally exits with `a0 = (v24 << 32) | v8`.

The expected result is `v8 = 5` and `v24 = 5 + 5 + 5 = 15`, i.e. `a0 = 0x0000000F00000005`. The immediate-source `vadd.vi` is correct on the DUT; only the GPR source is lost. The DUT exits with `a0 = 0x0000000500000000`, which encodes `v8 = 0` and `v24 = 5`:

- `vmv.v.x v8,t0` splatted 0 instead of 5
- `vadd.vx v24,v16,t0` produced `v16 + 0 = 5` instead of `v16 + 5 = 15`
- exit event PC `0x8000008c`, instruction `0x0005006b`, `a0 = 0x0000000500000000`

The vector issue pipes have no integer register read port, and the vector FU configurations declare the `.vx` source as `VecData()`. The uop does carry `gpRen = 1`, but there is no port to service it, so the function unit receives `src0 = 0`.


### Expected behavior

Spike (RV64GCV) and KMHv2 (`fe2288cb09`) both produce `a0 = 0x0000000F00000005`:

- `v8 = 5` from `vmv.v.x`
- `v16 = 10` from `vadd.vi`
- `v24 = 15` from `vadd.vx`

Spike writes `tohost = 0x1e0000000b`, i.e. `(0x0000000F00000005 << 1) | 1`. This is a value-only defect: the only trap in that run is the exit word `.word 0x0005006b`, which Spike treats as an illegal instruction.


### Environment

- Hardware
  - CPU:
  - Memory (GB):
  - Storage (GB):
- Software
  - Operating system:
  - gcc version: <!-- run `gcc --version 2>&1 | head -n 1` to get the version -->
  - clang version: <!-- run `clang --version 2>&1 | head -n 1` to get the version, only needed when you use clang -->
  - java version: <!-- run `java -version 2>&1 | head -n 1` to get the version -->
  - mill version: <!-- run `mill -i --version 2>&1 | head -n 1` to get the version -->
- Repo
  - XiangShan commit id: ``
  - NEMU commit id (if difftest failed with NEMU): ``
  - SPIKE commit id (if difftest failed with SPIKE): ``
- Build & Run
  - Build command: ``
  - Run command (if applicable): ``
  - Also upload workload (binary and source code) in "To Reproduce" section if applicable.


### To Reproduce

1. Assemble the program with `-march=rv64gcv -mabi=lp64d -nostdlib -Wl,--no-relax` and run it on the KMHv3 build with the same image and link base (`0x80000000`).
2. Set `t0 = 5`, execute `vmv.v.x v8,t0`, store `v8`, and execute `vadd.vi v16,v8,5` as the immediate-source control.
3. Execute `vadd.vx v24,v16,t0`, store `v24`, and exit with `a0 = (v24 << 32) | v8`.
4. Run the same ELF on KMHv3, KMHv2, and Spike, and compare `a0`.

Reproducer ELF SHA-256: `58c40e8d7537c38c6bfec4bcd04388cdac9553cde90e2fc017ea5fe9dc578759`

[xiangshan-vec-scalar-src-zero-upload.zip](https://github.com/user-attachments/files/34894051/xiangshan-vec-scalar-src-zero-upload.zip)

### Additional context

## Root cause

`src/main/scala/xiangshan/Parameters.scala:492` assigns only vector register read ports (plus one FP read port for `src0`) to the vector issue pipe `VFEX0`:

```scala
Seq(Seq(VfRD(0, 0), FpRD(12, 1)), Seq(VfRD(1, 0)), Seq(VfRD(2, 0))),
```

`VFEX1` (`:504`) and `VFEX2`/`VFEX3` (`:513`, `:522`) likewise contain only `VfRD`. None of the four vector issue pipes has an `IntRD` port.

`src/main/scala/xiangshan/backend/vector/IssuePipe.scala:76-78` builds integer read requests only from ports declared as `IntData()`:

```scala
val is1GpRdAddrReqSrcIdx: Seq[Int] = is1Next.bits.exuParam.readPortCfgs.zipWithIndex collect {
  case (readCfg, srcIdx) if readCfg.exists(_.getDataConfig == IntData()) => srcIdx
}
```

With no `IntRD` port the collected list is empty, so a `gpRen` request is never turned into a register read.

`src/main/scala/xiangshan/backend/fu/FuConfig.scala:636-641` also declares the `.vx` source as a vector source:

```scala
val VmoveCfg = FuConfig (
  name = "vmove",
  fuType = FuType.vmove,
  fuGen = null,
  srcData = Seq(
    Seq(VecData(), VecData(), VecData()), // vs1, vs2, vd_old
```

`VialuCfg` (`:569-575`) declares the same three `VecData()` sources. Consequently `VecFuConfig.numIntSrc` (`vector/fu/VecFuConfig.scala:93`) is 0 and no `IntRD` port is created for the pipe. The uop still carries `gpRen = 1`, but no port consumes it, and the function unit sees `src0 = 0`.
