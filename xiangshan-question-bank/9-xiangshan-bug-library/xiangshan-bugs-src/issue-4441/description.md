**Read this PR(https://github.com/OpenXiangShan/XiangShan/pull/4442) from LoadUnit first.**

---

In addition to the above, `nc` has been added to the misalign checks as well.
(https://github.com/OpenXiangShan/XiangShan/pull/4441/files#diff-cd162a95fcb65b10cf6ac087d3aac686ccb932ab4f5e270c0cfdb38437462b37L464-R471)

---

Currently, it is not to fully replace `s2_mmio` with `s2_actually_uncache` due to inconsistencies in the way `nc` and `mmio` are handled in the `StoreQueue`, so the original `s2_mmio` is still retained.
Other than that, there is no longer a need for an additional `s2_uncache`, as StoreUnit does not need to handle prefetching.
