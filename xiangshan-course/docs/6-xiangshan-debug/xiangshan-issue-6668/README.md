# XiangShan Issue #6668 交付包

Issue：[#6668 `csrr x0, vl` / `csrr x0, vlenb` renames x0](https://github.com/OpenXiangShan/XiangShan/issues/6668)
任务：[XiangShanLab#178](https://github.com/OpenXiangShan/XiangShanLab/issues/178)

本包包含：缺陷基线与修复基线的源码定位、issue 原始复现 ELF 与自写用例、四套构建（缺陷基线、缺陷基线 + 最小修复、最新树、最新树受控回退）的构建/运行日志，用于解释并验证 `csrr x0, vl` / `csrr x0, vlenb` 为什么会在 Rename 触发
`rfWen cannot be 1 when Int regfile ldest is 0` 与 `pdest for $0 should be 0`。

## 目录结构

```text
.
├── analysis.md                 分析报告（交付主文档）
├── README.md                   本说明
└── replay/
    ├── case/
    │   ├── xs_csrr_x0_vl.elf   issue 原始复现 ELF（附件原样）
    │   ├── x0_readback.S/.elf  `csrr x0, vl` + `csrr x0, vlenb` 后回读 x0
    │   ├── read_vlenb.S/.elf   rd != 0 正向对照（vlenb = 16）
    │   ├── x0_vtype_control.S/.elf  `csrr x0, vtype` 回归用例（最新树）
    │   ├── link.ld             裸机链接脚本（0x80000000 / tohost=0x80001000）
    │   └── run_cases.sh        一键跑 4 个用例 × {no-diff, NEMU}
    ├── patch/
    │   ├── 0001-fix-pseudo-decode-rd-nonzero.patch  09453ff8f 上的最小修复
    │   └── 0002-revert-prefix-behavior.patch        最新 v3 上的受控回退
    └── logs/
        ├── base09453/          09453ff8f（issue 报告的缺陷基线，原样）
        ├── base09453-fix/      09453ff8f + 0001（同一基线上的最小修复）
        ├── head/               b9638259（含上游修复）
        └── revert/             b9638259 回退 0002（等价于缺陷行为）
```

## 复现步骤（简述）

```bash
# 1) 构建（DefaultConfig、单核、difftest）
git clone --recurse-submodules https://github.com/OpenXiangShan/XiangShan.git
cd XiangShan && git checkout <基线>          # b9638259 / 09453ff8f
export NOOP_HOME=$PWD
make emu CONFIG=DefaultConfig NUM_CORES=1 \
    OPT_FAST="-O3 -fno-tree-slp-vectorize" -j64

# 2) 单条复现
./build/emu -i replay/case/xs_csrr_x0_vl.elf --no-diff -I 60
#   缺陷基线 / 回退构建上：Assertion failed: rfWen cannot be 1 when Int regfile ldest is 0
#                           Assertion failed: pdest for $0 should be 0
#                          （instrCnt = 38, cycleCnt = 8,321，与 issue 附件日志逐项一致）

# 3) 全量对照（4 用例 × no-diff/NEMU）
bash replay/case/run_cases.sh ./build/emu replay/logs/out
#   NEMU so 会自动在 $NOOP_HOME/ready-to-run、$PWD/ready-to-run 等位置查找，
#   也可以用 NEMU=/path/to/riscv64-nemu-interpreter-so 显式指定；
#   summary.txt 里同时给出 rc、分类和 instrCnt/cycleCnt。
```

## 交付清单对照（[XiangShanLab#178](https://github.com/OpenXiangShan/XiangShanLab/issues/178)）

| 清单项 | 交付物 | 位置 |
| --- | --- | --- |
| ① 复现：测试用例、运行方法、结果，注明 commit 与配置 | 4 个用例 + 一键脚本 + 逐次日志；缺陷基线 `09453ff8f`、其最小修复、最新 `b9638259`、受控回退四套结果矩阵 | `replay/case/`、`replay/logs/`、`analysis.md` §1–§2 |
| ② 触发条件与根因，附关键证据 | ISA 语义、V3 四通道译码、Rename/RAT 三段源码证据（含两条基线的行号）+ 原始失败签名 | `analysis.md` §3 |
| ③ 修复方案与前后对照；已有修复附 PR/commit，自行修复附补丁 | 上游 PR [#6779](https://github.com/OpenXiangShan/XiangShan/pull/6779) / `429980ea22`；缺陷基线上的最小修复补丁（已构建验证）；最新树“只回退一行”的正反实验 | `analysis.md` §4–§5、`replay/patch/` |
| ④ 按指定目录提交；如确定修复方案可尝试提出 XiangShan PR | 本目录 + 第 6 章 `VERSIONS.md` 登记。缺陷已由上游修复合入，不需要再提 RTL PR；课程文档以 PR 形式提交到 XiangShanLab | 本目录、`../../VERSIONS.md` |

## 结论摘要（详见 analysis.md）

- **缺陷已修复**：`kunminghu-v3` 的 PR [#6779](https://github.com/OpenXiangShan/XiangShan/pull/6779)（merge commit `429980ea228c`，2026-11-02）在 `PseudoDecodeChannel.scala` 上加了
  `&& instBitFields.RD =/= 0.U`。
- **根因**：`vl` / `vlenb` 走 pseudo 译码通道；该通道直接使用 uop 表的 `gpWen`，没有像 simple/vset/vector 通道那样在 `rd == x0` 时清零。Rename 以 `rfWen` 为唯一依据分配物理寄存器并把 `ldest = 0` 写进 RAT，因此 x0 被映射到装载 `vl` 值的物理寄存器。
- **为什么 V2 没有**：V2 的 `vl` 是普通 CSR，且 `DecodeUnit.scala:1145` 有全局 `ldest != 0` 兜底；V3 分通道译码后该兜底被拆散，pseudo 通道漏掉了。
- **缺陷基线已重建**：在 issue 报告的 `09453ff8f`（difftest `4c64fe86`）上复现出 `38 / 8,321`、`pc = 0x185e2bbf3c2bc`、`RenameTableWrapper.sv:930` / `Rename.sv:5458`，与 issue 附件日志逐项相同；同一基线加上 `patch/0001` 后 4 个用例全部通过。最新树 `b9638259` 只回退那一行也会复现同一签名（行号不同）。

| 用例 | `09453ff8f` | `09453ff8f` + 0001 | `b9638259` | `b9638259` + 0002 |
| --- | --- | --- | --- | --- |
| `xs_csrr_x0_vl`（issue 原样） | ABORT 38 / 8,321 | COMPLETED | COMPLETED | ABORT 38 / 8,321 |
| `x0_readback`（vl + vlenb） | ABORT 6 / 8,234 | COMPLETED | COMPLETED | ABORT 6 / 8,234 |
| `read_vlenb`（rd ≠ 0） | COMPLETED | COMPLETED | COMPLETED | COMPLETED |
| `x0_vtype_control` | COMPLETED | COMPLETED | COMPLETED | ABORT 6 / 8,233 |

## 环境

- Host：AMD EPYC 7H12（2×64C/256T），本机仿真速度约 60 sim-cycle/s（含 ~8k 周期冷启动），因此用例统一用 `-I 60` 截断。
- 工具：Verilator 5.052、OpenJDK 21、mill 0.12.17、`riscv64-unknown-elf-gcc`（xPack 15.2.0）。
- 构建参数：`CONFIG=DefaultConfig NUM_CORES=1 OPT_FAST="-O3 -fno-tree-slp-vectorize"`。
- 注意：本机根文件系统曾满（`/` 100%），构建时需把 `TMPDIR` / `java.io.tmpdir` 指到 NFS 目录；这只影响构建，不影响 RTL 语义。
