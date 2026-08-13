Should use `s2_real_exception` for `s_safe_writeback` and `s2_wakeup` judgement.

When misaligned encounters mmio, we should actually generate the misaligned exception and write it back directly.
Here's something I forgot to add to LoadUnit's writeback condition earlier.
