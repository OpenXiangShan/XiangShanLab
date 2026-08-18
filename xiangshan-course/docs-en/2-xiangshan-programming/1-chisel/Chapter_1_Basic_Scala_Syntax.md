<!-- # 第一章 Scala 基础语法 -->
# Chapter 1: Basic Scala Syntax

<!-- Scala基础语法实战教程（适配Chisel开发专属） -->
Practical tutorial on basic Scala syntax (tailored for Chisel development)

<!-- ## 前言 -->
## Preface

<!-- Scala是Chisel硬件描述语言的底层支撑，Chisel的参数化模块、批量电路生成、高阶语法、面向对象封装均依赖Scala特性。本教程**只聚焦Chisel开发必备的Scala语法**，剔除无用冗余知识点，配套完整可运行代码、场景解析，适配芯片开发、数字电路设计学习，是Chisel入门的前置核心教程。 -->
Scala is the foundation of the Chisel hardware construction language. Chisel's parameterized modules, bulk circuit generation, higher-order syntax, and object-oriented encapsulation all rely on Scala features. This tutorial **focuses only on the Scala syntax required for Chisel development**, omits unnecessary details, and provides complete runnable code and scenario analyses. It is intended for chip development and digital-circuit design learners as a core prerequisite for learning Chisel.

<!-- ## Scala入门基础 & 输出语法 -->
## Scala Fundamentals and Output Syntax

<!-- ### 1.1 最简HelloWorld程序 -->
### 1.1 Minimal HelloWorld Program

<!-- Scala程序以`object`为程序入口，继承`App`或实现`main`方法，无需多余配置，可直接运行。 -->
A Scala program uses an `object` as its entry point. It can extend `App` or implement a `main` method, requires no extra configuration, and can be run directly.

```scala
// 标准入口对象
// Standard entry-point object
object HelloWorld {
  // 程序主入口方法
  // Main entry method
  def main(args: Array[String]): Unit = {
    println("Hello, Scala & Chisel!")
  }
}
```

<!-- ### 1.2 类与对象基础（核心） -->
### 1.2 Class and Object Fundamentals (Core)

<!-- Scala是面向对象语言，支持类定义、实例化、自定义方法，是Chisel Module、Bundle封装的基础。 -->
Scala is an object-oriented language that supports class definitions, instantiation, and user-defined methods; these are the foundation for encapsulating Chisel `Module` and `Bundle` components.

```scala
// 自定义工具类
// User-defined utility class
class Hello {
  // 自定义方法：通用打印方法，支持任意字符串输入
  // User-defined method: generic printer accepting any string
  def display(str: String): Unit = {
    println("输出内容：" + str)
  }
}

// 程序入口
// Program entry point
object HelloDemo {
  def main(args: Array[String]): Unit = {
    // 实例化类对象
    // Instantiate a class object
    val hello = new Hello()
    // 调用类方法
    // Call class methods
    hello.display("Hello Scala")
    hello.display("Chisel Hardware Code")
  }
}
```

<!-- ## 变量、常量与函数定义 -->
## Variables, Constants, and Function Definitions

<!-- ### 2.1 变量与常量核心区分 -->
### 2.1 Key Distinction Between Variables and Constants

<!-- * **val**：常量，不可二次赋值，**Chisel硬件信号唯一使用** -->
* **val**: A constant that cannot be assigned a second time; **the only declaration form used for Chisel hardware signals**.
<!-- * **var**：变量，可二次修改，**Chisel硬件开发禁止使用** -->
* **var**: A mutable variable that can be reassigned; **do not use it for Chisel hardware development**.

```scala
object VarDemo extends App {
  val constData = 100  // 常量，不可修改; constant, immutable
  var varData = 200    // 变量，可修改; variable, mutable
  
  varData = 300        // 合法; valid
  // constData = 200   // 编译报错，常量不可赋值; compilation error: a constant cannot be assigned
  
  println(s"常量：$constData，变量：$varData")
}
```

<!-- ### 2.2 自定义函数（带参数、返回值） -->
### 2.2 User-Defined Functions (Parameters and Return Values)

<!-- 标准函数语法：`def 函数名(参数: 类型): 返回值类型 = { 函数体 }`，单行函数可省略return，默认返回最后一行结果。 -->
The standard function syntax is `def functionName(parameter: Type): ReturnType = { body }`. A single-expression function may omit `return`; by default, the last expression is returned.

```scala
// 自定义浮点运算类（适配数值计算场景）
// User-defined floating-point operation class (for numerical computation)
class FPU {
  // 双参数、带返回值加法函数
  // Two-parameter addition function with a return value
  def Add(x: Double, y: Double): Double = {
    x + y
  }
}

object FuncDemo extends App {
  val fpu = new FPU()
  val res: Double = fpu.Add(1.9, 2.8)
  println(s"浮点加法结果：$res")
}
```

