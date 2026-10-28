# csr_ooo_read_write 复现说明

本目录用于观察 CSR 指令在香山乱序核里的三种不同行为：

| 标记 | 指令 | PC | 预期行为 |
|---|---|---|---|
| (A) | `csrr t3, mscratch` | `0x8000000c` | `mscratch`（0x340）不在 `CSROoORead` 清单，**允许乱序读**，`waitForward=0` |
| (B) | `addi t4, x0, 0x444` | `0x80000010` | 年轻整数指令，与 (A) 无依赖，**可以越过 (A) 执行/写回** |
| (C) | `csrr t5, mstatus` | `0x80000014` | `mstatus`（0x300）在 `waitForward` 清单，**必须等 ROB 排空**后才能派发 |
| (D) | `csrw mscratch, t0` | `0x80000018` | CSR 写，译码置 `noSpecExec=1`，**始终串行化** |
| (E) | `csrw mscratch, t1` | `0x8000001c` | 同上，且要等 (D) 提交流出 ROB |

程序最后在 `0x80000020` 原地跳转，等待仿真 `-C` 到期。

## 依赖

- **RISC-V 工具链**：本机路径
  `/nfs/home/yaoliping/xs_workspace/riscv-verify/tools/riscv64-elf/bin/`
  （`riscv64-unknown-elf-gcc` 10.2.0，支持 `Zicsr`）。Makefile 已写成绝对路径，不在 `PATH` 里也能编。
- **XiangShan emu**：`/nfs/home/yaoliping/XiangShan/build/emu`，编译时已启用 FST trace（`EMU_TRACE=fst`）。
- **波形查看**：
  - 无显示环境，`/nfs/home/share/unitychip/bin/gtkwave` 缺 `libtk8.6.so` / `libJudy.so.1`，**不可用**；
  - 使用 `/nfs/home/share/chenchen/oss-cad-suite/bin/` 下的 `surfer`、`gtkwave`、`fst2vcd`、`vcd2fst`（自带依赖）。

## 目录结构

```
csrwr/
├── csr_ooo_read_write.c   # 主程序（裸机，入口 _start）
├── link.ld                # 链接脚本，入口 0x80000000
├── Makefile               # 编译规则
├── README.md              # 本文件
└── （make 生成）.elf / .bin / .txt
```

## 编译

```bash
cd /nfs/home/yaoliping/xs_workspace/task_note/CSR/src/csrwr
make
```

等价的一条命令（不需要 make，也不依赖 `PATH`）：

```bash
/nfs/home/yaoliping/xs_workspace/riscv-verify/tools/riscv64-elf/bin/riscv64-unknown-elf-gcc \
  -march=rv64imac_zicsr -mabi=lp64 -nostdlib -nostartfiles -ffreestanding -O0 \
  -T link.ld -o csr_ooo_read_write.elf csr_ooo_read_write.c && \
/nfs/home/yaoliping/xs_workspace/riscv-verify/tools/riscv64-elf/bin/riscv64-unknown-elf-objcopy \
  -O binary csr_ooo_read_write.elf csr_ooo_read_write.bin && \
/nfs/home/yaoliping/xs_workspace/riscv-verify/tools/riscv64-elf/bin/riscv64-unknown-elf-objdump \
  -d csr_ooo_read_write.elf > csr_ooo_read_write.txt
```

## 核对反汇编

```bash
cat csr_ooo_read_write.txt
```

期望看到：

```
80000000: 11100293    li    t0,273        # 0x111
80000004: 22200313    li    t1,546        # 0x222
80000008: 33300393    li    t2,819        # 0x333
8000000c: 34002e73    csrr  t3,mscratch   # (A) csr 0x340
80000010: 44400e93    li    t4,1092       # (B) 0x444
80000014: 30002f73    csrr  t5,mstatus    # (C) csr 0x300
80000018: 34029073    csrw  mscratch,t0   # (D)
8000001c: 34031073    csrw  mscratch,t1   # (E)
80000020: a001        j     80000020      # c.j 压缩指令
80000022: 0001        nop                 # 对齐填充
```

说明：

- `addi t0,x0,0x111` 的立即数在 12 位范围内，所以 `li` 只展开成一条 `addi`；
- `csrr rd, csr` 是 `csrrs rd, csr, x0` 的伪指令，`csrw csr, rs` 是 `csrrw x0, csr, rs` 的伪指令；
- `j 1b` 被汇编器压成 2 字节的 `c.j`，后面补一条 `c.nop` 对齐；若想要 4 字节 `j`，在 asm 块开头加 `.option norvc`；
- 程序没有初始化 `mscratch`，它的复位值没有保证。本 dump 里 (A) 实际读到 `0xc4a64dd286bb263f`
  （见 `csrMod.io_out_bits_rData`），不能假定为 0，程序里也不要依赖这个值。

