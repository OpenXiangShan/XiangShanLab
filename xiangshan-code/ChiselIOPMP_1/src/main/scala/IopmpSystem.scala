/***************************************************************************************
* Copyright (c) 2024 Beijing Institute of Open Source Chip (BOSC)
*
* ChiselIOPMP is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*
* See the Mulan PSL v2 for more details.
***************************************************************************************/

/***************************************************************************************
 * IOPMP系统顶层 - 完整的系统连接
 *
 * 【系统架构】
 * ============================================================================
 *
 *   ┌─────────────┐      ┌─────────────┐      ┌─────────────┐
 *   │   Dcache    │      │    IOPMP    │      │   Memory    │
 *   │  (Master)   │ ───> │ (Bridge)    │ ───> │   (Slave)   │
 *   └─────────────┘      └─────────────┘      └─────────────┘
 *        │                     │                    │
 *        │                     │                    │
 *        ▼                     ▼                    ▼
 *   控制接口              APB配置接口           调试接口
 *   (发起请求)            (设置规则)           (查看数据)
 *
 *
 * 【数据流说明】
 * ============================================================================
 * 1. Dcache发起读写请求 → IOPMP的Slave端口接收
 * 2. IOPMP检查请求权限：
 *    - 通过：请求转发到IOPMP的Master端口 → Memory执行
 *    - 不通过：返回错误响应，触发中断
 * 3. Memory响应请求 → 数据原路返回
 *
 * 【Diplomacy连接语法】
 * ============================================================================
 * - sink := source   : 数据从source流向sink
 * - Master节点是数据源，Slave节点是数据宿
 * - 连接时：SlaveNode := MasterNode
 *
 * 【LazyModule层次结构】
 * ============================================================================
 * IopmpSystemWrapper (顶层包装器)
 *     │
 *     └── IopmpSystemLazy (系统核心)
 *             ├── DcacheLazy (数据缓存模拟器)
 *             ├── IopmpLazy (IOPMP核心)
 *             │       ├── IopmpBridgeLazy (AXI4桥接)
 *             │       ├── IopmpChecker (权限检查器)
 *             │       └── APB2Reg (配置寄存器)
 *             └── MemoryLazy (存储器模拟器)
 *
 ***************************************************************************************/

package iopmp

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.util._
import _root_.circt.stage.ChiselStage

/***************************************************************************************
 * IopmpSystemLazy - IOPMP系统的核心LazyModule
 *
 * 【设计说明】
 * - 这是系统的核心模块，包含所有子模块及其连接
 * - 使用Diplomacy自动处理AXI4接口参数协商
 * - 对外暴露控制接口和配置接口
 *
 * 【参数说明】
 * - numBridge: IOPMP桥接器数量，支持多个独立的数据通道
 * - memDepth: Memory存储深度
 ***************************************************************************************/
