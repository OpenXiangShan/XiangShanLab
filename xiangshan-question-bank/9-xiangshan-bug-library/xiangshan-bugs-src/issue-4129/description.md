### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

While testing XiangShan, an issue was observed when executing the instructions `lui zero, 0xaaaaa` and `fld ft0, 526(zero)`. The XiangShan processor exhibited incorrect behavior. 

Specifically, the value of the `mtval `register was different. Additionally, when using NEMU as the reference model, a discrepancy in the` mcause` value was also noted.

### Expected behavior

Log screenshot when using` nemu` as a reference model:

![image](https://github.com/user-attachments/assets/f7211b89-c7ec-40b9-8523-34e847115a90)
![image](https://github.com/user-attachments/assets/47483077-2074-4892-982d-da2af70b4aa2)

Log screenshot when using` spike` as a reference model:
![image](https://github.com/user-attachments/assets/dfe617ab-8050-43e2-9462-dae6397be503)


### To Reproduce

This is the test program and log information：[test.zip](https://github.com/user-attachments/files/18305402/test.zip)


### Environment

- XiangShan branch: 
- XiangShan commit id: 718a93f52f619fdd55e746bbff8d96518e6c648a
- NEMU commit id: 0ae504faeccf00edba54478261d8d7571239e599
- SPIKE commit id:
ready-to-run: 37e90060f7baf8feddf0aea69a3a588f0576594e

### Additional context

_No response_
