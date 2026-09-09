# XiangShan Issue #6149 交付包

Issue：[#6149 `sbpctl.RAS_ENABLE` 无法禁用 RAS/µRAS，secret 相关的 wrong-path fetch](https://github.com/OpenXiangShan/XiangShan/issues/6149)（修复 PR [#6461](https://github.com/OpenXiangShan/XiangShan/pull/6461)）。

本包内容：分析报告、两代处理器（kunminghu-v3 / kunminghu-v2）基线上的 PoC 与判据脚本、泄露事件的波形与解析结果，以及 PR #6461 head 的验证（修复暴露的 assertion 问题 + S1 µRAS 残留）。

## 目录结构

```text
delivery/
├── analysis.md            分析报告（交付主文档）
├── v3/                    kunminghu-v3 基线（e85a929a3）上的复现
│   ├── poc/               PoC（issue 作者原版 + mtvec 处理器）、复现脚本、monitor/summarize
│   ├── waves/             双 secret 波形（FST，窗口 C6500-C9000）
│   └── results/           monitor JSON/log、运行日志、objdump、pair_verify.json
├── v2/                    kunminghu-v2（0fa7bb82）上的移植复现
│   ├── poc/               移植 PoC（sbpctl bit5 → 0x5f + mtvec 处理器）、一键脚本、monitor/summarize
│   ├── waves/             双 secret 波形（VCD，窗口 C9400-C10200）
│   └── results/           monitor JSON/log、运行日志、objdump、pair_summary_v2.json
└── v3-patched/            PR #6461 head（dcdf1c1c9）验证
    ├── poc/               最小 assertion PoC（RAS_OFF 开关）、S1 残留实验 PoC、monitor、bin
    ├── waves/             S1 残留实验双 secret 波形（VCD，窗口 C11000-C13500）
    └── results/           assertion 三组对照日志、monitor JSON/log、objdump、pair_patched.json
```

## 复现步骤

V3 基线（需要 v3 replay emu，路径可在脚本中用 `EMU=` 覆盖）：

```bash
cd v3/poc && bash reproduce_v3.sh        # 双 secret 运行 + monitor + pair_verify.json
```

V2（需要 `xs-env/XiangShan/build/emu` 为 kunminghu-v2 构建）：

```bash
cd v2/poc
SECRET_BIT=0 bash run_v2_secret_fetch.sh
SECRET_BIT=1 bash run_v2_secret_fetch.sh
python3 summarize_pair_v2.py
```

V3-patched（需要按 `v3-patched/README.md` 构建两个 emu：PR head 原样 / PR head + assertion 禁用）：

```bash
cd v3-patched/poc
# 实验 1：最小 assertion PoC（RAS_OFF=1 abort / RAS_OFF=0 正常 / 禁用 assertion 后正常）
# 实验 2：S1 残留（在 assertion 禁用构建上复跑双 secret + monitor）
```

预期：v3 与 v2 的 `secret_dependent_wrong_path_fetch` 均为 `true`；v3-patched 的实验 1 中 `RAS_OFF=1` 在 PR head 原样构建上 abort，实验 2 中 S1 仍有禁用态 µRAS 预测事件但无 fetch。

## 核心结论（详见 analysis.md）

- **V3 基线**：`RAS_ENABLE=0` 时 S1 µRAS 与 S3 主 RAS 的 target mux 不检查 enable，secret 相关返回地址直接成为预测 target 并被 FTQ fetch（波形：secret1 的 S3 路径 C6728 push → C6738 fetch）。
- **V2 基线**：S2/S3 `jalr_target` mux 有 gate，但 RAS 栈顶经 `last_stage_spec_info.topAddr` 无 gate 存入 `ftq_redirect_mem`，IFU predecode 发现 RET 时 FTQ 直接用它替换 redirect target（`NewFtq.scala:1143-1145`）——同一 bug 类经第三条 consumer 路径复现（波形：C9824 push → C9867 redirect → C9869 fetch）。
- **两代基线**均由后端执行 `ret` 后 redirect 恢复，架构结果正确；危害是 secret 相关的 wrong-path fetch（Spectre 类侧信道前提）。
- **PR #6461**：S3 主 RAS 路径修复完整（state/consumer/fetch 三层验证）；**S1 µRAS 残留**（`MicroRas.scala` 仍不消费 `io.enable`，6 次 secret 相关 S1 预测事件，本配置下未转化为实际 fetch）；**修复暴露既有 assertion 缺陷（非硬件功能错误）**——#6461 的 gate 修复使 "RAS 禁用时 return 由 mBTB 提供" 成为正常状态，而 #5639 引入的旧 assertion 仍要求 return 的 source 为 RAS，最小 PoC（关 RAS + 两条 ret）即 fatal abort；禁用该 assertion 后功能行为与 RAS 开启时完全一致，故应随 PR 更新断言（对 `!ras_enable` 豁免）。

## 文件校验（SHA-256）

| 文件 | SHA-256 |
| --- | --- |
| `analysis.md` | `57e6ce76dd0185de812f1756cdf280dbc046a9d47374fa8858d06bda15684023` |
| `v3/waves/ras_enable_secret0_mtvec_6500_9000.fst` | `41bb80ec2101c9ecb94e393cc65cc0e09fcf7169bb8d64b6d2efdbd2860eef2a` |
| `v3/waves/ras_enable_secret1_mtvec_6500_9000.fst` | `0842a332b7c61f996409d2308d5f668e1e265eb335861fc1399921c53e4fdbd9` |
| `v2/waves/ras_enable_secret0_v2_9400_10200.vcd` | `dfc2d14ce30dfa71902c950978274fa523895207ba6d5ecd5802aae4246c7dda` |
| `v2/waves/ras_enable_secret1_v2_9400_10200.vcd` | `0bccb02152d374cc090c7bfe98a6a833f48bf40dc8fea23b3e3bed8f7e013852` |
| `v3/results/pair_verify.json` | `5d38fcdd7bd1d36f1e59545961dfee6bfcb75f6c5403f3d0bebfd85ecb484520` |
| `v2/results/pair_summary_v2.json` | `8463ac7d81319a2c36e292a906974f10390a985afc42b574934b49650af15678` |
| `v3-patched/waves/patched_s0_11000_13500.vcd` | `a0d2dbfc2345fcde50bfa372aa428e18aa4df118eb81570281c2987fe509b21f` |
| `v3-patched/waves/patched_s1_11000_13500.vcd` | `bad38ed2fc4510fb40eb5eaa1fdaf401c416e2348c0eae5e3bf6f464075f4974` |
| `v3-patched/results/pair_patched.json` | `274ffe024f13c9895a2ab820dd657888841aaa77e878786ba53a0554ff859cdb` |
