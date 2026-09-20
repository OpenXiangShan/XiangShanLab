# 香山外部贡献项目

本目录用于登记和维护由香山社区、合作实验室、高校、企业或个人贡献的外部项目。项目可以是独立仓库，也可以是围绕香山处理器开展的实验、工具、验证框架、扩展模块或研究成果。

## 项目目录

| 目录 | 当前定位 | 公开仓库 | 状态 |
| --- | --- | --- | --- |
| [`haco/`](./haco/) | HACO 项目资料目录；项目全称、技术范围和维护组织待补充 | 待补充 | 占位 |
| [`memory-safety/`](./memory-safety/) | 面向内存安全的项目资料目录；具体实现和公开仓库待补充 | 待补充 | 占位 |
| [`xiangshan-verification/`](./xiangshan-verification/) | 香山处理器外部验证项目资料目录 | [UnityChipForXiangShan](https://github.com/XS-MLVP/UnityChipForXiangShan) | 已登记 |

新增项目时，请复制 [`CONTRIBUTION_TEMPLATE.md`](./CONTRIBUTION_TEMPLATE.md)，将副本放到对应项目目录，并完整填写项目简介、关系、链接、环境和贡献者信息。

## 与 RISC-V 和香山处理器的关系

- **RISC-V**：RISC-V 提供开放的指令集架构规范。外部项目可以围绕指令实现、编译器、模拟器、验证、性能分析、安全或系统软件开展工作。
- **香山处理器**：香山是基于 RISC-V 的开源高性能处理器实现。本目录中的项目不等同于香山 RTL 主仓库；项目应说明自己是使用香山、扩展香山、验证香山，还是为香山提供工具和基础设施。
- **香山支撑项目**：支撑项目通常不直接改变 CPU 核心 RTL，但会服务于香山的系统集成、运行、验证和研究，例如：
  - DDR：内存控制器、内存模型、内存系统集成和相关验证；
  - Verification：模块级、子系统级、系统级验证，测试生成和覆盖率分析；
  - AI：面向 AI 工作负载的指令扩展、加速器、编译支持和性能评估；
  - NoC：片上网络、互连、Cache 一致性和片上数据传输；
  - Security：内存安全、隔离、可信执行、侧信道分析和安全验证。

项目登记时必须明确依赖的 RISC-V 扩展、香山版本或 commit，以及与上述支撑方向的关系。

## 如何在香山开发环境中使用

项目应优先提供可复现的版本和命令。项目不一定直接使用 `xs-env`，也可以基于香山仓库、NEMU、Difftest、编译器或其他工具链和环境运行。具体参数以项目自己的 README 为准。

```bash
# 按项目实际需要获取香山、环境仓库或其他依赖
git clone --recursive <香山仓库或环境仓库>
git clone --recursive <项目公开仓库>

# 按项目 README 完成依赖安装、环境初始化和工具配置
```

使用时至少应记录：

1. 香山仓库地址、分支和 commit（如项目依赖香山）；
2. 实际使用的环境仓库、工具链及其版本，例如 `xs-env`、NEMU、Difftest 和编译器；
3. 外部项目的构建、仿真、验证或集成命令；
4. 预期输出、测试报告或波形位置；
5. 已知限制和不支持的配置。

相关入口：

- [香山处理器主仓库](https://github.com/OpenXiangShan/XiangShan)
- [香山前端开发环境 xs-env](https://github.com/OpenXiangShan/xs-env)
- [香山官方文档](https://docs.xiangshan.cc/)
- [XiangShanLab](https://github.com/OpenXiangShan/XiangShanLab)
- [DDR：YuQuan](https://github.com/OpenXiangShan/YuQuan)
- [验证：UnityChipForXiangShan](https://github.com/XS-MLVP/UnityChipForXiangShan)

## 组织、实验室和贡献者

项目页面应区分以下角色：

| 角色 | 说明 |
| --- | --- |
| 项目发起组织 | 负责提出项目目标和公开项目 |
| 具体实验室/团队 | 负责项目日常研发、环境维护或技术审核 |
| 项目负责人 | 负责版本、Issue、发布和对外联系 |
| 贡献者 | 负责代码、测试、文档、设计、数据或问题分析 |
| 香山对接人 | 负责说明项目与香山主线或支撑项目的集成关系 |

项目页面还应在贡献者表中记录 GitHub 账号和联系邮箱，便于项目维护和技术问题沟通。邮箱仅在贡献者明确同意公开时填写；未提供或不公开时填写“待补充”或“不公开”，不要根据 GitHub 账号推测邮箱。

姓名、组织、贡献范围和联系方式应以公开仓库、论文、项目主页或贡献者本人确认的信息为准。未知信息填写“待补充”，不要根据仓库名称推测。

## 贡献方式

1. 复制 [`CONTRIBUTION_TEMPLATE.md`](./CONTRIBUTION_TEMPLATE.md)；
2. 填写并核对项目链接、版本、组织和贡献者；
3. 将模板副本放入对应项目目录；
4. 提交 Pull Request，并在说明中列出验证命令和结果。

参与项目协作或贡献申请时：

- **Project Collaboration Proposal**：提议新的协作项目；请说明背景、范围、里程碑、交付物和参与方式。
- **Contribution Certificate Application**：为已完成的香山项目或贡献申请贡献证明；请提供可核验的项目链接和证书信息。
- **参与已有项目贡献**：请先查看项目 README、Issue 或任务入口，说明计划贡献的代码、测试、文档、设计、数据或问题分析内容，并附上可复现的验证命令和结果。

项目代码、文档和数据的许可证以各项目公开仓库中的声明为准。
