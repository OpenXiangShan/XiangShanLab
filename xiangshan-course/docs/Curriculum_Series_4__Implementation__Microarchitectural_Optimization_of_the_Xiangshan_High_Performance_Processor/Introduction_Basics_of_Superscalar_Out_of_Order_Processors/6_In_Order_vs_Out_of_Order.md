# 6.顺序 vs. 乱序

## [附件: In_Order_vs_Out_of_Order_Xiangshan_Execution_Architecture_Deep_Dive.pptx](./attachments/eBC_LgF-_5-lFJZw/In_Order_vs_Out_of_Order_Xiangshan_Execution_Architecture_Deep_Dive.pptx)
## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">6.1概述</font>
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">香山处理器是国产自主研发的高性能 RISC-V 架构处理器，其指令执行架构的核心设计围绕 “顺序执行保障正确性、乱序执行提升性能” 展开。本章节将详细解析香山处理器中顺序与乱序执行的实现逻辑、应用场景及核心价值，帮助开发者理解其执行架构的设计思路与底层原理。</font>

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">6.2顺序执行：架构正确性的核心保障</font>
### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">6.2.1 执行阶段的顺序性体现</font>
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">香山处理器在指令处理的前端（取指、译码、寄存器重命名）及访存、结果提交阶段，严格遵循程序固有顺序执行，具体表现为：</font>

1. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">前端取指与译码</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：取指单元按程序计数器（PC）递增顺序从指令缓存中读取指令流，译码单元逐一解析指令的操作码、操作数类型及寻址模式，确保指令语义被精准识别，无任何顺序错乱；</font>
2. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">寄存器重命名</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：该阶段虽会对架构寄存器进行重映射（消除写后读、写后写冲突），但仍为每条指令保留程序顺序标记，不改变指令的逻辑执行顺序；</font>
3. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">访存指令执行</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：基于RISC-V内存一致性模型，香山对Load/Store类访存指令采用严格顺序执行规则，确保地址计算、缓存访问、数据回写全流程按程序顺序完成，避免内存数据竞争或错乱；</font>
4. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">结果提交</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：所有指令执行结果需按程序顺序提交至架构状态，保证最终输出符合 RISC-V 架构规范。</font>

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">6.2.2 顺序执行的核心价值</font>
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">顺序执行是香山处理器稳定运行的底层基石，尤其适配对时序敏感、数据一致性要求高的场景，如操作系统内核调度、实时控制程序、嵌入式设备驱动等，可有效避免因执行顺序错乱导致的程序崩溃或数据错误。</font>

## <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">6.3 乱序执行：高性能算力的核心支撑</font>
### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">6.3.1 乱序执行的实现逻辑</font>
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">为突破顺序执行的性能瓶颈，香山处理器的后端执行单元采用深度乱序执行架构，核心流程如下：</font>

1. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">指令分发</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：译码后的指令被送入统一的保留站（Reservation Station），而非直接按顺序进入执行单元；</font>
2. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">动态调度</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：保留站持续监测指令操作数就绪状态（如寄存器数据是否写入、访存数据是否返回），并结合 ALU、浮点运算单元、乘法器等功能单元的空闲状态，动态调度指令执行 —— 若某条指令因访存延迟、运算依赖阻塞，调度器会跳过该指令，优先执行后续操作数就绪的指令；</font>
3. **<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">结果暂存</font>**<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">：乱序执行完成的指令不会立即更新架构状态，而是先存入重排序缓冲区（ROB），待前置所有指令执行完毕后，再按程序顺序提交结果。</font>

### <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">6.3.2 乱序执行的核心价值</font>
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">乱序执行充分利用香山处理器多发射、多执行单元的硬件资源，有效掩盖访存延迟和运算延迟，最大化挖掘指令级并行性（ILP），显著提升处理器在通用计算、服务器、高性能运算等场景下的峰值算力与吞吐率。</font>

# <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">6.4</font><font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">香山处理器顺序与乱序执行的协同机制  </font>
<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">香山处理器通过 “分层设计” 实现顺序与乱序执行的协同，核心逻辑可总结为：</font>

```plain
前端（取指/译码）严格顺序 → 后端（执行）灵活乱序 → 提交阶段恢复顺序
```

<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">该机制既满足了架构对指令执行正确性的硬性要求，又通过乱序执行释放硬件算力，实现“正确性”与“高性能”的平衡。具体协同流程如下：</font>

1. <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">前端保证指令解析的顺序性，为后续执行提供准确的指令语义与顺序标记；</font>
2. <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">后端通过乱序调度提升执行效率，利用硬件资源并行处理无依赖的指令；</font>
3. <font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">重排序缓冲区（ROB）按程序顺序提交结果，将乱序执行的结果还原为符合架构要求的顺序输出，最终保障程序运行的正确性。</font>



<font style="color:rgb(0, 0, 0);background-color:rgba(0, 0, 0, 0);">综上所述，香山处理器的顺序与乱序执行设计，是针对RISC-V架构特性与高性能计算需求的最优解：顺序执行筑牢了架构正确性的基础，适配低延迟、高一致性的业务场景；乱序执行则充分释放硬件潜力，满足高算力、高吞吐的应用需求。理解这一执行机制，有助于开发者针对香山处理器进行程序优化（如合理调整指令依赖、优化访存顺序），最大化发挥处理器的性能优势。  </font>



> 更新: 2026-05-12 14:34:41  
