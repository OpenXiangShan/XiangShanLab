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
 * Dcache模块 - 数据缓存模拟器
 *
 * 【设计原理】
 * ============================================================================
 * 1. 为什么需要Dcache模块？
 *    - IOPMP需要接收来自主设备的读写请求进行检查
 *    - Dcache模拟一个AXI4主设备，用于生成测试请求
 *    - 在真实系统中，这里会是CPU的数据缓存或DMA控制器
 *
 * 2. AXI4协议基础知识：
 *    - AXI4有5个独立通道：AW(写地址)、W(写数据)、B(写响应)、AR(读地址)、R(读数据)
 *    - 读写操作是分离的，各自有独立的地址和数据通道
 *    - 支持突发传输(burst)，一次地址传输可以对应多拍数据
 *
 * 3. AXI4读写时序：
 *    - 读操作：AR(发地址) → R(收数据)
 *    - 写操作：AW(发地址) → W(发数据) → B(收响应)
 *
 * 4. Diplomacy框架的作用：
 *    - Diplomacy是RocketChip提供的模块间自动连接框架
 *    - LazyModule允许在硬件生成前进行参数协商
 *    - Node定义了模块间的接口类型和连接关系
 *
 * 【语法要点】
 * ============================================================================
 * - LazyModule: 延迟实例化模块，用于Diplomacy连接
 * - lazy val: 延迟计算，只在首次访问时求值
 * - implicit p: Parameters: 隐式参数传递，用于配置系统参数
 * - ChiselEnum: Chisel3枚举类型，用于状态机定义
 * - RegInit: 带复位值的寄存器
 * - switch/is: Chisel状态机语法
 * - fire: Decoupled接口的握手信号，等于 valid && ready
 * - <>: Chisel批量连接操作符
 ***************************************************************************************/

package iopmp

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.util._

/***************************************************************************************
 * DcacheLazy - Dcache的核心LazyModule
 *
 * 【设计说明】
 * - 继承自LazyModule，是Diplomacy框架的基础构建块
 * - 包含一个AXI4MasterNode，用于向外发起AXI4请求
 * - 内部实现AXI4协议的状态机控制
 *
 * 【参数说明】
 * - address: 地址空间范围，默认覆盖整个地址空间
 * - beatBytes: 每拍传输的字节数，对应AXI4的数据位宽/8
 * - implicit p: Parameters: 隐式配置参数，用于传递系统级配置
 ***************************************************************************************/
