### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

`vzext.vf4` 和 `vsext.vf4` 只写入了目标向量寄存器的低 64 位（对于 e32,m1 即 elements[0..1]），高 64 位（elements[2..3]）保持为 0。而 Spike/NEMU/QEMU 均期望所有 4 个元素正确扩展并写入。

**规范依据（RVV v1.0 §11.3）：**
> `vzext.vf4` 对每个 active element 执行 SEW/4 → SEW 的零扩展，所有 `vl` 个元素均须正确写入。

**核心 PoC 片段：**
```asm
# mem_region[0..3] = 0xAA（4 字节，vf4 输入）
vsetvli x0, t0, e8, mf4, tu, ma
vle8.v v4, (t6)             # v4 = [0xAA, 0xAA, 0xAA, 0xAA]
vsetvli x0, x0, e32, m1, tu, ma
vzext.vf4 v8, v4

# 期望: v8 = [0x000000AA × 4]，v8_high = 0x000000AA000000AA
# 实测: v8_high = 0x0000000000000000（高半部分 elements[2..3] 未写入）



### Expected behavior

DiffTest 观测结果（详见附件 trigger.log）：
[trigger.log](https://github.com/user-attachments/files/29380822/trigger.log)

Negative Controls（均 CLEAN，详见附件 neg.log）：

[neg.log](https://github.com/user-attachments/files/29381084/neg.log)

根据 RISC-V Vector 规范 v1.0 §11.3，`vzext.vf4` 应将每个 8-bit 输入元素零扩展为 32-bit 输出元素。当 `vl=4` 且输入为 4 个 `0xAA` 时，目标寄存器 `v8` 的 4 个元素应全部为 `0x000000AA`。

对于 VLEN=128 的配置（e32,m1），具体期望为：
- `v8[0] = 0x000000AA`（低 32 位）
- `v8[1] = 0x000000AA`（次低 32 位）
- `v8[2] = 0x000000AA`（次高 32 位）
- `v8[3] = 0x000000AA`（高 32 位）

即 `v8_high`（包含 elements[2..3] 的 64 位）应等于 `0x000000AA000000AA`，而非 `0x0000000000000000`。

### Environment

已运行 `./scripts/bug-report.sh` 生成报告，见附件 `environment.txt`（或 `bug_report.tar.gz`）。

关键环境信息摘要：
- XiangShan commit: 6f49d19e2b (dirty: 1)
- 工具链: riscv64-unknown-elf (GNU Binutils 2.42)
- 参考模型: riscv64-spike-so (1.1.1-dev)
- ISA: rv64gcv_zicsr_zifencei
- 操作系统: Ubuntu 24

[run.sh](https://github.com/user-attachments/files/29380913/run.sh)

.04

### To Reproduce

```bash
# 进入 bug 目录
cd bug_17_vzext_vf4

[run.sh](https://github.com/user-attachments/files/29380960/run.sh)

bash run.sh

bash

export ARCH=rv64gcv_zicsr_zifencei
riscv64-unknown-elf-as -march=${ARCH} -o poc.o poc.S
riscv64-unknown-elf-ld -T link.ld -o poc.elf poc.o
riscv64-unknown-elf-objcopy -O binary poc.elf poc.img

${EMU} -i poc.img --diff ${SPIKE_SO} -I 500 2>&1 | grep -E "different at pc|right =|wrong ="

预期：
v8_high different at pc = 0x00800000de
  right = 0x000000aa000000aa
  wrong = 0x0000000000000000

### Additional context


根因假设（Root-cause Hypothesis）：

XiangShan 的 VEU（向量扩展单元）宽化路径在 `vf4` / `vf8` 操作时，只写入了目标向量寄存器的低 64 位。对于 `e32,m1` 且 VLEN=128 的配置，elements[2..3]（位于高 64 位）未被写入或未被正确清零，导致保留了初始值 0。

`vsext.vf4` 也触发了相同问题，表明有符号/无符号扩展共用同一硬件路径。

可能相关的模块：
- Vector Extend Unit (VEU) 的 `vf4` / `vf8` 写回逻辑
- 目标向量寄存器的高位写使能（write-enable）生成逻辑

与已有 Issue 的关系：
经搜索，未发现 `vzext.vf4` / `vsext.vf4` 相关 Issue（与 `vmv.x.s` 的 #5927 无关，属于独立 Bug）。

[bug_17_vzext_vf4.zip](https://github.com/user-attachments/files/29381132/bug_17_vzext_vf4.zip)

附件文件清单：
- `README.md` - 完整说明文档
- `trigger.log` - DiffTest 触发日志
- `neg.log` - 负控制通过日志
- `objdump.txt` - 反汇编标注
- `sha256.txt` - 文件校验和
