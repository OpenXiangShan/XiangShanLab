# AI 编译器

## 1. 文字讲解

### 1.1 AI 编译器解决什么问题

神经网络通常由矩阵乘法、卷积、归一化、激活函数等算子组成。训练框架中的模型描述强调易于构建和训练，但执行系统还需要解决另外一组问题：

- 算子之间存在怎样的数据依赖？
- 哪些计算可以在执行前化简或删除？
- 算子应按什么顺序执行？
- 中间张量何时产生、何时失效，能否复用存储空间？
- 高层算子最终应怎样变成运行时可以执行的程序？

AI 编译器负责把模型描述转换为经过分析和优化的可执行形式。它和传统编译器的共同点是都包含中间表示、优化、降级和代码生成；不同点是 AI 编译器主要处理张量、计算图、张量形状和算子等领域信息。

### 1.2 计算图与中间表示

计算图（Computation Graph）用节点表示算子，用有向边表示张量的数据流。例如：

```text
x -----> Mul -----> Add -----> y
          ^          ^
          |          |
        Const(6)   Const(3)
```

图中的 `Mul` 必须等输入 `x` 和常量 `6` 就绪后才能执行，`Add` 又依赖 `Mul` 的结果。没有环的前向计算图可以按照拓扑顺序执行。

编译器通常不会一直直接操作用户的 Python 代码，而会把模型转换成中间表示（Intermediate Representation，IR）。IR 会显式记录算子类型、输入输出、常量、张量形状、数据类型和属性，使编译器可以用统一规则分析和改写模型。

编译过程中可以存在多层 IR：

- **高层 IR**：接近模型计算图，保留 MatMul、Conv、Attention 等算子语义。
- **低层 IR**：逐渐显式描述循环、索引、缓冲区和更基础的运算。
- **目标程序**：运行时可加载的指令、内核调用或其他可执行表示。

从高层表示逐步转换到低层表示称为降级（Lowering）。每次降级都减少一部分抽象信息，同时补充下一层执行所需的具体信息。

### 1.3 核心编译流程

一个简化的 AI 编译流程如下：

1. **模型导入或图捕获**：把框架模型转换为计算图。
2. **合法性与形状分析**：检查算子输入、数据类型和张量形状。
3. **图优化**：进行常量折叠、死代码消除、公共子表达式消除、算子融合等语义保持变换。
4. **算子降级**：把高层算子转换为更基础的算子、循环或库调用。
5. **算子调度**：在满足数据依赖的前提下确定执行顺序，并为具体计算选择实现计划。
6. **内存规划**：分析中间张量的生命周期，为张量分配或复用缓冲区。
7. **代码生成与打包**：产生运行时可以加载的可执行计划。
8. **运行与校验**：输入真实数据，比较编译前后的输出是否一致。

这些阶段并非所有系统都采用完全相同的顺序。例如，算子融合可能改变张量生命周期，内存规划一般要基于较稳定的优化后计算图；目标相关优化也可能在降级后的 IR 上继续执行。

### 1.4 图优化、调度与内存规划

**常量折叠**会提前计算只依赖常量的子图。例如：

$$

6=2\times 3

$$

可以在编译期完成，因此运行时只需读取常量 `6`。

**死代码消除**会删除最终输出不依赖的节点。例如，计算了 `2+3`，但其结果既不是模型输出，也没有被后续节点使用，那么这个节点可以删除。

**算子调度**首先必须满足数据依赖：生产某个张量的节点要先于使用它的节点执行。在此基础上，不互相依赖的节点可能存在多种合法顺序。更低层的张量程序还会涉及循环分块、循环交换等实现调度，但它们不能改变程序应有的计算结果。

**内存规划**会记录每个中间张量从产生到最后一次使用的生命周期。若两个张量的生命周期不重叠，它们可以使用同一个缓冲区编号；如果生命周期重叠，就不能让后一个张量覆盖前一个仍然需要的数据。

### 1.5 常见工具的基本定位

这些名称经常一起出现，但它们不是同一层面的同类产品：

- **ONNX**：开放的模型表示格式和通用计算图 IR，定义图结构、标准算子和数据类型，主要用于模型交换；ONNX 本身不是完整编译器。
- **MLIR**：用于构建编译器的多层中间表示基础设施。它通过不同方言表达不同抽象层级，并支持在方言之间逐步降级。
- **TVM**：张量编译器栈。高层可使用 Relax 表示模型计算，TensorIR 表示和优化张量程序，并提供规则或搜索驱动的调度能力。
- **XLA**：面向机器学习线性代数计算的编译器。它对 StableHLO/HLO 表示执行优化、分析、后端降级和代码生成。
- **TensorRT**：面向 NVIDIA 平台的深度学习推理优化器、构建器和运行时。构建阶段把网络生成序列化的推理引擎，运行阶段加载并执行该引擎。

