1. Level: Previously, the code always used the smaller value between s1.stage and s2.stage, regardless of the virtualization stage. In fact, only the allStage case should compare both stages; other cases should determine the level independently based on their respective stage.

2. VPN: Previously, only the allStage case checked the level to decide whether to concatenate the lower bits of the VPN. However, in reality, other cases also need to perform VPN concatenation based on the level.
