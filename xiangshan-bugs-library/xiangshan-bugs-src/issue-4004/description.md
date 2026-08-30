### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集相关的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have searched the previous discussions and did not find anything relevant. 我已经搜索过之前的 discussions，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the question

### Describe
Use `riscv64-spike-so `as the reference model to test Xiangshan, set the 13th bit (`LCOFI`) in the `hvip` register to 1, and the 13th bit in the reference model `mip` register is also set to 1, but Xiangshan does not.

![image](https://github.com/user-attachments/assets/99b067c8-9bb3-4a42-8641-1ab471cebbaa)

This is the test program[test.zip](https://github.com/user-attachments/files/18055721/test.zip) The following is a screenshot of the log information：

![image](https://github.com/user-attachments/assets/d115bcd9-899f-44d8-85c4-f194e075db26)
![image](https://github.com/user-attachments/assets/691b77dd-a4ca-44d9-b770-783ebc52c504)

And after testing, when VSSIP, VSTIP, and VSEIP in hvip are set to 1, the corresponding fields in mip will also be set to 1

### Environment：
xiangshan:commit 7d20eb3bd11035e82be160a13c8a11f3cb590f2b
ready-to-run:commit 567138c30ae3b5987124342303753151541ee96c
