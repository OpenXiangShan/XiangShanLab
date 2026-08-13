`s1_exception_out` is for prefetch s2 only, but we want backend exception to be considered as part of itlb exception and sent to waylookup, so we merge it to `s1_itlb_exception`
