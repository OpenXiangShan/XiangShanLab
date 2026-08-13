I didn't REVIEW this pr(https://github.com/OpenXiangShan/XiangShan/pull/4326) carefully. :skull:
It resulted in a change in the semantics of the modification.

---

The loadAddrMisaligned exception is generated when misaligned accesses uncache space.

---

A misaligned load sets a loadAddrMisaligned exception at the s0 flag to ensure that it only enters the loadmisalignbuffer and has no other side effects.
So it will prevent s2_uncache from spawning properly.
Previously we used an additional `s2_un_misalign_exception` to flag this.
Now, after examining the semantics of s2_uncache, the semantics of s2_uncache can be appropriately represented by directly removing the excepiont related signals
