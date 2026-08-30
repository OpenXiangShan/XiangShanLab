### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

The RISC-V specification define rules that guarantee Debug Mode access to Smstateen-gated CSRs.

Debug Spec, Sdtrig:

<img width="294" height="416" alt="Image" src="https://github.com/user-attachments/assets/b46b4edf-14ec-4609-999c-a863c5953b5c" />

But XiangShan violates, which causes CSRs such as `scontext` and `hcontext` to be inaccessible when halted from a non-M-mode privilege level.
When a debugger accesses hcontext/scontext (and other Smstateen-gated CSRs) in Debug Mode, the access is incorrectly rejected with an illegal instruction exception.

### Root Cause
CSRPermitModule forwards the debugMode signal to PrivilegePermitModule (which correctly bypasses via || debugMode), but does not forward it to MLevelPermitModule or VirtualLevelPermitModule. Their Smstateen checks use only !privState.isModeM as the exemption condition:

```
val accessContext_EX_II = csrIsContext && !privState.isModeM && !mstateen0.CONTEXT.asBool
```

In Debug Mode, privState retains the pre-debug privilege level (e.g. HS-mode), so isModeM=false and the check fires. Since the final exception is OR'd (mPermit_EX_II || pPermit_EX_II), mPermit_EX_II=true overrides the correct pPermit_EX_II=false.

### Expected behavior

Debug Mode, having "even more access than M-mode" (Priv Spec §1.2), should therefore be exempt from Smstateen restrictions and able to access these registers unconditionally.

### Environment

Branch: kunminghu-v3
Config: DefaultConfig
Emulator: emu with --enable-jtag --no-diff
OpenOCD: riscv-openocd ([RISC-V fork](https://github.com/riscv-collab/riscv-openocd)),
The configuration process refers to https://docs.xiangshan.cc/projects/design/zh-cn/nanhu/misc/debug/ .
Only use OpenOCD


### To Reproduce

[smstateen-debug-poc.tar.gz](https://github.com/user-attachments/files/26240708/smstateen-debug-poc.tar.gz)

#### shell 1. Build the PoC program and Run the emulator with JTAG enabled
The PoC runs in M-mode, then drops to S-mode and spins, waiting for the debugger to halt.

#### shell 2. Connect OpenOCD

```bash

openocd -f openocd-xs.cfg

```

#### shell 3.

test1(non-gated CSRs, should succeed):
```bash
(sleep 1; echo "halt"; sleep 5; \
echo "reg sstatus"; sleep 2; \
echo "reg misa"; sleep 2; \
echo "exit") | nc localhost 4444
```

Result — ****all succeed**** (Debug Mode channel works correctly):

```
> halt
riscv.cpu halted due to debug-request.
> reg sstatus
sstatus (/64): 0x8000000200006000
> reg misa
misa (/64): 0x80000000003411af
```

test2(should succeed but fails):

```bash
(sleep 1; echo "halt"; sleep 5; \
echo "reg scontext"; sleep 2; \
echo "reg hcontext"; sleep 2; \
echo "exit") | nc localhost 4444
```

Result — ****both fail**** (Smstateen check incorrectly blocks Debug Mode access):

```

> halt
riscv.cpu halted due to debug-request.
> reg scontext
Could not read register 'scontext'
> reg hcontext
Could not read register 'hcontext'

```

### Additional context

_No response_
