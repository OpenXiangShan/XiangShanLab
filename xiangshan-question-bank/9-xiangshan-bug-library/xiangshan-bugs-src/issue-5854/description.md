### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and this is not a RISC-V ISA question. 我已经阅读过 RISC-V 指令集手册，这不是一个指令集本身的问题。
- [x] I have read the [XiangShan Documents](https://xiangshan-doc.readthedocs.io/zh_CN/latest). 我已经阅读过香山文档。
- [x] I have searched the previous issues and did not find anything relevant. 我已经搜索过之前的 issue，并没有找到相关的。
- [x] I have reviewed the commit messages from the relevant commit history. 我已经浏览过相关的提交历史和提交信息。
- [x] I have reproduced the incorrect behaviors using the latest commit on the master branch. 我已经使用 master 分支最新的 commit 复现了不正确的行为。

### Branch

kunminghu-v3

### Describe the bug

In L2TLB.scala (function `contiguous_pte_to_merge_ptwResp`, line 757), when assigning the bitmap check result (`cf`) to each of the 8 PTEs packed into the same TLB entry, the code uses the wrong bits to index into the `cfs` vector:

```scala
// L2TLB.scala:738-757
for (i <- 0 until tlbcontiguous) {
  ptw_resp.ppn     := pte_in.getPPN()(ptePPNLen - 1, sectortlbwidth) // line 741: HIGH bits of PPN (PPN[PPNLen-1:3])
  ptw_resp.ppn_low := pte_in.getPPN()(sectortlbwidth - 1, 0)         // line 742: LOW 3 bits = PPN[2:0]
  ...
  ptw_resp.cf := cfs(ptw_resp.ppn(sectortlbwidth - 1, 0))            // line 757: BUG
}
```

The `cfs` vector is an 8-bit result from `BitmapCheck`, where `cfs(k)` is the bitmap result for the page whose **low 3 bits of PPN** equal `k`. Therefore indexing into `cfs` should use `PPN[2:0]`.

However, `ptw_resp.ppn` was already **truncated** at line 741 (it stores `PPN[PPNLen-1:3]`, i.e., the high bits). So `ptw_resp.ppn(sectortlbwidth - 1, 0)` actually retrieves `PPN[5:3]`, **not** `PPN[2:0]`.

Since all 8 contiguous pages share the same `PPN[5:3]`, the bug causes **all 8 PTEs to receive the same `cf` value** — specifically `cfs(PPN[5:3])`.

**Concrete example.** Consider 8 consecutive pages `PPN = 0x80400..0x80407`, with bitmap forbidding only page 5 (`cfs = [0,0,0,0,0,1,0,0]`):

- **Expected:** the entry for `0x80405` receives `cf = cfs[5] = 1` (forbidden); 
- **Actual (buggy):** all 8 entries take `cfs[PPN[5:3]] = cfs[0] = 0`; the TLB caches `0x80405` as accessible.

**Impact:** the bug can either allow access to a forbidden page (security bypass) or deny access to an allowed page , depending on the bitmap pattern of neighboring pages.

**Affected branches:** Introduced by PR #3980 (commit `8882eb68`, 2025-02-21), present on all `kunminghu-v2` / `kunminghu-v3` branches.

### Expected behavior

Each of the 8 PTEs packed into the same TLB entry should receive its own bitmap result, indexed by the low 3 bits of the PPN (`ptw_resp.ppn_low`, which holds `PPN[2:0]`), not by `PPN[5:3]`.
the semantics of cfs are uniquely determined by how it is generated upstream in:
```scala
BitmapCheck.scala:209-213
val ppnPart = req_real_ppn(log2Up(XLEN)-1, log2Up(8))                      // = PPN[5:3]
val selectedBits = bitmapdata(index).asTypeOf(Vec(8, UInt(8.W)))(ppnPart)  // byte selection using PPN[5:3]
for (j <- 0 until tlbcontiguous) {
  entries(enq_ptr).cfs(j) := selectedBits(j)                               // cfs(j) ≡ bitmap bit for the page with PPN[2:0]=j
}
```
By my understanding, Each element cfs(j) is the bitmap result for the page whose PPN[2:0] = j. The only remaining step is the bit-level selection within cfs, which by definition must use PPN[2:0] as the index.

### Environment

Branch: kunminghu-v3

### To Reproduce

provided subsequently

### Additional context

_No response_

### Suggested fix
```scala
// L2TLB.scala:757
// Before
ptw_resp.cf := cfs(ptw_resp.ppn(sectortlbwidth - 1, 0))   // = cfs(PPN[5:3]) — semantically incorrect
// After
ptw_resp.cf := cfs(ptw_resp.ppn_low)                      // = cfs(PPN[2:0])
```
