# GRPO（组相对策略优化）

## 1. 文字讲解

### 1.1 这个算法解决什么问题

PPO 通常使用 Critic（价值模型）估计优势，但在大语言模型训练中，Critic 往往与策略模型规模相近，而且还要学习每个 token 对应的价值。

GRPO（Group Relative Policy Optimization，组相对策略优化）不再训练 Critic。对于同一个问题，它让旧策略生成一组回答，根据这些回答的相对奖励计算优势，再用类似 PPO 的裁剪目标更新策略。

它的输入是问题、旧策略生成的一组回答及其奖励，输出是更新后的策略模型。GRPO 适合能够对回答进行评分的任务，例如答案可以自动判定的数学、代码和逻辑推理任务。

GRPO 最初由 DeepSeekMath 提出，后来被用于 DeepSeek-R1-Zero 和 DeepSeek-R1 的推理强化训练。它通过奖励比较一组候选回答，让模型逐步增加高质量推理路径的生成概率。

### 1.2 核心思想

GRPO 中的“Group Relative”表示：一个回答是否优秀，不只看它的绝对奖励，还要看它与同一问题下其他回答相比处于什么位置。

对于同一个问题生成 $G$ 个回答后，先计算组内奖励均值和标准差，再将每个奖励标准化：

- 奖励高于组内平均值，优势为正，策略应提高该回答中 token 的概率。
- 奖励低于组内平均值，优势为负，策略应降低相应概率。
- 奖励接近组内平均值，优势也接近 $0$，更新幅度较小。

GRPO 与 PPO 的主要区别是优势的来源：PPO 通常借助 Critic 和 GAE 估计优势；GRPO 使用同一问题下多个回答的组内相对奖励作为基线，因此省去了 Critic。

GRPO 仍保留 PPO 的概率比率和裁剪机制，并可通过 KL 正则限制当前策略偏离参考策略。

### 1.3 基本流程

1. 从训练集中采样一个问题 $q$。
2. 使用旧策略 $π_{\theta_{\text{old}}}$ 为该问题生成 $G$ 个回答。
3. 使用规则、奖励模型或验证器为每个回答计算奖励 $r_i$。
4. 在组内对奖励进行标准化，得到每个回答的相对优势 $\hat A_i$。
5. 将回答级优势分配给该回答中的 token。
6. 计算 token 在当前策略与旧策略下的概率比率。
7. 使用裁剪目标更新当前策略，并使用 KL 正则约束其与参考策略的偏离。
8. 使用更新后的策略重新采样，进入下一轮训练。

### 1.4 直观例子

假设模型对同一道题生成四个回答，奖励分别为：

$$

\mathbf r=[1.0,\ 0.6,\ 0.2,\ -0.2].

$$

组内平均奖励为 $0.4$，总体标准差约为 $0.4472$。标准化后的组相对优势约为：

$$

\hat{\mathbf A}=[1.3416,\ 0.4472,\ -0.4472,\ -1.3416].

$$

第一个回答明显好于组内平均水平，因此优势最大；第四个回答明显较差，因此优势最小。训练时，模型会倾向于提高前两个回答中已采样 token 的概率，降低后两个回答中已采样 token 的概率。

## 2. 公式讲解

### 2.1 核心公式

对于同一个问题 $q$，旧策略生成一组回答：

$$

\{o_1,o_2,\ldots,o_G\}\sim
\pi_{\theta_{\text{old}}}(\cdot\mid q).

$$

设这些回答的奖励为 $\mathbf r=\{r_1,r_2,\ldots,r_G\}$。组内奖励均值与总体标准差为：

$$

\mu_r=\frac{1}{G}\sum_{i=1}^{G}r_i,

$$

$$

\sigma_r=\sqrt{\frac{1}{G}\sum_{i=1}^{G}(r_i-\mu_r)^2}.

$$

在结果监督的基本形式中，第 $i$ 个回答的组相对优势为：

$$

\hat A_i=\frac{r_i-\mu_r}{\sigma_r}.

