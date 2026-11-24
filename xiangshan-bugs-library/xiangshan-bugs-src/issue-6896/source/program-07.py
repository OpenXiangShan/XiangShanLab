# Emit C stubs for the Constantin DPI readers, returning the elaborated init value
import re,glob,sys
out=[]
for f in glob.glob(sys.argv[1]+'/*_constantinReader.v'):
    s=open(f).read()
    fn=re.search(r'function longint (\w+)\(\)',s).group(1)
    v=re.search(r'initial value = (\d+);',s).group(1)
    out.append('extern "C" long long %s(){ return %s; }'%(fn,v))
open(sys.argv[2],'w').write('\n'.join(out)+'\n')
print(len(out))
