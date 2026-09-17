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

assert np.allclose(pair_losses, [0.2014, 0.0486, 0.2014], atol=1e-4)
assert np.isclose(reward_loss, 0.1505, atol=1e-4)
assert np.isclose(kl, 0.1054, atol=1e-4)
assert np.isclose(rlhf_objective, 1.2289, atol=1e-4)
print("RLHF verification passed.")
