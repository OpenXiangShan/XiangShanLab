# AI基础算法学习课程

欢迎来到AI基础算法学习课程！本课程从零开始，系统性地介绍深度学习的基础知识、经典网络架构、现代大语言模型技术、模型优化方法以及AI系统实现，适合不同水平的学习者。

每个课程包含详细的理论讲解（`.md`文件）和可运行的验证代码（`_verify.py`文件），帮助你在实践中加深理解。

---

## 📚 课程概览

本课程体系包含26个主题，分为五大模块：

- **基础模块**：数学基础与深度学习入门
- **经典网络**：计算机视觉与序列模型
- **现代架构**：Transformer与大语言模型
- **优化技术**：模型压缩与加速
- **系统实现**：AI编译器与硬件加速

---

## 🎯 推荐学习路径

### 路径一：AI初学者（零基础入门）

**适合人群**：没有深度学习背景，想系统学习AI的同学

#### 第一阶段：基础篇

1. [1.FFT](1.FFT/) - 快速傅里叶变换（理解信号处理基础）
2. [2.DNN](2.DNN/) - 深度神经网络（理解神经网络的基本原理）
3. [3.cnn](3.cnn/) - 卷积神经网络（理解卷积操作）

#### 第二阶段：经典网络篇

4. [4.vgg](4.vgg/) - VGG网络（理解深度网络设计）
5. [5.resnet](5.resnet/) - ResNet（理解残差连接）
6. [6.rnn](6.rnn/) - 循环神经网络（理解序列建模）
7. [7.lstm](7.lstm/) - LSTM（理解长序列处理）

#### 第三阶段：现代AI篇

8. [8.transformer](8.transformer/) - Transformer架构（现代AI的基石）
9. [11.attention](11.attention/) - 注意力机制（理解注意力原理）
10. [9.bert](9.bert/) - BERT模型（理解预训练模型）
11. [10.gpt](10.gpt/) - GPT模型（理解生成式模型）
12. [12.kv-cache](12.kv-cache/) - KV缓存（理解推理优化）

#### 第四阶段：进阶应用（选修）

13. [15.rlhf](15.rlhf/) - 人类反馈强化学习

14. [18.quantization](18.quantization/) - 模型量化（部署必备）

---

### 路径二：了解过AI的进阶者

**适合人群**：有一定深度学习基础，想深入学习大模型技术与优化的同学

#### 快速回顾

- [2.DNN](2.DNN/) → [3.cnn](3.cnn/) → [5.resnet](5.resnet/) → [8.transformer](8.transformer/)

#### 核心：大语言模型架构

1. [8.transformer](8.transformer/) - Transformer详解
2. [11.attention](11.attention/) - 注意力机制深入
3. [10.gpt](10.gpt/) - GPT架构与实现
4. [12.kv-cache](12.kv-cache/) - KV缓存优化
5. [13.moe](13.moe/) - 混合专家模型
6. [14.deepseek](14.deepseek/) - DeepSeek架构解析

#### 进阶：模型训练与对齐

7. [15.rlhf](15.rlhf/) - 人类反馈强化学习
8. [16.ppo](16.ppo/) - PPO算法
9. [17.grpo](17.grpo/) - Group Relative Policy Optimization

#### 高级：模型优化技术

10. [18.quantization](18.quantization/) - 量化技术
11. [19.pruning](19.pruning/) - 剪枝技术
12. [20.distillation](20.distillation/) - 知识蒸馏
13. [21.sparsity](21.sparsity/) - 稀疏化技术
14. [22.operator-fusion](22.operator-fusion/) - 算子融合

#### 系统：AI系统实现

15. [23.compiler](23.compiler/) - AI编译器
16. [24.parallel-strategies](24.parallel-strategies/) - 并行策略
17. [25.lenet-riscv](25.lenet-riscv/) - LeNet RISC-V实现
18. [26.riscv-npu](26.riscv-npu/) - RISC-V NPU设计

---

## 📖 完整课程列表

### 基础与经典网络（1-9）
| 编号 | 课程 | 内容 |
|------|------|------|
| 1 | [FFT](1.FFT/) | 快速傅里叶变换 |
| 2 | [DNN](2.DNN/) | 深度神经网络 |
| 3 | [CNN](3.cnn/) | 卷积神经网络 |
| 4 | [VGG](4.vgg/) | VGG网络架构 |
| 5 | [ResNet](5.resnet/) | 残差网络 |
| 6 | [RNN](6.rnn/) | 循环神经网络 |
| 7 | [LSTM](7.lstm/) | 长短期记忆网络 |
| 8 | [Transformer](8.transformer/) | Transformer架构 |
| 9 | [BERT](9.bert/) | BERT预训练模型 |

### 现代大语言模型（10-14）
| 编号 | 课程 | 内容 |
|------|------|------|
| 10 | [GPT](10.gpt/) | GPT生成式模型 |
| 11 | [Attention](11.attention/) | 注意力机制 |
| 12 | [KV-Cache](12.kv-cache/) | KV缓存优化 |
| 13 | [MoE](13.moe/) | 混合专家模型 |
| 14 | [DeepSeek](14.deepseek/) | DeepSeek架构 |

### 强化学习与对齐（15-17）
| 编号 | 课程 | 内容 |
|------|------|------|
| 15 | [RLHF](15.rlhf/) | 人类反馈强化学习 |
| 16 | [PPO](16.ppo/) | 近端策略优化 |
| 17 | [GRPO](17.grpo/) | 组相对策略优化 |

### 模型优化技术（18-22）
| 编号 | 课程 | 内容 |
|------|------|------|
| 18 | [Quantization](18.quantization/) | 模型量化 |
| 19 | [Pruning](19.pruning/) | 模型剪枝 |
| 20 | [Distillation](20.distillation/) | 知识蒸馏 |
| 21 | [Sparsity](21.sparsity/) | 稀疏化技术 |
| 22 | [Operator Fusion](22.operator-fusion/) | 算子融合 |

### AI系统与硬件（23-26）
| 编号 | 课程 | 内容 |
|------|------|------|
| 23 | [Compiler](23.compiler/) | AI编译器 |
| 24 | [Parallel Strategies](24.parallel-strategies/) | 并行策略 |
| 25 | [LeNet-RISC-V](25.lenet-riscv/) | LeNet RISC-V实现 |
| 26 | [RISC-V NPU](26.riscv-npu/) | RISC-V神经处理单元 |

---

## 🚀 如何使用

### 1. 阅读课程文档
每个文件夹中的 `.md` 文件包含详细的理论讲解和原理说明。

### 2. 运行验证代码
大部分课程提供 `_verify.py` 或 `_audit.py` 代码，可以直接运行验证理解：

```bash
cd 2.DNN
python dnn_verify.py
```

### 3. 动手实践
在理解原理后，尝试修改代码参数，观察不同配置下的效果。

---

## 💡 学习建议

1. **循序渐进**：按照推荐路径学习，不要跳过基础课程
2. **理论结合实践**：阅读文档后一定要运行代码验证
3. **做好笔记**：记录关键概念和自己的理解
4. **多做实验**：修改代码参数，观察不同结果
5. **构建项目**：学完一个模块后，尝试用所学知识完成小项目

---

## 📝 补充资源

- [algorithm-template.md](algorithm-template.md) - 算法模板
- [RISC-V-RAG系统设计文档_2026.md](RISC-V-RAG系统设计文档_2026.md) - RISC-V RAG系统设计

---

## 🤝 贡献

欢迎提交问题、建议或改进！

---

**祝学习愉快！如有问题欢迎交流讨论。** 🎓