class IopmpSystemLazy(
  numBridge: Int = 1,
  memDepth: Int = 1024
)(implicit p: Parameters) extends LazyModule {

  /*****************************************************************************
   * 创建子模块实例
   *
   * 【LazyModule实例化语法】
   * - LazyModule(new ModuleName): 创建LazyModule实例
   * - 实例化时不会立即生成硬件
   * - 只有在.module被访问时才生成硬件
   *
   * 【模块说明】
   * - dcache: 模拟数据缓存，作为AXI4 Master发起请求
   * - iopmp: I/O内存保护单元，检查请求权限
   * - memory: 模拟存储器，作为AXI4 Slave响应请求
   *****************************************************************************/
  val dcache = LazyModule(new DcacheLazy())
  val iopmp = LazyModule(new IopmpLazy(numBridge))
  val memory = LazyModule(new MemoryLazy(depth = memDepth))

  /*****************************************************************************
   * Diplomacy连接 - 核心部分！
   *
   * 【连接语法详解】
   * ============================================================================
   *
   * AXI4数据流: Dcache(Master) → IOPMP(Bridge) → Memory(Slave)
   *
   * ┌─────────────────────────────────────────────────────────────────────────┐
   * │  连接语法: sink := source (左边接收，右边发送)                            │
   * │                                                                         │
   * │  Dcache          IOPMP               Memory                             │
   * │  ┌──────┐       ┌──────┐            ┌──────┐                            │
   * │  │Master├──────>│Slave │            │      │                            │
   * │  │ Node │       │ Node │            │      │                            │
   * │  └──────┘       ├──────┤            │      │                            │
   * │                 │Master├───────────>│Slave │                            │
   * │                 │ Node │            │ Node │                            │
   * │                 └──────┘            └──────┘                            │
   * │                                                                         │
   * │  连接1: iopmp.slaveNodes(0) := dcache.masterNode                       │
   * │  连接2: memory.slaveNode := iopmp.masterNodes(0)                       │
   * └─────────────────────────────────────────────────────────────────────────┘
   *
   * 【关键理解】
   * - := 操作符左边是"接收端/目的地"(sink)
   * - := 操作符右边是"发送端/数据源"(source)
   * - 数据从右边流向左边
   * - Master节点发起请求，是数据源(source)
   * - Slave节点接收请求，是数据宿(sink)
   *
   * 【记忆口诀】"左边接收，右边发送" 或 "Slave := Master"
   *****************************************************************************/

  /*******************************************
   * 连接1: Dcache Master → IOPMP Slave
   *
   * 【说明】
   * - IOPMP的slaveNode接收来自Dcache的请求
   * - slaveNodes(0)是接收端(sink)，masterNode是发送端(source)
   * - 数据流: dcache.masterNode → iopmp.slaveNodes(0)
   * - IOPMP内部会对请求进行权限检查
   *******************************************/
  iopmp.slaveNodes(0) := dcache.masterNode

  /*******************************************
   * 连接2: IOPMP Master → Memory Slave
   *
   * 【说明】
   * - 通过IOPMP检查的请求被转发到Memory
   * - memory.slaveNode是接收端(sink)，masterNodes是发送端(source)
   * - 数据流: iopmp.masterNodes(0) → memory.slaveNode
   * - Memory接收请求并执行实际的读写操作
   *******************************************/
  memory.slaveNode := iopmp.masterNodes(0)

  /*****************************************************************************
   * lazy val module - 硬件实现
   *****************************************************************************/
  lazy val module = new Imp

  class Imp extends LazyModuleImp(this) {

    //========================================================================
    //                    IOPMP配置接口 (APB总线)
    //========================================================================
    /*****************************************************************************
     * APB配置接口
     *
     * 【APB协议说明】
     * - APB(Advanced Peripheral Bus)是简单的配置总线
     * - 用于访问IOPMP的配置寄存器
     * - 通过APB设置访问规则、使能IOPMP等
     *
     * 【信号说明】
     * - paddr: 地址
     * - psel: 选择信号
     * - penable: 使能信号
     * - pwrite: 读写方向
     * - pwdata: 写数据
     * - pready: 准备就绪
     * - prdata: 读数据
     *****************************************************************************/
    val apb_s = IO(new APBSlaveBundle(IopmpParams.regcfg_addrBits, IopmpParams.regcfg_dataBits))

    /*******************************************
     * 【<> 批量连接操作符】
     * - 连接两个接口的所有信号
     * - 自动匹配Input/Output方向
     *******************************************/
    iopmp.module.apb_s <> apb_s

    //========================================================================
    //                    IOPMP中断输出
    //========================================================================
    /*****************************************************************************
     * 中断信号
     *
     * 【中断触发条件】
     * - 当IOPMP检测到非法访问时
     * - 且中断使能被设置时
     * - int信号会被拉高
     *
     * 【中断处理】
     * - CPU收到中断后读取IOPMP的错误寄存器
     * - 获取错误地址、错误类型等信息
     * - 清除中断标志
     *****************************************************************************/
    val int = IO(Output(Bool()))
    int := iopmp.module.int

    //========================================================================
    //                    Dcache控制接口
    //========================================================================
    /*****************************************************************************
     * Dcache控制接口
     *
     * 【接口功能】
     * - 外部通过此接口控制Dcache发起读写请求
     * - 可以设置请求地址、大小、方向等
     * - 可以提供写数据和接收读数据
     *
     * 【信号分组】
     * - 请求控制: req_valid, req_ready, req_addr, req_write, req_size, req_len
     * - 写数据: wdata, wdata_valid, wdata_ready, wdata_last
     * - 读数据: rdata, rdata_valid, rdata_ready, rdata_last, rdata_resp
     * - 写响应: b_valid, b_ready, b_resp, b_id
     * - 状态: busy
     *****************************************************************************/
    val dcache_io = IO(new Bundle {
      // 请求控制
      val req_valid = Input(Bool())
      val req_ready = Output(Bool())
      val req_addr = Input(UInt(IopmpParams.axi_addrBits.W))
      val req_write = Input(Bool())
      val req_size = Input(UInt(3.W))
      val req_len = Input(UInt(8.W))

      // 写数据
      val wdata = Input(UInt(IopmpParams.axi_dataBits.W))
      val wdata_valid = Input(Bool())
      val wdata_ready = Output(Bool())
      val wdata_last = Input(Bool())

      // 读数据
      val rdata = Output(UInt(IopmpParams.axi_dataBits.W))
      val rdata_valid = Output(Bool())
      val rdata_ready = Input(Bool())
      val rdata_last = Output(Bool())
      val rdata_resp = Output(UInt(2.W))

      // 响应
      val b_valid = Output(Bool())
      val b_ready = Input(Bool())
      val b_resp = Output(UInt(2.W))
      val b_id = Output(UInt(IopmpParams.axi_idBits.W))

      // 状态
      val busy = Output(Bool())
    })

    /*****************************************************************************
     * 连接Dcache控制信号
     *
     * 【信号连接说明】
     * - 每个信号单独连接，方向明确
     * - dcache.module.io 表示Dcache模块的控制接口
     * - Input信号从外部接收，Output信号向外部输出
     *****************************************************************************/
    // 请求控制信号连接
    dcache.module.io.req_valid := dcache_io.req_valid
    dcache_io.req_ready := dcache.module.io.req_ready
    dcache.module.io.req_addr := dcache_io.req_addr
    dcache.module.io.req_write := dcache_io.req_write
    dcache.module.io.req_size := dcache_io.req_size
    dcache.module.io.req_len := dcache_io.req_len

    // 写数据信号连接
    dcache.module.io.wdata := dcache_io.wdata
    dcache.module.io.wdata_valid := dcache_io.wdata_valid
    dcache_io.wdata_ready := dcache.module.io.wdata_ready
    dcache.module.io.wdata_last := dcache_io.wdata_last

    // 读数据信号连接
    dcache_io.rdata := dcache.module.io.rdata
    dcache_io.rdata_valid := dcache.module.io.rdata_valid
    dcache.module.io.rdata_ready := dcache_io.rdata_ready
    dcache_io.rdata_last := dcache.module.io.rdata_last
    dcache_io.rdata_resp := dcache.module.io.rdata_resp

    // 写响应信号连接
    dcache_io.b_valid := dcache.module.io.b_valid
    dcache.module.io.b_ready := dcache_io.b_ready
    dcache_io.b_resp := dcache.module.io.b_resp
    dcache_io.b_id := dcache.module.io.b_id

    // 状态信号连接
    dcache_io.busy := dcache.module.io.busy

    //========================================================================
    //                    Memory调试接口
    //========================================================================
    /*****************************************************************************
     * Memory调试接口
     *
     * 【功能】
     * - 直接访问Memory内部存储
     * - 不通过AXI4接口，用于验证和调试
     * - 可以读取任意地址的数据
     *****************************************************************************/
    val mem_debug = IO(new Bundle {
      val mem_addr = Input(UInt(log2Ceil(memDepth).W))
      val mem_rdata = Output(UInt(IopmpParams.axi_dataBits.W))
    })

    mem_debug <> memory.module.debug

    //========================================================================
    //                    系统状态输出
    //========================================================================
    /*****************************************************************************
     * 系统状态信号
     *
     * 【状态信号说明】
     * - dcache_busy: Dcache正在处理请求
     * - iopmp_int: IOPMP检测到非法访问并触发中断
     *
     * 【用途】
     * - 顶层监控模块状态
     * - 用于测试验证
     * - 用于调试
     *****************************************************************************/
    val status = IO(new Bundle {
      val dcache_busy = Output(Bool())
      val iopmp_int = Output(Bool())
    })

    status.dcache_busy := dcache.module.io.busy
    status.iopmp_int := iopmp.module.int
  }
}

