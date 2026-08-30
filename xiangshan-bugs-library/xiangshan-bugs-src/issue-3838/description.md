### Before start

- [X] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

Here is the picture shows that instr `0x310d1413` is decoded `aes64ks1i   s0,s10,784` by Xiangshan & NEMU, spike decoded it as `aes64ks1i s0, s10, 0`. Spike is right.
![image](https://github.com/user-attachments/assets/59639a5e-3fac-429a-98e6-9ba04cefc0d2)

However, the commit instr trace shows `0x310d1413` is decoded correctly, **but**:
![image](https://github.com/user-attachments/assets/d2a75ba7-101f-4db4-810e-060dd2d7d2af)
Xiangshan & NEMU decode `0x317e9f13` as `aes64ks1i t5, t4, 7`, this is wrong. Spike still right: `core   0: 0x000000008000017c (0x31ff9493) aes64ks1i s1, t6, 15`.
Spike is right because of a piece of asm code:
```
pseg_0:
	aes64ks1i x8, x26, 0
	aes64ks1i x9, x31, 15
	aes64ks1i x25, x19, 4
```

I am not sure the inside of Xiangshan & NEMU whether decode correctly, if they are right, I got this:
![image](https://github.com/user-attachments/assets/dca4a1f5-7c3b-43bd-8e5d-b4f8f19fe3ad)



### Expected behavior

Instruction trace show correct instructions. `s1` is right. 

### To Reproduce

[test.zip](https://github.com/user-attachments/files/17641037/test.zip)


### Environment

- XiangShan branch: master
- XiangShan commit id: e80f666e9
- NEMU commit id: fce68dcb
- SPIKE commit id: 1.1.1-dev


### Additional context

_No response_
