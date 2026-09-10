# XiangShan Issue #6135 交付包

Issue：[#6135 RAS disable control is ignored for return prediction and leaves a secret-dependent timing side channel](https://github.com/OpenXiangShan/XiangShan/issues/6135)（2026-06-25，作者 YzhDDDing；维护者回复"V3 稳定后重新审视"，无独立修复 PR）。

姊妹篇：与 [#6149](https://github.com/OpenXiangShan/XiangShan/issues/6149)（[XiangShanLab#55 交付](https://github.com/OpenXiangShan/XiangShanLab/issues/55)）为同一根因；#6149 证明到 secret 相关 wrong-path fetch，本包把证据链延长到**端到端时序侧信道**（rdcycle 架构可读），并在 kunminghu-v2 基线上复现完整攻击链。

本包内容：分析报告、kunminghu-v3 基线（`e85a929a3`）上的 fresh replay 与作者数据的逐拍对照、kunminghu-v2 基线（`0fa7bb82`，仅基线、无 patch）上的移植复现，双 secret 波形（gzip 压缩 VCD）与 monitor 判据产物。

## 目录结构

```text
delivery/
├── analysis.md            分析报告（交付主文档，对外版本）
├── v3.zip / v2.zip        v3/、v2/ 两个复现目录的打包（内容与散装目录一致）
├── v3/                    kunminghu-v3 基线（e85a929a3）复现
│   ├── poc/               作者原版 PoC（ras_disable_sidechannel.S）、复现脚本、
│   │                      monitor（扩展了 6 月版 difftest 信号名候选）、summarize、
│   │                      双 secret bin
│   ├── waves/             双 secret 波形（focused VCD，xz 压缩，~20KB/个；窗口 C2500-C6500）
│   └── results/           monitor JSON/log、运行日志、objdump、pair_summary_2500_6500.json
└── v2/                    kunminghu-v2（0fa7bb82）移植复现
    ├── poc/               移植 PoC（sbpctl bit5→0x5f + mtvec 处理器）、v2 monitor、一键脚本、
    │                      双 secret bin、link.ld
    ├── waves/             双 secret 波形（focused VCD，xz 压缩，~15KB/个；窗口 C8600-C10600）
    └── results/           monitor JSON/log、运行日志、objdump、pair_summary_8600_10600.json
```

## 复现步骤

V3（需要 v3 replay emu，路径可用 `EMU=` 覆盖；本包产物由
`xs-bug-replay-6135/xs-env/XiangShan` @ `e85a929a3` 的 `make emu EMU_TRACE=vcd` 生成）：

```bash
cd v3/poc && bash reproduce_v3_6135.sh        # 默认窗口 C2500-C6500，双 secret + monitor + pair_summary
```

V2（需要 `xs-env/XiangShan/build/emu` 为 kunminghu-v2 构建，即 #6149 工作所用 emu）：

```bash
cd v2/poc && bash run_v2_sidechannel.sh       # 双 secret + monitor + pair_summary
```

波形解压：`xz -d -k <wave>.focused.vcd.xz`（或 `xzcat`）；monitor 可直接对解压后的 VCD 复核：
`python3 monitor_ras_disable_sidechannel.py <wave>.vcd <objdump> <secret> <out.json>`。

预期：两代四组运行的 `success` 均为 `true`；V3 时序差 48/161 拍、V2 为 42/144 拍，
selected probe 恒为快的一侧，`hit_loop` 提交。

## 核心结论（详见 analysis.md）

- **V3 基线**：`RAS_ENABLE=0` 后 S1 µRAS 先行、S3 主 RAS 4 tick 后跟进，把 `ret` 预测为 secret 选定的 ret_site；FTQ fetch → gadget 译码/派遣 → probe load 乱序执行 → DCache AcquireBlock 填充 secret 选定 line → 后端 redirect 恢复（架构结果正确）→ `rdcycle` 读出 48 vs 161 拍，程序走进 `hit_loop`。与作者数据的相对时序逐拍吻合（±8 tick）。
- **V2 基线**：S2/S3 `jalr_target` mux 的 gate 有效，泄露经 `NewFtq.scala:1143-1145`——IFU predecode RET redirect 用无 gate 的 `topAddr`（RAS 栈顶副本）替换 redirect target；同一 PoC 端到端复现（42 vs 144 拍），与 #6149 的 V2 路径定位互相印证。
- **修复状态**：#6135 无修复；#6461（Fixes #6149）修复 S3 主 RAS 路径但 S1 µRAS 残留、且不覆盖 V2——在两代基线上 `sbpctl.RAS_ENABLE` 都不能当作隔离原语。

## 文件校验（SHA-256）

| 文件 | SHA-256 |
| --- | --- |
| `analysis.md` | `9badf93802755c01632bc2451b1da63518a7dc624bf37915825b805ac2aa1951` |
| `v3/waves/ras_disable_sidechannel_secret0_2500_6500.focused.vcd.xz` | `a36aa4d9f87819594f6054baac2cfac562541e5316b3bb73c1647921cc6d0657` |
| `v3/waves/ras_disable_sidechannel_secret1_2500_6500.focused.vcd.xz` | `00247e7e3d30b6433cee9db193a7cb186e6fb6cb4bd77cbcd98cf364b0bdb806` |
| `v2/waves/ras_disable_sidechannel_secret0_v2_8600_10600.focused.vcd.xz` | `5e61df29757ecf626aab3b2d204e45c6501bcfbe454fbc2ed6f4f62d3a2199c4` |
| `v2/waves/ras_disable_sidechannel_secret1_v2_8600_10600.focused.vcd.xz` | `a9600250b0088355a95b1ff1bb93a024febc11636f0e06a446dbe77c7d2508e0` |
| `v3/results/pair_summary_2500_6500.json` | `32758b43aa0647cddb8b00fa061483c2b99c3a67179ca62a8ac28a7c40e5c115` |
| `v2/results/pair_summary_8600_10600.json` | `36319c3f123e846f2052ff22e175d2fd0b128c0cd7184f361401a57c47a0b19d` |
