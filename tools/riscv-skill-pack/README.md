# RISC-V Skill Pack

> RISC-V 规范学习、Gap 分析、需求分析、治理跟进的一站式 skill pack。

`riscv-skill-pack` 是面向 RISC-V 生态贡献者（软件开发、硬件开发、验证、
产品经理、架构师）的 OpenCode skill 合集。它将此前分散的四个 skill
合并为一个角色感知的入口（`SKILL.md`），由入口路由到对应的子文件。

所有子文件都是**脱敏模板**：会员名称、内部代号、内部路径均已替换为
通用占位符（`MEMBER-XX-XXX`、`ORG-00`、`RVI-XXX`），可直接用于开源
仓库。使用前请按 [脱敏与占位符约定](#脱敏与占位符约定) 替换为实际标识。

---

## 安装

### 前提条件

- [OpenCode](https://github.com/) 环境已配置并可加载 skill
- Skill 目录已纳入 OpenCode 的 skill 搜索路径
- （可选）如果要使用 `gap-analysis` 的幻灯片生成流程，需要能够产出
  PPTX 的工具链（例如 python-pptx 或等价工具）

### 安装步骤

1. **克隆本仓库**到 OpenCode 的 skill 目录下：

   ```bash
   git clone <your-fork-url> riscv-skill-pack
   ```

   或者将本目录添加为子模块：

   ```bash
   git submodule add <your-fork-url> riscv-skill-pack
   ```

2. **在 OpenCode 配置中注册 skill**。确认 `SKILL.md` 的 frontmatter
   可被 OpenCode 识别：

   ```yaml
   ---
   name: riscv-skill-pack
   version: "1.0"
   description: >
     One-stop RISC-V skill pack covering specification learning, gap
     analysis, requirement analysis, and ecosystem governance.
   license: Apache-2.0
   ---
   ```

3. **重启 OpenCode**，使新的 skill 入口生效。

4. **验证安装**。在 OpenCode 会话中输入：

   ```text
   /skill riscv-skill-pack
   ```

   应能看到入口 `SKILL.md` 的概述与子文件路由表。

> **Loading convention**: 子文件通过相对路径引用，例如
> `sub-skills/spec-learning.md`。不要硬编码绝对路径，以便在不同仓库
> 根目录下都能工作。

---

## 使用指南

### 快速开始

通过入口 `SKILL.md` 路由到 4 个子文件，每个子文件自包含、可独立加载：

| 子文件 | 路径 | 核心能力 |
|------|------|---------|
| **spec-learning** | `sub-skills/spec-learning.md` | ISA spec 文档导航（5 层引用层级：UDB → Normative → Ratified → post-v0.7 → Un-ratified）+ Tier-1 规范映射方法（RISC-V ↔ Arm/x86） |
| **gap-analysis** | `sub-skills/gap-analysis.md` | 单 feature Gap 分析文档（9 节结构，§1 Exec Summary → §9 Revision Log）+ 严格 3 slides/feature 的 PPTX 评审材料 |
| **requirement-analysis** | `sub-skills/requirement-analysis.md` | 需求分析工作流（spec → feature-mapping CSV，带 source tag 与 mapping type）+ feature list 参考 + 需求文档查询 |
| **governance** | `sub-skills/governance.md` | TG spec 成熟度跟踪 + ratification 里程碑监控 + 伙伴需求传递（9 维框架、7 步流程）+ 双语术语表（CN↔EN） |

> **设计原则**
> - **Evidence-first**：每个断言必须带 source tag（`[T1-VERIFIED]`、
>   `[T2-CROSS-CHECKED]`、`[UNVERIFIED]`）。禁止臆造。
> - **Redacted by default**：所有子文件使用通用占位符。
> - **Sequential where needed**：`gap-analysis` Part 2（slides）必须
>   以 Part 1 doc 作为输入，禁止在 slides 中重新推导证据。

### 何时激活

当任务涉及以下任一场景时，加载本 skill 入口：

- 查询或引用 RISC-V 规范、扩展、CSR 或指令
- 执行 feature gap 分析或产出 gap/评审文档
- 构建、刷新或查询 CPU 需求矩阵
- 跟踪 Task Group 的 spec 成熟度或向伙伴传递需求
- 翻译架构文档（CN ↔ EN）并保持术语一致

然后**分发**到匹配的子文件。不要从入口文件直接执行工作流。

### 跨子文件流程（典型路径）

一个真实的任务流通常横跨多个子文件：

```text
1. /skill spec-learning        → 用 tier-1 来源建立证据基线
2. /skill requirement-analysis → 找到匹配的需求行与优先级
3. /skill gap-analysis         → 产出 F{N} gap doc + 评审 slides
```

始终从 `spec-learning` 开始，建立证据基础，再扇出到分析型子文件。

---

## 角色推荐

| 角色 | 主要子文件 | 次要子文件 | 典型任务 |
|------|-----------|-----------|---------|
| **软件开发** | `spec-learning`, `requirement-analysis` | `gap-analysis` | 查询 ISA spec / CSR 语义；理解 SoC feature 需求；确认 Linux/toolchain 支持状态 |
| **硬件开发** | `spec-learning`, `gap-analysis` | `requirement-analysis` | 规范映射（RISC-V ↔ Arm/x86）；feature gap 分析；红线冲突识别 |
| **验证** | `spec-learning`, `gap-analysis` | `requirement-analysis` | 规范验证（MUST/SHOULD/MAY）；gap 文档证据复核；GitHub 仓库实现状态交叉验证 |
| **产品经理 (PM)** | `requirement-analysis`, `governance` | `spec-learning`, `gap-analysis` | 需求矩阵管理；Rx Arch Freeze；TG 跟进；会员/伙伴协作；release zip 打包 |
| **架构师** | `spec-learning`, `gap-analysis`, `requirement-analysis` | `governance` | 竞品架构对比；Tier-1 证据映射；V2R2→V3R3 delta；投资与竞争分析 |

> **跨角色提示**：`spec-learning` 是基础 skill——每个角色都应优先加载
> 它，确保所有断言都锚定在 tier-1 来源上。

---

## 示例

以下示例文件提供完整的查询输入与期望输出形态，供新用户快速上手：

### 查询 RISC-V 规范

参考：[`examples/spec-query-example.md`](examples/spec-query-example.md)

涵盖：
- 查询 Privileged spec v1.12 中 page table（Sv39/Sv48/Sv57）的定义
- Tier-1 规范映射（RISC-V ↔ Arm/x86）

### Gap 分析

参考：[`examples/gap-analysis-example.md`](examples/gap-analysis-example.md)

涵盖：
- 基于 MEMBER-00-GP 需求的 F{N} gap 文档生成（9 节结构）
- 基于 gap 文档的 3 slides/feature 评审材料生成

### 更多示例

入口 `SKILL.md` 的 "使用示例 (Usage Examples)" 一节还包含需求查询
（`requirement-analysis`）、治理跟踪（`governance`）、跨子文件流程的
示例，可直接参考。

---

## 脱敏与占位符约定

所有子文件都是**脱敏模板**。使用前，请将以下占位符替换为项目实际
标识：

| 占位符 | 代表 | 示例替换 |
|--------|------|---------|
| `ORG-00` | 你的组织代号 | （你的公司） |
| `MEMBER-XX-XXX` | 伙伴/会员代号 | （你的伙伴） |
| `MEMBER-00-GP` | 你的旗舰产品线代号 | （你的产品） |
| `RVI-XXX` | RISC-V International 角色/人员 | （实际姓名） |
| `Internal-XXX` | 内部顾问/总监 | （实际姓名） |
| `<delivery-dir>` | 你的项目交付目录 | `your-project/04_专题/` |

> **不要** 将带占位符的文档提交给外部受众。`_internal_` 文件前缀
> 标记的文档严禁进入外部 release zip。

---

## License

Apache-2.0 — 详见 [`LICENSE`](LICENSE)。
