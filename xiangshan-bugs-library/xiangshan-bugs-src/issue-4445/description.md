Hello, this change set is to remove the SRAM read-write conflict handling logic in the frontend, after OpenXiangShan/Utility#110 has been merged, which adds this logic to the SRAMTemplate. See that pull request and also #4242 for more context.

After this change, I see microbench IPC change 1.397 -> 1.413 and coremark IPC change 2.136 -> 2.147. The branch mispredictions also decreased slightly in both.

This probably cannot be merged automatically, since the utility submodule should point to the new revision after merging instead of the revision in my branch.

Thanks, Sam