<!-- ### 2.3 匿名函数（Chisel高频） -->
### 2.3 Anonymous Functions (Frequently Used in Chisel)

<!-- 匿名函数无需定义函数名，多用于集合遍历、批量电路生成，是Chisel高阶编程核心语法。 -->
Anonymous functions do not require a name. They are commonly used for collection traversal and bulk circuit generation and are a core part of higher-order Chisel programming.

<!-- 语法：`(参数1:类型, 参数2:类型) => { 函数体 }` -->
Syntax: `(parameter1: Type, parameter2: Type) => { body }`

```scala
object LambdaDemo extends App {
  // 定义匿名加法函数
  // Define an anonymous addition function
  val addFunc = (a: Int, b: Int) => a + b
  println(s"匿名函数计算结果：${addFunc(10,20)}")
}
```

<!-- ## 面向对象进阶：继承与特质 -->
## Object-Oriented Extensions: Inheritance and Traits

<!-- ### 3.1 类继承 Extends -->
### 3.1 Class Inheritance with `extends`

<!-- Scala**仅支持单类继承**，无多重类继承，适配Chisel模块层级复用设计。 -->
Scala **supports only single class inheritance** and has no multiple class inheritance, which suits hierarchical reuse of Chisel modules.

<!-- 标准语法：`class 子类(子类参数) extends 父类(父类参数)` -->
Standard syntax: `class Subclass(subclassParameters) extends ParentClass(parentParameters)`

```scala
// 父类
// Parent class
class Parent(name: String) {
  def showInfo(): Unit = {
    println(s"父类名称：$name")
  }
}

// 子类继承父类
// Child class inheriting from the parent
class Child(name: String, age: Int) extends Parent(name) {
  def showAge(): Unit = {
    println(s"子类年龄：$age")
  }
}

object ExtendDemo extends App {
  val child = new Child("Chisel模块", 3)
  child.showInfo()
  child.showAge()
}
```

<!-- ### 3.2 特质 Trait（多复用核心） -->
### 3.2 Traits (Core Mechanism for Multiple Reuse)

<!-- Trait是Scala实现**多重功能复用**的核心，弥补单继承缺陷，可被任意类/对象混入，Chisel工具扩展、模块特性复用高频使用。 -->
A trait is Scala's core mechanism for **reusing multiple features**. It compensates for the limitations of single inheritance, can be mixed into any class or object, and is frequently used to extend Chisel utilities and reuse module features.

<!-- 混入语法：`new 类 with Trait1 with Trait2` -->
Mixin syntax: `new Class with Trait1 with Trait2`

```scala
// 定义功能特质1：读数据
// Define feature trait 1: read data
trait Read {
  def readData(): Unit = println("读取数据完成")
}

// 定义功能特质2：写数据
// Define feature trait 2: write data
trait Write {
  def writeData(): Unit = println("写入数据完成")
}

// 混入多个特质，实现功能复用
// Mix in multiple traits for feature reuse
object TraitDemo extends Read with Write {
  def main(args: Array[String]): Unit = {
    readData()
    writeData()
  }
}
```

<!-- ## 数组与Apply隐式方法 -->
## Arrays and the Implicit `apply` Method

<!-- ### 4.1 Array 数组实战 -->
### 4.1 Practical Use of `Array`

<!-- Scala Array为固定长度同类型集合，与Chisel Vec数组逻辑高度一致，用于批量信号、阵列电路定义。 -->
A Scala `Array` is a fixed-length collection of elements of one type. Its behavior is closely analogous to a Chisel `Vec`, making it useful for defining batches of signals and array circuits.

```scala
// 自定义学生实体类
// User-defined student entity class
class Students(name: String, index: String) {
  def register(): Unit = {
    println(s"学号：$index，姓名：$name")
  }
}

object ArrayDemo extends App {
  // 实例化对象
  // Instantiate objects
  val stu1 = new Students("Tom", "2022C1200100")
  val stu2 = new Students("Tim", "2022C1200101")

  // 定义长度为2的自定义类型数组
  // Define a length-two array of the custom type
  val stuArray = new Array[Students](2)
  stuArray(0) = stu1
  stuArray(1) = stu2

  // 遍历数组
  // Traverse the array
  stuArray.foreach(_.register())
}
```

<!-- ### 4.2 Apply 隐式调用方法 -->
### 4.2 Implicit Invocation via `apply`

<!-- apply是Scala隐式方法，调用`对象()`会自动触发apply，Chisel数组、工具类、模块实例化大量依赖该特性。 -->
`apply` is a Scala convention for implicit method invocation: calling `object()` automatically invokes `apply`. Chisel arrays, utility classes, and module instantiation rely heavily on this feature.

