In the previous design, the input s2xlate signal was directly used to determine whether to virtualize, but the input signal changed to the default value 0 due to timing problems, resulting in the use of the wrong PBMTE.

In fact, LLPTW can handle both virtualized and non-virtualized requests simultaneously. This information is stored in entries(i).req_info.s2xlate. By using this signal, we can distinguish between PBMTEs under different virtualization modes. This commit fixes the bug.
