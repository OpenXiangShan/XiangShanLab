# ChiselIOPMP

<!-- vim-markdown-toc GFM -->

* [简介（Introduction）](#简介introduction)
* [使用方法（Usage）](#使用方法usage)
* [相关工作（Related Works）](#相关工作related-works)

<!-- vim-markdown-toc -->

ChiselIOPMP是RISC-V输入输出物理内存保护(I/O Physical Memory Protection, IOPMP)的开源Chisel实现。
现有的开源IOPMP实现主要是用Verilog编写的[相关工作](#相关工作related-works)。
ChiselIOPMP旨在将Chisel敏捷开发的方法应用于IOPMP的实现。

`ChiselIOPMP` is an open-sourced Chisel implementation of the RISC-V I/O Physical Memory Protection (IOPMP).
Existing open-sourced IOPMP implementations are primarily written in Verilog (see [Related Works](#相关工作related-works)).
`ChiselIOPMP` aims to leverage Chisel's agile development methodology for IOPMP implementation.

## 简介（Introduction）

该实现包括:

* IOPMP full-mode: src/main/scala/Iopmp.scala
  * srcmd 格式 0
  * mdcfg 格式 0
  * apb配置接口
  * 1对1的axi总线桥
  * 支持线中断
* IOPMP full-mode的单元测试: '待添加'

更多信息，请参阅[文档](https://openxiangshan.github.io/ChiselIOPMP/)。'待添加'

This implementation includes:

* IOPMP full-mode: src/main/scala/Iopmp.scala
  * srcmd format 0
  * mdcfg format 0
  * APB configuration interface
  * One-to-one AXI bus bridge
  * Supports wire interrupt
* The unit tests for IOPMP full-mode: 'TODO'

For more detailed information, please refer to the [documentation](https://openxiangshan.github.io/ChiselIOPMP/). 'TODO'

## 使用方法（Usage）

项目由Makfile构建，可以在命令行输入'make help'查看更详细的帮助说明。

The project is built using Makefile. You can enter `make help` in the command line to view more detailed help instructions.

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
