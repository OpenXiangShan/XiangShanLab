L1TLB does not store gpaddr, but gpaddr is needed when a guest page fault occurs. In that situation, L1TLB needs to send a PTW request to get the gpaddr, which we called it getGpa. The getGpa mechanism could only handle one GPF TLB request (the first one) and expects the corresponding TLB entry is still in L1TLB.

L1TLB replacement uses PLRU (Pseudo-LRU) algorithm, which may replace intems that are not necessarily the least recently used. We found an case that L1TLB replace that GPF TLB entry, although that GPF TLB entry is accessed recently. This results in a deadlock in getGpa mechanism, which eventually causes the entire core to freeze. To solve this problem, we decides to prevent any unrelated ptw refills when getGpa mechanism is working (need_gpa).

After solve such problem, we identified that under certain conditions, as other PTW response is never refilled, other TLB requests keep replying which trigger PTW requests and occupy the L2TLB request path, preventing the GPF PTW request from responding, ultimately leading to a processor freeze. To resolve this, we decides to prevent any unrelated ptw request when need_gpa.

This patch also changes the code style of some combinational logic signals. Using when/otherwise is clearer and easier to understand than complex logical expression.
