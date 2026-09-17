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

expected_advantages = np.array([
    1.34164076,
    0.44721359,
    -0.44721359,
    -1.34164076,
])
expected_ratios = np.array([
    [1.3, 1.1],
    [0.9, 0.8],
    [0.8, 0.9],
    [1.4, 1.4],
])

assert np.isclose(mean_reward, 0.4)
assert np.isclose(std_reward, np.sqrt(0.2))
assert np.allclose(advantages, expected_advantages)
assert np.isclose(advantages.mean(), 0.0, atol=1e-7)
assert np.isclose(advantages.std(), 1.0, atol=1e-7)
assert np.allclose(ratios, expected_ratios)
assert np.all(kl_terms >= 0.0)
assert np.isfinite(objective)
print("GRPO verification passed.")
