# RLHF（基于人类反馈的强化学习）

## 1. 文字讲解

### 1.1 这个算法解决什么问题

语言模型的预训练目标是预测下一个 token，但“预测得像互联网文本”不等于“按照用户意图给出有帮助、可信且安全的回答”。很多人类偏好也难以写成简单的自动评分规则。

RLHF（Reinforcement Learning from Human Feedback）通过人类示范和偏好比较，把人类对回答质量的判断转化为训练信号，再用强化学习调整语言模型。

RLHF 的目标不是给模型增加全新知识，而是让模型已有能力的表达方式更符合所收集的人类偏好。它学到的是特定标注规范和标注群体的偏好，并不等同于完整、统一的“人类价值观”。

### 1.2 核心思想

经典 RLHF 流程包含三个主要模型：

- **SFT 模型**：使用人类编写的高质量示范进行监督微调。
- **奖励模型**：输入提示词和回答，输出一个标量分数，用于预测人类更喜欢哪个回答。
- **策略模型**：需要继续优化的语言模型，根据提示词生成回答。

人类通常不需要参与每一次强化学习更新。标注者先比较多个回答，奖励模型学习这些偏好；之后奖励模型可以自动给策略模型生成的大量回答打分。

### 1.3 基本流程

经典 RLHF 可以按以下步骤执行：

1. 从预训练语言模型开始。
2. 收集“提示词—理想回答”示范数据，训练 SFT 模型。
3. 对同一个提示词采样多个候选回答，由标注者排序或两两比较。
4. 使用偏好数据训练奖励模型，使人类更喜欢的回答获得更高分数。
5. 以 SFT 模型作为初始策略和参考模型，让策略模型继续生成回答。
6. 奖励模型对回答评分，PPO 使用这些奖励更新策略模型。
7. 加入 KL 惩罚，限制新策略与参考模型相差过大。

奖励模型在策略优化阶段通常保持固定。PPO 的 Actor-Critic 和裁剪目标将在下一章单独讲解。

### 1.4 直观例子

给定提示词：“ $ 2+2 $ 等于多少？”模型生成两个回答：

- 回答 A：“ $ 2+2=4 $。”
- 回答 B：“ $ 2+2=5 $。”

标注者选择 A，形成一条偏好数据 $ A\succ B $。奖励模型学习使：

$$

r_\phi(x,A)>r_\phi(x,B).

$$

策略优化会提高生成 A 类回答的概率。但如果只追求奖励模型分数，策略可能利用奖励模型的缺陷，因此还要使用 KL 惩罚，使策略不要突然远离原来的 SFT 模型。

## 2. 公式讲解

### 2.1 核心公式

设对同一个提示词 $ x $，人类更喜欢回答 $ y_w $，不喜欢回答 $ y_l $。奖励模型使用 Bradley-Terry 形式表示偏好概率：

$$

P(y_w\succ y_l\mid x)
=\sigma\left(r_\phi(x,y_w)-r_\phi(x,y_l)\right).

$$

对应的成对偏好损失为：

$$

L_{\text{RM}}
=-\mathbb{E}_{(x,y_w,y_l)}
\left[
\log\sigma\left(r_\phi(x,y_w)-r_\phi(x,y_l)\right)
\right].

$$

策略优化的简化目标可以写成：

$$

J(\theta)=
\mathbb{E}_{x\sim D,\,y\sim\pi_\theta(\cdot\mid x)}
\left[r_\phi(x,y)\right]
-\beta\,
D_{\mathrm{KL}}\left(
\pi_\theta(\cdot\mid x)\,\|\,\pi_{\mathrm{ref}}(\cdot\mid x)
\right).

$$

离散回答集合上的 KL 散度为：

$$

D_{\mathrm{KL}}(\pi_\theta\|\pi_{\mathrm{ref}})
=\sum_y\pi_\theta(y\mid x)
\log\frac{\pi_\theta(y\mid x)}{\pi_{\mathrm{ref}}(y\mid x)}.

$$

### 2.2 变量含义