/***************************************************************************************
 * IopmpSystemWrapper - 系统包装器
 *
 * 【设计目的】
 * ============================================================================
 * 1. 提供简洁的顶层接口
 * 2. 隐藏LazyModule的复杂性
 * 3. 便于集成到更大的系统中
 *
 * 【Wrapper模式的优点】
 * - 外部使用者不需要了解Diplomacy细节
 * - 可以在不改变外部接口的情况下修改内部实现
 * - 便于测试和验证
 ***************************************************************************************/
class IopmpSystemWrapper(
  numBridge: Int = 1,
  memDepth: Int = 1024
)(implicit p: Parameters) extends LazyModule {

  // 创建核心系统实例
  val system = LazyModule(new IopmpSystemLazy(numBridge, memDepth))

  lazy val module = new LazyModuleImp(this) {

    //========================================================================
    //                    顶层接口定义
    //========================================================================

    /*****************************************************************************
     * APB配置接口
     *
     * 【说明】
     * - 直接透传到IOPMP的APB接口
     * - 用于配置IOPMP的访问规则
     *****************************************************************************/
    val apb_s = IO(new APBSlaveBundle(IopmpParams.regcfg_addrBits, IopmpParams.regcfg_dataBits))
    apb_s <> system.module.apb_s

    /*****************************************************************************
     * 中断输出
     *****************************************************************************/
    val int = IO(Output(Bool()))
    int := system.module.int

    /*****************************************************************************
     * Dcache控制接口
     *
     * 【说明】
     * - 与DcacheLazy的io接口相同
     * - 直接透传到Dcache的控制接口
     *****************************************************************************/
    val dcache_ctrl = IO(new Bundle {
      // 请求控制
      val req_valid = Input(Bool())
      val req_ready = Output(Bool())
      val req_addr = Input(UInt(IopmpParams.axi_addrBits.W))
      val req_write = Input(Bool())
      val req_size = Input(UInt(3.W))
      val req_len = Input(UInt(8.W))

      // 写数据
      val wdata = Input(UInt(IopmpParams.axi_dataBits.W))
      val wdata_valid = Input(Bool())
      val wdata_ready = Output(Bool())
      val wdata_last = Input(Bool())

      // 读数据
      val rdata = Output(UInt(IopmpParams.axi_dataBits.W))
      val rdata_valid = Output(Bool())
      val rdata_ready = Input(Bool())
      val rdata_last = Output(Bool())
      val rdata_resp = Output(UInt(2.W))

      // 响应
      val b_valid = Output(Bool())
      val b_ready = Input(Bool())
      val b_resp = Output(UInt(2.W))
      val b_id = Output(UInt(IopmpParams.axi_idBits.W))

      // 状态
      val busy = Output(Bool())
    })

    /*******************************************
     * 【<>批量连接】
     * - 接口结构完全匹配时可以使用<>
     * - 自动连接所有同名字段
     *******************************************/
    dcache_ctrl <> system.module.dcache_io

    /*****************************************************************************
     * Memory调试接口
     *****************************************************************************/
    val mem_debug = IO(new Bundle {
      val mem_addr = Input(UInt(log2Ceil(memDepth).W))
      val mem_rdata = Output(UInt(IopmpParams.axi_dataBits.W))
    })
    mem_debug <> system.module.mem_debug

    /*****************************************************************************
     * 系统状态
     *****************************************************************************/
    val status = IO(new Bundle {
      val dcache_busy = Output(Bool())
      val iopmp_int = Output(Bool())
    })
    status <> system.module.status
  }
}

