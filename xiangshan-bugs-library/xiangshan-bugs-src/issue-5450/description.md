### Before start

- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have searched the previous discussions and did not find anything relevant. 我已经搜索过之前的 discussions，并没有找到相关的。
- [x] I have reproduced the problem using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了问题。

### Describe you problem

When executing command make verilog CONFIG=FpgaDefaultConfig, the following error was reported:
[660] Exception in thread "main" java.util.NoSuchElementException: None.get
If I switch to commit 4b9ddb8a6311bff6f5e29cb3722d1f4236d66292, the.V code is generated successfully, but after switching to the latest or commit 64e7bff7f, an error occurs.

### What did you do before

I executed the following commands according to the manual.
git clone https://github.com/OpenXiangShan/XiangShan
cd XiangShan
export NOOP_HOME=$(pwd)
make init
make clean

### Environment

- XiangShan branch: kunminghu-v3
- XiangShan commit id: 64e7bff7f
- XiangShan config:  FpgaDefaultConfig
- NEMU commit id:
- SPIKE commit id:
- Operating System:  CentOS Linux release 7.9.2009
- gcc version: 11.2.0
- mill version: 0.12.15
- java version: openjdk 11.0.23-internal 2024-04-16


### Additional context

_No response_
