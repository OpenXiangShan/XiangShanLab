# 知识蒸馏

## 1. 文字讲解

### 1.1 这个算法解决什么问题

知识蒸馏（Knowledge Distillation）把一个已经训练好的模型所学到的输出规律，转移给另一个模型。

提供知识的模型称为 Teacher（教师模型），学习知识的模型称为 Student（学生模型）。Teacher 通常具有较强的任务能力，Student 通常更小或结构更简单，但两者不必使用相同的内部网络结构。

普通分类训练只告诉模型正确类别是什么。知识蒸馏还让 Student 学习 Teacher 对所有类别的概率分布，因此能够获得“哪些错误类别更相似”之类的额外信息。

蒸馏不能保证 Student 达到 Teacher 的能力。最终效果还取决于 Teacher 的质量、Student 的容量、训练数据、温度和损失权重。

### 1.2 核心思想

假设一张图片的真实类别是“猫”，硬标签只表示：

$$

\mathbf y=[1,0,0].

$$

如果三个类别依次为“猫、狗、汽车”，Teacher 可能输出：

$$

\mathbf p^{(T)}=[0.67,0.24,0.09].

$$

这个软标签除了指出“猫”的概率最高，还说明 Teacher 认为“狗”比“汽车”更接近当前样本。Student 模仿完整分布时，就能学习这种类别之间的相对关系。

蒸馏通常同时使用两种监督：

- **硬标签损失**：Student 学习数据集给出的真实类别。
- **软标签损失**：Student 学习 Teacher 在相同输入上的概率分布。

温度参数用于控制 Softmax 输出的平滑程度。温度大于 $1$ 时，概率分布通常更平滑，原本很小的非目标类别概率会变得更明显。

### 1.3 基本流程

1. 先训练或准备一个 Teacher 模型。
2. 固定 Teacher 参数，在蒸馏训练中不更新 Teacher。
3. 将同一批输入分别送入 Teacher 和 Student，得到两组 logits。
4. 使用温度 $T$ 对两组 logits 计算 Softmax。
5. 用 Teacher 的温度概率作为软标签，计算蒸馏损失。
6. 使用 Student 在普通温度下的输出计算硬标签交叉熵。
7. 对两项损失加权求和，只更新 Student 参数。
8. 训练结束后单独使用 Student 完成预测。

本章讲解最经典的输出蒸馏。其他方法还可以传递中间特征或注意力分布，但不影响理解这里的 Teacher、Student 和 Soft Label 核心关系。

### 1.4 直观例子

设 Teacher 对三个类别输出 logits：

$$

\mathbf z^{(T)}=[4,2,0].

$$

当温度为 $1$ 时，Teacher 的概率约为：

$$

\operatorname{softmax}(\mathbf z^{(T)})
\approx[0.8668,0.1173,0.0159].

$$

当温度提高到 $2$ 时，概率约为：

$$

\operatorname{softmax}\left(\frac{\mathbf z^{(T)}}{2}\right)
\approx[0.6652,0.2447,0.0900].

$$

最高概率类别没有改变，但另外两个类别的概率信息变得更明显。Student 会在真实类别监督之外，学习 Teacher 对第二类和第三类的不同判断。

## 2. 公式讲解

### 2.1 核心公式

Teacher 在温度 $T$ 下对第 $i$ 个类别的软标签为：

$$

p_i^{(T)}=
\frac{\exp\left(z_i^{(T)}/T\right)}
{\sum_{j=1}^{C}\exp\left(z_j^{(T)}/T\right)}.

$$

Student 在相同温度下的概率为：

$$

q_i^{(T)}=
\frac{\exp\left(z_i^{(S)}/T\right)}
{\sum_{j=1}^{C}\exp\left(z_j^{(S)}/T\right)}.

$$

Student 的硬标签交叉熵损失为：

$$

\mathcal L_{\text{hard}}
=-\sum_{i=1}^{C}y_i\log q_i^{(1)}.

$$

