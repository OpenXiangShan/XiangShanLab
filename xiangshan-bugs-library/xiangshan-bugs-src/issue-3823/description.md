This pr add zihintpause extension:
* decode pause to FENCE with pred=W, succ=0, fm=0, rd=x0, rs1=x0
* Currently, FENCE has no side effects.
