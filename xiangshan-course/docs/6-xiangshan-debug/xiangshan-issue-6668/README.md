# XiangShan Issue #6668 交付包

Issue：[#6668 `csrr x0, vl` / `csrr x0, vlenb` renames x0](https://github.com/OpenXiangShan/XiangShan/issues/6668)
任务：[XiangShanLab#178](https://github.com/OpenXiangShan/XiangShanLab/issues/178)

本包包含：缺陷基线与修复基线的源码定位、issue 原始复现 ELF 与自写用例、两个基线上的构建/运行日志、以及“只回退一行”的受控实验，用于解释并验证 `csrr x0, vl` / `csrr x0, vlenb` 为什么会在 Rename 触发
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
#   回退构建上：Assertion failed: rfWen cannot be 1 when Int regfile ldest is 0
#                Assertion failed: pdest for $0 should be 0
#               （instrCnt = 38, cycleCnt = 8,321，与 issue 附件日志逐位一致）

# 3) 全量对照（4 用例 × no-diff/NEMU；spike-so 复位向量不同，不可用）
bash replay/case/run_cases.sh ./build/emu replay/logs/out
```

## 结论摘要（详见 analysis.md）

- **缺陷已修复**：`kunminghu-v3` 的 PR [#6779](https://github.com/OpenXiangShan/XiangShan/pull/6779)（merge commit `429980ea228c`，2026-11-02）在 `PseudoDecodeChannel.scala` 上加了
  `&& instBitFields.RD =/= 0.U`。
- **根因**：`vl` / `vlenb` 走 pseudo 译码通道；该通道直接使用 uop 表的 `gpWen`，没有像 simple/vset/vector 通道那样在 `rd == x0` 时清零。Rename 以 `rfWen` 为唯一依据分配物理寄存器并把 `ldest = 0` 写进 RAT，因此 x0 被映射到装载 `vl` 值的物理寄存器。
- **为什么 V2 没有**：V2 的 `vl` 是普通 CSR，且 `DecodeUnit.scala:1145` 有全局 `ldest != 0` 兜底；V3 分通道译码后该兜底被拆散，pseudo 通道漏掉了。
- **受控实验**：在最新 v3 上只回退那一行，issue 原始 ELF 立即复现两条断言；恢复后不再触发。

## 环境

- Host：AMD EPYC 7H12（2×64C/256T），本机仿真速度约 60 sim-cycle/s（含 ~8k 周期冷启动），因此用例统一用 `-I 60` 截断。
- 工具：Verilator 5.052、OpenJDK 21、mill 0.12.17、`riscv64-unknown-elf-gcc`（xPack 15.2.0）。
- 构建参数：`CONFIG=DefaultConfig NUM_CORES=1 OPT_FAST="-O3 -fno-tree-slp-vectorize"`。
- 注意：本机根文件系统曾满（`/` 100%），构建时需把 `TMPDIR` / `java.io.tmpdir` 指到 NFS 目录；这只影响构建，不影响 RTL 语义。
