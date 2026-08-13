### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

MainBTB entry/counter arrays and MainBTB replacement metadata are indexed with different PC bit fields.

  In the current implementation, MainBTB entry/counter SRAM access uses `getSetIndex(pc)`, while MainBTB replacement state access uses `getReplacerSetIndex(pc)`.

  Affected files:

  - `src/main/scala/xiangshan/frontend/bpu/mbtb/Helpers.scala`
  - `src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbAlignBank.scala`
  - `src/main/scala/xiangshan/frontend/bpu/mbtb/MainBtbReplacer.scala`

  With the tested default parameters, the index fields are:

  ```text
  setIdx          = addr[15:8]
  replacerSetIdx  = addr[13:6]
 ```
  Therefore, two branch PCs can map to different physical MainBTB sets while sharing the same replacement-state row. For example, the PoC constructs:
```
  site_A1  addr=0x80010000  setIdx=0x00  replacerSetIdx=0x00
  site_B1  addr=0x80014000  setIdx=0x40  replacerSetIdx=0x00
```
  These are different MainBTB physical sets, but they access the same replacer metadata row. This breaks the expected per-set replacement invariant: replacement metadata for one physical set should not be updated by branch PCs from another physical set.

### Expected behavior

MainBTB replacement state should be indexed in the same domain as the MainBTB entry/counter arrays, or otherwise have a unique replacement-state row for every physical set it describes. Two branch PCs with different physical setIdx values should not share one replacement-state row.

### Environment

- Software
    - gcc version: `gcc (Ubuntu 13.3.0-6ubuntu2~24.04.1) 13.3.0`
    - clang version: `Ubuntu clang version 18.1.3 (1ubuntu1)`
    - java version: `openjdk version "11.0.30" 2026-01-20`
    - RISC-V GCC version used for the PoC workload: `riscv64-unknown-elf-gcc (g1b306039ac) 15.1.0`
  - Repo
    - XiangShan commit id: `3931c51` as reported by the stock emu log: `Core 0's Commit SHA is: f65a4e6c3f, dirty: 0`


### To Reproduce

The attachment [xiangshan-mainbtb-replacer-alias-poc.zip](https://github.com/user-attachments/files/27982659/xiangshan-mainbtb-replacer-alias-poc.zip) contains:

1. `branch_alias_sequence.S`: Minimal RISC-V assembly workload used by the main PoC. It contains direct branch/jump sites site_A1..site_A4 and site_B1..site_B4.
2. `poc.sh`: Main reproduction script. It builds the assembly workload, places branch sites at selected addresses with a linker script, computes setIdx / replacerSetIdx from the linked ELF symbol table, runs stock XiangShan emu, and writes the final verdict.
```
  out/poc.bin
  out/poc.elf
  out/poc.ld
  out/poc.txt
  out/symbols.txt
  out/static_proof.txt
  out/verdict.txt
```
Generated artifacts from a successful run. `static_proof.txt` shows the actual linked addresses and computed MainBTB indices. `verdict.txt` contains the runtime summary.
The key result is:
```
  site_A1_setIdx=0x00
  site_B1_setIdx=0x40
  site_A1_replacerSetIdx=0x00
  site_B1_replacerSetIdx=0x00
  cpu_image_loaded=1
  cpu_guest_cycle_seen=1
  cpu_good_trap=1
  cpu_mbtb_allocate_seen=1
```
This shows that site_A1 and site_B1 are in different physical MainBTB sets but share the same replacer row, and that the workload reaches MainBTB train/allocate activity on stock XiangShan emu.

To rerun the main PoC:
```bash
unzip xiangshan-mainbtb-replacer-alias-poc.zip
bash poc.sh
```
The script expects a built XiangShan emulator. By default it looks for:
```
  $XS_HOME/build/emu
  $XS_HOME/build/verilator-compile/emu
```
  or you can specify it explicitly:
```
  EMU=/path/to/xiangshan/emu bash poc.sh
```
The attachment also includes an optional reasoning/model PoC:
```
  exploit_sequence.S
  exploit_poc.sh
  exploit_model.py
  out_exploit/
```
exploit_poc.sh builds two workloads:
```
  alias:
  A* setIdx=0x00, replacerSetIdx=0x00
  B* setIdx=0x40, replacerSetIdx=0x00
  control:
  A* setIdx=0x00, replacerSetIdx=0x00
  B* setIdx=0x41, replacerSetIdx=0x04
```
`exploit_model.py` then models the current LruStateGen behavior and shows that, under the alias layout, B-side touches can affect the later A-side victim choice:
```
  alias_site_A5_victim=site_A1
  control_site_A5_victim=site_A2
  attacker_controls_cross_set_victim=1
```
  This optional part is a white-box reasoning aid. The direct stock-emu evidence in the main PoC is the static index aliasing plus runtime reachability of MainBTB train/allocate logic.

### Additional context

  The relevant code paths appear to use different index domains. `Entry/counter SRAM` writes use the physical set index:
  ```
  private val t1_setIdx = getSetIndex(t1_startPc)
  b.io.writeEntry.req.bits.setIdx := t1_setIdx
  b.io.writeCounter.req.bits.setIdx := t1_setIdx
  ```
  However, the replacement state is accessed with getReplacerSetIndex:
 ```
  replacer.io.victim.setIdx := getReplacerSetIndex(io.t0_startPc)
 ```
  alias:
 ```
  A* setIdx=0x00, replacerSetIdx=0x00
  B* setIdx=0x40, replacerSetIdx=0x00
  A* setIdx=0x00, replacerSetIdx=0x00
  B* setIdx=0x41, replacerSetIdx=0x04
  ```
  The model uses the current LruStateGen semantics: touch moves a way to MRU, victim is the LRU way. Under the alias case, B-side touches update the same replacement row used by A, so later A-side allocation can choose a victim based on B-side activity.
  Model output:
  ```
  alias_site_A5_victim=site_A1
  control_site_A5_victim=site_A2
  attacker_controls_cross_set_victim=1
 ```
  This victim result is a white-box model derived from the current replacement semantics and the static address aliasing. The stock emu portion demonstrates runtime reachability of the MainBTB train/allocate path; direct RTL observation of the exact victim way would require additional tracing of t1_startPc, setIdx, replacerSetIdx, and t1_entryWayMask.
