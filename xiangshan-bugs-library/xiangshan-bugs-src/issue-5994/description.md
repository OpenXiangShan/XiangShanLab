### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

A backend branch redirect for a compressed RISC-V branch instruction (`c.bnez`) carries incorrect instruction-length metadata.

The reproducer has a compressed branch at `0x80000040`:

```text
0000000080000040 <victim_branch>:
    80000040: e019  bnez s0,80000046 <taken_path>

0000000080000042 <fallthrough_path>:
    80000042: 0385  addi t2,t2,1
```

When the branch is redirected as a false-branch/mispredict event, the VCD shows the backend redirect is valid at cycle `8408` with:

```text
redirect.valid       = 1
redirect.bits.pc     = 0x80000040
redirect.bits.target = 0x80000042
redirect.bits.isRVC  = 0
redirect.bits.ftqOffset = 0
redirect.bits.isMisPred = 1
redirect.bits.taken = 0
```

This is inconsistent: the branch at `0x80000040` is a 16-bit compressed branch, and the fall-through target is `0x80000042`, but `redirect.bits.isRVC` is `0`.

This incorrect `isRVC` value changes the result of `Redirect.getPcOffset()`:

```scala
def getPcOffset() = {
  val ftqOffset = (this.ftqOffset << instOffsetBits).asUInt
  val rvcOffset = Mux(this.isRVC, 0.U, 2.U)
  val thisPcOffset = SignExt(ftqOffset -& rvcOffset, VAddrBits)
  thisPcOffset
}
```

With the VCD values:

```text
ftqOffset = 0
isRVC = 0
getPcOffset = 0 - 2 = -2
redirect CFI PC = 0x80000040 - 2 = 0x8000003e
```

The correct CFI PC should be `0x80000040`.
The likely source is that `BranchUnit` clears the redirect bundle and writes many redirect fields, but does not write `redirect.bits.isRVC`:

```scala
redirect.bits := 0.U.asTypeOf(io.out.bits.res.redirect.get.bits)
redirect.bits.ftqOffset := io.in.bits.ctrl.ftqOffset.get
redirect.bits.target := addModule.io.target
redirect.bits.pc := io.in.bits.data.pc.get
redirect.bits.isMisPred := isMisPred
redirect.bits.taken := dataModule.io.taken
```

`SimFrontend` consumes the redirect PC using `getPcOffset()`:

```scala
fetchHelper.io.redirectPc := io.backend.toFtq.redirect.bits.pc +
  io.backend.toFtq.redirect.bits.getPcOffset()
```
Therefore, the redirect metadata can report a wrong CFI PC for compressed branch redirects.

### Expected behavior

For a redirect corresponding to the compressed branch at `0x80000040`, `redirect.bits.isRVC` should be `1`.

The redirect metadata should then compute:

```text
pc = 0x80000040
ftqOffset = 0
isRVC = 1
getPcOffset = 0
redirect CFI PC = 0x80000040
```

For this false-branch redirect, `target=0x80000042` is expected because the compressed branch length is 2 bytes.

### Environment

- Software
  - gcc version: `gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0`
  - clang version: `Ubuntu clang version 18.1.3 (1ubuntu1)`
  - java version: `openjdk version "11.0.30" 2026-01-20`
  - mill version: `Mill Build Tool version 0.12.15`
  - verilator version: `Verilator 5.044 2026-01-01 rev v5.044`
- Repo
  - XiangShan commit id: `3931c5112c528299a23c256bdd77fb90813afa6e`
- Build & Run
  - Build command: 
 
```bash
  make -C /root/HardwareAgent/XiangShan/difftest emu \
  EMU_TRACE=1 EMU_TRACE_ALL=1 NO_DIFF=1 \
```


### To Reproduce

Attachment Files [xiangshan_cbnez.zip](https://github.com/user-attachments/files/28013819/xiangshan_cbnez.zip): 
- `poc.S`: minimal assembly reproducer. It contains the compressed `c.bnez` at `0x80000040`.
- `poc.elf`: ELF built from `poc.S`.
- `poc.bin`: binary image used as the emu workload.
- `poc.objdump`: disassembly showing `victim_branch` at `0x80000040` and fall-through at `0x80000042`.
- `parse_cbnez_vcd.py`: parser used to extract the redirect signals from the VCD and recompute `Redirect.getPcOffset()`.
- `vcd_result_fulltrace_8000_9000.json`: extracted result from the local VCD. It records the failing redirect at cycle `8408`.

The full VCD was generated locally and is about `347 MB`, so it is not uploaded.

From the artifact directory:

```bash
   emu \
  -C12000 \
  -b 8000 -e 9000 \
  -i./poc.bin \
  --no-diff \
  --dump-wave \
  --wave-path=./cbnez_redirect_fulltrace_8000_9000.vcd
```

Then parse the generated VCD:

```bash
python3 ./parse_cbnez_vcd.py \
  ./cbnez_redirect_fulltrace_8000_9000.vcd \
  ./poc.objdump \
  ./vcd_result_fulltrace_8000_9000.json
```

Expected parser result:

```text
passed: true
cycle: 8408
pc: 0x80000040
target: 0x80000042
isRVC: 0
ftqOffset: 0
isMisPred: 1
taken: 0
calcRedirectPc: 0x8000003e
```

The same result is already captured in:

```text
vcd_result_fulltrace_8000_9000.json
```

### Additional context

VCD signals to inspect manually:
Open the VCD:

```bash
gtkwave ./cbnez_redirect_fulltrace_8000_9000.vcd
```

Go to VCD timestamp `8408ps`. In this VCD, the timestamp corresponds to emu cycle `8408`.

Inspect these signals under:

```text
TOP.SimTop.cpu.l_soc.core_with_l2.core
```

Signals:

```text
_backend_io_frontend_toFtq_redirect_valid
_backend_io_frontend_toFtq_redirect_bits_pc
_backend_io_frontend_toFtq_redirect_bits_target
_backend_io_frontend_toFtq_redirect_bits_isRVC
_backend_io_frontend_toFtq_redirect_bits_ftqOffset
_backend_io_frontend_toFtq_redirect_bits_isMisPred
_backend_io_frontend_toFtq_redirect_bits_taken
```

At cycle `8408`, the bug is visible as:

```text
valid     = 1
pc        = 0x80000040
target    = 0x80000042
isRVC     = 0
ftqOffset = 0
isMisPred = 1
taken     = 0
```

`calcRedirectPc=0x8000003e` is not a separate VCD wire. It is computed from the VCD fields above using XiangShan's `Redirect.getPcOffset()` logic.