## 运行仿真 + dump 波形

```bash
mkdir -p /nfs/home/yaoliping/xs_workspace/task_note/CSR/wave

cd /nfs/home/yaoliping/XiangShan/build
./emu \
  -i /nfs/home/yaoliping/xs_workspace/task_note/CSR/src/csrwr/csr_ooo_read_write.bin \
  -C 4500 -b 4000 -e 4400 \
  --no-diff --dump-wave \
  --wave-path=/nfs/home/yaoliping/xs_workspace/task_note/CSR/wave/csr_ooo_read_write.fst
```

`-b 4000 -e 4400` 只 dump 启动完成后的窗口；如果换了程序或启动时间变化，按实际波形调整。

## 查看波形

### 时间轴约定

- 本 emu 的 FST trace 里，**一个时间戳就是一拍**；`-b 4000` 表示从第 4000 拍开始 dump，
  所以 `fst2vcd` 出来的第一个时间戳是 `#4000`，之后每拍 +1。
- 下文说的“4161 拍”就是这个时间戳编号，可以直接在波形工具里跳过去。

> 逐信号、逐拍的完整分析（每拍看什么、为什么、容易踩的坑）见
> [`../../file/csr-out-of-order-wave-analysis.md`](../../file/csr-out-of-order-wave-analysis.md)。

### 方式一：Surfer / GTKWave（有显示或 `ssh -X`）

```bash
export PATH=/nfs/home/share/chenchen/oss-cad-suite/bin:$PATH
surfer   /nfs/home/yaoliping/xs_workspace/task_note/CSR/wave/csr_ooo_read_write.fst
# 或
gtkwave  /nfs/home/yaoliping/xs_workspace/task_note/CSR/wave/csr_ooo_read_write.fst
```

打开后：

1. 左侧 **Scopes** 面板按层次展开：
   `TOP → SimTop → cpu → l_soc → core_with_l2 → core → backend`；
2. 用 Signals 面板顶部的搜索框按子串找信号（路径见下面的速查表），双击或拖到右侧波形区；
3. 在时间栏输入 4160 回车跳到程序附近，滚轮缩放、拖动平移；
4. 光标停在一拍，Value 栏显示该拍值；常用信号可以存成 `.gtkw` 下次直接加载。

### 方式二：无显示环境（`fst2vcd` + 脚本）

```bash
/nfs/home/share/chenchen/oss-cad-suite/bin/fst2vcd \
    /nfs/home/yaoliping/xs_workspace/task_note/CSR/wave/csr_ooo_read_write.fst \
    -o /tmp/csr_ooo_read_write.vcd

cd /nfs/home/yaoliping/xs_workspace/task_note/CSR/src/csrwr
python3 vcd_dump.py /tmp/csr_ooo_read_write.vcd 4159 4175 \
    decode.decoders_3.io_deq_decodedInst_pc \
    rob.io_enq_req_5_valid \
    rob.io_enq_req_5_bits_waitForward \
    dispatch.blockedByWaitForward_5
```

`vcd_dump.py` 的规则：

- 参数是 `<vcd> <起始拍> <结束拍> [信号名子串...]`，按**完整层次路径的子串**匹配；
- **只打印非零值**：某拍没打印某个信号，通常就是 0；
- 输出名是路径最后 3 段；遇到重名时在参数里写更长的路径区分；
- VCD 里存的是二进制值，**不能用 grep 搜 `0x8000000c`**，必须用脚本解析。

### 信号路径速查（本 dump 中实测存在）

公共前缀：

```
TOP.SimTop.cpu.l_soc.core_with_l2.core.backend.
```

