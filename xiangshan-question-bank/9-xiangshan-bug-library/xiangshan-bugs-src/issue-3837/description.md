### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [X] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [X] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [X] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。

### Describe the bug

Sorry If I was wrong, but I noticed that `s2_fire_dup` is assigned twice in the BPU.scala:
```
  for (
    (((s2_fire, s3_components_ready), s3_ready), s2_valid) <-
      s2_fire_dup zip s3_components_ready_dup zip s3_ready_dup zip s2_valid_dup
  )
    s2_fire := s2_valid && s3_components_ready && s3_ready
```
and 
```
  s2_fire_dup := s2_valid_dup
```

The second assignment completely overwrites the first one, relying only on s2_valid but not on the `s3_components_ready` and `s3_ready` signals.

Is this supposed to happen?
Jerry

### Expected behavior

It should be `s2_fire := s2_valid && s3_components_ready && s3_ready`

### To Reproduce

Issue at file: xiangshan/frontend/BPU.scala around line 403-415


### Environment

- XiangShan branch: Master
- XiangShan commit id: 844fba5b86d00c2ec233eddeffc3d637862575fd
- NEMU commit id: Not Used
- SPIKE commit id: Not Used


### Additional context

_No response_
