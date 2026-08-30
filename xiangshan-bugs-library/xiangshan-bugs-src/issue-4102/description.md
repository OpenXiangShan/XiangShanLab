Currently, when `RAW` is full, a `RAW nack` is generated, which leads to `LoadQueueReplay`.
And when `RAW` is non-empty, commands are reissued from `Replay`.

Currently, a load instruction goes into `LoadUnit` at `S2`, and then if an exception occurs, a `revoke` is generated at `S3`.
Therefore, this will happen:

`RAW` has only one item remaining.

The instructions in `LoadQueueReplay` are sent to `LoadUnit1`.
The Load instruction also exists in `LoadUnit0`, so `LoadUnit0` has access to `RAW`, while a Load in `LoadUnit1` produces a `RAW nack`.
And `LoadUnit0` and `LoadUnit1` would generate `bank conflict`, thus causing `LoadUnit0` to get to `S3` to generate a `fast replay` and `revoke`, which would result in `RAW` being non-full, which would result in `RAW nack` in `LoadQueueReplay` would be allowed to reissue. The reissued instruction will in turn create a `bank conflict` with `fast replay` and cause itself to create another `RAW nack` due to priority issues.

When the above loop expands, it causes this to happen over and over again, leading to a jam.

`Wu Shen` suggested that this bug could be solved by allowing `fast replay` to spawn only once.