- $ x $：输入提示词。
- $ y_w $：人类偏好的回答，$ w $ 表示 winner。
- $ y_l $：未被偏好的回答，$ l $ 表示 loser。
- $ r_\phi(x,y) $：参数为 $ \phi $ 的奖励模型对回答 $ y $ 给出的标量分数。
- $ \sigma(z)=1/(1+e^{-z}) $：Sigmoid 函数，将奖励差转换为偏好概率。
- $ L_{\text{RM}} $：奖励模型的成对偏好损失。
- $ D $：用于策略训练的提示词分布。
- $ \pi_\theta(y\mid x) $：参数为 $ \theta $ 的策略模型生成回答 $ y $ 的概率。
- $ \pi_{\mathrm{ref}}(y\mid x) $：固定的参考策略，通常由 SFT 模型得到。
- $ D_{\mathrm{KL}} $：衡量两个概率分布差异的 KL 散度。
- $ \beta $：KL 惩罚强度。
- $ J(\theta) $：希望最大化的策略目标。

### 2.3 公式怎么理解

奖励模型只关心两个回答的分数差。若偏好回答的分数更高，Sigmoid 输出接近 $ 1 $，损失变小；如果顺序相反，损失会变大。

策略目标的第一项鼓励模型生成奖励更高的回答。第二项是 KL 惩罚，防止策略为了提高奖励而过度偏离参考模型。$ \beta $ 越大，更新越保守；$ \beta $ 越小，策略越倾向于追逐奖励模型分数。

实际 PPO 会在 token 级别估计回报和优势并限制单次更新幅度，这里只给出 RLHF 的总体优化方向。

## 3. 代码示例

### 3.1 最小可运行代码

下面用三个候选回答演示奖励模型损失和带 KL 惩罚的策略目标，不实现完整语言模型和 PPO。

```python
import numpy as np


def pairwise_reward_loss(preferred_scores, rejected_scores):
    reward_diff = preferred_scores - rejected_scores
    losses = np.logaddexp(0.0, -reward_diff)
    return losses.mean(), losses


def kl_divergence(policy_probs, reference_probs):
    return np.sum(
        policy_probs * np.log(policy_probs / reference_probs)
    )


reward_scores = np.array([2.0, 0.5, -1.0])

# 人类偏好：回答 0 > 回答 1 > 回答 2
preferred = reward_scores[[0, 0, 1]]
rejected = reward_scores[[1, 2, 2]]
reward_loss, pair_losses = pairwise_reward_loss(preferred, rejected)

policy_probs = np.array([0.60, 0.30, 0.10])
reference_probs = np.array([0.40, 0.35, 0.25])
beta = 0.20

expected_reward = np.sum(policy_probs * reward_scores)
kl = kl_divergence(policy_probs, reference_probs)
rlhf_objective = expected_reward - beta * kl

np.set_printoptions(precision=4, suppress=True)
print("pairwise losses:", pair_losses)
print("reward model loss:", round(reward_loss, 4))
print("expected reward:", round(expected_reward, 4))
print("KL divergence:", round(kl, 4))
print("RLHF objective:", round(rlhf_objective, 4))

assert reward_loss > 0
assert kl >= 0
assert rlhf_objective < expected_reward
```

### 3.2 输入输出说明

- `reward_scores` 是奖励模型给三个回答的分数。
- `preferred` 和 `rejected` 组成三对人类偏好数据。
- `policy_probs` 是当前策略生成三个回答的概率。
- `reference_probs` 是参考策略对应的概率。
- `reward_loss` 衡量奖励模型是否正确排列偏好。
- `rlhf_objective` 是期望奖励减去 KL 惩罚后的目标值。

运行结果为：

```text
pairwise losses: [0.2014 0.0486 0.2014]
reward model loss: 0.1505
expected reward: 1.25
KL divergence: 0.1054
RLHF objective: 1.2289
```

### 3.3 关键代码解释

`np.logaddexp(0.0, -reward_diff)` 是 $ -\log\sigma(r_w-r_l) $ 的数值稳定写法。偏好回答与拒绝回答的奖励差越大，损失越小。

`kl_divergence` 计算当前策略相对参考策略的 KL 散度。当前策略分布变化越大，惩罚越明显。

`expected_reward - beta * kl` 展示 RLHF 的核心权衡：一方面提高人类偏好奖励，另一方面限制策略偏离参考模型。
