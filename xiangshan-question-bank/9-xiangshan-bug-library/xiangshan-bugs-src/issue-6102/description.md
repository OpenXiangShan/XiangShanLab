Register the selected prefetch candidate metadata at the s1 boundary
and use s1 valid signals to forward pending sent_vec updates during
new candidate generation.

Update the persistent sent_vec state only when the corresponding s1
prefetch request fires. This keeps downstream ready out of the s0
prefetch candidate path while still preventing duplicated requests
from the in-flight s1 entry.

This touches the L1, L2, and L3 prefetch issue paths in
L1PrefetchComponent. The L2/L3 s1 valid signals are declared with the
shared s1 metadata and assigned in their send stages to keep the
pipeline boundary explicit.
