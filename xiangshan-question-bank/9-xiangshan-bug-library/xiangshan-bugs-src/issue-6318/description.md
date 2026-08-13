### Before start

- [x] I have read the [RISC-V ISA Manual](https://github.com/riscv/riscv-isa-manual) and [XiangShan Documents](https://docs.xiangshan.cc/zh-cn/latest), and I believe this is a XiangShan RTL issue. 我已经阅读过 [RISC-V 指令集手册](https://github.com/riscv/riscv-isa-manual) 和 [香山文档](https://docs.xiangshan.cc/zh-cn/latest)，确认这应该是香山 RTL 的问题。
- [x] I have searched previous issues and PRs and did not find anything relevant. 我已经搜索过之前的 issue 和 PR，并没有找到相关的。
- [x] I have reproduced the issue using the latest commit on the default branch. 我已经使用默认分支最新的 commit 复现了问题。
- [ ] If this report was generated with AI assistance (otherwise leave unchecked), I have verified the correctness of its content. 如果这是由 AI 生成的（否则请不要勾选），我已经验证了内容的正确性。

### Branch

kunminghu-v3

### Describe the bug

For a cross-page misaligned store, the head and tail translations may be
returned on different cycles. If the tail's first DTLB response is a miss,
StoreUnit can enqueue that tail into UnalignQueue even though its physical
address is not valid. The replay mechanism subsequently obtains a valid tail
translation, but it appends a second FIFO entry instead of replacing the
already enqueued untranslated entry.

The dequeue path uses the old FIFO entry for the split store's second SBuffer
port. Consequently, the processor can commit a store using a physical address
sampled while the tail translation was invalid. 

### Expected behavior

A cross-page store tail must not enter persistent queue state before both
required translations are valid. After a tail DTLB miss is replayed, its store
commit must use the physical address from the successful translation, never a
value sampled from an invalid TLB response.

### Environment

Please see `bug-report.tar.gz` for details

### To Reproduce

In `bug-report/tests/cross-page-store-untranslated-tail/`, run `make run`.

### Additional context

[bug-report.tar.gz](https://github.com/user-attachments/files/30614606/bug-report.tar.gz)

At StoreUnit S1, the request sent to UnalignQueue is currently valid whenever
the request fires, is an unaligned tail, and crosses a page:

```scala
io.toUnalignQueue.valid := fire && isUnalignTail && cross4KPage
```

This predicate does not require `tlbHit`, while the queue payload is populated
from the TLB response's `paddr`. A tail miss can therefore complete the
Decoupled enqueue handshake with an untranslated address. The replay path then
inserts the valid translation as a separate FIFO entry rather than replacing
the invalid one.
