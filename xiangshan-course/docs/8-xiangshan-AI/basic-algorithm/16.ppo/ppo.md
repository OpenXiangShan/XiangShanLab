# PPO（近端策略优化）

## 1. 文字讲解

### 1.1 这个算法解决什么问题

策略梯度可以直接调整策略，使高回报动作的概率增加、低回报动作的概率降低。但是一次更新过大时，新策略可能突然偏离旧策略，导致训练不稳定。

PPO（Proximal Policy Optimization，近端策略优化）通过比较新旧策略的动作概率，并对过大的有利变化进行裁剪，让策略以相对保守的步幅更新。

PPO 是一种 on-policy 算法：训练数据由当前或旧策略采样，经过若干轮更新后，需要重新使用最新策略采集数据。

### 1.2 核心思想

PPO 可以从三个概念理解：

- **Policy Gradient**：根据动作带来的优势调整策略概率。
- **Actor-Critic**：Actor 是策略模型，负责选择动作；Critic 是价值模型，估计当前状态未来能获得多少回报。
- **Clipped Objective**：限制新策略从同一批数据中获得过大的更新收益。

优势 $\hat A_t$ 表示动作 $a_t$ 相对当前状态下平均水平的好坏：

- $\hat A_t>0$：该动作比预期好，应提高其概率。
- $\hat A_t<0$：该动作比预期差，应降低其概率。

### 1.3 基本流程

PPO 的基本训练流程如下：

1. 使用旧策略 $\pi_{\theta_{\text{old}}}$ 与环境交互，收集状态、动作和奖励。
2. Critic 估计状态价值，并据此计算回报和优势。
3. 计算同一动作在新旧策略下的概率比率。
4. 使用裁剪代理目标更新 Actor。
5. 使用实际回报更新 Critic，使价值估计更准确。
6. 在同一批数据上执行有限轮小批量更新。
7. 将更新后的策略作为新的旧策略，重新采样数据。

在 RLHF 中，状态可以理解为“提示词和已经生成的 token”，动作是“下一个 token”，Actor 是语言模型，Critic 估计回答的期望回报，奖励主要来自奖励模型并可包含 KL 惩罚。

### 1.4 直观例子

假设旧策略选择某动作的概率为 $0.4$，新策略将其提高到 $0.6$，则概率比率为：

$$

\rho=\frac{0.6}{0.4}=1.5.

$$

若该动作的优势为 $1$，普通策略梯度会把这次变化记为 $1.5$。当裁剪范围为 $[0.8,1.2]$ 时，PPO 只把这次有利变化按 $1.2$ 计算，避免继续鼓励策略一步走得过远。

裁剪并不是把实际概率强制改回区间，而是改变优化目标中的激励。

## 2. 公式讲解

### 2.1 核心公式

策略梯度的基本形式为：

$$

\nabla_\theta J(\theta)
=\mathbb{E}_t\left[
\nabla_\theta\log\pi_\theta(a_t\mid s_t)\hat A_t
\right].

$$

Actor-Critic 使用价值函数 $V_\phi(s_t)$ 作为基线。一种常用的优势估计从 TD 残差开始：

$$

\delta_t=R_t+\gamma V_\phi(s_{t+1})-V_\phi(s_t),

$$

$$

\hat A_t=\sum_{l=0}^{T-t-1}(\gamma\lambda)^l\delta_{t+l}.

$$

PPO 定义新旧策略的概率比率：

$$

\rho_t(\theta)=
\frac{\pi_\theta(a_t\mid s_t)}
{\pi_{\theta_{\text{old}}}(a_t\mid s_t)}.

$$

PPO-Clip 的核心目标为：

$$

L^{\text{CLIP}}(\theta)=
\mathbb{E}_t\left[
\min\left(
\rho_t(\theta)\hat A_t,
\operatorname{clip}\left(\rho_t(\theta),1-\epsilon,1+\epsilon\right)\hat A_t
\right)
\right].

$$

Critic 可以使用均方误差训练：

$$

L_V(\phi)=
\mathbb{E}_t\left[
\left(V_\phi(s_t)-\hat V_t\right)^2
\right].

$$

### 2.2 变量含义

