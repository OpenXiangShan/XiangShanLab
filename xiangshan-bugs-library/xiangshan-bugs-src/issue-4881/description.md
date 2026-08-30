1.Scenario: A, B are normal blocks, C is MMIO. If B stalls (iTLB/ICache miss) and A commits while C enters waitLastCommit, C may wrongly assume B has committed, sending its request to the bus prematurely.
2.This issue can likely be avoided by using PMP to mark bus-unmapped or side-effecting memory addresses as non-executable.
