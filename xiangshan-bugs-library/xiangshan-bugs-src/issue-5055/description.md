In v3 Ftq, we have `redirect` to Bpu and `redirectNext` to prefetch, therefore bp1=pf0. In this case, when an s1 prediction is overridden by s3, it can reach at most pf2/if1 (i.e. in prefetchPipe `s2` stage reg, wayLookup `entries(writePtr - 1)`, or mainPipe `s1` stage reg). In old design, we only flush pf1, that could be wrong.

Though, we don't have to flush prefetchPipe s2, anyway it's prefetch and has no impact on control flow. (If we don't flush it, it can be seen as some sort of wrong-path prefetch).

So, in this PR we implement wayLookup & mainPipe s1 flush from Bpu s3 override.

NOTE: To reduce implementation cost, wayLookup assumes that we can flush at most 1 entry at tail, this could be wrong if we have Bpu s4/5/even more flush in the future. See comments there.