- $s_t$：时间步 $t$ 的状态。
- $a_t$：旧策略在状态 $s_t$ 选择的动作。
- $R_t$：时间步 $t$ 获得的奖励。
- $\pi_\theta$：正在更新的 Actor 策略。
- $\pi_{\theta_{\text{old}}}$：采集当前训练数据时使用的旧策略。
- $V_\phi(s_t)$：参数为 $\phi$ 的 Critic 对状态价值的估计。
- $\hat V_t$：由采样数据估计的目标回报。
- $\delta_t$：一步 TD 残差。
- $\hat A_t$：动作相对平均水平的优势估计。
- $\gamma$：未来奖励的折扣因子。
- $\lambda$：广义优势估计中平衡偏差与方差的参数。
- $\rho_t(\theta)$：新策略与旧策略对同一动作的概率比率。
- $\epsilon$：裁剪范围的超参数。
- $L^{\text{CLIP}}$：希望最大化的 Actor 裁剪目标。
- $L_V$：希望最小化的 Critic 价值损失。

### 2.3 公式怎么理解

概率比率 $\rho_t=1$ 表示新旧策略对该动作的概率相同；大于 $1$ 表示概率提高，小于 $1$ 表示概率降低。

当优势为正时，提高动作概率是有利更新，但超过 $1+\epsilon$ 后不再获得额外收益。当优势为负时，降低动作概率是有利更新，但低于 $1-\epsilon$ 后也不再获得额外收益。

公式取两项中的较小值，形成较保守的代理目标。PPO 裁剪的是目标中的有利激励，并不保证所有概率比率最终都严格位于裁剪区间内。

Critic 不负责生成动作，它提供价值基线以估计优势，从而降低策略梯度的方差。

## 3. 代码示例

### 3.1 最小可运行代码

下面直接计算四个样本的 PPO 裁剪目标，不实现完整神经网络训练。

```python
import numpy as np


def ppo_clipped_objective(
    new_probs, old_probs, advantages, epsilon=0.2
):
    ratios = new_probs / old_probs
    clipped_ratios = np.clip(ratios, 1 - epsilon, 1 + epsilon)
    unclipped = ratios * advantages
    clipped = clipped_ratios * advantages
    per_sample = np.minimum(unclipped, clipped)
    return per_sample.mean(), ratios, unclipped, clipped, per_sample


old_probs = np.array([0.40, 0.50, 0.20, 0.25])
new_probs = np.array([0.60, 0.40, 0.10, 0.40])

returns = np.array([1.5, 1.2, -0.7, -1.4])
values = np.array([0.5, 0.2, 0.3, -0.4])
advantages = returns - values

objective, ratios, unclipped, clipped, per_sample = (
    ppo_clipped_objective(
        new_probs, old_probs, advantages, epsilon=0.2
    )
)
value_loss = np.mean((values - returns) ** 2)

np.set_printoptions(precision=4, suppress=True)
print("advantages:", advantages)
print("probability ratios:", ratios)
print("unclipped terms:", unclipped)
print("clipped terms:", clipped)
print("selected terms:", per_sample)
print("PPO objective:", round(objective, 4))
print("value loss:", round(value_loss, 4))

assert np.allclose(advantages, [1.0, 1.0, -1.0, -1.0])
assert np.allclose(per_sample, [1.2, 0.8, -0.8, -1.6])
```

### 3.2 输入输出说明

- `old_probs` 和 `new_probs` 是旧、新策略对已采样动作的概率。
- `returns` 是采样得到的目标回报。
- `values` 是 Critic 给出的价值估计。
- `advantages` 使用 `returns - values` 构造简化优势。
- `unclipped` 是未裁剪的策略目标项。
- `clipped` 是将概率比率裁剪后得到的目标项。
- `per_sample` 对每个样本取两项中的较小值。

运行结果为：

```text
advantages: [ 1.  1. -1. -1.]
probability ratios: [1.5 0.8 0.5 1.6]
unclipped terms: [ 1.5  0.8 -0.5 -1.6]
clipped terms: [ 1.2  0.8 -0.8 -1.2]
selected terms: [ 1.2  0.8 -0.8 -1.6]
PPO objective: -0.1
value loss: 1.0
```

### 3.3 关键代码解释

`new_probs / old_probs` 对应概率比率 $\rho_t$。它衡量新策略对已采样动作的概率改变了多少。

`np.clip` 将用于代理目标的比率限制在 $[1-\epsilon,1+\epsilon]$。`np.minimum` 再选择未裁剪项和裁剪项中较保守的一项。

第一个样本优势为正且比率达到 $1.5$，因此收益从 $1.5$ 截到 $1.2$。第三个样本优势为负且比率降到 $0.5$，PPO 选择 $-0.8$，不再奖励继续大幅降低该动作概率。
