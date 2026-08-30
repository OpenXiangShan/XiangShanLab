**Bug-library**

**按照以下几个维度进行分析每一个Bug**
1. bug-program 
    借助xspdb分析触发bug的指令流和数据流
2. bug-log
    借助difftest工具差分分析bug的现象
3. bug-analyze 
    结合香山源代码分析触发的bug的条件和场景
4. bug-waveform
    在波形中找到关键信号和出错的第一现场
5. debug-skill
    借助AI工具完成debug-skill的沉淀
    
注：提交的每个bug, 需包含源程序（源代码，elf,反汇编），编译的命令，昆明湖的配置，difftest log, 波形，分析文档