package minixiangshan.util

import chisel3._
import chisel3.util._
 
/**
 * 环形队列指针 —— 带旗子的单调递增指针
 *
 * 【设计原理】
 *   环形队列有一个经典难题：当头尾指针指向同一个位置时，无法区分"满"和"空"。
 *   解决方案：给指针加一个1-bit的"旗子"(flag)。
 *
 *   - 指针每次走到队列末尾再绕回开头时，flag翻转（0→1 或 1→0）
 *   - 两个指针 value 相同 且 flag 相同 → 队列空（没差一圈）
 *   - 两个指针 value 相同 且 flag 不同 → 队列满（差了一圈）
 *
 * 【类比】
 *   就像两个人绕操场跑步，位置一样但圈数不同：
 *   - 同圈同位置 → 并排跑，没差距 → 空
 *   - 不同圈同位置 → 快的套了慢的一圈 → 满
 *
 * 【参数】
 *   entries: 队列容量，必须是2的幂（8, 16, 32, 64...）
 *            因为2的幂可以用位与(&)代替取余(%)，硬件更快
 *
 * 【字段】
 *   value: UInt(log2Ceil(entries).W)  —— 在队列数组中的索引位置
 *   flag:  Bool                        —— 翻转标志位，记录绕了几圈
 *
 * 【继承方式】
 *   class MyPtr extends CircularQueuePtr[MyPtr](entries=64)
 *   这样每个队列类型有自己的指针类，编译器能帮您检查不会混用
 */
class CircularQueuePtr[T <: CircularQueuePtr[T]](val entries: Int) extends Bundle {
  //this: T =>   // ★★★ 唯一修改：加这一行 ★★★
  
  // 编译期检查：队列大小必须是2的幂
  // 原因：entries = 2^n 时，取余可以用 value & (entries-1) 代替 value % entries
  //       位与操作在硬件中是一根线，取余需要除法器——代价天差地别
  require(isPow2(entries), s"环形队列大小必须是2的幂，当前值: $entries")
 
  // =====================================================================
  // 核心字段
  // =====================================================================
 
  /** 位置：指向队列数组中的第几个元素，范围 [0, entries-1] */
  val value: UInt = UInt(log2Ceil(entries).W)
 
  /** 旗子：每绕队列一圈翻转一次。
   *  初始为0（蓝旗），第一次绕回后为1（红旗），再绕回又是0……
   *  只需要1 bit就够了，因为旗子相同=同圈，不同=差一圈
   */
  val flag: Bool = Bool()
  
  // =====================================================================
  // 指针前进（核心操作）
  // =====================================================================
 
  /**
   * 指针前进一步。
   *
   * 【工作过程】
   *   1. value 加1
   *   2. 如果 value 加到了 entries（越界），则：
   *      - value 回绕到 0
   *      - flag 翻转
   *
   * 【为什么用位与而不是 if-else？】
   *   entries = 8 时，log2(8) = 3，value 是 3-bit 的 UInt
   *   value + 1 的结果可能是 4'b1000 (=8)，但 value 只有 3 bit
   *   所以 (value + 1) 截断后自动变成 3'b000 (=0)
   *   → 硬件天然实现了"绕回"，不需要显式判断！
   *
   *   但 flag 的翻转需要显式判断：
   *   flag := Mux(value === (entries-1).U, !flag, flag)
   *   "如果当前在最后一个位置，前进后要翻转旗子"
   *
   * 【返回值】
   *   返回前进后的新指针（本指针不变，产生一个新的Wire）
   */
  def +(inc: UInt): T = {
    val newPtr = Wire(this.asInstanceOf[T].cloneType.asInstanceOf[T])
    val newIncValue = this.value +& inc
    // 判断是否跨过了队列末尾（即是否发生了绕回）
    // newIncValue >= entries.U 意味着至少绕了一圈
    val wrap = newIncValue >= entries.U
    newPtr.value := newIncValue(log2Ceil(entries) - 1, 0)
    newPtr.flag  := Mux(wrap, !this.flag, this.flag)
    newPtr.asInstanceOf[T]
  }

  def -(inc: UInt): T = {
    val newPtr = Wire(this.asInstanceOf[T].cloneType.asInstanceOf[T])
    // ★ 核心思路：(value - inc) mod entries = (value + entries - inc) mod entries
    // 用 +& 做加法防止溢出截断，再用减法就不会下溢（因为 value+entries >= inc）
    val compensated = this.value +& entries.U    // value + entries，全宽度
    val newDecValue = compensated - inc           // 安全的减法，不会下溢
   
    // ── 检测反向绕回：inc > this.value 时，指针从0反向跨过了队列起点 ──
    val wrap = inc > this.value
   
    newPtr.value := newDecValue(log2Ceil(entries) - 1, 0)  // 取低位 = mod entries
    newPtr.flag  := Mux(wrap, !this.flag, this.flag)        // 绕回一次翻转flag
    newPtr.asInstanceOf[T]
  }



 
  /** 前进1步的简便写法 */
  def +(): T = this + 1.U
 
  // =====================================================================
  // 状态判断
  // =====================================================================
 
  /**
   * 判断两个指针是否指向同一位置。
   * 注意：这不是判断"队列空"，而是单纯的物理位置比较。
   */
  def ===(that: T): Bool = this.value === that.value && this.flag === that.flag
 
  /** 不等于 */
  def =/=(that: T): Bool = !(this === that)
 
  // =====================================================================
  // 顺序比较（用于乱序处理器中的时序判断）
  // =====================================================================
 
  /**
   * 判断 this 是否在 that 之后（程序序更晚）
   *
   * 【用途】
   *   在Load/Store Queue中，判断一条指令是否比另一条更晚执行：
   *   - 同flag时：value大 = 更晚
   *   - 不同flag时：value小 = 更晚（因为小的已经绕了一圈）
   *
   * 【例子】entries=8
   *   ptr(值=3,旗=0).isAfter(ptr(值=5,旗=0)) → false (3比5早)
   *   ptr(值=5,旗=0).isAfter(ptr(值=3,旗=0)) → true  (5比3晚)
   *   ptr(值=3,旗=1).isAfter(ptr(值=5,旗=0)) → true  (3已绕了一圈，更晚)
   *   ptr(值=5,旗=0).isAfter(ptr(值=3,旗=1)) → false (5还没绕，比3早)
   */
  def isAfter(that: T): Bool = {
    Mux(this.flag === that.flag,
      this.value > that.value,   // 同旗：value大 = 更晚
      this.value < that.value    // 异旗：value小 = 更晚（已绕圈）
    )
  }
 
  /** 判断 this 是否在 that 之前（程序序更早） */
  //def isBefore(that: T): Bool = that.isAfter(this)
 
  // =====================================================================
  // 距离计算
  // =====================================================================
 
  /**
   * 计算从 this 到 that 之间有多少个项
   * 通过不同的flag进行分类
   */
  def distanceTo(that: T): UInt = {
    Mux(this.flag === that.flag,
      this.value - that.value,
      (entries.U +& this.value) - that.value
    )
  }
}
 
/**
 * CircularQueuePtr 的伴生对象，提供便捷的工厂方法
 */
object CircularQueuePtr {
  /**
   * 从 flag 和 value 创建一个指针
   * 用法：val ptr = MyPtr(true.B, 5.U)  // 旗子=红，位置=5
   */
  def apply[T <: CircularQueuePtr[T]](gen: => T, f: Bool, v: UInt): T = {
    val ptr = Wire(gen)
    ptr.flag  := f
    ptr.value := v
    ptr
  }
}