/***************************************************************************************
 * IopmpSystem - Verilog生成入口
 *
 * 【ChiselStage说明】
 * ============================================================================
 * - ChiselStage是Chisel3提供的编译框架
 * - emitSystemVerilog: 将Chisel代码转换为SystemVerilog
 * - 支持多种firtool优化选项
 *
 * 【生成流程】
 * ============================================================================
 * 1. 创建LazyModule实例
 * 2. 访问.module触发硬件生成
 * 3. 调用firtool编译生成Verilog
 * 4. 输出到指定目录
 *
 * 【firtool选项说明】
 * ============================================================================
 * -disable-all-randomization: 禁用随机化(用于形式验证)
 * -strip-debug-info: 移除调试信息
 * --disable-annotation-unknown: 忽略未知注解
 * --lowering-options: 低级转换选项
 *   - explicitBitcast: 显式位转换
 *   - disallowLocalVariables: 不允许局部变量
 *   - disallowPortDeclSharing: 不允许端口声明共享
 *   - locationInfoStyle=none: 不生成位置信息
 * --split-verilog: 分割Verilog文件
 * -o: 输出目录
 ***************************************************************************************/
object IopmpSystem extends App {

  /*******************************************
   * 【隐式参数】
   * - Parameters.empty: 空配置参数
   * - implicit关键字使参数自动传递
   *******************************************/
  implicit val p: Parameters = Parameters.empty