```scala
class ApplyTest {
  // 自定义apply方法
  // Define a custom apply method
  def apply(msg: String): Unit = {
    println("隐式调用Apply：" + msg)
  }
}

object ApplyDemo extends App {
  val test = new ApplyTest()
  test("Chisel Apply语法测试") // 自动触发apply方法; automatically invokes apply
}
```

<!-- ## 核心集合操作（Chisel批量生成必备） -->
## Core Collection Operations (Essential for Bulk Chisel Generation)

<!-- ### 5.1 Map键值对集合 -->
### 5.1 `Map` Key-Value Collections

<!-- Map用于存储键值对数据，支持增删改查、遍历、排序，适配Chisel参数映射、状态编码映射场景。 -->
`Map` stores key-value data and supports insertion, deletion, updates, lookup, traversal, and sorting. It is useful for Chisel parameter maps and state-encoding maps.

```scala
object MapDemo extends App {
  // 初始化Map集合
  // Initialize the Map
  var map: Map[String, Int] = Map("a" -> 29, "c" -> 28)

  // 新增元素
  // Add elements
  map += ("d" -> 27)
  map += ("b" -> 26)

  // 基础操作
  // Basic operations
  println("所有Key："); map.keys.foreach(println)
  println("所有Value："); map.values.foreach(println)
  println("是否包含key k6：" + map.contains("k6"))
  println("集合大小：" + map.size)
  println("读取key，无则默认值：" + map.getOrElse("k1", "default"))

  // 遍历方式
  // Traversal methods
  println("遍历1：")
  map.foreach{case (k, v) => println(k, v)}
  println("遍历2：")
  for ((k, v) <- map) println(k, v)

  // 排序操作
  // Sorting operations
  println("Key升序：" + map.toSeq.sortBy(_._1))
  println("Value降序：" + map.toSeq.sortBy(_._2).reverse)

  // 清空集合
  // Clear the collection
  map = Map()
  println("清空后大小：" + map.size)
}
```

<!-- ### 5.2 Zip拉链操作 -->
### 5.2 `zip` Pairing Operation

<!-- zip可将两个集合一对一配对，实现数据关联对齐，常用于Chisel多组信号绑定、端口映射、并行数据匹配。 -->
`zip` pairs two collections element by element to align related data. It is often used for binding groups of Chisel signals, mapping ports, and matching parallel data.

```scala
object ZipDemo extends App {
  // 两组关联数据
  // Two related data sets
  val names = Array("tom", "jerry", "john")
  val scores = Array(70, 80, 90)

  // 一对一拉链配对
  // Pair element by element with zip
  val zipRes = names.zip(scores)
  println("姓名-分数配对结果：")
  zipRes.foreach(println)

  // 长度不一致补全拉链
  // Zip collections of different lengths with fill values
  val nums = Seq(0,1,2)
  val series = List("A","B","C","D")
  val zipAllRes = nums.zipAll(series, -1, "NULL")
  println("补全配对结果：" + zipAllRes)
}
```

<!-- ### 5.3 Reduce归约聚合 -->
### 5.3 `reduce` Reduction and Aggregation

<!-- reduce用于集合迭代聚合计算，可实现累加、累乘，Chisel多路数据规约、批量求和电路核心依赖该思想。 -->
`reduce` performs iterative aggregation over a collection, enabling operations such as summation and multiplication. Chisel multi-input reductions and bulk adder circuits are based on this idea.

```scala
object ReduceDemo extends App {
  val list = List(1,2,3,4,5,6,7,8,9,10)

  // 累加迭代函数
  // Iteration function for accumulation
  val sumFunc = (x: Int, y: Int) => {
    println(s"迭代：$x + $y")
    x + y
  }

  // 累乘迭代函数
  // Iteration function for multiplication
  val mulFunc = (x: Int, y: Int) => x * y

  println("累加总和：" + list.reduce(sumFunc))
  println("累乘结果：" + list.reduce(mulFunc))
}
```

<!-- ## 高阶算子（批量电路生成核心） -->
## Higher-Order Operators (Core of Bulk Circuit Generation)

<!-- Scala五大高阶算子是Chisel**编译期批量生成电路**的核心，可替代重复代码，生成规整的阵列、流水线电路。 -->
Scala's five major higher-order operators are central to **compile-time bulk circuit generation** in Chisel. They replace repetitive code and generate regular array and pipeline circuits.

```scala
object HighFuncDemo extends App {
  val data = List(1,2,3,4,5,6,7,8,9,10)

  // map：元素映射，生成新集合
  // map: transform elements to create a new collection
  val mapRes = data.map(_ * 2)
  // filter：条件过滤，保留偶数
  // filter: keep even elements
  val filterRes = data.filter(_ % 2 == 0)
  // sortBy：排序
  // sortBy: sort elements
  val sortRes = data.sortBy(-_)
  // groupBy：按规则分组
  // groupBy: partition elements by a rule
  val groupRes = data.groupBy(_ % 3)

  println("map映射：" + mapRes)
  println("filter过滤：" + filterRes)
  println("sort降序：" + sortRes)
  println("groupBy分组：" + groupRes)
}
```

