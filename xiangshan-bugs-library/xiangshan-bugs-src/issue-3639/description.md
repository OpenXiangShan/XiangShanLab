In LSU, for exceptions that can be detected before address translation(`preaf`, `prepf` or `pregpf`), the original vaddr should be retained. And for exceptions detected after address translation, the 48-bit vaddr needs to be zero-extended or sign-extended according to different modes(`GenExceptionVa`), and then write to *tval.

Also fix some connection bugs.
