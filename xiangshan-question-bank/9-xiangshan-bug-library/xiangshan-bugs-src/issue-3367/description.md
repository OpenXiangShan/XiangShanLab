1. Cancel most of the meta information stored in FTQ for ittage, cancel wrbypass, and read it again when updating.
2. The buffer during read priority conflicts is still stored in the ittage table
3. Use folded_hist read during updates instead of  `RegEnable(update.ghist, updateMask(i))`
4.