<!-- ## 参数校验与泛型 -->
## Parameter Validation and Generics

<!-- ### 7.1 require参数断言校验 -->
### 7.1 Parameter Assertions with `require`

<!-- require用于编译/运行时参数合法性校验，非法则抛异常，Chisel常用于模块位宽、深度参数容错校验。 -->
`require` validates parameters at compile time or run time and throws an exception when a condition is invalid. In Chisel it is commonly used to validate module widths and depth parameters.

```scala
object RequireDemo extends App {
  // 硬件位宽合法性校验
  // Validate the hardware width
  def checkWidth(width: Int): Unit = {
    require(width > 0, "错误：硬件位宽必须大于0！")
    println(s"位宽 $width 校验通过")
  }

  checkWidth(8)
  // checkWidth(0) // 触发断言异常，参数非法; triggers an assertion because the parameter is invalid
}
```

<!-- ### 7.2 泛型基础 -->
### 7.2 Generic Fundamentals

<!-- 泛型实现通用类、通用方法，适配Chisel泛型模块、通用接口开发，提升代码复用性。 -->
Generics enable general-purpose classes and methods. They support the development of generic Chisel modules and interfaces and improve code reuse.

```scala
// 通用泛型工具类
// Generic utility class
class GenericTest[T](value: T) {
  def getValue: T = value
}

object GenericDemo extends App {
  val intData = new GenericTest[Int](100)
  val strData = new GenericTest[String]("Chisel泛型模块")
  println(intData.getValue)
  println(strData.getValue)
}
```

<!-- ## for-yield批量生成语法 -->
## Bulk Generation with `for`-`yield`

<!-- for-yield可循环遍历并生成新集合，是Chisel**批量生成寄存器、阵列信号、多路电路**的核心语法。 -->
`for`-`yield` iterates over elements and produces a new collection. It is core syntax for **bulk generation of registers, array signals, and multi-path circuits** in Chisel.

```scala
object ForYieldDemo extends App {
  // 生成1-10所有偶数
  // Generate all even numbers from 1 through 10
  val evenList = for (i <- 1 to 10 if i % 2 == 0) yield i
  println("偶数集合：" + evenList)

  // 适配Chisel：批量生成硬件索引信号
  // Chisel use: generate hardware index signals in bulk
  val indexList = for (i <- 0 until 8) yield i.U
  println("批量硬件索引：" + indexList)
}
```

<!-- ## Scala适配Chisel核心开发规范 -->
## Core Scala Development Guidelines for Chisel

<!-- 1. **常量优先**：所有Chisel硬件信号、硬件连线、模块实例统一使用 `val` 定义；`var` 仅可用于Scala编译期辅助变量、循环临时变量，严禁用于定义硬件信号，避免生成异常硬件逻辑； -->
1. **Prefer constants**: Define all Chisel hardware signals, connections, and module instances with `val`; reserve `var` for Scala compile-time helper and loop-temporary variables, never for hardware signals, to avoid generating unintended hardware logic.
<!-- 2. **编译期慎用**：Scala循环、高阶算子仅用于**编译期电路生成**，不可用于硬件运行时动态逻辑； -->
2. **Use compile-time constructs carefully**: Scala loops and higher-order operators are for **compile-time circuit generation** only, not for dynamic hardware run-time logic.
<!-- 3. **类型区分**：Scala `==` 用于代码语法比较，硬件信号比较必须用Chisel `===` / `=/=`； -->
3. **Distinguish comparisons**: Scala `==` compares values in host-language code; hardware signals must be compared with Chisel `===` / `=/=`.
<!-- 4. **参数校验**：自定义参数化模块，优先使用 `require` 做参数合法性校验； -->
4. **Validate parameters**: Prefer `require` to validate parameters in custom parameterized modules.
<!-- 5. **功能复用**：多模块通用功能优先使用 `Trait` 混入，避免代码冗余； -->
5. **Reuse functionality**: Prefer mixing in `Trait` implementations for functionality shared by multiple modules, avoiding duplicated code.
<!-- 6. **批量生成**：阵列电路、多路并行电路，优先使用 map/for-yield/reduce 批量生成，拒绝重复代码。 -->
6. **Generate in bulk**: Use `map`, `for`-`yield`, and `reduce` to generate array and parallel circuits instead of duplicating code.


<!-- > 更新: 2026-05-26 15:47:47  -->
> Updated: 2026-05-26 15:47:47
<!-- > 原文: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/cv8yrpezn21etz3u> -->
> Source: <https://bosc.yuque.com/staff-xmw8rg/fb7qy3/cv8yrpezn21etz3u>