软标签蒸馏损失可以使用 Teacher 分布到 Student 分布的 KL 散度：

$$

\mathcal L_{\text{soft}}
=T^2D_{\mathrm{KL}}\left(
\mathbf p^{(T)}\middle\|\mathbf q^{(T)}
\right),

$$

$$

D_{\mathrm{KL}}\left(
\mathbf p^{(T)}\middle\|\mathbf q^{(T)}
\right)
=\sum_{i=1}^{C}
p_i^{(T)}
\log\frac{p_i^{(T)}}{q_i^{(T)}}.

$$

将硬标签和软标签组合后，本章采用的总损失为：

$$

\mathcal L
=\alpha\mathcal L_{\text{hard}}
+(1-\alpha)\mathcal L_{\text{soft}}.

$$

对于这一定义，蒸馏损失关于 Student 第 $i$ 个 logit 的梯度为：

$$

\frac{\partial\mathcal L_{\text{soft}}}
{\partial z_i^{(S)}}
=T\left(q_i^{(T)}-p_i^{(T)}\right).

$$

### 2.2 变量含义

- $C$：分类任务的类别数量。
- $i$ 、 $j$：类别索引。
- $z_i^{(T)}$：Teacher 对第 $i$ 类输出的 logit。
- $z_i^{(S)}$：Student 对第 $i$ 类输出的 logit。
- $T$：大于零的温度参数。
- $p_i^{(T)}$：Teacher 在温度 $T$ 下对第 $i$ 类的软标签概率。
- $q_i^{(T)}$：Student 在温度 $T$ 下对第 $i$ 类的概率。
- $q_i^{(1)}$：Student 在普通温度 $1$ 下的概率。
- $y_i$：真实硬标签的第 $i$ 个分量，分类任务中通常使用 one-hot 表示。
- $\mathcal L_{\text{hard}}$：真实标签交叉熵损失。
- $D_{\mathrm{KL}}$：衡量两个概率分布差异的 KL 散度。
- $\mathcal L_{\text{soft}}$：乘有温度平方的软标签蒸馏损失。
- $\alpha$：硬标签损失权重，取值通常位于 $[0,1]$。
- $\mathcal L$：用于训练 Student 的总损失。
- $\exp$ 、 $\log$：指数函数和自然对数。

### 2.3 公式怎么理解

Logits 除以温度后再进入 Softmax。当 $T>1$ 时，logits 之间的差距被缩小，输出分布变得更平滑；当 $T$ 较小时，分布会更集中在最大 logit 对应的类别。

KL 散度要求 Student 的温度分布接近 Teacher 的温度分布。使用软标签交叉熵也能得到相同方向的 Student 梯度，因为两者只相差一个与 Student 参数无关的 Teacher 熵项。

Softmax 对 logits 的梯度会随温度缩小，因此经典蒸馏通常将软标签损失乘以 $T^2$ ，使不同温度下的梯度尺度更容易保持在相近水平。

$\alpha$ 控制真实标签与 Teacher 知识之间的权衡。本章规定 $\alpha$ 是硬标签权重；不同资料或代码可能采用相反命名，阅读实现时应以具体公式为准。

训练时只对 Student 求梯度。Teacher 负责产生稳定的学习目标，其参数在当前蒸馏过程中保持不变。

## 3. 代码示例

### 3.1 最小可运行代码

下面使用固定的 Teacher logits 和一组可训练的 Student logits，直接演示软标签知识如何转移给 Student。它展示的是一个样本上的核心损失，不是完整神经网络训练。