class DcacheLazy(
  address: AddressSet = AddressSet(0x0, (1L << IopmpParams.axi_addrBits) - 1),
  beatBytes: Int = IopmpParams.axi_beatByte
)(implicit p: Parameters) extends LazyModule {

  /***********************************************************************************
   * AXI4MasterNode - AXI4主设备节点
   *
   * 【Diplomacy核心概念】
   * - Node是Diplomacy中模块间连接的端点
   * - AXI4MasterNode表示这是一个AXI4主设备(发起请求的一方)
   * - AXI4SlaveNode表示从设备(响应请求的一方)
   *
   * 【参数说明】
   * - AXI4MasterPortParameters: 主设备端口参数，包含所有主设备的配置
   * - AXI4MasterParameters: 单个主设备的参数
   *   - name: 主设备名称，用于调试和文档
   *   - id: 事务ID范围，AXI4用于支持乱序响应
   *
   * 【IdRange说明】
   * - AXI4协议使用ID来匹配请求和响应
   * - IdRange(0, N)表示可以使用0到N-1的ID
   * - 不同的ID允许从设备乱序返回响应
   ***********************************************************************************/
  val masterNode = AXI4MasterNode(Seq(AXI4MasterPortParameters(
    Seq(AXI4MasterParameters(
      name = "dcache",                                    // 主设备名称
      id = IdRange(0, IopmpParams.axi_idNum)             // ID范围: 0到axi_idNum-1
    ))
  )))

  /***********************************************************************************
   * lazy val module - 延迟实例化的硬件模块
   *
   * 【lazy关键字的必要性】
   * - LazyModule先构建连接关系，后生成硬件
   * - module必须在所有Node连接确定后才能实例化
   * - lazy val保证在首次访问时才求值，此时连接已确定
   *
   * 【LazyModuleImp vs Module】
   * - LazyModuleImp是LazyModule对应的硬件实现类
   * - 普通Module在定义时立即生成硬件
   * - LazyModuleImp在所有Diplomacy连接协商完成后才生成硬件
   ***********************************************************************************/
  lazy val module = new Imp

  /***********************************************************************************
   * class Imp extends LazyModuleImp - 硬件实现内部类
   *
   * 【设计模式】
   * - 使用内部类模式，Imp可以访问外部DcacheLazy的所有成员
   * - 包括masterNode等Diplomacy节点
   ***********************************************************************************/
  class Imp extends LazyModuleImp(this) {

    /*****************************************************************************
     * 获取AXI4 Bundle和Edge
     *
     * 【语法解释】
     * - masterNode.out: 返回Seq[(Bundle, Edge)]，所有输出连接
     * - .head: 取第一个连接(本模块只有一个输出)
     * - masterBundle: AXI4信号集合，包含aw/w/b/ar/r五个通道
     * - masterEdge: 边缘参数，包含协商后的参数信息
     *
     * 【Decoupled接口】
     * - AXI4的每个通道都是Decoupled接口
     * - Decoupled包含: bits(数据)、valid(有效)、ready(准备就绪)
     * - 握手协议: valid && ready 时传输发生
     *****************************************************************************/
    val (masterBundle, masterEdge) = masterNode.out.head

    /*****************************************************************************
     * 控制接口 - 用于外部控制读写请求
     *
     * 【设计原理】
     * - 这些接口允许外部(如测试平台或CPU)控制Dcache发起请求
     * - 分为请求控制、写数据、读数据、响应四组
     *
     * 【接口说明】
     * - req_*: 请求控制信号，用于发起读写操作
     * - wdata_*: 写数据通道，用于提供写操作的数据
     * - rdata_*: 读数据通道，用于接收读操作返回的数据
     * - b_*: 写响应通道，用于接收写操作的结果
     *****************************************************************************/
    val io = IO(new Bundle {
      // -------------------------------------------------------------------
      // 请求控制信号
      // -------------------------------------------------------------------
      // 【Decoupled接口模式】
      // - req_valid: 请求有效，表示外部想要发起请求
      // - req_ready: 请求准备就绪，表示Dcache可以接受新请求
      // - 当 valid && ready 同时为真时，请求被接受
      val req_valid = Input(Bool())
      val req_ready = Output(Bool())

      // 请求参数
      val req_addr = Input(UInt(IopmpParams.axi_addrBits.W))  // 目标地址
      val req_write = Input(Bool())                            // 1: 写操作, 0: 读操作
      val req_size = Input(UInt(3.W))                          // 每拍字节数的log2值，如3表示8字节
      val req_len = Input(UInt(8.W))                           // 突发长度-1，如15表示16拍

      // -------------------------------------------------------------------
      // 写数据通道
      // -------------------------------------------------------------------
      // 【写数据流控制】
      // - 外部通过wdata_valid提供数据
      // - Dcache通过wdata_ready表示可以接收数据
      // - wdata_last表示当前是最后一拍数据
      val wdata = Input(UInt(IopmpParams.axi_dataBits.W))
      val wdata_valid = Input(Bool())
      val wdata_ready = Output(Bool())
      val wdata_last = Input(Bool())

      // -------------------------------------------------------------------
      // 读数据通道
      // -------------------------------------------------------------------
      // 【读数据返回】
      // - Dcache通过rdata_valid表示读数据有效
      // - 外部通过rdata_ready表示可以接收数据
      // - rdata_resp是AXI4响应码: 0=OKAY, 2=SLVERR
      val rdata = Output(UInt(IopmpParams.axi_dataBits.W))
      val rdata_valid = Output(Bool())
      val rdata_ready = Input(Bool())
      val rdata_last = Output(Bool())
      val rdata_resp = Output(UInt(2.W))

      // -------------------------------------------------------------------
      // 写响应通道
      // -------------------------------------------------------------------
      // 【AXI4写响应】
      // - 写操作完成后，从设备返回B通道响应
      // - b_id: 事务ID，用于匹配对应的写请求
      // - b_resp: 响应状态
      val b_valid = Output(Bool())
      val b_ready = Input(Bool())
      val b_resp = Output(UInt(2.W))
      val b_id = Output(UInt(IopmpParams.axi_idBits.W))

      // -------------------------------------------------------------------
      // 状态信号
      // -------------------------------------------------------------------
      val busy = Output(Bool())  // Dcache正在处理请求
    })

    /*****************************************************************************
     * 状态机定义
     *
     * 【ChiselEnum语法】
     * - ChiselEnum是Chisel3提供的枚举类型
     * - 自动生成唯一的位向量编码
     * - 比使用常量定义状态更安全、更易读
     *
     * 【状态说明】
     * - sIdle:      空闲状态，等待新请求
     * - sReadAddr:  发送读地址(AR通道)
     * - sReadData:  接收读数据(R通道)
     * - sWriteAddr: 发送写地址(AW通道)
     * - sWriteData: 发送写数据(W通道)
     * - sWriteResp: 等待写响应(B通道)
     *
     * 【状态转换图】
     *   空闲 ──读请求──→ 发AR ──握手──→ 收R ──最后拍──→ 空闲
     *     │                                              ↑
     *     └──写请求──→ 发AW ──握手──→ 发W ──最后拍──→ 收B ──握手──┘
     *****************************************************************************/
    object State extends ChiselEnum {
      val sIdle, sReadAddr, sReadData, sWriteAddr, sWriteData, sWriteResp = Value
    }

    /*****************************************************************************
     * 【RegInit语法】
     * - RegInit(value): 创建一个带复位值的寄存器
     * - 复位时，寄存器被初始化为指定值
     * - RegInit(State.sIdle): 创建状态寄存器，复位值为sIdle
     *****************************************************************************/
    val state = RegInit(State.sIdle)

    /*****************************************************************************
     * 请求寄存器 - 保存请求参数
     *
     * 【为什么需要寄存器？】
     * - AXI4是流水线协议，请求可能在多个周期内完成
     * - 需要保存请求参数供后续状态使用
     * - 例如：发完地址后，数据阶段仍需要知道地址和长度
     *****************************************************************************/
    val req_addr_reg = RegInit(0.U(IopmpParams.axi_addrBits.W))
    val req_write_reg = RegInit(false.B)
    val req_size_reg = RegInit(0.U(3.W))
    val req_len_reg = RegInit(0.U(8.W))
    val req_id_reg = RegInit(0.U(IopmpParams.axi_idBits.W))

    /*****************************************************************************
     * ID计数器
     *
     * 【AXI4 ID机制】
     * - AXI4使用ID来匹配请求和响应
     * - 每次新请求递增ID，确保每次事务ID唯一
     * - 从设备必须按相同ID返回响应
     *****************************************************************************/
    val id_counter = RegInit(0.U(IopmpParams.axi_idBits.W))

    /*****************************************************************************
     * 读数据计数器和寄存器
     *****************************************************************************/
    val read_count = RegInit(0.U(8.W))                            // 已接收的读数据拍数
    val read_data_reg = RegInit(0.U(IopmpParams.axi_dataBits.W))  // 缓存读数据
    val read_last_reg = RegInit(false.B)                          // 缓存last信号
    val read_resp_reg = RegInit(0.U(2.W))                         // 缓存响应码

    /*****************************************************************************
     * 写数据计数器
     *****************************************************************************/
    val write_count = RegInit(0.U(8.W))  // 已发送的写数据拍数

    /*****************************************************************************
     * 状态机实现
     *
     * 【switch/is语法】
     * - switch(state): 根据state的值进行分支选择
     * - is(State.XXX): 当state等于XXX时执行对应代码块
     * - 等价于Verilog的case语句
     *
     * 【Chisel状态机特点】
     * - 所有分支并行存在，生成组合逻辑
     * - 状态转换使用 := 赋值，生成时序逻辑
     * - when/elsewhen/otherwise 用于内部条件判断
     *****************************************************************************/
    switch(state) {
      /***********************************************
       * sIdle状态 - 空闲，等待新请求
       ***********************************************/
      is(State.sIdle) {
        // 【握手检测】io.req_valid为真表示有新请求
        when(io.req_valid) {
          // 保存请求参数到寄存器
          req_addr_reg := io.req_addr
          req_write_reg := io.req_write
          req_size_reg := io.req_size
          req_len_reg := io.req_len
          req_id_reg := id_counter

          // 根据读写类型进入不同状态
          when(io.req_write) {
            state := State.sWriteAddr   // 写操作: 先发写地址
          }.otherwise {
            state := State.sReadAddr    // 读操作: 先发读地址
          }

          // 递增事务ID
          id_counter := id_counter + 1.U
        }
      }

      /***********************************************
       * sReadAddr状态 - 发送读地址(AR通道)
       ***********************************************/
      is(State.sReadAddr) {
        /*******************************************
         * 【fire信号】
         * - fire = valid && ready
         * - 表示一次成功的握手传输
         * - 当AR通道握手成功时，读地址已发送
         *******************************************/
        when(masterBundle.ar.fire) {
          read_count := 0.U           // 重置读计数器
          state := State.sReadData    // 进入接收数据状态
        }
      }

      /***********************************************
       * sReadData状态 - 接收读数据(R通道)
       ***********************************************/
      is(State.sReadData) {
        when(masterBundle.r.fire) {
          // 缓存接收到的数据
          read_data_reg := masterBundle.r.bits.data
          read_last_reg := masterBundle.r.bits.last
          read_resp_reg := masterBundle.r.bits.resp
          read_count := read_count + 1.U

          // 【last信号】AXI4突发传输的最后一拍
          // 当last为真时，表示这是最后一次数据传输
          when(masterBundle.r.bits.last) {
            state := State.sIdle  // 突发传输完成，回到空闲
          }
        }
      }

      /***********************************************
       * sWriteAddr状态 - 发送写地址(AW通道)
       ***********************************************/
      is(State.sWriteAddr) {
        when(masterBundle.aw.fire) {
          write_count := 0.U           // 重置写计数器
          state := State.sWriteData    // 进入发送数据状态
        }
      }

      /***********************************************
       * sWriteData状态 - 发送写数据(W通道)
       ***********************************************/
      is(State.sWriteData) {
        when(masterBundle.w.fire) {
          write_count := write_count + 1.U
          // 检测是否是最后一拍数据
          when(masterBundle.w.bits.last) {
            state := State.sWriteResp  // 数据发送完成，等待响应
          }
        }
      }

      /***********************************************
       * sWriteResp状态 - 等待写响应(B通道)
       ***********************************************/
      is(State.sWriteResp) {
        when(masterBundle.b.fire) {
          state := State.sIdle  // 收到响应，回到空闲
        }
      }
    }

    /*****************************************************************************
     * AXI4 AR通道 (读地址通道) 信号赋值
     *
     * 【AXI4 AR通道信号】
     * - valid: 地址有效信号
     * - addr: 读地址
     * - id: 事务ID
     * - size: 每拍字节数的log2值
     * - len: 突发长度-1
     * - burst: 突发类型 (0=FIXED, 1=INCR, 2=WRAP)
     * - lock, cache, prot, qos: 其他AXI4属性
     *****************************************************************************/
    // 只有在sReadAddr状态才驱动AR通道
    masterBundle.ar.valid := state === State.sReadAddr
    masterBundle.ar.bits.addr := req_addr_reg
    masterBundle.ar.bits.id := req_id_reg
    masterBundle.ar.bits.size := req_size_reg
    masterBundle.ar.bits.len := req_len_reg
    masterBundle.ar.bits.burst := 1.U   // INCR burst - 地址递增
    masterBundle.ar.bits.lock := 0.U    // 正常访问
    masterBundle.ar.bits.cache := 0.U   // 非缓存
    masterBundle.ar.bits.prot := 0.U    // 非特权访问
    masterBundle.ar.bits.qos := 0.U     // 无QoS

    /*****************************************************************************
     * AXI4 R通道 (读数据通道) 信号赋值
     *
     * 【Slave to Master方向】
     * - R通道数据由Slave发送，Master接收
     * - ready由Master驱动，表示可以接收数据
     * - 这里ready需要外部rdata_ready配合
     *****************************************************************************/
    // ready信号：在接收数据状态且外部可以接收数据时为真
    masterBundle.r.ready := state === State.sReadData && io.rdata_ready

    // 向外部输出读数据
    io.rdata := read_data_reg
    io.rdata_valid := masterBundle.r.valid && state === State.sReadData
    io.rdata_last := read_last_reg
    io.rdata_resp := read_resp_reg

    /*****************************************************************************
     * AXI4 AW通道 (写地址通道) 信号赋值
     *
     * 【与AR通道类似】
     * - AW用于写操作的地址传输
     * - 参数含义与AR相同
     *****************************************************************************/
    masterBundle.aw.valid := state === State.sWriteAddr
    masterBundle.aw.bits.addr := req_addr_reg
    masterBundle.aw.bits.id := req_id_reg
    masterBundle.aw.bits.size := req_size_reg
    masterBundle.aw.bits.len := req_len_reg
    masterBundle.aw.bits.burst := 1.U   // INCR burst
    masterBundle.aw.bits.lock := 0.U
    masterBundle.aw.bits.cache := 0.U
    masterBundle.aw.bits.prot := 0.U
    masterBundle.aw.bits.qos := 0.U

    /*****************************************************************************
     * AXI4 W通道 (写数据通道) 信号赋值
     *
     * 【W通道信号】
     * - data: 写数据
     * - strb: 写选通，每位对应一个字节是否有效
     * - last: 最后一拍数据标志
     *****************************************************************************/
    // valid: 在发送数据状态且外部有有效数据
    masterBundle.w.valid := state === State.sWriteData && io.wdata_valid
    masterBundle.w.bits.data := io.wdata

    /*******************************************
     * 【Fill函数】
     * - Fill(n, value): 将value复制n次
     * - Fill(beatBytes, 1.U)生成全1的写选通
     * - 表示所有字节都有效(写全部字节)
     *******************************************/
    masterBundle.w.bits.strb := Fill(IopmpParams.axi_beatByte, 1.U)

    // last信号：外部指定或达到最后一拍
    masterBundle.w.bits.last := io.wdata_last || (write_count === req_len_reg)

    // 向外部输出写数据准备信号
    io.wdata_ready := masterBundle.w.ready && state === State.sWriteData

    /*****************************************************************************
     * AXI4 B通道 (写响应通道) 信号赋值
     *
     * 【写响应机制】
     * - 写操作完成后，Slave通过B通道返回响应
     * - Master通过ready表示可以接收响应
     * - resp: 0=OKAY, 1=EXOKAY, 2=SLVERR, 3=DECERR
     *****************************************************************************/
    masterBundle.b.ready := state === State.sWriteResp

    io.b_valid := masterBundle.b.valid && state === State.sWriteResp
    io.b_resp := masterBundle.b.bits.resp
    io.b_id := masterBundle.b.bits.id

    /*****************************************************************************
     * 控制信号
     *****************************************************************************/
    // req_ready: 只在空闲状态可以接受新请求
    io.req_ready := state === State.sIdle

    // busy: 不在空闲状态即为忙
    // 【=/=操作符】不等于，等价于 !=
    io.busy := state =/= State.sIdle
  }
}