| 阶段 | 信号 | 说明 |
|---|---|---|
| 译码 | `inner_ctrlBlock.decode.decoders_{0..5}.io_deq_decodedInst_pc`、`_fuType`、`_fuOpType`、`_waitForward`、`_blockBackward` | 4159 拍能同时看到这一组 6 条；`fuType=0x20` 是 CSR，`0x40` 是 ALU |
| 重命名后的属性（ROB 入队口） | `inner_ctrlBlock.rob.io_enq_req_{0..5}_valid`、`_bits_instr`、`_bits_waitForward`、`_bits_blockBackward`、`_bits_firstUop` | rename 已按 CSR 地址清单清过 `waitForward`，这里才是最终值 |
| 派发 | `inner_ctrlBlock.dispatch.firedVec_{0..5}`、`blockedByWaitForward_{0..5}`、`io_enqRob_isEmpty` | `firedVec=1` 表示该 lane 真正派发 |
| ROB 表项 | `inner_ctrlBlock.rob.robBanks_{bank}_{row}_valid`、`_debug_instr`、`_debug_pc` | 表项地址 = `bank + 8*row`（`bank` 0..7，`row` 0..19） |
| CSR 发射队列 | `inner_intScheduler.IssueQueueAluCsrFenceDiv.io_enq_0_valid`、`io_enq_1_valid` | 本 dump 只导出了入队 valid，没有队列内的 instr |
| CSR FU | `inner_intExuBlock.exus_7.in1ToN.io_in_valid`、`exus_7.csr.csrMod.io_in_bits_op`、`_src`、`exus_7.csr.csrMod.mscratch.w_wen`、`io_out_valid`、`io_out_bits_rData`、`io_out_bits_regOut`、`state` | `io_in_bits_op` 是 func3 低 3 位：1=CSRRW，2=CSRRS；`state`：0=s_idle、1=s_waitIMSIC、2=s_finish |
| 提交 | `inner_ctrlBlock.rob.io_commits_commitValid_0_0`、`io_commits_info_0_debug_instr` | `commitValid_0_0=1` 的拍读 `info_0_debug_instr` |

注意：这份 FST 是当前 emu build 导出的，**不是所有 RTL 信号都有**（例如 IBuffer 的出参、
`csrMod.io_in_bits_addr` 就没有）。换 build 后先按名字搜，找不到就找等价信号。

### 按步骤读这个程序

1. **译码（4159 拍）**
   - `decode.decoders_0..5.io_deq_decodedInst_pc` = `0x80000000/04/08/0c/10/14`；
   - 此时所有 CSR 指令的 `_waitForward` 和 `_blockBackward` 都是 **1**（decode 的原始属性），
     包括 (A) `mscratch` 和 (C) `mstatus`。
2. **rename 清属性（看 ROB 入队口，必须和 `_valid` 一起看）**
   - 4162 拍 `rob.io_enq_req_3_valid=1`、`_bits_instr=0x34002e73`，而
     `_bits_waitForward` = **0**、`_blockBackward` = **0**：(A) `mscratch` 不在
     `CSROoORead` 清单里，rename 把它的串行化属性清掉了（脚本只打印非零值，所以这一行
     在 `vcd_dump.py` 输出里看不到；在 Surfer 里能直接看到它是 0）；
   - (C) 在 4161 被 waitForward 挡住，没有随组入队，所以**要到 4172 拍**才看到
     `rob.io_enq_req_5_valid=1`、`_bits_instr=0x30002f73`、`_bits_waitForward` = **1**；
     4162 拍 lane5 的 waitForward 虽然也是 1，但那是上一次的残留值，`valid=0` 时无意义；
   - 下一组的 (D)(E) 在 4181/4190 拍入队，`waitForward=1`、`blockBackward=1`（CSR 写始终串行）。
3. **派发（4161 拍起）**
   - 4161：`firedVec_0..4=1`（lane0–4 发射），`firedVec_5=0`、`blockedByWaitForward_5=1`：
     (C) 被自己的 waitForward 挡住；同批更老的 lane 也要入 ROB，所以即使
     `io_enqRob_isEmpty=1` 也不放行；
   - 4171：ROB 排空，`firedVec_5=1`，(C) 才发射；
   - 4172–4179：`blockedByWaitForward_0/1=1`（有效 lane 是 D/E），D/E 等 (C) 提交、ROB 再次排空；
   - 4180：`firedVec_0=1`，D 发射；4189：`firedVec_1=1`，E 发射。
4. **ROB 入队与压缩（4162 拍）**
   - 4162 拍 `rob.io_enq_req_0..4_valid=1`，但 `_bits_firstUop` = `1,0,0,1,1`：
     `addi t0/t1/t2` 三条连续的 ALU 指令被 `CompressUnit`（译码时 `canRobCompress=1`）
     **压进同一个 ROB 表项**，表项记录的是 t0；A 和 t4 各占一个表项；
   - 4163 拍 `rob.robBanks_6_0/7_0/0_1_valid=1`，对应表项地址 6/7/8，
     `_debug_instr` 分别是 `0x11100293`(t0)、`0x34002e73`(A)、`0x44400e93`(t4)。
5. **CSR FU（A 和 C 的读）**
   - (A)：4165 拍 `exus_7.in1ToN.io_in_valid=1`、`csrMod.io_in_bits_op=2`（CSRRS）；
     4166 拍 `csrMod.io_out_valid=1`，`io_out_bits_rData=0xc4a64dd286bb263f`（mscratch 的旧值）；
   - (C)：4175 拍进入、4176 拍 `io_out_bits_rData=0xa00000000`（本 dump 中 mstatus 的值，
     不要假定为复位值），和 mscratch 的乱序读形成对比。
