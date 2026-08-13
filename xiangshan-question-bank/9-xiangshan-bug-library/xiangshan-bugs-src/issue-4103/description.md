To solve the stuckness caused by insufficient RAW, we use `threshold` to control the execution of vector instructions.
When the mergebuffer has few free entries than it can hold in the flow, we backpressure to make `IssueQueue` send the oldest `Uop` for us.
