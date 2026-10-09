# ================================================================
#  miniXiangShan（RISC-V 乱序核）—— 仅负责生成 Verilog
#
#  用法：
#    make            等价于 make sim，生成仿真版（打开 difftest）
#    make sim        生成仿真版 Verilog
#    make fpga       生成上板版 Verilog
#    make clean      清理 sbt / mill 的中间产物
# ================================================================

.PHONY: all sim fpga s ff clean help
.DEFAULT_GOAL := sim

SHELL := /bin/bash

SBT_DIR   := .
BUILD_DIR := ./build

# 生成结果目录由 src/main/scala/Tops.scala 的 CoreGen 决定
RTL_DIR_SIMU := ./out
RTL_DIR_FPGA := ./out
TOP_V        := $(RTL_DIR_FPGA)/core_top.v

SCALA_FILES := $(shell find ./src/main/scala -name "*.scala")

all: sim

# ---------------- 生成仿真版 Verilog（difftest 打开） ----------------
sim:
	sbt "runMain minixiangshan.CoreGen simu"

# 兼容旧名字
s: sim

# ---------------- 生成上板版 Verilog（difftest 关闭） ----------------
fpga: $(TOP_V)

# 兼容旧名字
ff: fpga

$(TOP_V): $(SCALA_FILES)
	sbt "runMain minixiangshan.CoreGen fpga"

# ---------------- 清理 ----------------
clean:
	cd $(SBT_DIR) && sbt clean
	rm -rf $(BUILD_DIR) out

help:
	@echo "make / make sim : 生成仿真版 Verilog -> $(RTL_DIR_SIMU)"
	@echo "make fpga       : 生成上板版 Verilog -> $(RTL_DIR_FPGA)"
	@echo "make clean      : 清理中间产物"
