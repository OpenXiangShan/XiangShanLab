# scenario4_l3_probe_l1 场景构造程序分析

## 1. 场景目标

本程序构造双核一致性场景：core 0 先读取并缓存目标 line，core 1 在确认 core 0
已经完成后读取同一 line。LLC 发现 peer RN0 持有该 line，向 core 0 发起 CHI snoop；
core 0 L2 将 snoop 转换为 L1 TileLink `Probe`，L1 返回 `ProbeAck`，随后 core 1
获得目标数据并完成 load。

该场景验证的是 **LLC/CHI snoop 到 L2/L1 Probe 的跨层路径**，不是普通的单核 miss
或 replacement 场景。

## 2. 共享数据与同步变量

程序定义：

| 对象 | 属性 | 用途 |
| --- | --- | --- |
| `target_line` | 64-byte 对齐，初值 `0x1122334455667788` | 两个 hart 访问的共享 cache line |
| `hart0_ready` | 独立 cache line 对齐 | core 0 通知 core 1 可以读取 |
| `hart1_done` | 独立 cache line 对齐 | core 1 通知 core 0 已完成 |
| `CACHE_LINE_BYTES` | `64` | 目标 line 对齐大小 |

目标地址为 `0x80001680`。`hart0_ready` 和 `hart1_done` 单独对齐，减少同步变量与
目标 cache line 发生同 line false sharing 的可能。

## 3. 目标 load 封装

```c
static inline uint64_t load_target(void) {
  uint64_t value;
  asm volatile("ld %0, 0(%1)" : "=r"(value) : "r"(&target_line) : "memory");
  return value;
}
```

函数使用显式 RISC-V `ld` 指令，并通过 `memory` clobber 告知编译器该汇编访问内存。
因此场景中的关键数据访问不会被编译器替换为普通软件计算。两个 hart 调用同一个
函数，但实际运行上下文不同，分别对应 core 0 和 core 1 的 DCache。

## 4. Core 0 执行流程

### 4.1 第一次读取目标 line

```c
if (hart == 0) {
  if (load_target() != TARGET_VALUE) {
    return 1;
  }
```

core 0 首先读取目标 line。冷启动下预期产生目标地址的 L1 `AcquireBlock`，随后从
下级获得 `0x1122334455667788`。该 load 在反汇编中的目标 PC 为 `0x80000190`。

波形应确认：

1. core 0 DCache 对目标地址发出 `AcquireBlock`。
2. core 0 收到正确数据且 `denied=0`、`corrupt=0`。
3. core 0 ROB 中目标 load 提交。

### 4.2 发布 core 0 已就绪

```c
asm volatile("fence rw, rw" ::: "memory");
hart0_ready = 1;
asm volatile("fence rw, rw" ::: "memory");
```

第一次 fence 将目标 load 与通知动作隔开，随后对 `hart0_ready` 写 1。第二次 fence
保证通知写入在程序顺序上先于后续等待。core 1 通过轮询该变量决定何时发起目标访问。

### 4.3 等待 core 1 完成

```c
while (!hart1_done) {
  asm volatile("fence rw, rw" ::: "memory");
}
return 0;
```

core 0 不再访问目标 line，而是等待 core 1 写入 `hart1_done`。这样 core 0 的目标
副本在 core 1 发起请求期间保持存在，便于 LLC 观察到 peer RN0 持有该 line。

## 5. Core 1 执行流程

### 5.1 等待 core 0 发布就绪

```c
while (!hart0_ready) {
  asm volatile("fence rw, rw" ::: "memory");
}
asm volatile("fence rw, rw" ::: "memory");
```

core 1 不能在 core 0 完成第一次读取前访问目标 line。轮询与 fence 共同构造了明确的
先后关系，减少 core 1 先完成而无法形成 peer snoop 的情况。

### 5.2 读取同一目标 line

```c
if (load_target() != TARGET_VALUE) {
  return 2;
}
```

core 1 对与 core 0 完全相同的 `0x80001680` 发起 load，反汇编中的目标 PC 为
`0x80000154`。由于 core 0 已持有目标副本，LLC 处理 core 1 的请求时预期满足：

1. `peerRNs_hit_s4=1`。
2. `request_snoop_s4=1`。
3. 生成指向 RN0 的 `SnpNotSharedDirty`。
4. core 0 L2 接收 CHI snoop。
5. core 0 L1 接收 TileLink B `Probe`。
6. core 0 L1 返回 TileLink C `ProbeAck`。
7. core 0 L2 返回 CHI `SnpResp`，txnID 与 snoop 请求一致。
8. core 1 收到正确 `GrantData` 并提交目标 load。

### 5.3 通知完成并保持运行

```c
hart1_done = 1;
asm volatile("fence rw, rw" ::: "memory");

while (1) {
  asm volatile("nop");
}
```

core 1 完成目标 load 后写 `hart1_done=1`，通知 core 0 可以退出。之后 core 1 保持
运行，避免测试环境过早结束导致 core 0 的等待或最后事务在波形中被截断。

## 6. 与波形的对应关系

| 程序阶段 | 关键波形现象 | 判定 |
| --- | --- | --- |
| core 0 首次 `load_target` | `0x80001680` 的 `AcquireBlock` 和正确 `GrantData` | core 0 建立副本 |
| `hart0_ready` 发布 | core 1 仅在此后进入目标 load | 保证访问顺序 |
| core 1 目标请求 | 同地址 `AcquireBlock` | 触发共享一致性处理 |
| LLC 决策 | `peerRNs_hit=1`、`request_snoop=1`、snoop vector `1/0` | 选择 RN0 作为 probe 对象 |
| CHI snoop | LLC TxSnp 与 core 0 L2 RxSnp 地址/opcode/txnID 一致 | snoop 跨层传递 |
| L1 一致性动作 | B `Probe`，C `ProbeAck` | core 0 L1 参与一致性 |
| CHI 响应 | `SnpResp` txnID 与 1041 一致 | snoop 往返完成 |
| core 1 完成 | 正确 `GrantData`、E `GrantAck`、目标 commit | 请求方事务完成 |

## 7. 构造成立的前提与限制

- `hart0_ready` 的同步只表达程序层面的先后关系；具体缓存可见性仍依赖 `fence` 和
  硬件一致性实现，最终必须检查波形。
- 程序只要求 core 0 先读、core 1 后读同一地址；它不直接规定 core 0 的权限状态。报告
  中观察到的是 `Probe` 参数为 1、L1 返回 `ProbeAck`，说明该实现选择降权并保留共享副本。
- core 0 不执行主动 eviction，因此 peer RN 命中应来自 core 0 的目标访问副本，而不是
  本程序显式构造的 replacement。
- `while (1)` 是为了保留双核测试窗口，不是缓存协议的一部分。
- 若平台地址映射、RN 编号或 LLC slice 数变化，必须重新确认目标地址、snoop vector
  和信号路径。

## 8. 相关文件

- 源程序：`scenario4_l3_probe_l1.c`
- 反汇编：`disassembly.txt`
- 波形：`l3_probe_l1.fst`
- 波形报告：`WAVEFORM_ANALYSIS.md`
