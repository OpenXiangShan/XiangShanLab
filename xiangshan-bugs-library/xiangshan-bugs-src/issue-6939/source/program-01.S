vsetivli zero, 4, e32, m1, ta, ma
csrwi   vxsat, 0
vnclip.wv v26, v12, v13    # encoding 0xbec68d57
li      a0, 0x1111         # fall-through marker if no trap
