# #3399 VFALU：修复 fflagsRedMask 误用 outVecCtrl

- 修复提交：`aff5ef05de463d14bed375e76dda971b6b204ea8`（2024-08-19，“Vfalu: fix fflagsRedMask use outVecCtrl (#3399)”）
- 上游 PR：https://github.com/OpenXiangShan/XiangShan/pull/3399
- Bug 版本 emu：`TASK/files/build/emu-a8810fc614fe940b6739ae18ca882a40a8599c4b`（RTL `a8810fc6`）
- 反向验证 emu：`TASK/files/build/emu-03459344a42cb5769a45896f71fb0a5b82d3e038`（其基线已含 `aff5ef05`）
- 测试程序：`pr3399_repro.S`（唯一保留的最终程序）
- 运行日志：`pr3399_repro.baseline.log`（BUG）、`pr3399_repro.fixed.log`（PASS）
- 类别：`vector`（向量）

> 2026-11-26 清理：早期 `fflagsmask.S`、波形变体 `fflagsmask_w.S` 以及
> `probe_red / probe_red2 / probe_ab / probe_ab32 / probe_ab_vl1 /
> probe_unsum_vl1 / min_e16` 等探针及其 VCD 已删除；最终程序波形保留为
> `pr3399_repro.baseline.vcd`。

## 缺陷（来自 diff）

`src/main/scala/xiangshan/backend/fu/wrapper/VFALU.scala` 的
`genMaskForRedFFlag` 生成归约 fflags 的 per-lane 掩码时，读的是 **FU 输入端**
的控制包 `vecCtrl`（即下一条进入 FU 的 uop），而不是正在写回的结果对应的
`outVecCtrl`：

```scala
def genMaskForRedFFlag(sew:UInt): UInt = {
  val default = "b11111111".U
  val f64FoldMask = Mux(vecCtrl.fpu.isFoldTo1_2, "b00000001".U, default)
  val f32Fold = vecCtrl.fpu.isFoldTo1_2 || vecCtrl.fpu.isFoldTo1_4
  val f32FoldMask = Mux1H(Seq(
    vecCtrl.fpu.isFoldTo1_2 -> "b00000011".U,
    vecCtrl.fpu.isFoldTo1_4 -> "b00000001".U))
  ...
}
```

而调用处传的是 **out 段** 的 sew：

```scala
val fflagsRedMask = genMaskForRedFFlag(outVecCtrl.vsew)
```

`fflagsRedMask` 进入 `allFFlagsEn`；归约模式下 `firstNeedFFlags ||
outIsVfRedUnSum` 会把第 1..7 位使能，因此这个掩码决定“被折叠/非活跃 lane 的
异常能否进入结果”。因为 `vecCtrl` 是下一拍进入的 uop，uop *i* 的掩码实际由
uop *i+1* 的 fold 位决定。当下一条指令没有 fold 位（任何非归约指令，例如
`vfadd.vv`）时，掩码退化成 `0b11111111`，非活跃 lane 的异常就泄漏出来。

修复就是把这 8 行里的 `vecCtrl` 换成 `outVecCtrl`
（`git show aff5ef05`）。

## 触发条件（从 diff 推导）

要同时满足三个条件：

1. **归约本身没有合法 fflags**：`vl=1`，只有元素 0 活跃，且元素 0 不是 sNaN；
   sNaN 只放在 tail lane（架构上 tail 元素不得置 NV）；
2. **归约写回拍，FU 输入端是非 fold 的 VFALU 指令**：这样 `fflagsRedMask`
   退化成 `0xFF`，把某个被折叠 lane 已经算出的 NV 也 OR 进来；
3. **尾部指令自身不提交 fflags**：用 `v0=0` 把它全屏蔽，它自己写回时被
   `vlMaskEn` 抑制；但它的组合逻辑每-lane fflags 仍存在，泄漏无歧义。

在基线 `a8810fc6` 上，`vfredusum` 不能暴露这个 bug：该基线带的 yunsuan 已经
修过（`is_fsum_ure_masked` 路径不再为被折叠 lane 计算 NV），没有可泄漏的 NV。
`vfredmax/vfredmin` 的 FA 仍会为被折叠的 sNaN 计算 NV，所以用它构造。

## 程序

`pr3399_repro.S`：

- `v8 = [1.0, sNaN, sNaN, sNaN]`，`v11 = sNaN×4`，`v10/v24/v0 = 0`，`vl=1`；
- case 1：`vfredmax.vs v16, v8, v24`，紧跟
  `vfadd.vv v12, v11, v10, v0.t`；
- case 2：`vfredmin.vs`，同样的尾部指令；
- 读 `fflags`：NV（0x10）存在 → `BUG`；为 0 → `PASS`。

| # | 指令序列 | 正确结果 | 基线上实测 |
|---|---|---|---|
| 1 | `vfredmax.vs` + 全屏蔽 `vfadd.vv`(sNaN+0) | fflags=0 | **0x10（BUG）** |
| 2 | `vfredmin.vs` + 全屏蔽 `vfadd.vv`(sNaN+0) | fflags=0 | **0x10（BUG）** |

## 实测

基线 emu（`emu-a8810fc6...`，`pr3399_repro.baseline.log`）：

```
BUG case1
BUG case2
DONE pr3399
Core 0: HIT GOOD TRAP at pc = 0x80000240
```

已含修复的 emu（`emu-03459344...`，`pr3399_repro.fixed.log`）：

```
PASS casePASS caseDONE pr3399
Core 0: HIT GOOD TRAP at pc = 0x80000240
```

## 波形证据

VCD 为文本格式且体积较大（585 MB / 616 MB，超过 GitHub 单文件 100 MB
限制），因此以 gzip 压缩形式随仓库提供。两个文件均由最终
`pr3399_repro.bin` 生成：

- `pr3399_repro.baseline.vcd.gz`：基线 `emu-a8810fc6...`，窗口 `[9000, 11399]`，解压后 585 MB
- `pr3399_repro.fixed.vcd.gz`：已含修复 `emu-03459344...`，窗口 `[7340, 9751]`，解压后 616 MB

查询前先解压：

```bash
gunzip pr3399_repro.baseline.vcd.gz pr3399_repro.fixed.vcd.gz
```

生成命令：

```bash
# 基线
../../build/emu-a8810fc6... -i pr3399_repro.bin --no-diff -C 11400 \
  --dump-wave --wave-path pr3399_repro.baseline.vcd -b 9000 -e 11399
# 修复版
../../build/emu-03459344... -i pr3399_repro.bin --no-diff -C 9755 \
  --dump-wave --wave-path pr3399_repro.fixed.vcd -b 7340 -e 9751
```

关键拍（实例 `exus_1/Vfalu` 与 `exus_3/Vfalu`）：

| t | 信号 | 值 | 含义 |
|---|---|---|---|
| 9665 | `fflagsRedMask`（exus_1） | **11111111** | 错误掩码（来自输入端的非 fold `vfadd`） |
| 9665 | `firstNeedFFlags` / `outIsResuction` | 1 / 1 | 归约写回拍，掩码被用于 `allFFlagsEn` |
| 9665 | `io_out_bits_res_fflags`（exus_1） | **10000** | 归约写回把被折叠 lane 的 NV 带进 fflags |
| 9665 | `fflagsRedMask`（exus_3） | 00000001 | 同指令另一 uop 的掩码正确 |
| 9665 | `io_out_bits_res_fflags`（exus_3） | 00000 | 对应写回无泄漏 |
| 9666 | `outIsResuction` / `io_out_bits_res_fflags`（exus_1） | 0 / 00000 | 全屏蔽尾部 vfadd 自己写回，被 `vlMaskEn` 抑制 |

9665/9666 两拍共同证明：泄漏发生在归约的写回拍，来源是错误掩码，而不是
尾部 `vfadd` 自身的合法 flags；同一归约在 exus_3 的另一个 uop 取到正确掩码
`00000001`、写回 `00000`，排除了“信号恒错/读错信号”。

复现波形查询：

```bash
python3 tools/vcdtab.py pr3399_repro.baseline.vcd exus_1/Vfalu 9660 9670 \
  'fflagsRedMask$|firstNeedFFlags$|io_out_bits_res_fflags$|outIsResuction$'
```

## 结论

已复现（`reproduced`）：bug 版本 emu 报 `BUG`，含修复的 emu 报 `PASS`，波形证实
泄漏来自 `fflagsRedMask` 取错。
