# 阶段二：条件分支循环

`main.S` 只有一个持续运行的奇偶循环。`branch_even` 的 beq 判断奇偶，偶数 taken、奇数 not-taken；每轮 i 加一，通过 `jal zero, loop` 无条件返回循环开头。只有一条条件分支，没有嵌套循环，不使用栈或数据内存。

## 编译

在本目录执行：

```bash
make
```

默认工具链前缀为 `riscv64-linux-gnu-`。使用另一套工具链时：

```bash
make CROSS_COMPILE=riscv64-unknown-elf-
```

产物位于 `build/`：`main.elf`、仿真使用的 `main.bin` 和反汇编 `main.disasm`。链接入口为 `0x80000000`，应与仿真镜像装载地址一致。

使用当前链接脚本时，主要标签地址如下；以生成的反汇编核对：

| 标签 | 地址 | 含义 |
| --- | --- | --- |
| `_start` | `0x80000000` | 程序入口 |
| `loop` | `0x80000040` | 循环入口 |
| `branch_even` | `0x80000044` | 主要观察的 beq |
| `even_path` | `0x8000004c` | beq 的 taken 目标 |
| `jal zero, loop` | `0x80000050` | 无条件返回同一个循环 |

完成 n 轮时，t0=n、t3=n/2 向下取整。程序不主动退出，通过 emu 的周期上限停止；达到周期上限不等同于执行了成功退出 trap。循环后填充 nop 供提前取指使用。

## 运行并导出波形

指定你自己的、支持 tracing 的 emu：

```bash
make run EMU=/absolute/path/to/your/emu
```

该命令加载 raw binary，从周期 0 开始导出波形，默认观察上限为 100000 周期。波形输出位置是 `build/stage2.vcd`，实际格式由 emu 的 tracing 构建决定；后缀不会强制转换格式。运行参数采用 `--dump-wave-full --wave-path`，可先用你的 `emu --help` 核对支持情况。

如需更多训练样本：

```bash
make run CYCLES=1000000 EMU=/absolute/path/to/your/emu
```

通过 CYCLES 控制观察时长，无需修改循环次数或重新编译。延长观察可以增加训练机会，但不保证必然出现某个 provider/alternate 命中组合或 S2 redirect。此前 `build/loops-*` 下的有限循环产物不再使用，新的运行命令只加载 `build/main.bin`。

可以调整波形路径和额外 emu 参数：

```bash
make run EMU=/absolute/path/to/your/emu WAVE=/absolute/path/to/stage2.vcd
make run EMU=/absolute/path/to/your/emu EMU_FLAGS='--diff=/absolute/path/to/ref.so'
```

默认使用 `--no-diff`，用于先观察前端波形；这不代表完成差分验证。若当前工作流要求额外的启动镜像或参数，也通过 `EMU_FLAGS` 传入。编译后的程序入口可能晚于复位 ROM 的执行，不要将第一条启动预测直接认作 `branch_even`。

## 波形定位

先找分支 PC `0x80000044`，确认预测块起点与块内 CFI 位置，再将同一动态分支的 FTQ 指针对齐。`0x80000050` 是无条件跳转，不应把它的 taken 信息当作 beq 的方向；块级 taken 信号必须结合 CFI 位置辨认。

加入阶段 valid/fire/PC、`s1_pred_taken`、`s2_redirect`、`s3_redirect`、`provider_hit`、`alt_hit`、provider/alternate 计数器与选择策略、`sc_sum`、`sc_used`、实际分支方向和后端误预测信息。`sc_sum` 使用有符号显示。

在文章图 7、图 10、图 11 的占位中分别补充 cold miss、预测正确和 S2 方向修正窗口。首次执行 beq 的真实结果为 taken；后续执行交替 T/N。没有 S2/S3 redirect 只表示预测级之间一致，是否正确仍需对照执行结果；S2 redirect 则表示后级修改早期预测，不能单凭这一信号断言 TAGE 错误。