6. **CSR 写（D/E）**
   - 4184 拍 D 进入 FU，`io_in_bits_src=0x111`、`op=1`（CSRRW）；
     4185 拍 `mscratch.w_wen=1`，写进 0x111；
   - 4193 拍 E 进入 FU，4194 拍 `mscratch.w_wen=1`，写进 0x222；
     这拍 `io_out_bits_rData=0x111`，正好是 D 刚写进去的值，说明两次写严格按序。
7. **提交（按程序顺序）**
   - `commitValid_0_0=1` 的拍：4167 (`0x11100293` t0 组，t1/t2 随它一起退休)、
     4168 (`0x34002e73` A)、4169 (`0x44400e93` t4)、4178 (`0x30002f73` C)、
     4187 (`0x34029073` D)、4196 (`0x34031073` E)；
   - (B) t4 的执行可以比 (A) 早，但**提交一定在 A 之后**；本 dump 的 debug 时间戳
     （`rob.debug_microOp_debugReg_8/7_debugInfo_writebackTime`）也能看到 t4 的写回早于 A。

### 本 dump 实测时间线

| 拍 | 事件 | 关键信号 |
|---|---|---|
| 4159 | 译码 6 条：t0, t1, t2, (A), t4, (C) | `decode.decoders_0..5.io_deq_decodedInst_pc` |
| 4160 | 译码下一组：D, E | `decoders_0/1.io_deq_decodedInst_pc=0x80000018/1c` |
| 4161 | 派发 lane0–4；lane5=(C) 被 waitForward 挡住 | `firedVec_0..4=1`、`firedVec_5=0`、`blockedByWaitForward_5=1` |
| 4162 | ROB 入队 3 个表项（t0 组压缩、A、t4）；A 的 enq `waitForward=0` | `rob.io_enq_req_*`、`robBanks_6/7/[0,1]`（4163 起） |
| 4165 | (A) 进入 CSR FU | `exus_7.in1ToN.io_in_valid=1` |
| 4166 | (A) 读回 mscratch | `io_out_valid=1`、`rData=0xc4a64dd286bb263f` |
| 4167/4168/4169 | t0 组 / A / t4 依次提交 | `commitValid_0_0=1` + `info_0_debug_instr` |
| 4171 | ROB 空，(C) 发射 | `firedVec_5=1` |
| 4175/4176 | (C) 进入 FU / 读回 mstatus | `rData=0xa00000000` |
| 4178 | (C) 提交 | `info_0_debug_instr=0x30002f73` |
| 4180 | ROB 空，D 发射 | `firedVec_0=1` |
| 4184/4185 | D 进入 FU / 写 mscratch=0x111 | `mscratch.w_wen=1`、`src=0x111` |
| 4187 | D 提交 | `0x34029073` |
| 4189 | E 发射 | `firedVec_1=1` |
| 4193/4194 | E 进入 FU / 写 mscratch=0x222 | `mscratch.w_wen=1`、`src=0x222` |
| 4196 | E 提交 | `0x34031073` |

## 常见坑

1. **数据必须和 valid 相与**：`io_commits_info_0_debug_instr` 在没有提交的拍会保留旧值，
   ROB bank 的 `_debug_instr` 在 `_valid=0` 之后也不清空；`rob.io_enq_req_*_bits_*` 同理，
   比如 lane5 的 `waitForward` 在 (C) 入队前就已经是 1，但那只是残留值。
2. **t1/t2 没有单独的提交记录不是 bug**：连续 `addi` 被 ROB 压缩进 t0 的表项（`CompressUnit`，
   `canRobCompress`）。看 `_bits_firstUop`（lane0–2 为 1,0,0）和 `_debug_instr` 就能确认。
3. **`commitValid_0_0` 的 0 不是“ROB 第 0 项”**：`io.commits.commitValid` 是旋转/压缩后的
   提交向量，bit0 表示本拍最老的提交项；本 dump 中每拍提交 1 个表项。
4. **decode 的 `waitForward` 不是最终值**：所有 CSR 在 decode 都是 1，rename 才按
   `CSROoORead` 清单清 0；要看最终属性用 `rob.io_enq_req_*_bits_waitForward`。
5. **mscratch 的初值不是 0**：本 dump 里 (A) 读到 `0xc4a64dd286bb263f`，不能假定复位为 0，
   程序里也不要依赖它。
6. **VCD 值是二进制**：用 `vcd_dump.py`，不要 `grep 0x...`。
7. **信号可见性随 build 变**：换 emu 后先搜名字，找不到就找等价信号；上表是这份 dump 的实测集合。
8. 最开始的约 4000 拍是 BootROM/启动代码，直接看 `-b 4000` 之后的窗口。
