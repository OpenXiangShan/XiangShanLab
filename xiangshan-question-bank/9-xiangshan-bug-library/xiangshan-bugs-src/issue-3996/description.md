ITLB does not store `gpaddr` to save resources, instead it takes `gpaddr` from L2TLB when gpf occurs, which poses a two-option requirement for the requestor (i.e. IPrefetchPipe):
1. resend the same `itlb.io.req.vaddr` until `itlb.io.resp.miss` is pulled down
2. flush gpf entry in ITLB by pulling up `itlb.io.flushPipe`

Otherwise, ITLB is unable to handle the next gpf and the core hangs.

However, the first point cannot be guaranteed during the speculative execution, as IPrefetchPipe sends request to ITLB at s0 stage and may receive a flush request from BPU s3 stage, IFU or Backend at s1 stage, then the same vaddr is never resend to ITLB.

Therefore, we must ensure that ITLB is flushed synchronously when IPrefetchPipe s1 stage is flushed, thus satisfying the second point. This PR implements this.
