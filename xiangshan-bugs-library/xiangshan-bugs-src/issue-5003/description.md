With the new `ftqoffset` definition, computing the PC now requires `isRVC`. 
When ROB compression is enabled, we previously recorded the `isRVC` of the last instruction, mainly to compute the PC when the last instruction is a jump. 
However, if an interrupt hits the first instruction of a ROB entry, the PC computation cannot obtain the correct `isRVC`, causing the calculation to fail. 
Therefore, we change the policy: 
- use the last instruction’s `isRVC` when dispatching to the execution pipelines
- use the first instruction’s `isRVC` when enqueuing into the ROB.

This pr also open the ROBcompress for branch kunminghu-v3 (reverts a987696)