$$

同一回答中的各个 token 可以共享该回答的优势：

$$

\hat A_{i,t}=\hat A_i.

$$

当前策略与旧策略对已采样 token 的概率比率为：

$$

\rho_{i,t}(\theta)=
\frac{\pi_\theta(o_{i,t}\mid q,o_{i,<t})}
{\pi_{\theta_{\text{old}}}(o_{i,t}\mid q,o_{i,<t})}.

$$

忽略批次期望后，GRPO 的核心裁剪目标可以写为：

$$

\begin{aligned}
J_{\text{GRPO}}(\theta)
=\frac{1}{G}\sum_{i=1}^{G}\frac{1}{|o_i|}
\sum_{t=1}^{|o_i|}
\Big[&\min\big(
\rho_{i,t}(\theta)\hat A_{i,t},\\
&\operatorname{clip}(\rho_{i,t}(\theta),1-\epsilon,1+\epsilon)
\hat A_{i,t}
\big)
-\beta D_{\mathrm{KL},i,t}\Big].
\end{aligned}

$$

原始 GRPO 论文使用下面的逐 token KL 估计量：

$$

D_{\mathrm{KL},i,t}=
\frac{\pi_{\text{ref}}(o_{i,t}\mid q,o_{i,<t})}
{\pi_\theta(o_{i,t}\mid q,o_{i,<t})}
-\log\frac{\pi_{\text{ref}}(o_{i,t}\mid q,o_{i,<t})}
{\pi_\theta(o_{i,t}\mid q,o_{i,<t})}-1.

$$

### 2.2 变量含义

- $q$：输入问题或提示词。
- $G$：针对同一问题采样的回答数量。
- $o_i$：第 $i$ 个完整回答， $|o_i|$ 是其 token 数量。
- $o_{i,t}$：第 $i$ 个回答中的第 $t$ 个 token。
- $o_{i,<t}$：该回答在第 $t$ 个 token 之前的前缀。
- $r_i$：第 $i$ 个回答获得的奖励。
- $\mu_r$ 、 $\sigma_r$：组内奖励的均值和总体标准差。
- $\hat A_i$ 、 $\hat A_{i,t}$：回答级和 token 级的组相对优势。
- $\pi_\theta$：正在更新的当前策略。
- $\pi_{\theta_{\text{old}}}$：生成当前训练样本的旧策略。
- $\pi_{\text{ref}}$：用于 KL 约束的参考策略，通常来自训练开始时的策略模型。
- $\rho_{i,t}$：当前策略与旧策略对同一个已采样 token 的概率比率。
- $\epsilon$：裁剪范围超参数。
- $\beta$：KL 正则强度。
- $D_{\mathrm{KL},i,t}$：当前策略与参考策略之间的逐 token KL 估计。
- $J_{\text{GRPO}}$：希望最大化的 GRPO 目标。

### 2.3 公式怎么理解

组内标准化先减去均值，使一组优势的平均值为 $0$；再除以标准差，使不同问题下奖励尺度更容易比较。这里的优势是相对量：同一个奖励在强组和弱组中可能对应不同优势。

概率比率 $\rho_{i,t}$ 衡量当前策略相对旧策略改变了多少。优势为正时，提高该 token 的概率是有利更新；优势为负时，降低概率是有利更新。裁剪项限制策略从过大的有利变化中继续获得收益，其含义与 PPO 相同。

KL 项约束当前策略不要过度偏离参考策略。它与旧策略不是同一个角色：旧策略用于计算本轮样本的概率比率，参考策略用于提供长期正则基准。

如果一组回答的奖励完全相同，则 $\sigma_r=0$，组内比较无法提供学习方向。实际实现通常会在分母加入很小的数，或者跳过没有奖励差异的组。

GRPO 省去的是 Critic，而不是奖励来源。它仍需要规则、奖励模型或其他验证器判断回答质量。

## 3. 代码示例

### 3.1 最小可运行代码

