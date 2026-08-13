Actually, the bit-width of s1ppn is sectorppnLen, which is defined as PaddrBits(48) - Offset(12) - TLB compression(3). In contrast, the L2 TLB returns item.s1.entry.ppn with a bit-width of sectorptePPNLen, which equals ptePaddrLen(56) - Offset(12) - TLB compression(3).

The part of the PPN beyond PaddrBits is only used to generate gpaddr when a guest page fault occurs, so it isn’t stored in the L1 TLB entry. Here, we simply assign the lower (sectorppnLen - 1, 0) bits. In fact, the original implementation would also work correctly, as it automatically truncates the lower bits of item.s1.entry.ppn and assigns them to s1ppn.

TODO: Not storing gpaddr (the upper PPN bits) in the L1 TLB currently provides minimal benefits and significantly increases the complexity, scalability, and maintainability issues of the TLB. In the new architecture, we need to store gpaddr in the L1 TLB to avoid all the additional handling related to guest page faults (gpf).
