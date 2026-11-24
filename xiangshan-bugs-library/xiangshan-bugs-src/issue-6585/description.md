### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this issue is related to XiangShan itself.
- [x] I have searched previous issues and PRs and did not find anything relevant.
- [ ] I have reproduced the problem using the latest commit on the default branch. (Reproduced at 37ce1b50, 2026-09-14. The head today, c8d7b3a5c1ab, has the same code at every line named below; I checked the files, not a re-run.)
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content.

Per the AI Use section of CONTRIBUTING.md: this report was drafted with AI assistance (Claude) from my own analysis and patch, and I have reviewed every claim in it.

Apologies for the first version of this issue, which was filed without the template; this is the corrected one.

### Branch

kunminghu-v3

### Describe your problem

Generating Verilog with `--disable-always-basic-diff` fails in elaboration:

```
Backend.scala:499   csrio.vpu.vl := vecRegion.out.diff.get.diffVl    // None.get
```

`vecRegion.out.diff` is `Option.when(backendParams.basicDebugEn)`, and `basicDebugEn` is `AlwaysBasicDiff || debugEn`, so with the flag the CSR's vl has no source. The line sits under the existing `//Todo here need change design`.

With the default (`AlwaysBasicDiff = true`) it elaborates, but the vl the CSR reads then comes through `DiffRatStateBuffer`: the committed vl mapping is `diffRatBuffer.get.io.diffRat.vlRat`, read out of eight banks of `Mem(704, 776)`, about 4.4 Mbit of state whose purpose is to align difftest with commit. Synthesising the core with difftest otherwise off, that buffer is the largest single memory in the design, and it is there only because vl is read through it.

This is not a report of incorrect behaviour: with the default options the vl value is correct. It is a build problem with the flag, and a design remark about what the default carries.

### What did you do before

1. Elaborate `XSCore` from the `kunminghu-v3` branch at 37ce1b50 with `DebugOptions(AlwaysBasicDiff = false, EnableDifftest = false)`, which is what `--disable-always-basic-diff` sets in `ArgParser`, otherwise `DefaultConfig`.
2. Elaboration stops with `java.util.NoSuchElementException: None.get` from `Backend.scala:499`.
3. With `AlwaysBasicDiff = true` elaboration succeeds; in the generated Verilog, `DiffRatStateBuffer` is the largest memory (8 x 704 x 776 bits), and tracing its readers shows the CSR's vl is the only consumer that is not difftest.

### Environment

- Hardware
  - CPU: AMD Ryzen Threadripper 3960X, 24 cores
  - Memory (GB): 62
  - Storage (GB): n/a
- Software
  - Operating system: Ubuntu 26.04 LTS
  - gcc version: gcc (Ubuntu 15.2.0-16ubuntu1) 15.2.0
  - clang version: not used
  - java version: Bazel-provided OpenJDK (rules_java remote JDK)
  - mill version: not used, see Build
- Repo
  - XiangShan commit id: `37ce1b50b5b8fa1865205e76175e48fc636454d6` (the same lines at `c8d7b3a5c1ab`)
  - NEMU commit id: n/a (no difftest run)
  - SPIKE commit id: n/a
- Build & Run
  - Build command: the Chisel generator invoked through a Bazel wrapper around the same sources and the ten submodule repositories at the commits your tree records, Chisel 7.13.0 with its firtool. The failure is in Chisel elaboration, so it does not depend on the build system; `make verilog CONFIG=DefaultConfig ... --disable-always-basic-diff` should show the same `None.get`.
  - Run command: n/a

### Additional context

A possible fix, if you want the flag to work: make the committed vl mapping unconditional and give it a source that does not need the buffer. The vl rename table already exposes its architectural mapping (`arch_table(0)`, today `debug_vl`, gated on `debugEn`). Concretely:

- `RenameTable.io.debug_vl` for `Reg_Vl` unconditional;
- `RenameTableWrapper.io.diff_vl_rat`, `Rename.io.diff_vl_rat` and `CtrlBlockIO.diff_vl_rat` always `Some`, driven from the buffer when it exists and from `vlRat`'s architectural copy otherwise;
- `VecRegionModule`'s vl read port and its `DiffIn` / `DiffOut` unconditional; every other difftest structure stays behind `basicDebugEn`.

Behaviour with difftest on is unchanged: same buffer, same value. With it off, vl reads through the committed mapping directly rather than the per-commit snapshot, and the buffer is not built. I have this as a patch of five files and about twenty lines; it elaborates and runs with the flag on my side (CoreMark, which does not touch vl, so a smoke test rather than a vector regression). I understand vector work on `kunminghu-v3` is in progress and this may wait; if the shape is acceptable I can open it as a PR, or follow a different source for vl if you prefer one.
