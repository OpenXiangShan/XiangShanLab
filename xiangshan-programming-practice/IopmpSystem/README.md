# IopmpSystem

<!-- vim-markdown-toc GFM -->

* [简介（Introduction）](#简介introduction)
* [使用方法（Usage）](#使用方法usage)
* [相关工作（Related Works）](#相关工作related-works)

<!-- vim-markdown-toc -->

IopmpSystem 是一个用于教学与实验的系统级 IOPMP 示例工程。
它的顶层代码不是单独的 IOPMP 核心，而是 `IopmpSystem.scala` 中的完整连接：

- `Dcache -> IOPMP -> Memory`
- Dcache 作为 AXI4 Master 发起请求
- IOPMP 负责访问检查与转发
- Memory 作为 AXI4 Slave 响应请求

`IopmpSystem` is a teaching-oriented system-level IOPMP example.
Its top-level code is not only the IOPMP core, but a complete `Dcache -> IOPMP -> Memory` system defined in `IopmpSystem.scala`.

## 简介（Introduction）

该工程包括：

* `src/main/scala/IopmpSystem.scala`：系统顶层与 Verilog 生成入口
* `src/main/scala/Dcache.scala`：AXI4 Master 侧请求发起器
* `src/main/scala/Iopmp.scala` / `IopmpBridge.scala` / `IopmpChecker*.scala`：IOPMP 核心与桥接逻辑
* `src/main/scala/Memory.scala`：AXI4 Slave 侧存储器模型

它的重点不是展示一个通用上游项目名，而是展示一个可运行、可观察的系统级 IOPMP 教学工程。

This project includes:

* `IopmpSystem.scala` as the top-level system and Verilog generation entry
* `Dcache.scala` as the AXI4 master-side requester
* `Iopmp*.scala` as the IOPMP core and bridge logic
* `Memory.scala` as the AXI4 slave-side memory model

## 使用方法（Usage）

项目由 Makefile 构建，可以在命令行输入 `make help` 查看更详细的帮助说明。

The project is built using a Makefile. You can enter `make help` to view more detailed help instructions.

```bash
# 环境初始化，更新子仓库
# Environment initialization, update submodules.
make init

# 生成Verilog
# Generate Verilog
make verilog
```

## 相关工作（Related Works）

* [zero-day-labs/riscv-iopmp](https://github.com/zero-day-labs/riscv-iopmp)
  * 采用Verilog（Implemented in Verilog）
  * 支持IOPMP full-mode
