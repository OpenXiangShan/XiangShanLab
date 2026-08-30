Previous:

If StoreMisalignBuffer is full and the store have exception, the store will be writebacked and will be replay from RS, which will lead to this store was writeback multiples times.

If the store writeback multiple times, it will lead to `uopNum` overflow, when rob is unable to handle exceptions in a prompt manner.

Current:

If scalar store none exception:

	[1]. needReplay: not writeback

	[2]. !needReplay: not writeback

If scalar store have exception:

	[1]. needReplay: not writeback

	[2]. !needReplay: writeback

If vector store have exception or needReplay, it need to writeback.
