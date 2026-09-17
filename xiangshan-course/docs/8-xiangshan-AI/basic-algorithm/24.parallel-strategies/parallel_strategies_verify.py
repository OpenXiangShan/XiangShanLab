import numpy as np


def scalar_half_squared_error_gradient(x, target, weight):
    prediction = weight * x
    return np.mean(x * (prediction - target))


def data_parallel_gradient(x, target, weight, shard_count):
    x_shards = np.array_split(x, shard_count)
    target_shards = np.array_split(target, shard_count)
    local_gradients = [
        scalar_half_squared_error_gradient(x_part, y_part, weight)
        for x_part, y_part in zip(x_shards, target_shards)
    ]
    return np.mean(local_gradients), local_gradients


def column_tensor_parallel(x, weight, shard_count):
    weight_shards = np.array_split(weight, shard_count, axis=1)
    output_shards = [x @ shard for shard in weight_shards]
    return np.concatenate(output_shards, axis=1)


def pipeline_schedule(stage_count, microbatch_count):
    slots = []
    for time_step in range(stage_count + microbatch_count - 1):
        active = []
        for stage in range(stage_count):
            microbatch = time_step - stage
            if 0 <= microbatch < microbatch_count:
                active.append((stage, microbatch))
        slots.append(active)
    return slots


def pipeline_forward(x, microbatch_count):
    microbatches = np.array_split(x, microbatch_count)
    outputs = []
    for microbatch in microbatches:
        stage_0_output = 2.0 * microbatch
        stage_1_output = stage_0_output + 3.0
        outputs.append(stage_1_output)
    return np.concatenate(outputs)


def expert_0(x):
    return 2.0 * x


def expert_1(x):
    return x + 10.0


def expert_parallel(tokens, routes):
    experts = [expert_0, expert_1]
    output = np.empty_like(tokens, dtype=np.float64)
    for expert_id, expert in enumerate(experts):
        token_indices = np.flatnonzero(routes == expert_id)
        output[token_indices] = expert(tokens[token_indices])
    return output


x_train = np.array([1.0, 2.0, 3.0, 4.0])
target = 2.0 * x_train
weight = 1.0
full_gradient = scalar_half_squared_error_gradient(
    x_train, target, weight
)
dp_gradient, local_gradients = data_parallel_gradient(
    x_train, target, weight, shard_count=2
)

x_linear = np.array([
    [1.0, 2.0, -1.0],
    [0.5, -1.0, 2.0],
])
linear_weight = np.array([
    [1.0, 0.0, 2.0, -1.0],
    [0.5, 1.0, -0.5, 2.0],
    [-1.0, 3.0, 1.0, 0.5],
])
dense_output = x_linear @ linear_weight
tp_output = column_tensor_parallel(
    x_linear, linear_weight, shard_count=2
)

pipeline_input = np.arange(1.0, 7.0)
pipeline_output = pipeline_forward(
    pipeline_input, microbatch_count=3
)
direct_output = 2.0 * pipeline_input + 3.0
schedule = pipeline_schedule(
    stage_count=2, microbatch_count=3
)

tokens = np.array([1.0, 2.0, 3.0, 4.0])
routes = np.array([0, 1, 0, 1])
ep_output = expert_parallel(tokens, routes)
serial_output = np.array([
    expert_0(token) if route == 0 else expert_1(token)
    for token, route in zip(tokens, routes)
])

assert np.isclose(full_gradient, -7.5)
assert np.allclose(local_gradients, [-2.5, -12.5])
assert np.isclose(dp_gradient, full_gradient)
assert np.allclose(tp_output, dense_output)
assert schedule == [
    [(0, 0)],
    [(0, 1), (1, 0)],
    [(0, 2), (1, 1)],
    [(1, 2)],
]
assert np.allclose(pipeline_output, direct_output)
assert np.allclose(ep_output, [2.0, 12.0, 6.0, 14.0])
assert np.allclose(ep_output, serial_output)
print("Parallel strategies verification passed.")