实际工具链可以把它们组合使用，例如框架先导出 ONNX，后续系统再导入模型并完成优化和执行。不能因为一个工具使用 IR，就把它等同于完整编译器；也不能因为一个编译器能够导入 ONNX，就认为 ONNX 负责代码生成。

### 1.6 直观例子

考虑下面的计算：

$$

\begin{aligned}
c_6&=2\times 3,\\
t&=x\times c_6,\\
d&=2+3,\\
y&=t+3.
\end{aligned}

$$

其中 $ d $ 不参与输出 $ y $，因此可以删除；$ c_6 $ 只由常量决定，因此可以折叠。原始图：

```text
c6 = Mul(2, 3)
t  = Mul(x, c6)
d  = Add(2, 3)    # 与输出无关
y  = Add(t, 3)
```

优化后可写成：

```text
t = Mul(x, 6)
y = Add(t, 3)
```

当 $ x=4 $ 时，两张图都得到：

$$

y=4\times 6+3=27.

$$

优化后的执行计划只需要两个运行时算子。中间张量 $ t $ 在 `Add` 执行后不再使用，其缓冲区随后可以释放或复用。

## 2. 公式讲解

### 2.1 计算图

把计算图记为有向无环图：

$$

G=(V,E).

$$

对于节点 $ v\in V $，设其直接前驱节点集合为 $ \operatorname{pred}(v) $，则节点输出为：

$$

z_v=f_v\left(\{z_u\mid u\in\operatorname{pred}(v)\};\theta_v\right).

$$

这里的边 $ (u,v)\in E $ 表示节点 $ v $ 需要使用节点 $ u $ 的输出，因此 $ u $ 是 $ v $ 的前驱。

### 2.2 常量折叠与死代码消除

若节点 $ v $ 的所有输入 $ c_1,c_2,\ldots,c_k $ 都是编译期已知常量，则可以在编译期求值：

$$

c_v=f_v(c_1,c_2,\ldots,c_k).

$$

随后用常量 $ c_v $ 替换原算子节点。这个过程称为常量折叠。

设模型输出节点集合为 $ O $。所有必须保留的节点构成集合：

$$

V_{\text{live}}
=
\{u\in V\mid
\exists o\in O,\ u\leadsto o\}
\cup O.

$$

$u\leadsto o $ 表示从节点 $ u $ 到输出节点 $ o $ 存在一条有向路径。不属于 $ V_{\text{live}} $ 的节点不会影响模型输出，可以由死代码消除删除。

### 2.3 合法调度

给每个节点分配执行次序 $ s(v) $。若边 $ (u,v)\in E $ 表示 $ v $ 依赖 $ u $，合法拓扑调度必须满足：

$$

(u,v)\in E
\Longrightarrow
s(u)<s(v).

$$

只要所有数据依赖都满足这个约束，图中互不依赖的节点就可能采用不同的合法顺序。

### 2.4 张量生命周期与内存规划

设中间张量 $ t $ 在第 $ b_t $ 步产生，在第 $ e_t $ 步最后一次被使用，其生命周期区间为：

$$

L_t=[b_t,e_t].

$$

两个张量 $ t_i $ 和 $ t_j $ 可以复用同一个缓冲区的充分条件之一，是生命周期互不重叠：

$$

e_{t_i}<b_{t_j}
\quad\text{或}\quad
e_{t_j}<b_{t_i}.

$$

若张量 $ t $ 占用的字节数为 $ \operatorname{size}(t) $，第 $ k $ 个执行步骤仍然存活的张量集合为 $ A_k $，则不考虑复用时，该步骤的中间张量存储量可以写为：

$$

M_k=\sum_{t\in A_k}\operatorname{size}(t).

$$

整个执行计划对应的峰值为：

$$

M_{\text{peak}}=\max_k M_k.

$$

内存规划根据生命周期和张量大小分配缓冲区。生命周期不重叠只是允许复用的基本条件，实际系统还需要检查大小、对齐、数据布局和原地计算规则。

### 2.5 变量含义