下面使用四个回答、每个回答两个 token，计算组相对优势、裁剪代理目标和 KL 正则。代码只展示 GRPO 的核心数值关系，不实现完整语言模型训练。

```python
import numpy as np


def group_relative_advantages(rewards, numerical_epsilon=1e-8):
    mean_reward = rewards.mean()
    std_reward = rewards.std()
    advantages = (
        (rewards - mean_reward) / (std_reward + numerical_epsilon)
    )
    return advantages, mean_reward, std_reward


def grpo_objective(
    new_probs,
    old_probs,
    ref_probs,
    advantages,
    clip_epsilon=0.2,
    kl_beta=0.04,
):
    token_advantages = advantages[:, None]
    ratios = new_probs / old_probs
    clipped_ratios = np.clip(
        ratios, 1 - clip_epsilon, 1 + clip_epsilon
    )

    unclipped_terms = ratios * token_advantages
    clipped_terms = clipped_ratios * token_advantages
    surrogate_terms = np.minimum(unclipped_terms, clipped_terms)

    ref_to_new_ratio = ref_probs / new_probs
    kl_terms = (
        ref_to_new_ratio - np.log(ref_to_new_ratio) - 1
    )
    objective = np.mean(surrogate_terms - kl_beta * kl_terms)
    return objective, ratios, surrogate_terms, kl_terms


rewards = np.array([1.0, 0.6, 0.2, -0.2])

# 每行对应一个回答，每列对应该回答中的一个已采样 token。
old_probs = np.array([
    [0.40, 0.50],
    [0.30, 0.40],
    [0.20, 0.50],
    [0.25, 0.40],
])
new_probs = np.array([
    [0.52, 0.55],
    [0.27, 0.32],
    [0.16, 0.45],
    [0.35, 0.56],
])
ref_probs = old_probs.copy()

advantages, mean_reward, std_reward = (
    group_relative_advantages(rewards)
)
objective, ratios, surrogate_terms, kl_terms = grpo_objective(
    new_probs, old_probs, ref_probs, advantages
)

np.set_printoptions(precision=4, suppress=True)
print("mean reward:", round(mean_reward, 4))
print("reward std:", round(std_reward, 4))
print("group advantages:", advantages)
print("probability ratios:\n", ratios)
print("selected surrogate terms:\n", surrogate_terms)
print("KL terms:\n", kl_terms)
print("GRPO objective:", round(objective, 4))

assert np.isclose(advantages.mean(), 0.0, atol=1e-7)
assert np.isclose(advantages.std(), 1.0, atol=1e-7)
```

### 3.2 输入输出说明

- `rewards` 是同一个问题下四个回答的奖励，形状为 `(4,)`。
- `old_probs`、`new_probs` 和 `ref_probs` 是旧策略、当前策略和参考策略对已采样 token 的概率，形状均为 `(4, 2)`。
- `advantages` 是四个回答的组相对优势，形状为 `(4,)`。
- `ratios`、`surrogate_terms` 和 `kl_terms` 是逐回答、逐 token 的计算结果，形状均为 `(4, 2)`。
- `objective` 是平均后的 GRPO 目标，训练时希望将其最大化。

运行代码会得到组内平均奖励 $0.4$ 、标准差约 $0.4472$，以及均值接近 $0$ 、标准差接近 $1$ 的组相对优势。具体的代理目标和 KL 数值由代码直接打印。

### 3.3 关键代码解释

`rewards.mean()` 和 `rewards.std()` 计算组内奖励基线与尺度。`advantages[:, None]` 将每个回答的优势扩展到该回答的所有 token。

`new_probs / old_probs` 对应概率比率 $\rho_{i,t}$。`np.minimum` 在未裁剪项与裁剪项之间选择更保守的一项。

`ref_to_new_ratio - np.log(ref_to_new_ratio) - 1` 实现原始 GRPO 目标中的逐 token KL 估计。最后从代理目标中减去乘有系数 $\beta$ 的 KL 项，得到需要最大化的目标。
