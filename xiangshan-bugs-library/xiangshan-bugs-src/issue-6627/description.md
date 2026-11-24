### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

RVV: `vl=0` and `vstart=0` updates no body elements. Tail-agnostic (`vta=1`) is **not** required to write the whole register; old vd is undisturbed. XiangShan yunsuan `MergeUnit` does `agnosticMask |= Fill(vlenb, vta) & tailMask`. `vl=0` ⇒ merge `end=0` ⇒ `tailMask=~0` ⇒ `vta` writes `0xFF` over every byte.

Observed on kunminghu-v3 (pinned CI emu commit 4e0288460d; assignment still present on origin yunsuan `098c505a` / XiangShan HEAD e4566c29e), twice, identical instrCnt, `--no-diff`:

```
vec06-vl0-keep-vd start
vl0=0 out0=255 out15=255
FAIL vec06 vl=0 vta=1 overwrote old vd
HIT GOOD TRAP at pc = 0x80000206
instrCnt = 2,932  cycleCnt = 20,801
```

Old vd was 5. After `vsetivli uimm=0` then `vadd.vv`, every byte is 255.

`HIT GOOD TRAP` is not a pass. `--diff` on this pin ABORTs at the setup `vle`. Oracle for this ticket is the AM self-check after `--no-diff`.

Do not alias this to other tail/mask issues without the `vl=0` contract. An earlier AM that used `vsetvli rd, x0` (VLMAX, not 0) is discarded; the successful pair uses `vsetivli uimm=0`.

No matching issue/PR found on kunminghu-v3.


### Expected behavior

For `vle8` of 5s into v1, then `vsetivli 0, e8,m1,ta,mu` (`vl=0`), then `vadd.vv v1, v2, v3`:

1. `vl` must be 0.
2. No body update (there is no body).
3. `vta=1` must **not** force a whole-register agnostic fill. Old vd (5) must remain.
4. `out0=255 out15=255` is not a legal `vl=0` outcome.

ISA (`vector-common.adoc` `vstart_vl_dep` / note on `vl=0`): when `vl=0`, no elements are processed. Implementations are permitted to leave vd unchanged; they are not required to write agnostic tails over the entire register.

### Environment

- Hardware
  - CPU: AMD EPYC 7H12 64-Core Processor
  - Memory (GB): 1007
  - Storage (GB): 4946 (NFS home; 2297 available)
- Software
  - Operating system: Ubuntu 24.04.3 LTS
  - gcc version: gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0
  - clang version: Ubuntu clang version 19.1.1 (1ubuntu1~24.04.2)
  - java version: openjdk version "21.0.12" 2026-07-21
  - mill version: Mill Build Tool version 0.12.3
- Repo
  - XiangShan commit id: `4e0288460d483e6048000a13fa7825f0f81d8e38` (commit that built the emu which produced the attached logs). Source assignment re-checked on default-branch HEAD `e4566c29e251c60159c6796db60a2569dd7700f2` (origin/kunminghu-v3, 2026-09-21) / origin yunsuan `098c505af11b67f57cf2cd39433276f8c8610d8e`: `agnosticMask = Fill(vlenb, vma) & bodyMask & maskOffEn | Fill(vlenb, vta) & tailMask` is still present in `yunsuan/src/main/scala/yunsuan/vector/v2/MergeUnit.scala`.
  - NEMU commit id (if difftest failed with NEMU): n/a, reproduced with `--no-diff` (this pin's `--diff` ABORTs at the setup `vle` vector-RF writeback; that is not this ticket)
  - SPIKE commit id (if difftest failed with SPIKE): n/a
- Build & Run
  - Build command: used existing CI emu, not rebuilt. Binary compiled Sep 2 2026, XSNoCTopConfig --enable-difftest, DefaultConfig tile. emu sha256 `6b322b984c3f868a7c09246200f9b098803e8556f276cf69a39359c70eba5222`. NEMU so sha256 `ed85429bd4ea0ab53ea498120d7deebe67f121e11085122ca4098508ffa6feb5`.
  - Run command: `<emu> -i vec06-vl0-keep-vd-riscv64-xs.bin --max-instr=20000 --no-diff`
  - Also upload workload (binary and source code) in "To Reproduce" section if applicable.


### To Reproduce

Minimal AM workload: enable VS, fill v1 with 5s, `vsetivli uimm=0, e8,m1,ta`, `vadd.vv v1, v2, v3`, `vse8 v1`. DUT should keep 5s. It stores 255s.

Workload (source + bin) is attached: vec06-vl0-keep-vd.zip
  - main.c
  - Makefile
  - build/vec06-vl0-keep-vd-riscv64-xs.bin
    sha256 9599c9c214699fb72fa780a9eff4f244de9e8a90b5d629cd20d169bd20f5438a

Steps:

1. Obtain the XiangShan emu from Environment (commit 4e0288460d, or any kunminghu-v3 emu whose yunsuan MergeUnit still ORs `vta & tailMask` with `end=0` ⇒ all-ones tail).
2. Unzip the attached workload. Optional rebuild with AM:

   make -C vec06-vl0-keep-vd ARCH=riscv64-xs LINUX_GNU_TOOLCHAIN=0 image

3. Run (same command shape as Environment / Run command):

   <emu> -i vec06-vl0-keep-vd/build/vec06-vl0-keep-vd-riscv64-xs.bin \
         --max-instr=20000 \
         --no-diff

Observed, twice, identical instrCnt:

```
vec06-vl0-keep-vd start
vl0=0 out0=255 out15=255
FAIL vec06 vl=0 vta=1 overwrote old vd
HIT GOOD TRAP at pc = 0x80000206
instrCnt = 2,932  cycleCnt = 20,801
```

`--no-diff` is required on this pin to reach the AM check. Do not attach `--diff` `vle` ABORT logs, and do not attach the discarded `could not set vl=0` AM (`194611`).

[vec06-vl0-keep-vd.zip](https://github.com/user-attachments/files/32525061/vec06-vl0-keep-vd.zip)

### Additional context

Root cause (origin yunsuan `098c505a`, same line on the pin), `yunsuan/src/main/scala/yunsuan/vector/v2/MergeUnit.scala` (not `vector/MergeUnit.scala`):

```
tailMask  := ~endMask
...
private val agnosticMask = Fill(vlenb, vma) & bodyMask & maskOffEn | Fill(vlenb, vta) & tailMask
...
resTmp(i) := Mux(agnosticMask(i), bytes1s, oldVd(i))
```

`Exu.scala` feeds `end := elemIdxMapElemE8Idx(vl, …)` so `vl=0` ⇒ `end=0` ⇒ `endMask=0` ⇒ `tailMask=~0`. `vta=1` then fills every byte with `0xFF`.

A fix should treat `vl=0` / `end==begin==0` as “no tail” (leave old vd), or mask the vta term by `end =/= 0` / `vl =/= 0`.

Do not cite local HEAD yunsuan `de6dcbf` as origin. Origin tree pin is submodule `098c505a`.

DefaultConfig VLEN=128. Encoding is `vsetivli uimm=0`, not `vsetvli rd, x0` (that is VLMAX).

AI: draft assembled with Claude Code. RTL quotes, log signatures, image SHA256, and origin greps were checked against origin/kunminghu-v3 e4566c29e, origin yunsuan 098c505a, and the archived emu logs before filing.
