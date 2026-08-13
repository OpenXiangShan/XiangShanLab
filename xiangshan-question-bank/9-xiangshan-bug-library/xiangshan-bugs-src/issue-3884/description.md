* Bug descriptions:
When an instruction first enq LoadQueueReplay and needs to be redirected, the EnqMask generation does not take this situation into account, then incorrectly updating the age matrix.

* Bug fix
use newEnqueue to generate EnqMask
