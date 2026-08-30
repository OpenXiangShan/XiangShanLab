Since a load instruction that cross 16Byte needs to be split and accessed twice, it needs to enter the `RAR Queue` twice, but occupies only one `virtual load queue`, so in the extreme case it may happen that 36 load instructions that span 16Byte fill all 72 `RAR queues`.

---

There is some problem with our previous handling; if the oldest load instruction spanning 16Byte enters the `replayqueue` and at the same time there exists an instruction in the `loadmisalignbuffer` that can't finish executing because the `RAR Queue` is full, then the oldest load instruction is never cannot be issued because the `loadmisalignbuffer` has instructions in it all the time.

---

Therefore, we use a more violent scheme to do this.
When the RAR is full, we let the misaligned load generate a rollback, and the next load instruction that the loadmisalignbuffer can receive must be the oldest (if it is misaligned).
