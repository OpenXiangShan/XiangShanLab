- Replace the RAW rollback oldest select tree with a age matrix that produces a one-hot winner
 before selecting the UopEntry payload.

- The comparison results of robidx are selected here to describe the age relationships 
 in the age matrix

- This keeps the existing RAW select pipeline boundaries while separating age comparison 
 from repeated bundle muxing.

- This solution can avoid the serial execution of excessive robidx comparison circuits 
 in the original scheme and prevent repeated data movement within the select tree.
