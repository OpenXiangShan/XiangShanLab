### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

When executing the `prefetch.w 1856(s0)` instruction (s0:0xffffffffffffffff), the `mcause` values ​​of Xiangshan and the reference model are inconsistent. Specifically, the mcause value of Xiangshan is 5, indicating a `load access fault`, but neither nemu nor spike causes an exception.

Please let me know if I missed any details. Thanks so much!

### Expected behavior

The log screenshot is as follows：

![image](https://github.com/user-attachments/assets/50ce5330-84b5-413f-a9aa-5cb0a2ac9a38)
![image](https://github.com/user-attachments/assets/1de2161a-b0c8-4832-b073-35334643d227)
![image](https://github.com/user-attachments/assets/75f39771-9f46-4501-b102-64e94e950d9c)

The log information of nemu and spike is the same

### To Reproduce

This is the test program and log information [test.zip](https://github.com/user-attachments/files/18140662/test.zip)


### Environment

- XiangShan branch: 
- XiangShan commit id: c7ca40e4d71e157897f43817976971d7cedfa22a (HEAD -> master, origin/master, origin/HEAD)
- NEMU commit id: cc72c9aa97dc2504f807191d03c57242da5aaeda
- SPIKE commit id:
ready-to-run:commit 96f40214d13db437a4aa5b118420cfe91e9c9836

### Additional context

_No response_
