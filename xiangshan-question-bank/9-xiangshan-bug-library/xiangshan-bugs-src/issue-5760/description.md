This pull request refactors StoreUnit, with major changes including:

* **Modularized the StoreUnit structure and refactored the interface Bundle**
  The stages of StoreUnit are modularized, and the interface Bundle is refactored. The usage of each field is controlled via parameters.

* **Applying a new solution for handling unaligned store requests**
  For unaligned store request that cross page boundaries, they are split into two store request executed back-to-back. For store request that do not cross pages, only saved the unaligned information and handled by the StoreQueue. For the split store requests, they are written back to the ROB only when both requests have TLB hits and the UnalignQueue is ready; otherwise, the RS needs to replay store request.

* **Replacing NewExuOutput with MemWriteBack**
  The original NewExuOutput used a DecoupledIO wrapped with ValidIO, which could potentially cause deadlock during random initialization. The new MemWriteBack uniformly uses ValidIO to avoid this situation.

After evaluation, the above modifications have a very minimal impact on the performance of spec06, and it can be considered that there is almost no effect.
