In the previous design, every time a page table entry was fetched from memory, a PMP check was performed on the physical address of the next-level page table. However, for cases requiring Stage-2 address translation or when a page fault occurs during the page table fetch, a PMP check is unnecessary. Performing a PMP check in these cases could lead to false access fault reports. This commit fixes the issue.

Future work: The current exception handling logic is messy and unsustainable. A complete refactor of this code is needed in the future, rather than continuing to add patches.
