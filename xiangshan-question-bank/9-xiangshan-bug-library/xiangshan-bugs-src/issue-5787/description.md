* Export backend empty state from CtrlBlock so FTQ treat last instruction as retired.
 * Use the direct commit ftqIdx selection in CtrlBlock and remove redundant ROB crossFtqCommit propagation in the commit path.
