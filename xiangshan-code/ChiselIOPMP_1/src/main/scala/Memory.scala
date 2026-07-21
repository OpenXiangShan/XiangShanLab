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
 * Memory模块 - 存储器模拟器
 *
 * 【设计原理】
 * ============================================================================
 * 1. 为什么需要Memory模块？
 *    - IOPMP检查通过的请求需要发送到目标设备
 *    - Memory模拟一个AXI4从设备，响应读写请求
 *    - 在真实系统中，这里会是DDR控制器或外设寄存器
 *
 * 2. AXI4从设备工作流程：
 *    - 读操作：接收AR地址 → 从存储读取数据 → 通过R通道返回
 *    - 写操作：接收AW地址 → 通过W通道接收数据 → 写入存储 → 返回B响应
 *
 * 3. 突发传输处理：
 *    - AXI4支持突发传输，一次地址传输对应多拍数据
 *    - len字段表示拍数-1，如len=15表示16拍
 *    - burst=1(INCR)表示地址递增模式
 *
 * 4. 读/写通道独立性：
 *    - 读通道和写通道使用独立的状态机
 *    - 允许同时处理读写操作(全双工)
 *    - 每个通道有独立的寄存器和计数器
 *
 * 【语法要点】
 * ============================================================================
 * - Mem: Chisel内存原语，生成SRAM或寄存器阵列
 * - Mem.read/write: 内存读写操作
 * - 独立状态机: 读通道和写通道使用不同的状态机对象
 * - 位运算: >>, << 用于地址计算
 ***************************************************************************************/

package iopmp

import chisel3._
import chisel3.util._
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.util._

/***************************************************************************************
 * MemoryLazy - Memory的核心LazyModule
 *
 * 【设计说明】
 * - 继承自LazyModule，是Diplomacy框架的基础构建块
 * - 包含一个AXI4SlaveNode，用于接收AXI4请求
 * - 内部使用Mem实现数据存储
 *
 * 【参数说明】
 * - address: 地址空间范围
 * - beatBytes: 每拍传输的字节数
 * - depth: 存储深度(可存储多少拍数据)
 ***************************************************************************************/
