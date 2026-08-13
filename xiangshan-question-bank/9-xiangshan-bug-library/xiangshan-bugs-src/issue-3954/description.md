### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集相关的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have searched the previous discussions and did not find anything relevant. 我已经搜索过之前的 discussions，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the question

### describe
When I was testing Xiangshan, when executing the following test sample, there would be inconsistencies in the `fcsr` register, specifically the `UF `flag.

### Log screenshot

The inconsistent information between nemu and spike is as follows
![image](https://github.com/user-attachments/assets/84359231-b334-4dbf-9fd9-0fbdb0636237)
![image](https://github.com/user-attachments/assets/d6944b01-26b4-400d-8c0d-9db5f6220b7e)
![image](https://github.com/user-attachments/assets/318a5f62-4add-4de5-9959-822ef767daa0)

Test samples and log information:[fcsr.zip](https://github.com/user-attachments/files/17942854/fcsr.zip)

### Version Information
xiangshan:commit b00d5822032eadad2744fcfc0a8c03fa011ff81c (HEAD -> master, origin/master, origin/HEAD)
ready-to-run:commit 457f091898b2bcd26c8f6e983f3df174f990af43 (HEAD -> master, origin/master, origin/HEAD)

I also noticed that there seems to be a fix for the OF and UF flags in the yunsuan module(https://github.com/OpenXiangShan/YunSuan/commit/548dbea6c0e0c017e20cf373abe7dd149d750f5b), but the Xiangshan version I use has already been merged.
