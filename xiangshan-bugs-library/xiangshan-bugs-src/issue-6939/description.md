### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v2

### Describe the bug

Narrowing `vnclip.wv vd, vs2, vs1` with **SEW=32, LMUL=1** and **vs1 overlapping the double-width vs2 group** is a **reserved** encoding: the same physical vector register is read with two different EEWs in one instruction. XiangShan does not treat it as illegal and **executes** it (updates `vd`, may set `vxsat`).

Minimal stimulus (`vnclip_src_eew_overlap.S`):

```assembly
vsetivli zero, 4, e32, m1, ta, ma
csrwi   vxsat, 0
vnclip.wv v26, v12, v13    # encoding 0xbec68d57
li      a0, 0x1111         # fall-through marker if no trap
```

Operand EEWs:

| operand | register group | EEW | EMUL |
|---|---|---|---|
| `vs2 = v12` | `{v12, v13}` | 64 (`2*SEW`) | 2 |
| `vs1 = v13` | `{v13}` | 32 (`SEW`) | 1 |
| `vd  = v26` | `{v26}` | 32 | 1 |

Physical register **`v13` is read as EEW=64 (in vs2) and EEW=32 (as vs1)**.

### Expected behavior

RISC-V Unprivileged ISA `norm:vreg_source_eew_rsv`: encodings that read the same vector register with more than one EEW in a single instruction are **reserved**.

Latest `sail-riscv` master raises **illegal-instruction** on this seed (`mtval=0xbec68d57`). XiangShan should likewise trap; `vd` / `vxsat` must not be updated by the reserved op.

The bug also exists in the latest version of NEMU, so using NEMU as the reference model cannot detect this bug.

### Environment

- Repo
  - XiangShan commit id: `7b16188f7f18a97be69c9b1a2dae8a446eec3b47`
  - XiangShan config: DefaultConfig


### To Reproduce

1) Install Sail and build sail-riscv
```shell
curl -fsSL -o sail.tgz \
  https://github.com/rems-project/sail/releases/latest/download/sail-Linux-x86_64.tar.gz
tar xf sail.tgz
export PATH="$PWD/sail/bin:$PATH"

git clone https://github.com/riscv/sail-riscv.git
cd sail-riscv
./build_simulator.sh
# output: build/c_emulator/sail_riscv_sim
```

2) Run with Sail (expect illegal-instruction)
```shell
SAIL=./build/c_emulator/sail_riscv_sim
"$SAIL" --print-default-config > sail-default.jsonc
# Copy to sail-v128.json and set extensions.V.vlen_exp = 7 (VLEN=128)
"$SAIL" --config sail-v128.json --trace-instr --trace-exception \
  vnclip_src_eew_overlap.elf
# expect: illegal-instruction on vnclip.wv, tval=0xBEC68D57
```

3) Reproduce on an existing kunminghu-v2 emu (expect execute, no trap)
```shell
./build/emu -i vnclip_src_eew_overlap.bin --no-diff --dump-commit-trace
# expect: commit 0xbec68d57, then a0=0x1111 (fall-through)
```

[vnclip_src_eew_overlap.zip](https://github.com/user-attachments/files/34695866/vnclip_src_eew_overlap.zip)
