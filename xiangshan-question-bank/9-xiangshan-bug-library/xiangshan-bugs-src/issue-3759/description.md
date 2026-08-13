In the previous design, the H extension information was lost in the `fuOpType` of the misalignBuffer split instruction, causing the split instruction to not perform two-stage address translation and cause errors.

This PR fixes the information about H extension in `fuOpType` in misalignBuffer.
