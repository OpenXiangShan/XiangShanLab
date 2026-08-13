`first_s2xlate_fault` means G-stage translate triggers gpf or gaf. `check_g_perm_fail` also means gpf, however previous design forgot to consider this situation. This PR fixes the bug.