class MemoryLazy(
  address: AddressSet = AddressSet(0x0, (1L << IopmpParams.axi_addrBits) - 1),
  beatBytes: Int = IopmpParams.axi_beatByte,
  depth: Int = 1024  // 存储深度，单位为"拍"
)(implicit p: Parameters) extends LazyModule {

  /***********************************************************************************
   * AXI4SlaveNode - AXI4从设备节点
   *
   * 【从设备参数说明】
   * - AXI4SlavePortParameters: 从设备端口参数集合
   * - AXI4SlaveParameters: 单个从设备的参数
   *   - address: 从设备响应的地址范围
   *   - regionType: 区域类型(UNCACHED表示不缓存)
   *   - executable: 是否支持取指
   *   - supportsRead/Write: 支持的传输大小范围
   *   - interleavedId: 交错ID配置(用于优化性能)
   *
   * 【TransferSizes说明】
   * - TransferSizes(min, max): 支持的最小和最大传输大小
   * - 这里支持1字节到beatBytes的传输
   ***********************************************************************************/
  val slaveNode = AXI4SlaveNode(Seq(AXI4SlavePortParameters(
    Seq(AXI4SlaveParameters(
      address = Seq(address),                        // 响应的地址范围
      regionType = RegionType.UNCACHED,              // 非缓存区域
      executable = false,                            // 不支持取指
      supportsRead = TransferSizes(1, beatBytes),    // 支持的读大小
      supportsWrite = TransferSizes(1, beatBytes),   // 支持的写大小
      interleavedId = Some(0)                        // 交错ID: 不支持交错
    )),
    beatBytes = beatBytes  // 数据位宽(字节)
  )))

  lazy val module = new Imp

  class Imp extends LazyModuleImp(this) {

    /*****************************************************************************
     * 获取AXI4 Bundle
     *
     * 【slaveNode.in说明】
     * - 对于SlaveNode，in表示接收来自Master的连接
     * - 返回Seq[(Bundle, Edge)]
     * - slaveBundle包含AXI4的所有通道(方向相对于Slave)
     *****************************************************************************/
    val (slaveBundle, slaveEdge) = slaveNode.in.head

    /*****************************************************************************
     * 内部存储 - 使用Chisel Mem
     *
     * 【Mem语法详解】
     * - Mem(depth, dataType): 创建同步读写的内存
     * - depth: 内存深度(有多少个元素)
     * - dataType: 每个元素的数据类型
     *
     * 【Mem vs RegFile】
     * - Mem: 综合为SRAM或Block RAM，面积效率高
     * - RegFile: 用寄存器实现，速度快但面积大
     * - 深度较大时推荐使用Mem
     *
     * 【读写接口】
     * - read(addr): 异步或同步读，返回数据
     * - write(addr, data): 同步写
     *****************************************************************************/
    // 存储地址位宽 (以beatBytes为单位)
    val memAddrWidth = log2Ceil(depth)

    /*******************************************
     * 【创建内存】
     * - 深度: depth (可存储depth拍数据)
     * - 位宽: axi_dataBits (256位)
     * - 每拍存储一个完整的数据位宽
     *******************************************/
    val mem = Mem(depth, UInt(IopmpParams.axi_dataBits.W))

    //========================================================================
    //                        读通道状态机
    //========================================================================
    /*****************************************************************************
     * 读状态机定义
     *
     * 【状态说明】
     * - sIdle: 空闲，等待AR地址到达
     * - sRead: 正在读取数据并通过R通道返回
     *
     * 【为什么读状态机只需2个状态？】
     * - 读操作较简单：收到地址后直接读内存返回数据
     * - 不需要等待外部数据(写操作需要等W通道数据)
     *****************************************************************************/
    object ReadState extends ChiselEnum {
      val sIdle, sRead = Value
    }
    val readState = RegInit(ReadState.sIdle)

    /*****************************************************************************
     * 读请求寄存器
     *
     * 【保存的信息】
     * - readAddr: 起始地址，用于计算后续地址
     * - readId: 事务ID，必须原样返回给Master
     * - readLen: 突发长度，知道何时结束
     * - readSize: 每拍字节数，用于地址递增
     * - readCount: 已发送的拍数计数
     * - readDataBuf: 数据缓冲，保存从Mem读出的数据
     * - readValid: R通道valid信号寄存器
     * - readLast: 是否是最后一拍
     *****************************************************************************/
    val readAddr = RegInit(0.U(IopmpParams.axi_addrBits.W))
    val readId = RegInit(0.U(IopmpParams.axi_idBits.W))
    val readLen = RegInit(0.U(8.W))
    val readSize = RegInit(0.U(3.W))
    val readCount = RegInit(0.U(8.W))
    val readDataBuf = RegInit(0.U(IopmpParams.axi_dataBits.W))
    val readValid = RegInit(false.B)
    val readLast = RegInit(false.B)

    /*****************************************************************************
     * 读状态机实现
     *****************************************************************************/
    switch(readState) {
      /***********************************************
       * sIdle状态 - 等待AR地址
       ***********************************************/
      is(ReadState.sIdle) {
        // 【AR通道握手】fire = valid && ready
        when(slaveBundle.ar.fire) {
          // 保存地址和参数
          readAddr := slaveBundle.ar.bits.addr
          readId := slaveBundle.ar.bits.id
          readLen := slaveBundle.ar.bits.len
          readSize := slaveBundle.ar.bits.size
          readCount := 0.U

          // 开始读数据
          readValid := true.B

          /*******************************************
           * 【地址计算】
           * - 地址右移log2(beatBytes)位，得到内存索引
           * - 例如：beatBytes=32, 地址0x40 -> 索引2
           *******************************************/
          readDataBuf := mem.read(slaveBundle.ar.bits.addr >> log2Ceil(beatBytes).U)

          // 判断是否只有一拍(len=0表示1拍)
          readLast := slaveBundle.ar.bits.len === 0.U

          readState := ReadState.sRead
        }
      }

      /***********************************************
       * sRead状态 - 通过R通道返回数据
       ***********************************************/
      is(ReadState.sRead) {
        // 【R通道握手】数据被Master接收
        when(slaveBundle.r.fire) {
          readCount := readCount + 1.U

          // 检查是否是最后一拍
          when(readCount === readLen) {
            // 突发传输完成
            readValid := false.B
            readLast := false.B
            readState := ReadState.sIdle
          }.otherwise {
            /*******************************************
             * 【计算下一拍地址】
             * - INCR模式下地址递增
             * - nextAddr = baseAddr + (count + 1) << size
             * - size=5表示32字节(2^5=32)
             *******************************************/
            val nextAddr = readAddr + (readCount + 1.U) << readSize
            readDataBuf := mem.read(nextAddr >> log2Ceil(beatBytes).U)

            // 判断是否是最后一拍
            readLast := readCount === (readLen - 1.U)
          }
        }
      }
    }

    /*****************************************************************************
     * AR通道(读地址)信号
     *
     * 【Slave视角的AR通道】
     * - ready由Slave驱动，表示可以接收地址
     * - 只有在空闲状态才能接收新地址
     *****************************************************************************/
    slaveBundle.ar.ready := readState === ReadState.sIdle

    /*****************************************************************************
     * R通道(读数据)信号
     *
     * 【Slave视角的R通道】
     * - valid由Slave驱动，表示数据有效
     * - data: 读数据
     * - id: 事务ID(必须与AR.id匹配)
     * - last: 最后一拍标志
     * - resp: 响应码(0=OKAY)
     *****************************************************************************/
    slaveBundle.r.valid := readValid
    slaveBundle.r.bits.data := readDataBuf
    slaveBundle.r.bits.id := readId
    slaveBundle.r.bits.last := readLast
    slaveBundle.r.bits.resp := 0.U  // OKAY响应

    //========================================================================
    //                        写通道状态机
    //========================================================================
    /*****************************************************************************
     * 写状态机定义
     *
     * 【状态说明】
     * - sIdle: 空闲，等待AW地址
     * - sWriteData: 接收W通道数据并写入内存
     * - sWriteResp: 等待B通道响应被接收
     *
     * 【为什么写状态机需要3个状态？】
     * - 写操作需要先接收地址，再接收数据
     * - 数据可能有多拍，需要循环接收
     * - 最后需要等待B通道响应被Master接收
     *****************************************************************************/
    object WriteState extends ChiselEnum {
      val sIdle, sWriteData, sWriteResp = Value
    }
    val writeState = RegInit(WriteState.sIdle)

    /*****************************************************************************
     * 写请求寄存器
     *****************************************************************************/
    val writeAddr = RegInit(0.U(IopmpParams.axi_addrBits.W))
    val writeId = RegInit(0.U(IopmpParams.axi_idBits.W))
    val writeLen = RegInit(0.U(8.W))
    val writeSize = RegInit(0.U(3.W))
    val writeCount = RegInit(0.U(8.W))
    val writeRespValid = RegInit(false.B)  // B通道valid
    val writeResp = RegInit(0.U(2.W))       // B通道resp

    /*****************************************************************************
     * 写状态机实现
     *****************************************************************************/
    switch(writeState) {
      /***********************************************
       * sIdle状态 - 等待AW地址
       ***********************************************/
      is(WriteState.sIdle) {
        // 【AW通道握手】接收写地址
        when(slaveBundle.aw.fire) {
          writeAddr := slaveBundle.aw.bits.addr
          writeId := slaveBundle.aw.bits.id
          writeLen := slaveBundle.aw.bits.len
          writeSize := slaveBundle.aw.bits.size
          writeCount := 0.U
          writeState := WriteState.sWriteData
        }
      }

      /***********************************************
       * sWriteData状态 - 接收W数据并写入内存
       ***********************************************/
      is(WriteState.sWriteData) {
        // 【W通道握手】接收到有效数据
        when(slaveBundle.w.fire) {
          /*******************************************
           * 【写入内存】
           * - 计算当前拍对应的内存地址
           * - writeAddrIndex = (baseAddr + count<<size) >> log2(beatBytes)
           *******************************************/
          val writeAddrIndex = (writeAddr + (writeCount << writeSize)) >> log2Ceil(beatBytes).U
          mem.write(writeAddrIndex, slaveBundle.w.bits.data)

          writeCount := writeCount + 1.U

          // 检查是否是最后一拍
          when(slaveBundle.w.bits.last || writeCount === writeLen) {
            writeState := WriteState.sWriteResp
            writeRespValid := true.B   // 准备发送B响应
            writeResp := 0.U           // OKAY
          }
        }
      }

      /***********************************************
       * sWriteResp状态 - 发送B响应
       ***********************************************/
      is(WriteState.sWriteResp) {
        // 【B通道握手】响应被Master接收
        when(slaveBundle.b.fire) {
          writeRespValid := false.B
          writeState := WriteState.sIdle
        }
      }
    }

    /*****************************************************************************
     * AW通道(写地址)信号
     *****************************************************************************/
    slaveBundle.aw.ready := writeState === WriteState.sIdle

    /*****************************************************************************
     * W通道(写数据)信号
     *****************************************************************************/
    slaveBundle.w.ready := writeState === WriteState.sWriteData

    /*****************************************************************************
     * B通道(写响应)信号
     *****************************************************************************/
    slaveBundle.b.valid := writeRespValid
    slaveBundle.b.bits.id := writeId
    slaveBundle.b.bits.resp := writeResp

    //========================================================================
    //                        调试接口
    //========================================================================
    /*****************************************************************************
     * 调试接口 - 用于外部查看内存内容
     *
     * 【设计目的】
     * - 在仿真或调试时查看内存内容
     * - 不影响正常的AXI4操作
     * - 可以用于验证写入的数据是否正确
     *****************************************************************************/
    val debug = IO(new Bundle {
      val mem_addr = Input(UInt(memAddrWidth.W))
      val mem_rdata = Output(UInt(IopmpParams.axi_dataBits.W))
    })

    // 异步读取调试数据
    debug.mem_rdata := mem.read(debug.mem_addr)
  }
}

