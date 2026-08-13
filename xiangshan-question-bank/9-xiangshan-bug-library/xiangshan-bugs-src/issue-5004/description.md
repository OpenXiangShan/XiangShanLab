This reverts #4977 and fix the original bug correctly

#4977 tries to stop alloc new entry when in initEntryIfNotUseful(), but it does not control write condition in L244,
so it actually copies entry(0) to victim, as `t1_updatedEntry = WireDefault(t1_hitEntry)`, this is absolutely wrong and causes "s1_hitOH must be one-hot" assertion to fail

Now, we control the write back condition directly (hit, or !hit and taken)
