from pathlib import Path
import re,json
root=Path(__file__).resolve().parents[1]
p=root/'src/main/java/MainActivity.java';s=p.read_text(encoding='utf-8-sig');catalog=json.loads((root/'src/main/assets/greek.json').read_text(encoding='utf-8'))
edits={}
for call in re.finditer(r'\b(?:setTitle|setMessage|setText|setHint|setPositiveButton|setNegativeButton|setNeutralButton|nodeActionResult)\s*\(',s):
    pos=call.end();depth=0
    while pos<len(s):
        c=s[pos]
        if c=='"':
            end=pos+1
            while end<len(s):
                if s[end]=='\\':end+=2;continue
                if s[end]=='"':break
                end+=1
            literal=s[pos:end+1]
            try:value=json.loads(literal)
            except Exception:value=''
            if depth==0 and value.strip() in catalog:edits[pos]=(end+1,'ui('+literal+')')
            pos=end+1;continue
        if c=='(':depth+=1
        elif c==')':
            if depth==0:break
            depth-=1
        elif c==',' and depth==0:break
        pos+=1
for start,(end,value) in sorted(edits.items(),reverse=True):s=s[:start]+value+s[end:]
p.write_text(s,encoding='utf-8')
print('Native UI literals localized:',len(edits))