  println("=" * 60)
  println("IOPMP System - Dcache -> IOPMP -> Memory")
  println("=" * 60)

  /*******************************************
   * 【LazyModule实例化】
   * - 创建顶层模块，此时不生成硬件
   * - 只是构建模块树和节点连接
   *******************************************/
  val top = LazyModule(new IopmpSystemWrapper(numBridge = 1, memDepth = 1024))

  /*******************************************
   * 【生成Verilog】
   * - top.module 触发硬件生成
   * - ChiselStage.emitSystemVerilog 转换为Verilog
   *******************************************/
  ChiselStage.emitSystemVerilog(
    top.module,
    args = Array("--dump-fir"),  // 保存FIRRTL中间表示
    firtoolOpts = Array(
      "-disable-all-randomization",
      "-strip-debug-info",
      "--disable-annotation-unknown",
      "--lowering-options=explicitBitcast,disallowLocalVariables,disallowPortDeclSharing,locationInfoStyle=none",
      "--split-verilog",       // 分割成多个Verilog文件
      "-o=./build/iopmp_system"  // 输出目录
    )
  )

  println()
  println("=" * 60)
  println("Verilog生成完成!")
  println("输出目录: ./build/iopmp_system/")
  println("=" * 60)
}

/***************************************************************************************
 * 【附录：Chisel语法速查】
 * ============================================================================
 *
 * 1. 类型系统
 * ----------------------------------------------------------------------------
 * - UInt(n.W): n位无符号整数
 * - SInt(n.W): n位有符号整数
 * - Bool(): 布尔类型
 * - Bits(n.W): n位原始比特
 *
 * 2. 寄存器
 * ----------------------------------------------------------------------------
 * - Reg(t): 类型为t的寄存器
 * - RegInit(value): 带复位值的寄存器
 * - RegNext(value): 延迟一拍的寄存器
 * - RegEnable(value, enable): 带使能的寄存器
 *
 * 3. 赋值
 * ----------------------------------------------------------------------------
 * - := 单次赋值
 * - <> 批量连接(用于接口)
 *
 * 4. 条件语句
 * ----------------------------------------------------------------------------
 * - when(cond) { ... }
 * - when(cond) { ... }.elsewhen(cond2) { ... }
 * - when(cond) { ... }.otherwise { ... }
 *
 * 5. 状态机
 * ----------------------------------------------------------------------------
 * - object State extends ChiselEnum { val sA, sB = Value }
 * - val state = RegInit(State.sA)
 * - switch(state) { is(State.sA) { ... } }
 *
 * 6. 内存
 * ----------------------------------------------------------------------------
 * - Mem(depth, dataType): 创建内存
 * - mem.read(addr): 读操作
 * - mem.write(addr, data): 写操作
 *
 * 7. 接口
 * ----------------------------------------------------------------------------
 * - IO(new Bundle { ... }): 创建IO接口
 * - Input/Output: 指定方向
 * - Flipped: 翻转方向
 *
 * 8. Diplomacy
 * ----------------------------------------------------------------------------
 * - LazyModule: 延迟实例化模块
 * - AXI4MasterNode: AXI4主设备节点
 * - AXI4SlaveNode: AXI4从设备节点
 * - := : 连接操作符
 *
 ***************************************************************************************/
