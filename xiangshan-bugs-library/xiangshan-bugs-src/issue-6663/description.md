### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue.
- [x] I have searched previous issues and PRs and did not find anything relevant.
- [x] I have reproduced the issue using the latest commit on the default branch.
- [x] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content.

### Branch

kunminghu-v3

### Describe the bug

With MPT elaborated (`HasMptCheck = true` and `HasBitmapCheck = false`; no configuration in `Configs.scala` sets `HasMptCheck`) and `mmpt.MODE` = 1 (Smmpt43), `MptChecker` takes `N` from bit 63 and `XWR[i]` from bits 12+3i:10+3i of a leaf MPTE, which is the layout of Smmtt v0.3.0 to v0.3.6. Since v0.3.7, including v0.4, the version named in #5915's title, `N` is bit 2 and `XWR[i]` is bits 10+3i:8+3i. Leaves written for v0.4 are therefore decoded two bits off: on the core, execute-only pages become readable, read-only pages fault, and page 0's `W` and `R` bits land in what the checker treats as reserved. For example, an S-mode load from a page that a v0.4 leaf makes execute-only returned the page's data (`0x5441524745544c44`), and the `ecall` after it trapped with `mcause` 9; v0.4 requires a load access fault there (`mcause` 5).

In detail, the MPT checker added by 96f3a4d4 ([#5915](https://github.com/OpenXiangShan/XiangShan/pull/5915), `feat(mpt): add mpt v0.4 support for Xiangshan`) decodes a leaf MPTE with `N` at bit 63, the sixteen `XWR` fields in bits 57:10, and bits 9:2 and 62:58 reserved. Every Smmtt tag from v0.3.7 to v0.9.0 (2026-08-19), v0.4 included, has `N` at bit 2 and, in a non-NAPOT leaf, bits 7:3 reserved, `XWR[i]` in bits 10+3i to 8+3i, and bits 63:56 reserved. For example, `0x0092492492492403` is a valid leaf that makes all sixteen pages execute-only under v0.4: every `XWR` field is `100`, so bits 10, 13, ..., 55 are set. The checker takes `XWR[j]` from bits 12+3j to 10+3j, finds `001` in every field, and treats every page as read-only, which is what the same word means in v0.3.6.

This report locates and tests the leaf permission layout. The non-leaf entries these programs use work as table pointers under both layouts: a non-leaf MPTE's PPN starts at bit 10 in both, and every program below whose own leaves are usable either way completed its fetches, loads and stores through the three-level table. `MptData.data` holds both a leaf's permission fields and a non-leaf entry's PPN (`extractPerm` and `getPPN` below read the same bits 57:10), so a decode for v0.4 needs a different slice for each.

The decode:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/09453ff8f44e9d2b526e6149c60e1dc3f08c7192/src/main/scala/xiangshan/cache/mmu/MptChecker.scala#L245-L264
class MptData(implicit p: Parameters) extends XSBundle with MPTCacheParam {
  val data = UInt(perms16Len.W)
  def apply(data: UInt): Unit = this.data := data // zero extended
  def getPPN: UInt = this.data(ppnLen - 1, 0)
  def getAddr(offset: UInt): UInt = Cat(this.getPPN, Cat(offset, 0.U(3.W))) // 2|3 byte = 64bit
  def extractPerm(select: UInt): (UInt) = (this.data >> (select * 3.U))(2, 0) // extract XWR using 4bit offset
  // cal start end and extract
}

class MptEntry(implicit p: Parameters) extends XSBundle with MPTCacheParam {
  val N    = Bool()
  val data = new MptData()
  val L    = Bool()
  val V    = Bool()
  def apply(sMEMResp: UInt): Unit = {
    this.V := (sMEMResp(0) === 1.U)
    this.L := (sMEMResp(1) === 1.U)
    this.N := (sMEMResp(63) === 1.U)
    this.data.apply(sMEMResp(57, 10)) // xiangshan only support 48bit PA, so PPN only needs 36
  }
```

The reserved-bit checks, which feed `entryError`:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/09453ff8f44e9d2b526e6149c60e1dc3f08c7192/src/main/scala/xiangshan/cache/mmu/MptChecker.scala#L1130-L1132
  val rsvZeroError0 = RegEnable(mem.resp.bits(9, 2).orR, false.B, memRespAccepted)
  // max 3 level or gate on top of mem.resp，NON ZERO error of mtpe
  val rsvZeroError1 = RegEnable(mem.resp.bits(62, 58).orR, false.B, memRespAccepted)
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/09453ff8f44e9d2b526e6149c60e1dc3f08c7192/src/main/scala/xiangshan/cache/mmu/MptChecker.scala#L1158
  val entryError = mpteInvalid || rsvZeroError0 || rsvZeroError1 || rsvZeroError2 || ((!isLeafMpte) && level === 1.U)
```

From the same code, and not run here: a v0.4 NAPOT leaf has `N` = 1 in bit 2, inside the bits 9:2 that `rsvZeroError0` checks, so the walker flags every v0.4 NAPOT leaf as an entry error.

On the assembled core with MPT enabled, S-mode and U-mode accesses through v0.4 leaves get the outcomes below. In each of the first six rows the target leaf gives one `XWR` value to all sixteen pages of its range. Each entry is the outcome v0.4 requires, then the outcome the core produced; it holds for S and U mode and for pages 0 and 15 of the range unless it names a page. Bold marks a difference.

| Target leaf: `XWR` of its pages | Leaf word | Fetch (v0.4 → core) | Load (v0.4 → core) | Store (v0.4 → core) |
|---|---|---|---|---|
| `000`, no access | `0x0000000000000003` | fault → fault | fault → fault | fault → fault |
| `001`, read-only | `0x0024924924924903` | fault → fault | allowed → **fault** | fault → fault |
| `011`, read-write | `0x006db6db6db6db03` | fault → fault | allowed → **fault** | allowed → **fault** |
| `100`, execute-only | `0x0092492492492403` | allowed → **fault** | fault → **allowed** | fault → fault |
| `101`, read-execute | `0x00b6db6db6db6d03` | allowed → **fault** | allowed → **fault** | fault → **allowed** (page 0), fault (page 15) |
| `111`, read-write-execute | `0x00ffffffffffff03` | allowed → **fault** | allowed → **fault** | allowed → allowed (page 0), **fault** (page 15) |
| mixed: page 0 `100`, page 1 `011`, pages 2 to 15 `001`; page 0 accessed | `0x0024924924925c03` | allowed → allowed | fault → **allowed** | fault → **allowed** |

The table covers 78 accesses: 6 uniform leaves, each from S and U mode on pages 0 and 15 with a fetch, a load and a store (72), and the mixed leaf's page 0 from S and U mode with the same three (6). 44 of the 78 differ from what v0.4 requires. 10 of them are allowed where v0.4 requires an access fault: loads from execute-only pages (S and U, pages 0 and 15), stores to a read-execute page (S and U, page 0), and loads and stores to page 0 of the mixed leaf, which is execute-only there (S and U, page 0). With v0.4 leaves that make every code page `101` and every data page `011`, all 6 such programs (a fetch, a load and a store to page 0 of a read-write-execute target, each from S and from U mode) faulted on their first S-mode or U-mode instruction fetch (`mcause` 1). Written in the checker's own layout (`XWR[i]` in bits 12+3i to 10+3i) instead, the same permissions gave the required outcome in all 66 accesses: every row except `000`, whose word is the same in both layouts (5 × 12 + 6).

Every v0.4 word above that gives page 0 `R` (`001`, `011`, `101` and `111`) sets bit 8, and `011` and `111` also set bit 9, both in the range the checker treats as reserved; every fetch and load through these leaves faulted. As the checker reads them, page 0 of `101` and `111` is `011` and `111`, which allow a store, and those stores completed; page 0 of `001` and `011` is `010` and `110`, which do not. A reserved bit in a leaf MPTE does not stop a store, in the checker's own layout too; we report that in #6662. 2 of the 10 allowed accesses above, the stores to page 0 of the read-execute leaf, also depend on it: if the reserved bit stopped stores, they would fault, as v0.4 requires.

<details>
<summary>All 78 accesses, one program each</summary>

Evidence word is word 35 for a fetch, 31 for a load and 32 for a store, as in step 4 under "To Reproduce".

| Leaf word | Mode | Page | Access | v0.4 | Core | `mcause` | Evidence word |
|---|---|---|---|---|---|---|---|
| `0x0000000000000003` | S | 0 | fetch | fault | fault | 1 | word 35 = `0x0000000000000000` |
| `0x0000000000000003` | S | 0 | load | fault | fault | 5 | word 31 = `0x0000000000000000` |
| `0x0000000000000003` | S | 0 | store | fault | fault | 7 | word 32 = `0x2222222222222222` |
| `0x0000000000000003` | S | 15 | fetch | fault | fault | 1 | word 35 = `0x0000000000000000` |
| `0x0000000000000003` | S | 15 | load | fault | fault | 5 | word 31 = `0x0000000000000000` |
| `0x0000000000000003` | S | 15 | store | fault | fault | 7 | word 32 = `0x2222222222222222` |
| `0x0000000000000003` | U | 0 | fetch | fault | fault | 1 | word 35 = `0x0000000000000000` |
| `0x0000000000000003` | U | 0 | load | fault | fault | 5 | word 31 = `0x0000000000000000` |
| `0x0000000000000003` | U | 0 | store | fault | fault | 7 | word 32 = `0x2222222222222222` |
| `0x0000000000000003` | U | 15 | fetch | fault | fault | 1 | word 35 = `0x0000000000000000` |
| `0x0000000000000003` | U | 15 | load | fault | fault | 5 | word 31 = `0x0000000000000000` |
| `0x0000000000000003` | U | 15 | store | fault | fault | 7 | word 32 = `0x2222222222222222` |
| `0x0024924924924903` | S | 0 | fetch | fault | fault | 1 | word 35 = `0x0000000000000000` |
| `0x0024924924924903` | S | 0 | load | allowed | fault | 5 | word 31 = `0x0000000000000000` |
| `0x0024924924924903` | S | 0 | store | fault | fault | 7 | word 32 = `0x2222222222222222` |
| `0x0024924924924903` | S | 15 | fetch | fault | fault | 1 | word 35 = `0x0000000000000000` |
| `0x0024924924924903` | S | 15 | load | allowed | fault | 5 | word 31 = `0x0000000000000000` |
| `0x0024924924924903` | S | 15 | store | fault | fault | 7 | word 32 = `0x2222222222222222` |
| `0x0024924924924903` | U | 0 | fetch | fault | fault | 1 | word 35 = `0x0000000000000000` |
| `0x0024924924924903` | U | 0 | load | allowed | fault | 5 | word 31 = `0x0000000000000000` |
| `0x0024924924924903` | U | 0 | store | fault | fault | 7 | word 32 = `0x2222222222222222` |
| `0x0024924924924903` | U | 15 | fetch | fault | fault | 1 | word 35 = `0x0000000000000000` |
| `0x0024924924924903` | U | 15 | load | allowed | fault | 5 | word 31 = `0x0000000000000000` |
| `0x0024924924924903` | U | 15 | store | fault | fault | 7 | word 32 = `0x2222222222222222` |
| `0x006db6db6db6db03` | S | 0 | fetch | fault | fault | 1 | word 35 = `0x0000000000000000` |
| `0x006db6db6db6db03` | S | 0 | load | allowed | fault | 5 | word 31 = `0x0000000000000000` |
| `0x006db6db6db6db03` | S | 0 | store | allowed | fault | 7 | word 32 = `0x2222222222222222` |
| `0x006db6db6db6db03` | S | 15 | fetch | fault | fault | 1 | word 35 = `0x0000000000000000` |
| `0x006db6db6db6db03` | S | 15 | load | allowed | fault | 5 | word 31 = `0x0000000000000000` |
| `0x006db6db6db6db03` | S | 15 | store | allowed | fault | 7 | word 32 = `0x2222222222222222` |
| `0x006db6db6db6db03` | U | 0 | fetch | fault | fault | 1 | word 35 = `0x0000000000000000` |
| `0x006db6db6db6db03` | U | 0 | load | allowed | fault | 5 | word 31 = `0x0000000000000000` |
| `0x006db6db6db6db03` | U | 0 | store | allowed | fault | 7 | word 32 = `0x2222222222222222` |
| `0x006db6db6db6db03` | U | 15 | fetch | fault | fault | 1 | word 35 = `0x0000000000000000` |
| `0x006db6db6db6db03` | U | 15 | load | allowed | fault | 5 | word 31 = `0x0000000000000000` |
| `0x006db6db6db6db03` | U | 15 | store | allowed | fault | 7 | word 32 = `0x2222222222222222` |
| `0x0092492492492403` | S | 0 | fetch | allowed | fault | 1 | word 35 = `0x0000000000000000` |
| `0x0092492492492403` | S | 0 | load | fault | allowed | 9 | word 31 = `0x5441524745544c44` |
| `0x0092492492492403` | S | 0 | store | fault | fault | 7 | word 32 = `0x2222222222222222` |
| `0x0092492492492403` | S | 15 | fetch | allowed | fault | 1 | word 35 = `0x0000000000000000` |
| `0x0092492492492403` | S | 15 | load | fault | allowed | 9 | word 31 = `0x5441524745544c44` |
| `0x0092492492492403` | S | 15 | store | fault | fault | 7 | word 32 = `0x2222222222222222` |
| `0x0092492492492403` | U | 0 | fetch | allowed | fault | 1 | word 35 = `0x0000000000000000` |
| `0x0092492492492403` | U | 0 | load | fault | allowed | 8 | word 31 = `0x5441524745544c44` |
| `0x0092492492492403` | U | 0 | store | fault | fault | 7 | word 32 = `0x2222222222222222` |
| `0x0092492492492403` | U | 15 | fetch | allowed | fault | 1 | word 35 = `0x0000000000000000` |
| `0x0092492492492403` | U | 15 | load | fault | allowed | 8 | word 31 = `0x5441524745544c44` |
| `0x0092492492492403` | U | 15 | store | fault | fault | 7 | word 32 = `0x2222222222222222` |
| `0x00b6db6db6db6d03` | S | 0 | fetch | allowed | fault | 1 | word 35 = `0x0000000000000000` |
| `0x00b6db6db6db6d03` | S | 0 | load | allowed | fault | 5 | word 31 = `0x0000000000000000` |
| `0x00b6db6db6db6d03` | S | 0 | store | fault | allowed | 9 | word 32 = `0x5441524745545354` |
| `0x00b6db6db6db6d03` | S | 15 | fetch | allowed | fault | 1 | word 35 = `0x0000000000000000` |
| `0x00b6db6db6db6d03` | S | 15 | load | allowed | fault | 5 | word 31 = `0x0000000000000000` |
| `0x00b6db6db6db6d03` | S | 15 | store | fault | fault | 7 | word 32 = `0x2222222222222222` |
| `0x00b6db6db6db6d03` | U | 0 | fetch | allowed | fault | 1 | word 35 = `0x0000000000000000` |
| `0x00b6db6db6db6d03` | U | 0 | load | allowed | fault | 5 | word 31 = `0x0000000000000000` |
| `0x00b6db6db6db6d03` | U | 0 | store | fault | allowed | 8 | word 32 = `0x5441524745545354` |
| `0x00b6db6db6db6d03` | U | 15 | fetch | allowed | fault | 1 | word 35 = `0x0000000000000000` |
| `0x00b6db6db6db6d03` | U | 15 | load | allowed | fault | 5 | word 31 = `0x0000000000000000` |
| `0x00b6db6db6db6d03` | U | 15 | store | fault | fault | 7 | word 32 = `0x2222222222222222` |
| `0x00ffffffffffff03` | S | 0 | fetch | allowed | fault | 1 | word 35 = `0x0000000000000000` |
| `0x00ffffffffffff03` | S | 0 | load | allowed | fault | 5 | word 31 = `0x0000000000000000` |
| `0x00ffffffffffff03` | S | 0 | store | allowed | allowed | 9 | word 32 = `0x5441524745545354` |
| `0x00ffffffffffff03` | S | 15 | fetch | allowed | fault | 1 | word 35 = `0x0000000000000000` |
| `0x00ffffffffffff03` | S | 15 | load | allowed | fault | 5 | word 31 = `0x0000000000000000` |
| `0x00ffffffffffff03` | S | 15 | store | allowed | fault | 7 | word 32 = `0x2222222222222222` |
| `0x00ffffffffffff03` | U | 0 | fetch | allowed | fault | 1 | word 35 = `0x0000000000000000` |
| `0x00ffffffffffff03` | U | 0 | load | allowed | fault | 5 | word 31 = `0x0000000000000000` |
| `0x00ffffffffffff03` | U | 0 | store | allowed | allowed | 8 | word 32 = `0x5441524745545354` |
| `0x00ffffffffffff03` | U | 15 | fetch | allowed | fault | 1 | word 35 = `0x0000000000000000` |
| `0x00ffffffffffff03` | U | 15 | load | allowed | fault | 5 | word 31 = `0x0000000000000000` |
| `0x00ffffffffffff03` | U | 15 | store | allowed | fault | 7 | word 32 = `0x2222222222222222` |
| `0x0024924924925c03` | S | 0 | fetch | allowed | allowed | 9 | word 35 = `0x0000000046455443` |
| `0x0024924924925c03` | S | 0 | load | fault | allowed | 9 | word 31 = `0x5441524745544c44` |
| `0x0024924924925c03` | S | 0 | store | fault | allowed | 9 | word 32 = `0x5441524745545354` |
| `0x0024924924925c03` | U | 0 | fetch | allowed | allowed | 8 | word 35 = `0x0000000046455443` |
| `0x0024924924925c03` | U | 0 | load | fault | allowed | 8 | word 31 = `0x5441524745544c44` |
| `0x0024924924925c03` | U | 0 | store | fault | allowed | 8 | word 32 = `0x5441524745545354` |

</details>

### Expected behavior

The Smmpt43 non-NAPOT leaf MPTE in Smmtt v0.4:

```text
// https://github.com/riscv/riscv-smmtt/blob/58e864868ca4d6edf735f31b5bfc5ca622140bdf/chapter4.adoc#L293-L318
[title="Smmpt43 non-NAPOT leaf `MPTE` entry", id="rv64-mpte-leaf"]
[wavedrom, ,svg]
....
{reg: [
  {bits:   1, name: 'V'},
  {bits:   1, name: 'L=1'},
  {bits:   1, name: 'N=0'},
  {bits:   5, name: 'Reserved'},
  {bits:   3, name: 'XWR[0]'},
  {bits:   3, name: 'XWR[1]'},
  {bits:   3, name: 'XWR[2]'},
  {bits:   3, name: 'XWR[3]'},
  {bits:   3, name: 'XWR[4]'},
  {bits:   3, name: 'XWR[5]'},
  {bits:   3, name: 'XWR[6]'},
  {bits:   3, name: 'XWR[7]'},
  {bits:   3, name: 'XWR[8]'},
  {bits:   3, name: 'XWR[9]'},
  {bits:   3, name: 'XWR[10]'},
  {bits:   3, name: 'XWR[11]'},
  {bits:   3, name: 'XWR[12]'},
  {bits:   3, name: 'XWR[13]'},
  {bits:   3, name: 'XWR[14]'},
  {bits:   3, name: 'XWR[15]'},
  {bits:   8, name: 'Reserved'},
], config:{lanes: 4, hspace:1024}}
```

and the meaning of each `XWR` value:

```text
// https://github.com/riscv/riscv-smmtt/blob/58e864868ca4d6edf735f31b5bfc5ca622140bdf/chapter4.adoc#L159-L172
[[Smmpt-xwr-encoding]]
.Encoding of `MPTE` X/W/R permission tuple
[%autowidth,float="center",align="center",cols="^,^,^,<",options="header"]
|===
| X | W | R | Meaning
| 0 | 0 | 0 | No access.
| 0 | 0 | 1 | Read-only page.
| 0 | 1 | 0 | _Reserved for future use._
| 0 | 1 | 1 | Read-write page.
| 1 | 0 | 0 | Execute-only page.
| 1 | 0 | 1 | Read-execute page.
| 1 | 1 | 0 | _Reserved for future use._
| 1 | 1 | 1 | Read-write-execute page.
|===
```

The checker should take `N` from bit 2 and, for a non-NAPOT leaf, take `XWR[i]` from bits 10+3i to 8+3i and treat bits 7:3 and 63:56 as reserved. Each access in the table above should then get the first outcome of its entry.

#5915's title names v0.4. If an earlier draft is the intended version instead, the checker matches v0.3.0 to v0.3.6, and the leaves software writes for v0.4 through v0.9.0 will not be read as written. Is v0.4 the intended target?

### Environment

- Hardware
  - CPU: `AMD EPYC 9534 64-Core Processor`.
  - Memory (GB): 64, shared by up to 42 emulator processes on 42 cores.
- Software
  - Operating system: `Ubuntu 24.04.4 LTS`, in the container the emulator ran in.
  - gcc version: `x86_64-conda-linux-gnu-c++ (conda-forge gcc 15.2.0-19) 15.2.0`, which built the emulator.
  - java version: 17.0.19 (Temurin).
  - mill version: 0.12.17, with `firtool` 1.149.0 and `Verilator 5.048 2026-04-26 rev conda-forge build 0`.
- Repo
  - XiangShan commit id: `09453ff8f44e9d2b526e6149c60e1dc3f08c7192`, the head of `kunminghu-v3`, with one file added: the configuration under "To Reproduce". The emulator prints `Core  0's Commit SHA is: 09453ff8f4, dirty: 0`.
  - NEMU commit id (if difftest failed with NEMU): difftest was not used; every run passed `--no-diff`. The `difftest` submodule is at `4c64fe86b17e0564232b52bb3800d9b15c45a3a0`.
- Build & Run
  - Build command: `SIM_VFLAGS="--output-split 1000 --output-split-cfuncs 1000" make emu CONFIG=P4Q3RG8SmmptFullCoreConfig NUM_CORES=1 OPT_FAST="-O3 -fno-tree-slp-vectorize"`.
  - Run command (if applicable): `./build/emu -i execute_only_load_s_page0.elf --no-diff --seed 1 --max-cycles 1000000 --ram-size 256MB`.

### To Reproduce

MPT is not elaborated by default:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/09453ff8f44e9d2b526e6149c60e1dc3f08c7192/src/main/scala/xiangshan/Parameters.scala#L56-L57
  HasMptCheck: Boolean = false, //enable mpt
  HasBitmapCheck: Boolean = true,
```

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/09453ff8f44e9d2b526e6149c60e1dc3f08c7192/src/main/scala/xiangshan/Parameters.scala#L666
  def HasMptCheck = coreParams.HasMptCheck && !coreParams.HasBitmapCheck
```

No configuration in `src/main/scala/top/Configs.scala` sets `HasMptCheck`. The runs used this configuration, added as `src/main/scala/top/P4Q3RG8SmmptFullCoreConfig.scala` with its comment omitted. It changes only those two fields, over `MinimalConfig`:

```scala
package top

import org.chipsalliance.cde.config.Config
import xiangshan.XSTileKey

class P4Q3RG8SmmptFullCoreConfig(n: Int = 1) extends Config(
  new Config((site, here, up) => {
    case XSTileKey => up(XSTileKey).map { original =>
      val selected = original.copy(HasMptCheck = true, HasBitmapCheck = false)
      println(
        s"P4_Q3R_G8_CONFIG_CLOSURE base=MinimalConfig changed=HasBitmapCheck,HasMptCheck " +
          s"has_mpt_check=${selected.HasMptCheck} has_bitmap_check=${selected.HasBitmapCheck}"
      )
      selected
    }
  }) ++ new MinimalConfig(n)
)
```

1. Build the emulator. Leave `YAML_CONFIG` unset: `Default.yml` sets `EnableBitmapCheck: true`, which turns MPT off. `make emu` builds with `--enable-difftest` by default; the runs turn the comparison off with `--no-diff` (difftest is discussed under "Additional context").

```sh
SIM_VFLAGS="--output-split 1000 --output-split-cfuncs 1000" make emu CONFIG=P4Q3RG8SmmptFullCoreConfig NUM_CORES=1 OPT_FAST="-O3 -fno-tree-slp-vectorize"
```

2. Build a program. Each access in the table above is one bare-metal program. In M mode it opens PMP to all of memory, points `mmpt` at a three-level Smmpt43 table, executes `MFENCE` once, and drops to S or U mode. There it loads from and stores to its own data page, then makes one access to the target page (a load, a store, or a jump into the page) and executes `ecall`. The M-mode trap handler records the trap that ended the access and prints one line. Every program has the same text; one small file per program supplies its inputs and its tables. The four files at the end of this section are self-contained. Our own scripts generated them; for this report their names and comments were changed and unused definitions dropped, and we checked that the ELF they build has the same entry point, program headers and `objcopy -O binary` image as the one we ran. Put them in `src/`, create `obj/`, and compile:

```sh
riscv64-unknown-elf-gcc -march=rv64i_zicsr_zifencei -mabi=lp64 -I src -c -o obj/mpt_guest.o src/mpt_guest.S
riscv64-unknown-elf-gcc -march=rv64i_zicsr_zifencei -mabi=lp64 -I src -c -o obj/execute_only_load_s_page0.o src/execute_only_load_s_page0.S
riscv64-unknown-elf-gcc -march=rv64i_zicsr_zifencei -mabi=lp64 -nostdlib -nostartfiles -static -Wl,--build-id=none,--no-relax -T src/mpt_guest.ld -o execute_only_load_s_page0.elf obj/mpt_guest.o obj/execute_only_load_s_page0.o
```

We used `riscv64-unknown-elf-gcc (13.2.0-11ubuntu1+12) 13.2.0` with GNU Binutils 2.42. The ELF records its source file names, so compare its loaded content instead: `riscv64-unknown-elf-objcopy -O binary execute_only_load_s_page0.elf image.bin` gives sha256 `ab3fc0f7d91075af1af93fe64385313c8622531fdaaa67e57645568f67f83c24`, and its `.text` section, which is the same in every program, has sha256 `d5f26311679f64b8243bc650acbc98bcd265eefcf1573fc61bbb94b655122260`.

3. Run it:

```sh
./build/emu -i execute_only_load_s_page0.elf --no-diff --seed 1 --max-cycles 1000000 --ram-size 256MB
```

4. Read the line. The line starts with the tag `G8R`, then 37 sixteen-digit hex words, the last the XOR of the others. Word 24 is `mcause` of the trap that ended the access: 9 or 8 (`ecall` from S or U mode) means the access completed, and 1, 5 or 7 is an instruction, load or store access fault. Word 25 is `mepc`. Word 31 is the value the target load returned, `0x5441524745544c44` when it read the page; word 32 is what M mode reads back from the store's slot, `0x5441524745545354` when the store wrote it; word 35 is `0x0000000046455443` when a jump into the page ran its code. This is the line for the program above:

```text
G8R 4738525245433031 0000000000000012 0000000000000000 0000000000000001 0000000000000001 1000000000080100 1000000000080100 000000000000001f 000000000000001f ffffffffffffffff 00003fffffffffff 0000000000000000 0000000000000000 0000000000000000 0000000a00000800 0000000000000000 0000000000001444 0000000000000000 0000000000000001 0000000000000000 0000000000000000 0000000000000000 0000000000000006 0000000000000009 0000000080000220 0000000000000000 0000000000000007 5343524154434831 5354525f53435231 0000000080040000 5441524745544c44 0000000000000000 0000000000000000 0000000000000000 0000000000000000 0000000000000001 ec91c0010713780b
```

Word 24 is 9 and word 31 is `0x5441524745544c44`: the S-mode load from an execute-only page completed and returned the page's data.

5. For another row, change the program file's target leaf (the `.dword` after `.org 0x2020`) to the row's leaf word, `SC_PRIV` (the 4th input word) to 1 for S mode or 0 for U mode, `SC_ACCESS` (the 5th) to 0 for a fetch, 1 for a load or 2 for a store, and `SC_TARGET` (the 10th) to `0x80040000` for page 0 or `0x8004f000` for page 15. Input words 2 and 3 are labels the program only copies into its line, and words 13 to 15 are not read. The leaf word of the mixed-leaf row is in the table; the code and data leaves (`.org 0x2000` and `.org 0x2010`) stay as they are.

<details>
<summary><code>src/mpt_guest.S</code>, the program text</summary>

```asm
/* The program every row of the report runs; a second, per-program file supplies .scenario and .mpt.
 *
 * Constants come from mpt_constants.h. SC_* are the offsets of the per-program scenario words (the
 * inputs M mode reads from .scenario); RC_* are the offsets of the record words the program prints.
 * The per-program file also holds the three static Smmpt43 tables (root, level 1, level 0), so no MPTE
 * is written by a store while the program runs.
 *
 * M-mode setup: mtvec, mie/mideleg/medeleg = 0, mstatus = 0 (MXR = MPRV = SUM = 0),
 * satp = 0; PMP pmpaddr0, pmpaddr1, pmpcfg0 written and read back, then
 * SFENCE.VMA x0, x0; mmpt written and read back; when the program uses MPT, MFENCE (F0_WORD,
 * 0xbe000073) once, with the handler resuming at mepc + 4 and recording mcause and mtval; the
 * fixed-context readbacks; then either the privilege drop to S or U (MPP, mret) or,
 * for an M-mode program, the target access in M-mode.
 *
 * S/U path: the preamble (this code executing from the code range is the fetch; a load
 * from and a store to the data page), each completion marked in s9, then the
 * target access: a load of the target sentinel, a store of the target pattern, or a
 * jump into the target page's marker code; each ends in ECALL.
 *
 * The M-mode handler takes every trap. At the MFENCE stage it records the trap and
 * resumes; otherwise it records the final trap, reads back the data page's slot and (for
 * a store program whose final trap came at the target stage) the target slot, prints
 * one checksummed record line and terminates normally.
 *
 * Registers: s0 scenario base, s1 access, s2 target, s3 run-preamble flag, s4
 * data page base, s5 privilege, s6 trap count, s7 target load value, s8 preamble load
 * value, s9 preamble marks, s10 stage, s11 record base, t6 fetch mark.
 */
#include "mpt_constants.h"

/* The two nops are padding. */
#define MMPT_WRITE(r) csrw MMPT_CSR, r; nop; nop
#define MMPT_READ(r) csrr r, MMPT_CSR; nop; nop

.option norvc
.option norelax

.section .text.init,"ax",@progbits
.globl _start
_start:
    la t0, trap
    csrw mtvec, t0
    csrw mie, zero
    csrw mideleg, zero
    csrw medeleg, zero
    csrw mstatus, zero
    csrw satp, zero
    li s11, RECORD_BASE
    li s10, STAGE_SETUP
    li s6, 0
    li s7, 0
    li s8, 0
    li s9, 0
    li t6, 0
    li s0, SCENARIO_BASE
    li s4, SCRATCH_BASE
    li s1, ACC_NONE
    li s2, 0
    li s3, 0
    li s5, PRIV_M
    ld t0, SC_MAGIC(s0)
    li t1, SCENARIO_MAGIC
    bne t0, t1, emit
    li t0, RECORD_MAGIC
    sd t0, RC_MAGIC(s11)
    ld t0, SC_ORDINAL(s0)
    sd t0, RC_ORDINAL(s11)
    ld t0, SC_ARM(s0)
    sd t0, RC_ARM(s11)
    ld s5, SC_PRIV(s0)
    sd s5, RC_PRIV(s11)
    ld s1, SC_ACCESS(s0)
    sd s1, RC_ACCESS(s11)
    ld s2, SC_TARGET(s0)
    sd s2, RC_TARGET(s11)
    ld s3, SC_RUN_PREAMBLE(s0)

    /* PMP: address registers, then the configuration; read back; SFENCE.VMA x0, x0. */
    ld t0, SC_PMPADDR0(s0)
    sd t0, RC_PMPADDR0_W(s11)
    csrw pmpaddr0, t0
    ld t0, SC_PMPADDR1(s0)
    sd t0, RC_PMPADDR1_W(s11)
    csrw pmpaddr1, t0
    ld t0, SC_PMPCFG0(s0)
    sd t0, RC_PMPCFG0_W(s11)
    csrw pmpcfg0, t0
    csrr t0, pmpcfg0
    sd t0, RC_PMPCFG0_R(s11)
    csrr t0, pmpaddr0
    sd t0, RC_PMPADDR0_R(s11)
    csrr t0, pmpaddr1
    sd t0, RC_PMPADDR1_R(s11)
    sfence.vma zero, zero

    /* mmpt: write, then read back. */
    ld t0, SC_MMPT(s0)
    sd t0, RC_MMPT_W(s11)
    MMPT_WRITE(t0)
    MMPT_READ(t0)
    sd t0, RC_MMPT_R(s11)

    /* MFENCE once, when the program uses MPT, after the mmpt write and before the privilege drop. */
    ld t0, SC_RUN_F0(s0)
    sd t0, RC_F0_EXECUTED(s11)
    beqz t0, 1f
    li s10, STAGE_F0
.globl f0_insn
f0_insn:
    .word F0_WORD
    li s10, STAGE_F0_DONE
1:
    csrr t0, satp
    sd t0, RC_SATP_R(s11)
    csrr t0, medeleg
    sd t0, RC_MEDELEG_R(s11)
    csrr t0, mideleg
    sd t0, RC_MIDELEG_R(s11)
    csrr t0, mie
    sd t0, RC_MIE_R(s11)
    li t0, PRIV_M
    beq s5, t0, m_target
    slli t0, s5, 11
    csrw mstatus, t0
    csrr t0, mstatus
    sd t0, RC_MSTATUS_R(s11)
    la t0, su_entry
    csrw mepc, t0
    li s10, STAGE_DROP
    mret

m_target:
    csrr t0, mstatus
    sd t0, RC_MSTATUS_R(s11)
    li s10, STAGE_TARGET
    j target_access

/* ---- S or U from here until a trap ---- */
.globl su_entry
su_entry:
    li s10, STAGE_PREAMBLE
    beqz s3, 2f
    ori s9, s9, 1
    ld s8, 0(s4)
    ori s9, s9, 2
    li t0, SCRATCH_PATTERN
    sd t0, 8(s4)
    ori s9, s9, 4
2:
    li s10, STAGE_TARGET
.globl target_access
target_access:
    li t0, ACC_LOAD
    beq s1, t0, do_load
    li t0, ACC_STORE
    beq s1, t0, do_store
    li t0, ACC_FETCH
    beq s1, t0, do_fetch
.globl target_none_ecall
target_none_ecall:
    ecall
    j dead
do_load:
.globl target_load_insn
target_load_insn:
    ld s7, TARGET_LOAD_OFFSET(s2)
    ecall
    j dead
do_store:
    li t0, TARGET_PATTERN
.globl target_store_insn
target_store_insn:
    sd t0, TARGET_STORE_OFFSET(s2)
    ecall
    j dead
do_fetch:
.globl target_fetch_jump
target_fetch_jump:
    jalr ra, 0(s2)
dead:
    j dead

/* ---- M-mode trap handler ---- */
.balign 64
.globl trap
trap:
    addi s6, s6, 1
    csrr t0, mcause
    csrr t1, mepc
    csrr t2, mtval
    li t3, STAGE_EMIT
    beq s10, t3, emit_failed
    li t3, STAGE_F0
    beq s10, t3, f0_trap
    li t3, STAGE_READBACK
    beq s10, t3, readback_trap
    sd s10, RC_STAGE(s11)
    sd t0, RC_MCAUSE(s11)
    sd t1, RC_MEPC(s11)
    sd t2, RC_MTVAL(s11)
    sd s9, RC_PREAMBLE_MARKS(s11)
    sd s8, RC_PREAMBLE_LOAD(s11)
    sd s7, RC_TARGET_LOAD(s11)
    sd t6, RC_FETCH_MARK_R(s11)
    ld t3, 8(s4)
    sd t3, RC_SCRATCH_READBACK(s11)
    li t3, STAGE_TARGET
    bne s10, t3, emit
    li t3, ACC_STORE
    bne s1, t3, emit
    li s10, STAGE_READBACK
    ld t3, TARGET_STORE_OFFSET(s2)
    sd t3, RC_TARGET_READBACK(s11)
    j emit

readback_trap:
    li t3, 1
    sd t3, RC_READBACK_TRAPPED(s11)
    sd t0, RC_READBACK_MCAUSE(s11)
    j emit

f0_trap:
    li t3, 1
    sd t3, RC_F0_TRAPPED(s11)
    sd t0, RC_F0_MCAUSE(s11)
    sd t2, RC_F0_MTVAL(s11)
    addi t1, t1, 4
    csrw mepc, t1
    mret

/* One line: "G8R" and RECORD_WORDS sixteen-digit hex words, the last the XOR of the others. */
emit:
    li s10, STAGE_EMIT
    sd s6, RC_TRAP_COUNT(s11)
    li t3, 0
    li t4, 0
    li t5, RECORD_WORDS - 1
3:  slli a1, t4, 3
    add a1, a1, s11
    ld a1, 0(a1)
    xor t3, t3, a1
    addi t4, t4, 1
    bltu t4, t5, 3b
    sd t3, RC_CHECKSUM(s11)
    li a0, 71
    jal ra, putchar
    li a0, 56
    jal ra, putchar
    li a0, 82
    jal ra, putchar
    li t4, 0
4:  li a0, 32
    jal ra, putchar
    slli a1, t4, 3
    add a1, a1, s11
    ld a2, 0(a1)
    li a3, 60
5:  srl a0, a2, a3
    andi a0, a0, 15
    li a4, 10
    bltu a0, a4, 6f
    addi a0, a0, 87
    j 7f
6:  addi a0, a0, 48
7:  jal ra, putchar
    addi a3, a3, -4
    bgez a3, 5b
    addi t4, t4, 1
    li t5, RECORD_WORDS
    bltu t4, t5, 4b
    li a0, 10
    jal ra, putchar
    li a0, 0
    j finish

emit_failed:
    li a0, 1
    j finish

/* XiangShan's UARTLite write-only transmit register; putchar uses a5. */
putchar:
    li a5, UART_TX
    sw a0, 0(a5)
    ret
/* XiangShan's trap instruction: a0 = 0 is HIT GOOD TRAP. */
finish:
    .word 0x0000006b
    j finish

/* ---- the target range's page 0 and page 15: identical marker code, sentinel and slot ---- */
.macro TARGET_PAGE section
.section \section,"ax",@progbits
    li t6, FETCH_MARK
    ecall
12: j 12b
    .org TARGET_LOAD_OFFSET
    .dword TARGET_SENTINEL
    .dword TARGET_SLOT_INITIAL
.endm
TARGET_PAGE .target_lo
TARGET_PAGE .target_hi

.section .scratch,"aw",@progbits
    .dword SCRATCH_SENTINEL
    .dword SCRATCH_SLOT_INITIAL

.section .record,"aw",@progbits
    .zero 0x400
mmpt_emulated:
    .dword 0
```

</details>

<details>
<summary><code>src/mpt_constants.h</code></summary>

```c
/* Constants for mpt_guest.S. */
#define F0_WORD 0xbe000073
#define FETCH_MARK 0x46455443
#define MMPT_CSR 0x382
#define RECORD_BASE 0x80030000
#define RECORD_MAGIC 0x4738525245433031
#define SCENARIO_BASE 0x80010000
#define SCENARIO_MAGIC 0x4738525343454e31
#define SCRATCH_BASE 0x80021000
#define SCRATCH_PATTERN 0x5354525f53435231
#define SCRATCH_SENTINEL 0x5343524154434831
#define SCRATCH_SLOT_INITIAL 0x1111111111111111
#define TARGET_LOAD_OFFSET 0x400
#define TARGET_PATTERN 0x5441524745545354
#define TARGET_SENTINEL 0x5441524745544c44
#define TARGET_SLOT_INITIAL 0x2222222222222222
#define TARGET_STORE_OFFSET 0x408
#define UART_TX 0x40600004
#define PRIV_M 3
#define ACC_FETCH 0
#define ACC_LOAD 1
#define ACC_STORE 2
#define ACC_NONE 3
#define STAGE_SETUP 1
#define STAGE_F0 2
#define STAGE_F0_DONE 3
#define STAGE_DROP 4
#define STAGE_PREAMBLE 5
#define STAGE_TARGET 6
#define STAGE_READBACK 7
#define STAGE_EMIT 8
#define SC_MAGIC 0
#define SC_ORDINAL 8
#define SC_ARM 16
#define SC_PRIV 24
#define SC_ACCESS 32
#define SC_MMPT 40
#define SC_PMPCFG0 48
#define SC_PMPADDR0 56
#define SC_PMPADDR1 64
#define SC_TARGET 72
#define SC_RUN_F0 80
#define SC_RUN_PREAMBLE 88
#define RC_MAGIC 0
#define RC_ORDINAL 8
#define RC_ARM 16
#define RC_PRIV 24
#define RC_ACCESS 32
#define RC_MMPT_W 40
#define RC_MMPT_R 48
#define RC_PMPCFG0_W 56
#define RC_PMPCFG0_R 64
#define RC_PMPADDR0_W 72
#define RC_PMPADDR0_R 80
#define RC_PMPADDR1_W 88
#define RC_PMPADDR1_R 96
#define RC_SATP_R 104
#define RC_MSTATUS_R 112
#define RC_MEDELEG_R 120
#define RC_MIDELEG_R 128
#define RC_MIE_R 136
#define RC_F0_EXECUTED 144
#define RC_F0_TRAPPED 152
#define RC_F0_MCAUSE 160
#define RC_F0_MTVAL 168
#define RC_STAGE 176
#define RC_MCAUSE 184
#define RC_MEPC 192
#define RC_MTVAL 200
#define RC_PREAMBLE_MARKS 208
#define RC_PREAMBLE_LOAD 216
#define RC_SCRATCH_READBACK 224
#define RC_TARGET 232
#define RC_TARGET_LOAD 240
#define RC_TARGET_READBACK 248
#define RC_READBACK_TRAPPED 256
#define RC_READBACK_MCAUSE 264
#define RC_FETCH_MARK_R 272
#define RC_TRAP_COUNT 280
#define RC_CHECKSUM 288
#define RECORD_WORDS 37
```

</details>

<details>
<summary><code>src/mpt_guest.ld</code></summary>

```text
/* Linker script for mpt_guest.S. */
OUTPUT_ARCH(riscv)
ENTRY(_start)
SECTIONS
{
    . = 0x80000000;
    .text : { *(.text.init) *(.text*) }
    ASSERT(. <= 0x80001000, "code escapes page 0 of its range")
    . = 0x80010000;
    .scenario : { KEEP(*(.scenario)) }
    ASSERT(SIZEOF(.scenario) == 120, "scenario word count differs")
    . = 0x80021000;
    .scratch : { *(.scratch) }
    . = 0x80030000;
    .record : { *(.record) }
    . = 0x80040000;
    .target_lo : { *(.target_lo) }
    . = 0x8004f000;
    .target_hi : { *(.target_hi) }
    . = 0x80100000;
    .mpt : { KEEP(*(.mpt)) }
    ASSERT(SIZEOF(.mpt) == 0x3000, "the three tables are not three pages")
    /DISCARD/ : { *(.comment) *(.note*) *(.eh_frame*) *(.riscv.attributes) }
}
```

</details>

<details>
<summary><code>src/execute_only_load_s_page0.S</code>, the inputs and tables of the program above</summary>

```asm
/* Inputs and tables for an S-mode load from page 0 of an execute-only v0.4 leaf. */
.section .scenario,"a",@progbits
.balign 8
    .dword 0x4738525343454e31
    .dword 0x0000000000000012
    .dword 0x0000000000000000
    .dword 0x0000000000000001
    .dword 0x0000000000000001
    .dword 0x1000000000080100
    .dword 0x000000000000001f
    .dword 0xffffffffffffffff
    .dword 0x0000000000000000
    .dword 0x0000000080040000
    .dword 0x0000000000000001
    .dword 0x0000000000000001
    .dword 0x0000000000000004
    .dword 0x0000000000000000
    .dword 0x0000000000000000
.section .mpt,"a",@progbits
.balign 4096
    .org 0x0
    .dword 0x0000000020040401
    .org 0x1200
    .dword 0x0000000020040801
    .org 0x2000
    .dword 0x0024924924925c03
    .org 0x2010
    .dword 0x0024924924927c03
    .org 0x2020
    .dword 0x0092492492492403
    .org 0x3000
```

</details>

### Additional context

**Difftest.** The runs pass `--no-diff`. NEMU's `riscv64-xs-ref_defconfig` leaves MPT out (`# CONFIG_RV_MPT_CHECK is not set`). With `riscv64-xs-ref_mpt_defconfig`, which sets `CONFIG_RV_MPT_CHECK=y`, NEMU's MPT walk reads a leaf in the same layout as the checker: bits 9:2 reserved and the permission fields from bit 10. A change to the checker alone would therefore leave the two reading the same leaf differently under that configuration.

```c
// https://github.com/OpenXiangShan/NEMU/blob/42dd82b6a7a7e12de4a0ced28834ada36b70f274/src/isa/riscv64/system/mmu.c#L1489-L1491
    bool res = ((mpte >> 2) & 0xff)==0 && ((mpte >> 62) & 0x1)==0;
    bool res_l= ((mpte >> 58) & 0x1f)==0;
    uint64_t data = ((mpte << 1) >> 1) >>10;
```

```c
// https://github.com/OpenXiangShan/NEMU/blob/42dd82b6a7a7e12de4a0ced28834ada36b70f274/src/isa/riscv64/system/mmu.c#L1509
      mpt_perm = (data>>(range_off[level]*3)) & 0x7;
```

**`MFENCE`.** Smmtt v0.4 calls the fence `MFENCE.SPA` and v0.9.0 calls it `MFENCE.PA`; neither gives its `func7` value. The program uses `0xbe000073`, the encoding XiangShan decodes as `MFENCE` at this commit:

```scala
// https://github.com/OpenXiangShan/XiangShan/blob/09453ff8f44e9d2b526e6149c60e1dc3f08c7192/src/main/scala/xiangshan/backend/decode/isa/Instructions.scala#L375
  def MFENCE             :BitPat = BitPat("b1011111??????????000000001110011") // HasMptCheck
```

In the example line, word 19 is 1 (the program executes `MFENCE`) and word 20 is 0 (it did not trap).

riscv/riscv-opcodes#389, an open pull request, proposes `mfence.pa` with `0b0011001` in bits 31:25, where XiangShan decodes `0b1011111`; the two patterns are otherwise the same. Which encoding is right is outside this report.

**Build.** The emulator the results above come from was built with the RTL step of `make emu` split into `mill` and `firtool` runs with the Makefile's options, and then `make emu`. We also ran the single command under "To Reproduce" on a checkout of this commit holding only the configuration file and no build output, adding `-j16` and, since our cluster is offline, local toolchain and library paths. All 159 programs of our full run, which include every program in this report, gave the same outcomes on that emulator. `OPT_FAST` is set because difftest's default at this commit, `-O3 -fno-slp-vectorize` (from OpenXiangShan/difftest#968), is a clang option that GCC rejects; `-fno-tree-slp-vectorize` is GCC's option for the same pass. `SIM_VFLAGS` only splits Verilator's output into more files.

**The program's own pages.** Since the checker reads a v0.4 leaf differently, the program's code and data leaves are chosen so that the pages it uses work under both readings, and only the target page's outcome depends on the layout. The code leaf, `0x0024924924925c03`, is the mixed leaf of the table's last row: page 0, which holds all the code (the linker script checks this), is `100` (execute-only) in v0.4 and `111` as the checker reads it. The data leaf is `0x0024924924927c03`, and the data page is page 1 of its range: `111` in v0.4 and `011` as the checker reads it. In both words bits 9:2 and 62:58 are zero.
