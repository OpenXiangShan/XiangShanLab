When the some align bank is located on the next page,BPU may generate a cross-page fetch block.
For example, [alignBank0, alignBank1], if alignBank1 located on the next page, and alignBank0 has no taken branch, alignBank1 has a taken branch, the fetch block which generated will be a cross-page fetch block.
