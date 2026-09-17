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
assert np.allclose(ratios, [1.5, 0.8, 0.5, 1.6])
assert np.allclose(per_sample, [1.2, 0.8, -0.8, -1.6])
assert np.isclose(objective, -0.1)
assert np.isclose(value_loss, 1.0)
print("PPO verification passed.")
