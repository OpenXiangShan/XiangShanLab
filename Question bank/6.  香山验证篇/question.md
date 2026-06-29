# 香山验证篇题库

1.  **switch-case**
2.  **冒泡排序**
3.  **覆盖率收集**
    使用 [https://github.com/riscv-software-src/riscv-isa-sim](https://github.com/riscv-software-src/riscv-isa-sim) 收集覆盖率。
    1.  增加 AFL 以及分支覆盖率、路径覆盖率的收集。
    2.  思考如何提升覆盖率。时间：2 周。
4.  **RISC-V Matrix Extension**
    **题目：**
    RISC-V 矩阵扩展是一种用于加速矩阵运算的指令集扩展。NEMU-Matrix 是一个基于 NEMU 修改的、支持该扩展的指令级模拟器。

    **目标：**
    请你通过阅读该扩展的文档和代码，理解矩阵扩展的指令格式与行为，使用 Python 构建一个**自动化测试程序生成器**，能够：
    1.  随机生成一段合法的 RISC-V 汇编代码或 C 代码（内联汇编），其中必须包含对 RISC-V 矩阵扩展指令的调用。
    2.  自动调用给定的编译器 (`triton-cpu-llvm-install-self-define`) 将生成的汇编/C 代码编译为 RISC-V 二进制程序 (ELF 或 BIN)。
    3.  将该二进制程序载入 NEMU-Matrix 模拟器中运行，并捕获运行结果（如日志、模拟器退出状态等）。
    4.  使用 YAML 或 Mako 等模板来结构化描述指令信息。
    5.  其余功能可自行扩展。

    **提交内容：**
    -   Python 源代码（以及必要的辅助脚本）。
    -   一份简短的 README.md。
    -   一个生成并运行成功的测试用例输出样例（日志、汇编代码、二进制文件等）。

    **资源链接：**
    -   [https://github.com/yu-yake2002/NEMU-Matrix.git](https://github.com/yu-yake2002/NEMU-Matrix.git)
5. **触发BUG**
    - 如何写出触发ROB FULL的测试用例
    - 如何写出触发LQ/SQ FULL的测试用例