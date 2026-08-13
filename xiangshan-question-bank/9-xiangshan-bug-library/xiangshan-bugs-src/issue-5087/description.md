In previous design, the smaller value between the stage1 and stage2 levels was always written back into the TLB entry. However, this approach caused issues when exceptions occurred: a larger page could mistakenly be treated as a smaller one. During TLB lookups this would only result in performance bugs, for example:

(1) The first lookup of vpn 0x0 should return a 1 GB page, but instead a 2 MB page is written back. (2) The second lookup of vpn 0x0 + 4 MB should hit, but because the level written back last time was incorrect, it actually misses, triggering another PTW. (3) After the PTW completes, a new 2 MB page starting at vpn 0x0 + 4 MB is written back.

However, this handling leads to a functional bug in the sfence scenario. For a 1 GB page, an sfence with any address within the 1 GB range should be able to invalidate the page. If the page is mistakenly treated as only 2 MB, the sfence may fail to invalidate the page as expected, causing a functional bug.

Specifically, for allStage with exceptions:

1. If stage1 encounters an exception, the entry’s level should be written back as s1_level.
2. If stage2 encounters an exception: (1) If stage1 is a fakePTE, the entry’s level should be written back as the maximum value (indicating vsatp is misconfigured). (2) If stage1 is a non-leaf node, the entry’s level should be written back as s1_level. (3) If stage1 is a leaf node, the entry’s level should be written back as the smaller value of stage1 and stage2.

In fact, the stage1_level min stage2_level logic is used in multiple places in the code. But in those other cases, it is only used for lookups and does not affect sfence invalidation. Therefore, for now, only this particular case needs to be fixed.
