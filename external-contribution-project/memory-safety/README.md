# 基于 cpu-sec-bench 的香山内存安全测试

## 1. 项目基本信息

- **项目名称**：基于 cpu-sec-bench 的香山内存安全测试
- **项目简称**：memory-safety
- **项目类型**：`安全`
- **项目状态**：`可复现`
- **许可证**：GPL-3.0
- **最后核验日期**：2026-11-09

## 2. 项目简介

[cpu-sec-bench](https://github.com/comparch-security/cpu-sec-bench) 是一套跨平台的处理器安全测试基准，包含五个类别的测试样例。每个样例对目标平台发起一类典型的内存安全攻击，测试结果记录该攻击是否成功：

| 类别 | 内容 |
| --- | --- |
| `acc` | 内存访问控制（ASLR, IBT, GOT, 函数指针读取等） |
| `cfi` | 控制流完整性（返回地址劫持, 虚表伪造, 代码注入等） |
| `cpi` | 代码指针完整性 |
| `mss` | 空间内存安全（越界读写, 指针算术等） |
| `mts` | 时间内存安全（释放后使用等） |

本项目将上述基准部署在香山的指令级模拟器 NEMU 上运行。本目录附带完整的执行脚本（`venue/` 目录），依次完成测试语料的交叉编译, 客户机镜像组装, NEMU 启动执行和结果汇总；使用者只需另行准备一份 cpu-sec-bench 源码（任意路径，只读使用）和一份已编译的 riscv64 系统模式 NEMU（任意路径），运行 `run_cpu_sec_bench_nemu.sh` 即可完成整个测试流程并输出分类统计结果。

NEMU 是功能级 ISA 模拟器，只实现指令集行为，不包含微体系结构防御机制。因此本项目的测试结果反映的是 RISC-V ISA 层面的内存安全基线：哪些攻击能被体系结构机制（如页权限检查）阻止，哪些攻击在 ISA 层无法阻止。处理器微体系结构的防御能力评估（基于 RTL 或 Difftest 的场景）不在本项目范围内。

本项目面向香山安全与验证方向的研究人员，提供一条可复现, 可跨平台对比的内存安全基线。

## 3. 与 RISC-V, 香山和支撑项目的关系

- **RISC-V 关系**：测试语料基于 RV64，由 riscv64 交叉工具链（Ubuntu gcc-13，RVA23U64 用户态）编译，客户机系统为 OpenSBI + Linux 6.18；测试对象是 ISA 级的内存安全行为，包括页权限故障, 信号语义和系统调用行为。
- **香山处理器关系**：`验证`，通过香山官方指令模拟器 NEMU 执行测试。
- **涉及的香山版本或 commit**：OpenXiangShan/NEMU，采用 `riscv64-xs_defconfig`（系统模式）配置构建，产物为 `build/riscv64-nemu-interpreter`；本登记验证所用版本为 `ae1ec3cd`（2026-10-09 master）。项目不直接依赖香山 RTL 仓库和 Difftest；NEMU 不锁定版本，每次运行所用 commit 与二进制 sha256 均记录在结果中。
- **DDR 关系**：无直接依赖。
- **Verification 关系**：无直接依赖。
- **AI 关系**：无直接依赖。
- **NoC 关系**：无直接依赖。
- **Security 关系**：内存空间安全（`mss`）, 时间内存安全（`mts`）, 控制流完整性（`cfi`）, 代码指针完整性（`cpi`）和访问控制（`acc`）。

## 4. 公开链接

- **项目主仓库**：https://github.com/comparch-security/cpu-sec-bench
- **NEMU 执行环境**：XiangShanLab 仓库的 `external-contribution-project/memory-safety/` 目录，提供运行脚本 `run_cpu_sec_bench_nemu.sh` 与 `venue/` 执行脚本；其构建流程移植自 [cpu-sec-test-env](https://github.com/comparch-security/cpu-sec-test-env) 的 riscv64-nemu 执行环境（2026-10-31 验证版本），两者保持同源，使用本项目不需要克隆该仓库
- **NEMU（香山指令模拟器）**：https://github.com/OpenXiangShan/NEMU
- **Issue / 讨论区**：cpu-sec-bench 仓库的 GitHub Issues
- **论文, 报告或演示材料**：
  - Ciyan Ouyang, Da Xie, Hao Ma, Wei Song, Jiameng Ying, Sihao Shen, and Peng Liu. _MSTest: A property-oriented, comprehensive, and cross-platform test suite of memory safety_. IEEE Transactions on Dependable and Secure Computing, vol. 23, no. 2, pp. 2371–2386, March 2026.
  - Wei Song et al. _A comprehensive and cross-platform test suite for memory security_. [arXiv:2111.14072](https://arxiv.org/abs/2111.14072), November 2021.
  - 沈思豪, 解达, 宋威. 跨平台内存安全测试集设计. 计算机系统应用, 2022, 31(10).
- **上游香山仓库或相关 PR**：无直接依赖

## 5. 在香山开发环境中的使用方法

### 环境要求

- 操作系统：Linux x86_64 宿主机，已在 Ubuntu 上验证。
- 软件依赖（一次性安装；本项目不使用 qemu）：

  ```bash
  sudo apt install build-essential gcc-13-riscv64-linux-gnu g++-13-riscv64-linux-gnu \
                   binutils-riscv64-linux-gnu device-tree-compiler cpio curl git python3 perl make \
                   libsdl2-dev
  ```

  `libsdl2-dev` 供 NEMU 编译使用（其 Makefile 在启用设备模拟时无条件链接 SDL2）。

- 语料编译器为 `riscv64-linux-gnu-g++-13`；NEMU 的准备方式见下文。
- 环境配置的完整说明参见本仓库课程《香山开发环境》（`xiangshan-course/docs/1-xiangshan-development-environment/`）第 1 章与第 4 章。
- 网络：首次运行需下载固定版本的上游源码（workload-builder, OpenSBI, LibCheckpointAlpha 三个源码仓，按 commit 校验；内核与 busybox 源码包，按 sha256 校验），并编译客户机内核。首次运行全程约二十分钟（多核机器实测；主要耗时为客户机内核编译），性能较弱的机器会更长。之后可离线运行，数分钟完成。单步超时上限可用环境变量 `DEPLOY_TIMEOUT` 调整。若不想本地编译，可以直接使用预编译镜像，见下文。
- 磁盘：工作目录（默认 `build/`，已加入 `.gitignore`）约 3–4 GB。

### 获取代码

```bash
# 本项目（XiangShanLab 仓库的一部分）
git clone https://github.com/OpenXiangShan/XiangShanLab

# 测试基准（任意路径，脚本只读使用）
git clone https://github.com/comparch-security/cpu-sec-bench
```

本页始终假设 cpu-sec-bench 的 master 分支可用，不固定基准版本。每次运行会把所用基准 commit 记入结果目录（`versions.txt` 的 `bench-head` 行）；同一 commit 的重复运行应得到逐记录一致的结果，见下文“结果复现与一致性”。

### 准备 NEMU

两种方式任选其一。

方式一（推荐，与《香山开发环境》课程一致）：若已按课程第 1 章克隆并部署了 xs-env，NEMU 源码已包含在 `xs-env/NEMU`，无需单独克隆。source xs-env 的 `env.sh` 之后，`NEMU_HOME` 环境变量指向该目录，运行脚本会自动使用它；此时只需编译系统模式解释器：

```bash
cd $NEMU_HOME
make riscv64-xs_defconfig && make -j$(nproc)
# 编译产物：build/riscv64-nemu-interpreter
```

方式二（未使用 xs-env 时）：单独克隆 NEMU 并编译：

```bash
git clone https://github.com/OpenXiangShan/NEMU
cd NEMU && make riscv64-xs_defconfig && make -j$(nproc)
# 编译产物：build/riscv64-nemu-interpreter
```

运行脚本按 `NEMU_BIN`, `NEMU_DIR`, `NEMU_HOME` 的顺序查找解释器，也可以用 `--nemu` 直接给出 NEMU 检出目录或解释器二进制的路径。

注意：不要使用 `riscv64-xs-novga_defconfig`。该配置面向检查点生产，没有 PLIC 和 RVV 支持，串口 MMIO 行为也与 Linux 负载机不匹配，无法启动本项目的客户机；请始终使用 `riscv64-xs_defconfig`。

### 使用预编译镜像（可选）

镜像组装需要在本地交叉编译客户机内核，是首次运行的主要耗时。如果不希望本地编译，可以使用预先发布的镜像：设置两个环境变量后，运行脚本会跳过语料编译和镜像组装两个步骤，下载并校验镜像后直接启动测试，数分钟即可得到首次结果。cpu-sec-bench 仓库已发布与本登记核验快照配套的镜像（releases 标签 `xiangshan-nemu`，语料构建自 `cbe3bf9`）：

```bash
export CPU_SEC_BENCH_IMAGE_URL=https://github.com/comparch-security/cpu-sec-bench/releases/download/xiangshan-nemu/fw_payload.bin
export CPU_SEC_BENCH_REGISTRY_URL=https://github.com/comparch-security/cpu-sec-bench/releases/download/xiangshan-nemu/configure.json
./run_cpu_sec_bench_nemu.sh --bench /path/to/cpu-sec-bench --nemu /path/to/NEMU
```

脚本会自动下载同目录下的 `.sha256` 文件校验下载内容，也可以用环境变量 `CPU_SEC_BENCH_IMAGE_SHA256` 与 `CPU_SEC_BENCH_REGISTRY_SHA256` 直接指定校验值；校验不符时拒绝执行、不发布任何结果。

镜像内打包了测试语料，与基准版本一一对应。预编译镜像适用于快速试用，以及复现其发布时所基于的那个 commit；以当前 master 做正式运行时，请使用本地构建路径（不设置上述环境变量即可）。使用其他版本的 cpu-sec-bench 时应配套发布对应的镜像。设置 `CPU_SEC_BENCH_IMAGE_BENCH_COMMIT` 后，脚本会将其与 `--bench` 检出的 commit 比对，不一致时给出告警。

### 复用本机已有源码（可选）

三个上游源码仓与两个源码包在本机已存在时可以复用，避免重复下载：

- `WORKLOAD_BUILDER_LOCAL`, `OPENSBI_LOCAL`, `LIB_CHECKPOINT_ALPHA_LOCAL`：指向本机已有的对应 git 检出。脚本将其克隆到工作目录内作为种子并检出所需 commit，已有检出本身不会被修改；种子中缺少所需 commit 时再从上游拉取。
- `CPU_SEC_BENCH_SEED_DIR`：指向一个存放内核与 busybox 源码包（`linux-6.18.56.tar.xz`, `busybox-1.36.1.tar.bz2`）的目录，使用前脚本按 sha256 校验，不匹配的文件不会被使用。

全部设置后，部署阶段无需外网。

### 构建, 运行和验证

```bash
cd XiangShanLab/external-contribution-project/memory-safety

# 完整流程：语料交叉编译, 客户机镜像组装, NEMU 执行, 结果汇总
./run_cpu_sec_bench_nemu.sh --bench /path/to/cpu-sec-bench --nemu /path/to/NEMU
# 已 source xs-env 的 env.sh 时，--nemu 可省略（脚本经 NEMU_HOME 自动定位）；
# --nemu 也可以直接给出解释器二进制路径，或通过环境变量 CPU_SEC_BENCH, 
# NEMU_BIN（或 NEMU_DIR, NEMU_HOME）传入。

# 镜像已存在时，只重新执行测试并汇总结果
./run_cpu_sec_bench_nemu.sh --bench /path/to/cpu-sec-bench --run-only

# 只汇总最近一次运行的结果
./run_cpu_sec_bench_nemu.sh --summary

# 清空工作目录
./run_cpu_sec_bench_nemu.sh --clean
```

三个执行步骤也可以单独调用以便调试：`venue/stage-corpus.sh`（语料编译与校验）, `venue/build-image.sh`（镜像组装）和 `venue/boot.sh`（NEMU 执行与结果提取），用法见各脚本头部的注释。

- **预期输出**：每次运行的结果写入工作目录（默认 `build/`）下的 `results/<UTC 时间戳>/`，包含 `results.json`（逐条测试的结果码）, `results.dat`, `variables.json`, `boot.log`（完整的客户机控制台输出）, `meta.json`（运行元数据：基准 commit, NEMU 与镜像的 sha256, 记录数, 客户机退出码）和 `versions.txt`（运行环境信息）。脚本最后输出分类统计。完整的运行要求记录数与基准注册表条数一致，且客户机以 exit 0 结束, NEMU 报告 `HIT GOOD TRAP`；执行超时的运行不写入任何结果。
- **核验快照（2026-11-09，cpu-sec-bench `cbe3bf9`，NEMU `ae1ec3cd`）**：全部 246 条记录执行完毕，客户机 exit 0，NEMU 报告 `HIT GOOD TRAP`。其中 225 条攻击在 ISA 层执行成功（该层不存在对应的防御机制，属预期基线），其余 21 条为基准注册表中声明的预期结果，包括 5 条静默, 13 条被体系结构或库捕获, 3 条 SIGABRT。各类别的记录数为 `mss` 141, `cfi` 57, `acc` 22, `mts` 16, `cpi` 10。上述数字可用 `--summary` 复测。
- **测试报告**：结果目录即为测试报告，`boot.log` 保存完整的控制台输出。
- **已知限制**：
  1. NEMU 为功能级 ISA 模拟器，不包含微体系防御机制，本基线不能用于评估处理器的硬件安全特性。
  2. 测试语料是蓄意构造的攻击程序。本执行环境中所有指令都在 NEMU 内解释执行，不会以宿主机进程的形式运行；宿主机上需要信任的只有 NEMU 二进制和 `build/` 目录下的镜像文件。
  3. 首次运行需要访问外部网络并下载固定版本的上游源码（或使用预编译镜像跳过本地编译）。上游版本变化时需同步更新 `venue/pins.sh`。
  4. cpu-sec-bench 与 NEMU 不锁定版本。基准或 NEMU 更新后，测试结果可能与本登记的快照不同；每次运行都会记录所用版本，结果可追溯。
  5. 首次运行的主要耗时在客户机内核编译。性能较弱的机器上，单步编译可能超过 `DEPLOY_TIMEOUT` 的默认上限（7200 秒），可按需调大。

### 结果复现与一致性

测试结果由四个版本共同决定：cpu-sec-bench 的 commit、执行包的上游源码版本（`venue/pins.sh`）、NEMU 的版本和交叉工具链版本。四者每次运行都完整记录在结果目录中（`versions.txt` 的 `bench-head`, `nemu-commit`, `nemu-sha256`, `corpus-compiler` 各行，以及 `meta.json` 的镜像 sha256）。

- 日常使用直接基于基准的 master 分支。基准演进而结果变化属正常现象：先用 `--summary` 与结果目录中的 `bench-head` 比对 commit，再判断差异来源。
- 同一 commit、同一组上游版本与同一工具链的重跑，应得到逐记录一致的结果。本登记验证期间，同一基准 commit 的三次独立部署产生了逐字节一致的镜像和逐记录一致的结果。
- 需要精确复现某个已记录结果（如本页的核验快照）时，检出该结果所记录的基准 commit 后重跑，用 `--summary` 对比即可。

## 6. 组织, 实验室和贡献者

- **项目发起组织**：北京科技大学计算机与通信学院
- **具体实验室/团队**：网络空间安全系处理器体系结构安全研究小组
- **项目负责人**：宋威，北京科技大学计算机与通信工程学院教授（https://wsong83.github.io/）
- **项目负责人 GitHub 账号**：wsong83
- **项目负责人邮箱**：wsong83@gmail.com, wsong83@ustb.edu.cn
- **香山对接人**：袁苗苗
- **贡献者**：

| 贡献者 | GitHub 账号 | 邮箱 | 组织/实验室 | 贡献内容 | 公开主页 |
| --- | --- | --- | --- | --- | --- |
| 宋威 | wsong83 | wsong83@gmail.com | 北京科技大学计算机与通信工程学院| 项目管理，机制设计，测例设计与维护 | https://wsong83.github.io/ |
| 欧阳慈俨 | zintown | Ciyan_UCAS@outlook.com | 中国科学院信息工程研究所| 测例设计与维护 | https://github.com/zintown |
| 解达 | xxie-xd | xieda@iie.ac.cn | 中国科学院信息工程研究所 | RISC-V测例设计 | https://github.com/xxie-xd |
| 杨杼鑫 | doyo2024 | yangzhuxin21@mails.ucas.ac.cn | 中国科学院大学网络空间安全学院 | 测试与维护 | https://github.com/doyo2024 |
| 应佳蒙 |  |  | 中国科学院信息工程研究所 | 初始测试集设计 |  |
| 李博雅 | Liboya-CB |  | 中国科学院信息工程研究所 | 初始测试集设计 | https://liboya-cb.github.io/ |
| 马浩 |  |  | 中国科学院信息工程研究所 | CVE覆盖与测例分类评估 |  |

## 7. 贡献方式

- **适合贡献的方向**：
  - 向 cpu-sec-bench 补充或移植攻击样例；
  - 将本执行环境扩展到香山 RTL / Difftest 场景，在 ISA 基线之上评估微体系防御；
  - 跨平台对比分析（x86_64 宿主机, qemu riscv64, NEMU, CHERI 平台）与结果解读；
  - 结果的可视化与报告工具。
- **应用负载分析内容**：不适用。
- **参与方式**：通过 cpu-sec-bench 仓库的 Issue/PR 提交；本目录的登记文档与执行脚本通过 XiangShanLab 仓库的 PR 提交。
- **Issue / 任务入口**：https://github.com/comparch-security/cpu-sec-bench/issues
- **提交前检查**：以 cpu-sec-bench 仓库自身的要求为准。
- **代码审查要求**：以 cpu-sec-bench 仓库自身的要求为准。
- **行为准则或安全披露入口**：不适用。

## 8. 版本和变更记录

| 日期 | 版本或 commit | 变更内容 | 维护者 |
| --- | --- | --- | --- |
| 2026-11-09 | 初始登记 | 完成项目登记；新增 NEMU 执行脚本（`run_cpu_sec_bench_nemu.sh` 与 `venue/`，覆盖语料交叉编译, 客户机镜像组装, NEMU 执行与结果提取），构建流程与上游源码版本移植自 cpu-sec-test-env 的 riscv64-nemu 执行环境，并在全新克隆的 cpu-sec-bench（`cbe3bf9`）与 NEMU（`ae1ec3cd`）上完成端到端验证 | 宋威 |