/***************************************************************************************
 * DcacheLazyWrapper - Dcache模块的包装器
 *
 * 【设计目的】
 * - 提供AXI4接口暴露，便于在顶层连接
 * - 隐藏Diplomacy连接细节
 * - 提供统一的控制接口
 *
 * 【Wrapper模式】
 * - 外层包装器处理复杂的连接逻辑
 * - 内层模块专注于核心功能
 * - 提高模块的可重用性
 ***************************************************************************************/
class DcacheLazyWrapper(
  beatBytes: Int = IopmpParams.axi_beatByte
)(implicit p: Parameters) extends LazyModule {

  // 创建Dcache实例
  val dcache = LazyModule(new DcacheLazy(beatBytes = beatBytes))

  /*****************************************************************************
   * AXI4SlaveNode - 用于暴露AXI4接口
   *
   * 【为什么Dcache需要SlaveNode？】
   * - Dcache内部是MasterNode(发起请求)
   * - 但在Wrapper层，我们需要把AXI4接口暴露出去
   * - SlaveNode创建一个"被动"接口，让外部可以连接
   *
   * 【连接方向】
   * - 内部: dcache.masterNode (Master，输出请求)
   * - 外部: slaveNode (Sink，接收请求)
   * - 连接: slaveNode := dcache.masterNode
   *****************************************************************************/
  val slaveNode = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    Seq(AXI4SlaveParameters(
      address = Seq(AddressSet(0x0, (1L << IopmpParams.axi_addrBits) - 1)),
      regionType = RegionType.UNCACHED,    // 非缓存区域
      executable = false,                   // 不可执行
      supportsRead = TransferSizes(1, beatBytes),
      supportsWrite = TransferSizes(1, beatBytes),
      interleavedId = Some(0)               // 不支持交错ID
    )),
    beatBytes = beatBytes
  )))

  /*****************************************************************************
   * Diplomacy连接语法
   *
   * 【:=操作符】
   * - sink := source (左边接收，右边发送)
   * - 数据从右边(source)流向左边(sink)
   * - 这里: slaveNode(宿/接收) := dcache.masterNode(源/发送)
   *****************************************************************************/
  slaveNode := dcache.masterNode

  /*****************************************************************************
   * InModuleBody - 在模块体内创建IO
   *
   * 【作用】
   * - makeIOs(): 创建AXI4接口的硬件端口
   * - ValName("axi4_m"): 设置接口名称
   * - InModuleBody确保IO在正确的模块范围内创建
   *****************************************************************************/
  val axi4_m = InModuleBody(slaveNode.makeIOs()(ValName("axi4_m")))

  lazy val module = new LazyModuleImp(this) {
    // 控制接口 - 与DcacheLazy的io接口相同
    val io = IO(new Bundle {
      val req_valid = Input(Bool())
      val req_ready = Output(Bool())
      val req_addr = Input(UInt(IopmpParams.axi_addrBits.W))
      val req_write = Input(Bool())
      val req_size = Input(UInt(3.W))
      val req_len = Input(UInt(8.W))

      val wdata = Input(UInt(IopmpParams.axi_dataBits.W))
      val wdata_valid = Input(Bool())
      val wdata_ready = Output(Bool())
      val wdata_last = Input(Bool())

      val rdata = Output(UInt(IopmpParams.axi_dataBits.W))
      val rdata_valid = Output(Bool())
      val rdata_ready = Input(Bool())
      val rdata_last = Output(Bool())
      val rdata_resp = Output(UInt(2.W))

      val b_valid = Output(Bool())
      val b_ready = Input(Bool())
      val b_resp = Output(UInt(2.W))
      val b_id = Output(UInt(IopmpParams.axi_idBits.W))

      val busy = Output(Bool())
    })

    /*****************************************************************************
     * 信号连接 - 使用 <> 批量连接
     *
     * 【<>操作符】
     * - 批量连接两个接口的对应信号
     * - 自动处理Input/Output方向
     * - 比单独连接每个信号更简洁
     *
     * 【这里使用单独连接的原因】
     * - 接口名称不完全匹配
     * - 需要明确展示每个信号的连接
     *****************************************************************************/
    dcache.module.io.req_valid := io.req_valid
    io.req_ready := dcache.module.io.req_ready
    dcache.module.io.req_addr := io.req_addr
    dcache.module.io.req_write := io.req_write
    dcache.module.io.req_size := io.req_size
    dcache.module.io.req_len := io.req_len

    dcache.module.io.wdata := io.wdata
    dcache.module.io.wdata_valid := io.wdata_valid
    io.wdata_ready := dcache.module.io.wdata_ready
    dcache.module.io.wdata_last := io.wdata_last

    io.rdata := dcache.module.io.rdata
    io.rdata_valid := dcache.module.io.rdata_valid
    dcache.module.io.rdata_ready := io.rdata_ready
    io.rdata_last := dcache.module.io.rdata_last
    io.rdata_resp := dcache.module.io.rdata_resp

    io.b_valid := dcache.module.io.b_valid
    dcache.module.io.b_ready := io.b_ready
    io.b_resp := dcache.module.io.b_resp
    io.b_id := dcache.module.io.b_id

    io.busy := dcache.module.io.busy
  }
}
