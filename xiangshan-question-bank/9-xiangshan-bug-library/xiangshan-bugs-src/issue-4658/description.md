Consider the following two-stage address translation process:

In the vs-stage, a 2MB large page is found during the lookup, which means the lower 9 bits of the resulting ppn are zeros. When generating the gvpn for the g-stage, it is formed as `gvpn = {s1_ppn, s1_vpn(9 bits)}`.

Then, in the g-stage translation, a 1GB large page is found, so the lower 9 * 2 = 18 bits of the resulting ppn are zeros. The final physical page number is constructed as ppn = `{s2_ppn, s2_gvpn(18 bits)}` = `{s2_ppn, s1_ppn(9 bits), s1_vpn(9 bits)}`.

In other words, if the g-stage page is larger than the vs-stage page, the final ppn should be composed of three parts: s2_ppn, s1_ppn, and s1_vpn.

However, in `handle_block`, the original implementation incorrectly concatenated the lower 18 bits of the ppn solely from s1_vpn, i.e., `{s2_ppn, s1_vpn(18 bits)}`, instead of the correct `{s2_ppn, s1_ppn(9 bits), s1_vpn(9 bits)}`. This commit fixes that bug.
