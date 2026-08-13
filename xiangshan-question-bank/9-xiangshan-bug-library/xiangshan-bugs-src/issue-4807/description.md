If vector misaligned store causes misalignBufferNack, it is necessary to enter mergebuffer to IQ replay.

Scalar misaligned store does not require continued pipelining, it will allow IQ to perform replay at S1.