/***************************************************************************************
 * MemoryLazyWrapper - Memory模块的包装器
 *
 * 【设计目的】
 * - 提供AXI4接口暴露
 * - 提供调试接口
 * - 隐藏内部实现细节
 ***************************************************************************************/
class MemoryLazyWrapper(
  beatBytes: Int = IopmpParams.axi_beatByte,
  depth: Int = 1024
)(implicit p: Parameters) extends LazyModule {

  // 创建Memory实例
  val memory = LazyModule(new MemoryLazy(beatBytes = beatBytes, depth = depth))

  /*****************************************************************************
   * AXI4MasterNode - 用于暴露AXI4接口
   *
   * 【为什么Memory需要MasterNode？】
   * - Memory内部是SlaveNode(接收请求)
   * - Wrapper层需要把AXI4接口暴露出去供外部连接
   * - MasterNode创建一个"主动"接口用于暴露
   *
   * 【连接关系】
   * - 外部(如IOPMP)通过这个MasterNode连接进来
   * - 内部连接: memory.slaveNode := masterNode
   *****************************************************************************/
  val masterNode = AXI4MasterNode(Seq(AXI4MasterPortParameters(
    Seq(AXI4MasterParameters(
      name = "memory-wrapper",
      id = IdRange(0, IopmpParams.axi_idNum)
    ))
  )))

  /*****************************************************************************
   * Diplomacy连接
   *
   * 【连接方向】
   * - memory.slaveNode(接收端) := masterNode(发送端)
   * - 数据流: masterNode -> memory.slaveNode
   * - 等价于外部Master -> Memory Slave
   *****************************************************************************/
  memory.slaveNode := masterNode

  // 暴露AXI4接口
  val axi4_s = InModuleBody(masterNode.makeIOs()(ValName("axi4_s")))

  lazy val module = new LazyModuleImp(this) {
    /*****************************************************************************
     * 调试接口 - 透传Memory的调试信号
     *****************************************************************************/
    val debug = IO(new Bundle {
      val mem_addr = Input(UInt(log2Ceil(depth).W))
      val mem_rdata = Output(UInt(IopmpParams.axi_dataBits.W))
    })

    /*******************************************
     * 【<>操作符】
     * - 批量连接两个接口
     * - 自动匹配同名字段并连接
     * - 这里将debug接口直接透传
     *******************************************/
    debug <> memory.module.debug
  }
}
