### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

master (kunminghu-v2)

### Describe the bug

<html>
<body>
<!--StartFragment--><!-- obsidian --><p>We encountered a pipeline hang issue. We suspect the vector LSU fails to trigger a load access fault when the indexed load instruction vluxei32. V accesses unmapped physical memory, resulting in permanent bus hang.</p>
<p><strong>1. Context &#x26; Commit Trace</strong><br>
The last successfully committed instruction in XiangShan is at PC <code>80001234</code>.</p>
<pre><code>[30] commit pc 0000000080001230 inst c71fa5af ...
[31] commit pc 0000000080001234 inst d228f8d3 ... &#x3C;-- Last commit
</code></pre>
<p>Looking at the disassembly:</p>
<pre><code>80001234:  fcvt.d.l    fa7, a7          &#x3C;-- Committed
80001238:  sha256sum0  s7, a7           &#x3C;-- Stuck in ROB
8000123c:  aes64ds     sp, s6, s7       &#x3C;-- Stuck in ROB
80001240:  vluxei32.v  v25, (a1), v17   &#x3C;-- The offending instruction
80001244:  aes64ks2    s4, s4, a4 
</code></pre>
<p><strong>2. Effective Address Analysis</strong><br>
The semantic of <code>vluxei32.v v25, (a1), v17</code> is an indexed vector load. Based on the snapshot:</p>
<ul>
<li><strong>Base address (a1)</strong>: <code>0x000000004ff0b1eb</code></li>
<li><strong>Index vector (v17)</strong>: <code>0x623d2db2b333c0b1_ee63dead4de4e48e</code></li>
<li><strong>VL</strong>: <code>4</code> (SEW=32, LMUL=1)<br>
Calculating the 4 Effective Addresses (EA = a1 + offset, 64-bit unsigned):</li>
</ul>

Element | Offset (v17) | Effective Address (EA) | Memory Region
-- | -- | -- | --
[0] | 0x4de4e48e | 0x9dd59679 | Unmapped
[1] | 0xee63dead | 0x13e549098 | Unmapped
[2] | 0xb333c0b1 | 0x10324729c | Unmapped
[3] | 0x623d2db2 | 0xb22ddf9d | Unmapped


<p>All generated addresses fall into unmapped physical regions.</p>
<p><strong>3. The Difftest Mismatch</strong><br>
According to the RISC-V Privileged Architecture, accessing these unmapped addresses should trigger a <strong>Load Access Fault (Cause = 5)</strong>.</p>
<p>However, the VLSU did not raise an exception. Instead, the memory request seemingly went to the system bus without returning, causing the ROB to deadlock for 15000 cycles:</p>
<p><code>No instruction of core 0 commits for 15000 cycles, maybe get stuck</code></p>
<p>Due to the timeout, the Difftest framework forced NEMU to step forward. NEMU handled the unmapped access (writing <code>0x0</code> to <code>v25</code>), while XiangShan's <code>v25</code> remained at its old state (<code>0xffffffffffffffff</code>), which exposed the mismatch:</p>
<pre><code>v25_low  different at pc = 0x0080000274, right = 0x0000000000000000, wrong = 0xffffffffffffffff
v25_high different at pc = 0x0080000274, right = 0x0000000000000000, wrong = 0xffffffffffffffff
</code></pre>
<p><strong>4. The report</strong></p><!--EndFragment-->

[emulator.zip](https://github.com/user-attachments/files/28209851/emulator.zip)

</body>
</html>

### Expected behavior

When a vector memory instruction targets unmapped/illegal physical addresses, XiangShan's Vector LSU should correctly throw a `Load Access Fault` (Cause 5) , rather than causing a bus hang.

### Environment

- Repo
  - XiangShan commit id: `abd0f867a8`（`kunminghu-v2`）
  - NEMU commit id: bundled `riscv64-nemu-interpreter-so`
- Build & Run
  - Build command: `NOOP_HOME=$(pwd)/difftest NEMU_HOME=$(pwd)/../NEMU make emu -j$(nproc)`
  - Run command : `/***/dut/XiangShan-v2/build-v2/emu   -b 0 -e 0   -i /***/seeds_170_.elf   --diff /***/dut/XiangShan-v2/ready-to-run/riscv64-nemu-interpreter-so`


### To Reproduce

[seed.zip](https://github.com/user-attachments/files/28209423/seed.zip)

### Additional context

_No response_
