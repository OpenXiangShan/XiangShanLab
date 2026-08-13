Add a new prefetcher **Berti**. The main changes are summarized below.
* add the Berti prefetcher, and replace the Stride prefetcher for a little better performance now.
* add the refill-train datapath: MissQueue->DCache->Prefetcher->Berti
* add a latency-record datapath: loadpipe->loadunit->Berti (read) and MissQueue->mainpipe->DCache (write).
* add a `berti_enable` parameter to `Spfctl`.
* add `strideEnable` as a separate control for Stride to better control the mode between Stride and Berti modes. For `modeStrideBerti`, 00-both off; 01-stride on, berti off; 10-stride off, berti on; 11-both on.
* add a handshake between SMS and PrefetcherWrapper to prevent losing sms prefetch requests.
* recover a `load_debug_table` in ROB for debugging, including tlb latency recorded.
* remove hardware prefetch request as training sources for all prefetchers to achieve slightly better performance.
* all loadunits now support high-confidence prefetch, whereas previously only ldu0 did.
*Because all the unfairness between ldu0 and ldu1/2, such as bank conflicts and lower entry priority in MissQueue, belong to the replay channel, whose priority is higher than prefetch channel in loadunit. Therefore, there is no need to distinguish among ldu0, ldu1, and ldu2.*

<img width="410" height="554" alt="image" src="https://github.com/user-attachments/assets/df1f3f49-adf7-45f8-8ff6-6bc4c90cbff5" />
