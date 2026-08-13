This PR needs to wait for PR #5962 and PR #5959 to be merged first.

 Fix description: Route cross-page uncache data to S1 for unified management. S3 can now directly concatenate uncache instructions using s3_prevLastIsHalfRvi during fetch. This fixes the edge case where instructions spanning both cache and uncache channels fell through the cracks of the existing S1 (cache) and S3 (uncache) cross-page handling logic.
