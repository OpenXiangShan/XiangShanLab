# Spec Query Example

> 本文件演示 `spec-learning` 子文件的典型用法。所有输出均为**模拟**
> 形态，用于展示 skill 应遵循的结构与 source-tag 规范，不代表任何
> 真实规范条目的最终答案。实际使用时，skill 会从 tier-1 来源实时
> 检索并标注证据层级。

---

## 示例 1: 查询 RISC-V Privileged Spec v1.12

### 查询输入

```text
/skill spec-learning
请查询 RISC-V Privileged spec v1.12 中关于 page table (Sv39/Sv48/Sv57)
的定义，并标注 ratification 状态和引用层级 (UDB / Normative / Ratified)。
```

### 期望输出形态

skill 应返回**带层级 tag 的引用块**，每条断言标注证据来源：

```markdown
### Sv39 / Sv48 / Sv57 Page Table Layout

**Sv39** (39-bit virtual address, 3-level page table)

- VPN 划分：`VPN[2] (9b) | VPN[1] (9b) | VPN[0] (9b)`
- Page sizes: 4 KiB leaf, 2 MiB megapage, 1 GiB gigapage
- [Layer 3] Ratified: RISC-V Privileged ISA Specification v1.12,
  §4.3.2 "Sv39: Page-Based 39-bit Virtual-Memory System"
  — https://github.com/riscv/riscv-isa-manual/releases/tag/v1.12
  [T1-VERIFIED]

**Sv48** (48-bit virtual address, 4-level page table)

- 在 Sv39 基础上增加 `VPN[3] (9b)`，支持 512 GiB terapage
- [Layer 3] Ratified: Privileged spec v1.12, §4.4 "Sv48: Page-Based
  48-bit Virtual-Memory System" [T1-VERIFIED]

**Sv57** (57-bit virtual address, 5-level page table)

- 在 Sv48 基础上增加 `VPN[4] (9b)`
- [Layer 3] Ratified: Privileged spec v1.12, §4.5 "Sv57: Page-Based
  57-bit Virtual-Memory System"（v1.12 新增）[T1-VERIFIED]

### Ratification 状态

| 模式 | Spec 版本 | 状态 | 来源层级 |
|------|----------|------|---------|
| Sv39 | Privileged v1.11+ | Ratified | Layer 3 (Ratified release) |
| Sv48 | Privileged v1.11+ | Ratified | Layer 3 (Ratified release) |
| Sv57 | Privileged v1.12  | Ratified | Layer 3 (Ratified release) |

> 所有三者均为 Layer 3（已 ratify 的 release tag）。若需查证
> post-ratification 勘误（Layer 4），应进一步检索
> riscv-isa-manual 仓库的 CHANGELOG / PR 历史。
```

**要点**：
- 每条事实都带 `[Layer N]` tag 和 `[T1-VERIFIED]` / `[T2-CROSS-CHECKED]` 证据等级
- 引用指向具体的 release tag 与章节，而非笼统的"spec 说"
- ratification 状态以 release tag 为准，而非 draft PDF

---

## 示例 2: Tier-1 规范映射（RISC-V ↔ Arm/x86）

### 查询输入

```text
/skill spec-learning
MEMBER-00-GP 的内存模型需求（一致性、原子性、memory ordering）映射到
哪些 Tier-1 规范？请给出 RISC-V、Arm、x86 三方的对照表，并标注
source tag。
```

### 期望输出形态

skill 应返回**三方对照表**，每行带 tier-1 来源：

```markdown
### Memory Model Tier-1 Mapping

| 能力 | RISC-V (RVWMO) | Arm (v8-A) | x86-64 |
|------|----------------|------------|--------|
| 弱内存模型基线 | RVWMO (Privileged spec v1.12, §14) | AArch64 Memory Model (Arm ARM v8-A, §B2) | x86-TSO (Intel SDM Vol. 3, §8.2) |
| Load-Store ordering | `.sq` / `.aq` annotation (Unprivileged spec, §2.3) | LDR/STR acquire/release (Arm ARM §C7.2) | MOV (Intel SDM §8.2.2) |
| Fence 指令 | `fence` / `fence.i` (Unprivileged §2.7) | DMB / DSB / ISB (Arm ARM §C6.2) | MFENCE / LFENCE / SFENCE (Intel SDM §8.3) |
| 原子 compare-and-swap | LR/SC pair (Unprivileged §8.1) | CAS / LDXR/STXR (Arm ARM §C7.3) | CMPXCHG (Intel SDM §8.1) |

### Source Layer Tags

- **RISC-V**: [Layer 3] Ratified — riscv-isa-manual v1.12 release tag
  [T1-VERIFIED]
- **Arm**: [Layer 3] Ratified — Arm Architecture Reference Manual
  Armv8-A (current edition) [T1-VERIFIED]
- **x86**: [Layer 3] Ratified — Intel 64 and IA-32 Architectures
  Software Developer's Manual Vol. 3A [T1-VERIFIED]

### MEMBER-00-GP 需求命中

- REQ-MM-001（多核一致性）→ RVWMO + `fence rw, rw`，Tier-1 三方均支持
- REQ-MM-002（设备 DMA 一致性）→ 需配合 IOMMU（见 governance: IOPMP TG）
  [T2-CROSS-CHECKED]

> ⚠️ 占位符说明：`MEMBER-00-GP` 为脱敏代号，使用前替换为你的
> 旗舰产品线实际标识。`REQ-MM-XXX` 为示例需求编号格式。
```

**要点**：
- 映射表三方对齐，便于架构师做竞品对比
- 每个来源标注 Layer（Ratified release > draft）与 source tag
- 需求命中行标注 `[T1-VERIFIED]` 或 `[T2-CROSS-CHECKED]`，体现交叉验证

---

## 检查清单（供 skill 自检）

在返回 spec query 结果前，确认：

- [ ] 每条事实都带 `[Layer N]` 引用层级（UDB / Normative / Ratified / post-v0.7 / Un-ratified）
- [ ] 每条断言都带 `[T1-VERIFIED]` / `[T2-CROSS-CHECKED]` / `[UNVERIFIED]` 之一
- [ ] ratification 状态以 riscv-isa-manual release tag 为准，而非 PDF 草案
- [ ] 章节号精确到节（§x.y.z），而非笼统的"spec 说"
- [ ] 所有会员/产品代号使用占位符，未泄露真实名称
