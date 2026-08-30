### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Describe the bug

When executing commands such as "make verilog CONFIG=XSNoCTopConfig", "make verilog CONFIG=KunminghuV2Config" in the latest code project, the error message is as follows：

[654] [error] /home/xiangshan/xs-env/XiangShan/src/main/scala/device/RocketDebugWrapper.scala:42:29: no arguments allowed for nullary constructor DebugIO: ()(implicit p: org.chipsalliance.cde.config.Parameters): freechips.rocketchip.devices.debug.DebugIO
[654] [error]   val debugIO = new DebugIO(asyncReset)(p)
[654] [error]                             ^
[654] [error] /home/xiangshan/xs-env/XiangShan/src/main/scala/xiangshan/L2Top.scala:117:28: unknown parameter name: EnablePrivateClint
[654] [error]         EnablePrivateClint = SeperateBus != top.SeperatedBusType.NONE
[654] [error]                            ^
[654] [error] /home/xiangshan/xs-env/XiangShan/src/main/scala/xiangshan/backend/rename/Rename.scala:39:38: object VmoveType is not a member of package yunsuan
[654] [error] did you mean VfcvtType?
[654] [error] import yunsuan.{VfaluType, VipuType, VmoveType}
[654] [error]                                      ^
[654] [warn] /home/xiangshan/xs-env/XiangShan/src/main/scala/xiangshan/backend/Region.scala:563:61: Auto-application to `()` is deprecated. Supply the empty argument list `()` explicitly to invoke method getForwardIndex,
[654] [warn] or remove the empty argument list from its definition (Java-defined methods are exempt).
[654] [warn] In Scala 3, an unapplied method like this will be eta-expanded into a function. [quickfixable]
[654] [warn]       sink.bits.data := source.bits.data(source.bits.params.getForwardIndex)
[654] [warn]                                                             ^
[654] [error] /home/xiangshan/xs-env/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:37:17: object FcmpOpCode is not a member of package yunsuan
[654] [error] did you mean FmaOpCode? or perhaps FaddOpCode or VfmaOpCode?
[654] [error] import yunsuan.{FcmpOpCode, VfaluType, VfcvtType, VfmaType, VfmaOpCode}
[654] [error]                 ^
[654] [error] /home/xiangshan/xs-env/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:572:76: not found: value FcmpOpCode
[654] [error]     FLEQ_H      -> FDecode(SrcType.fp, SrcType.fp, SrcType.X, FuType.fcmp, FcmpOpCode.fleq, xWen = T, canRobCompress = T),
[654] [error]                                                                            ^
[654] [error] /home/xiangshan/xs-env/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:573:76: not found: value FcmpOpCode
[654] [error]     FLEQ_S      -> FDecode(SrcType.fp, SrcType.fp, SrcType.X, FuType.fcmp, FcmpOpCode.fleq, xWen = T, canRobCompress = T),
[654] [error]                                                                            ^
[654] [error] /home/xiangshan/xs-env/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:574:76: not found: value FcmpOpCode
[654] [error]     FLEQ_D      -> FDecode(SrcType.fp, SrcType.fp, SrcType.X, FuType.fcmp, FcmpOpCode.fleq, xWen = T, canRobCompress = T),
[654] [error]                                                                            ^
[654] [error] /home/xiangshan/xs-env/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:575:76: not found: value FcmpOpCode
[654] [error]     FLTQ_H      -> FDecode(SrcType.fp, SrcType.fp, SrcType.X, FuType.fcmp, FcmpOpCode.fltq, xWen = T, canRobCompress = T),
[654] [error]                                                                            ^
[654] [error] /home/xiangshan/xs-env/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:576:76: not found: value FcmpOpCode
[654] [error]     FLTQ_S      -> FDecode(SrcType.fp, SrcType.fp, SrcType.X, FuType.fcmp, FcmpOpCode.fltq, xWen = T, canRobCompress = T),
[654] [error]                                                                            ^
[654] [error] /home/xiangshan/xs-env/XiangShan/src/main/scala/xiangshan/backend/decode/DecodeUnit.scala:577:76: not found: value FcmpOpCode
[654] [error]     FLTQ_D      -> FDecode(SrcType.fp, SrcType.fp, SrcType.X, FuType.fcmp, FcmpOpCode.fltq, xWen = T, canRobCompress = T),
[654] [error]                                                                 

### Expected behavior

generated .v code

### To Reproduce

Run "make verilog CONFIG=XSNoCTopConfig" or "make verilog CONFIG=KunminghuV2Config" in the latest code project.

### Environment

- XiangShan branch: kunminghu-v3
- XiangShan commit id: 150a7fa36e79dbaeece22e0ddd67d7e986913dc0
- XiangShan config:  XSNoCTopConfig
- NEMU commit id:
- SPIKE commit id:


### Additional context

_No response_
