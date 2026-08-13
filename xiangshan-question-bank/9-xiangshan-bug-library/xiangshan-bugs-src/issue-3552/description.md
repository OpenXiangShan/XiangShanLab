This commit optimizes CoupledL2 timing by:
* adding a pipeline stage to update `PCrdValids`
* adding a pipeline stage to arbitrate PCredits to all the slices
* always being ready for RXRSP responses
