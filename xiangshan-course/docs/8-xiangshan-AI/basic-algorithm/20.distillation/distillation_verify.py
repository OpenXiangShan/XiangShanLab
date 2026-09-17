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

assert np.allclose(
    softmax(teacher_logits, 1.0),
    [0.86681333, 0.11731043, 0.01587624],
)
assert np.allclose(
    initial[3],
    [0.66524096, 0.24472847, 0.09003057],
)
assert np.isclose(initial[3].sum(), 1.0)
assert np.isclose(final[4].sum(), 1.0)
assert final[0] < initial[0]
assert final[2] < initial[2]
assert np.argmax(trained_student_logits) == label
print("Distillation verification passed.")
