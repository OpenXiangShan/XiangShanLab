align the train situations of prefetchers in xs-gem5 to gain little performance.
1. training: for SMS prefetcher, it can train when any l1 prefetch hit
2. for training: for L2 prefetcher, it can train when l1 prefetch miss L1 and miss L2
    * fix the cut-off problem of l1 prefetch vaddr.

- [x] https://github.com/OpenXiangShan/XSCache/pull/18