```python
import numpy as np


def softmax(logits, temperature=1.0):
    scaled = logits / temperature
    shifted = scaled - np.max(scaled)
    exp_values = np.exp(shifted)
    return exp_values / exp_values.sum()


def distillation_losses(
    teacher_logits,
    student_logits,
    label,
    temperature=2.0,
    hard_weight=0.5,
):
    eps = 1e-12
    teacher_soft = softmax(teacher_logits, temperature)
    student_soft = softmax(student_logits, temperature)
    student_hard = softmax(student_logits, 1.0)

    hard_loss = -np.log(student_hard[label] + eps)
    kl = np.sum(
        teacher_soft
        * np.log((teacher_soft + eps) / (student_soft + eps))
    )
    soft_loss = (temperature ** 2) * kl
    total_loss = (
        hard_weight * hard_loss
        + (1.0 - hard_weight) * soft_loss
    )
    return total_loss, hard_loss, soft_loss, teacher_soft, student_soft


def train_student_logits(
    teacher_logits,
    initial_student_logits,
    label,
    temperature=2.0,
    hard_weight=0.5,
    learning_rate=0.1,
    steps=200,
):
    student_logits = initial_student_logits.copy()
    one_hot = np.zeros_like(student_logits)
    one_hot[label] = 1.0

    for _ in range(steps):
        teacher_soft = softmax(teacher_logits, temperature)
        student_soft = softmax(student_logits, temperature)
        student_hard = softmax(student_logits, 1.0)

        hard_gradient = student_hard - one_hot
        soft_gradient = temperature * (
            student_soft - teacher_soft
        )
        total_gradient = (
            hard_weight * hard_gradient
            + (1.0 - hard_weight) * soft_gradient
        )
        student_logits -= learning_rate * total_gradient

    return student_logits


teacher_logits = np.array([4.0, 2.0, 0.0])
initial_student_logits = np.array([0.5, 1.2, -0.3])
label = 0
temperature = 2.0
hard_weight = 0.5

initial = distillation_losses(
    teacher_logits,
    initial_student_logits,
    label,
    temperature,
    hard_weight,
)
trained_student_logits = train_student_logits(
    teacher_logits,
    initial_student_logits,
    label,
    temperature,
    hard_weight,
)
final = distillation_losses(
    teacher_logits,
    trained_student_logits,
    label,
    temperature,
    hard_weight,
)

np.set_printoptions(precision=4, suppress=True)
print("teacher probabilities, T=1:", softmax(teacher_logits, 1.0))
print("teacher soft labels, T=2:", initial[3])
print("initial student soft labels:", initial[4])
print("trained student logits:", trained_student_logits)
print("trained student soft labels:", final[4])
print("initial total loss:", round(initial[0], 6))
print("final total loss:", round(final[0], 6))
print("initial soft loss:", round(initial[2], 6))
print("final soft loss:", round(final[2], 6))

assert final[0] < initial[0]
assert final[2] < initial[2]
assert np.argmax(trained_student_logits) == label
```

### 3.2 输入输出说明

- `teacher_logits` 是 Teacher 对三个类别的 logits，形状为 `(3,)`。
- `initial_student_logits` 是 Student 的初始 logits，初始时错误地把第二类排在第一位。
- `label=0` 表示真实类别是第一类。
- `temperature=2.0` 用于生成较平滑的 Teacher 和 Student 分布。
- `hard_weight=0.5` 表示硬标签损失和软标签损失各占一半。
- `trained_student_logits` 是执行 $200$ 次梯度更新后的 Student logits。
- `total_loss` 是硬标签损失与蒸馏损失的加权和。

运行后，Student 的总损失和软标签损失都会下降，最高 logit 也会从错误的第二类转到真实的第一类。Student 的温度概率会同时受到硬标签和 Teacher 软标签的影响，因此不必与 Teacher 分布完全相同。

### 3.3 关键代码解释

`softmax` 先减去最大值，再计算指数，避免直接计算较大指数时出现数值溢出。

`distillation_losses` 使用温度为 $1$ 的 Student 概率计算硬标签损失，使用温度为 $2$ 的 Teacher 和 Student 概率计算 KL 蒸馏损失。

`hard_gradient` 让 Student 接近 one-hot 真实标签；`soft_gradient` 让 Student 接近 Teacher 的完整概率分布。训练循环只更新 `student_logits`，Teacher logits 始终不变。