- $ G $：计算图。
- $ V $：图中节点的集合，每个节点表示输入、常量或算子。
- $ E $：有向边集合，每条边表示一个数据依赖。
- $ u $、$ v $：计算图中的两个节点。
- $ \operatorname{pred}(v) $：节点 $ v $ 的直接前驱集合。
- $ f_v $：节点 $ v $ 执行的算子函数。
- $ z_u $、$ z_v $：节点 $ u $ 和节点 $ v $ 的输出张量。
- $ \theta_v $：节点 $ v $ 的属性或参数。
- $ c_1,\ldots,c_k $：编译期已知的常量输入。
- $ c_v $：常量折叠后得到的常量结果。
- $ O $：模型输出节点集合。
- $ V_{\text{live}} $：能够影响模型输出、因而必须保留的节点集合。
- $ u\leadsto o $：从 $ u $ 到 $ o $ 存在有向路径。
- $ s(v) $：节点 $ v $ 在执行计划中的次序。
- $ t $、$ t_i $、$ t_j $：执行过程中产生的中间张量。
- $ b_t $：张量 $ t $ 的产生步骤。
- $ e_t $：张量 $ t $ 的最后使用步骤。
- $ L_t $：张量 $ t $ 的生命周期区间。
- $ A_k $：第 $ k $ 步仍然存活的中间张量集合。
- $ \operatorname{size}(t) $：张量 $ t $ 的存储字节数。
- $ M_k $：第 $ k $ 步中所有存活中间张量的总字节数。
- $ M_{\text{peak}} $：所有执行步骤中 $ M_k $ 的最大值。
- $ \exists $：存在量词。
- $ \cup $：集合并集。
- $ \Longrightarrow $：逻辑蕴含。

### 2.6 公式怎么理解

计算图公式描述的是“每个节点使用哪些前驱结果”。编译器只有明确这些依赖，才能判断节点能否提前执行、删除或改写。

常量折叠把运行时计算变成编译期结果；死代码消除从输出反向寻找所有依赖，只保留可能影响输出的节点。这两种优化都不应改变输入到输出的函数关系。

调度公式规定了最基本的正确性边界：消费者必须排在生产者之后。内存规划公式进一步使用调度结果计算张量的产生和最后使用时刻。由此可见，图优化、调度和内存规划不是完全独立的步骤：图结构或执行顺序改变后，张量生命周期也可能随之改变。

## 3. 代码示例

### 3.1 最小可运行代码

下面用纯 Python 实现一个最小计算图编译器。它支持 `Input`、`Const`、`Add` 和 `Mul` 四种节点，并依次执行：

1. 常量折叠。
2. 从输出节点反向进行死代码消除。
3. 拓扑排序并生成线性执行计划。
4. 分析中间结果的最后使用位置。
5. 执行原始图和优化后的计划，比较结果。

```python
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

print("folded c6:", folded_graph["c6"])
print("optimized nodes:", list(optimized_graph))
print("execution plan:", [(node.op, node.name) for node in plan])
print("last use:", last_use)
print("original result:", original_result)
print("optimized result:", optimized_result)

assert folded_graph["c6"].op == "Const"
assert folded_graph["c6"].value == 6.0
assert "dead" not in optimized_graph
assert [node.name for node in plan] == ["t", "y"]
assert last_use == {"t": 1}
assert original_result == optimized_result == {"y": 27.0}
print("Compiler verification passed.")
```

### 3.2 输入输出说明

- `graph` 是原始计算图，共包含输入、常量、四个算子节点。
- `outputs` 指定 `y` 是需要保留的模型输出。
- `constant_fold` 把 `c6 = Mul(c2, c3)` 和不影响输出的常量子图都先计算成常量。
- `eliminate_dead_code` 从 `y` 反向遍历依赖，因此删除 `dead`。
- `plan` 只包含运行时仍需执行的 `t = Mul(x, c6)` 和 `y = Add(t, c3)`。
- `last_use` 等于 `{"t": 1}`，表示中间值 `t` 最后一次在执行计划第 `1` 步使用。
- 输入 `x=4.0` 时，原始图与优化图的输出都是 `{"y": 27.0}`。

### 3.3 关键代码解释

`Node` 是本示例的中间表示。每个节点保存名称、算子类型、输入名称和可选常量值。真实 AI 编译器的 IR 还会保存张量形状、数据类型、布局、算子属性和控制流等信息。

`constant_fold` 反复寻找输入全部为常量的 `Add` 或 `Mul` 节点，将其替换为 `Const`。反复扫描是为了处理多层常量子图：前一轮产生的新常量可以让后一层继续折叠。

`eliminate_dead_code` 从输出节点开始反向访问输入。没有被访问到的节点不会影响输出，因此不进入优化图。

`topological_order` 保证每个节点的输入先于该节点出现。`build_execution_plan` 再移除不需要运行时计算的输入和常量节点，得到线性算子计划。

`analyze_last_use` 演示内存规划所需的一项基础分析。它记录中间结果最后在哪个运行时步骤被读取；完整内存规划器还需要结合张量大小、产生步骤和缓冲区约束进行分配。

最后，代码分别解释执行原始图和优化图，并使用断言检查常量折叠、死代码消除、执行顺序、生命周期以及输出等价性。

