from dataclasses import dataclass


@dataclass(frozen=True)
class Node:
    name: str
    op: str
    inputs: tuple[str, ...] = ()
    value: float | None = None


def evaluate_op(op, values):
    if op == "Add":
        return values[0] + values[1]
    if op == "Mul":
        return values[0] * values[1]
    raise ValueError(f"unsupported operator: {op}")


def constant_fold(nodes):
    constants = {
        name: node.value
        for name, node in nodes.items()
        if node.op == "Const"
    }
    folded = dict(nodes)
    changed = True

    while changed:
        changed = False
        for name, node in list(folded.items()):
            if node.op in {"Add", "Mul"} and all(
                input_name in constants
                for input_name in node.inputs
            ):
                value = evaluate_op(
                    node.op,
                    [constants[input_name] for input_name in node.inputs],
                )
                folded[name] = Node(name, "Const", value=value)
                constants[name] = value
                changed = True

    return folded


def eliminate_dead_code(nodes, outputs):
    live = set()

    def visit(name):
        if name in live:
            return
        live.add(name)
        for input_name in nodes[name].inputs:
            visit(input_name)

    for output_name in outputs:
        visit(output_name)
    return {name: node for name, node in nodes.items() if name in live}


def topological_order(nodes):
    visited = set()
    order = []

    def visit(name):
        if name in visited:
            return
        for input_name in nodes[name].inputs:
            visit(input_name)
        visited.add(name)
        order.append(name)

    for name in nodes:
        visit(name)
    return order


def build_execution_plan(nodes):
    order = topological_order(nodes)
    return [
        nodes[name]
        for name in order
        if nodes[name].op not in {"Input", "Const"}
    ]


def analyze_last_use(nodes, plan):
    step_of = {node.name: step for step, node in enumerate(plan)}
    last_use = {}
    for step, node in enumerate(plan):
        for input_name in node.inputs:
            if input_name in step_of:
                last_use[input_name] = step
    return last_use


def execute(nodes, outputs, feeds):
    values = dict(feeds)
    for name in topological_order(nodes):
        node = nodes[name]
        if node.op == "Input":
            if name not in values:
                raise ValueError(f"missing input: {name}")
        elif node.op == "Const":
            values[name] = node.value
        else:
            arguments = [values[input_name] for input_name in node.inputs]
            values[name] = evaluate_op(node.op, arguments)
    return {name: values[name] for name in outputs}


graph = {
    "x": Node("x", "Input"),
    "c2": Node("c2", "Const", value=2.0),
    "c3": Node("c3", "Const", value=3.0),
    "c6": Node("c6", "Mul", ("c2", "c3")),
    "t": Node("t", "Mul", ("x", "c6")),
    "dead": Node("dead", "Add", ("c2", "c3")),
    "y": Node("y", "Add", ("t", "c3")),
}
outputs = ("y",)

folded_graph = constant_fold(graph)
optimized_graph = eliminate_dead_code(folded_graph, outputs)
plan = build_execution_plan(optimized_graph)
last_use = analyze_last_use(optimized_graph, plan)

original_result = execute(graph, outputs, {"x": 4.0})
optimized_result = execute(
    optimized_graph, outputs, {"x": 4.0}
)

assert folded_graph["c6"].op == "Const"
assert folded_graph["c6"].value == 6.0
assert "dead" not in optimized_graph
assert [node.name for node in plan] == ["t", "y"]
assert last_use == {"t": 1}
assert original_result == optimized_result == {"y": 27.0}
print("Compiler verification passed.")
