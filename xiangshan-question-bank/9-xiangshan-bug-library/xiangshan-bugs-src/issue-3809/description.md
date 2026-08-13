We should not use GatedRegNext here as `overwrite*` may be X-state when `shouldOverwrite` is false.
Also fix a bug about generate gpaddr when a cross-page misalign load request.

Should not squash these two commits.
