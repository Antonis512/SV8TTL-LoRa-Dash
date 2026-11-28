"""Import translator-provided text as data; never execute workbook contents."""
from pathlib import Path
import json,re,hashlib,argparse
import openpyxl
root=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('workbook',type=Path,help='Completed Greek translation workbook')
source=parser.parse_args().workbook
w=openpyxl.load_workbook(source,read_only=True,data_only=True)
catalog={};review=[]
for row in list(w.worksheets[0].values)[7:]:
    if len(row)<5 or not isinstance(row[2],str) or not isinstance(row[4],str):continue
    en,el=row[2],row[4]
    # Placeholders are identifiers, never translated or evaluated from this file.
    tokens=lambda s:sorted(re.findall(r'\$\{[^}]*\}|%(?:\d+\$)?[-+0 #]*\d*(?:\.\d+)?[a-zA-Z]',s))
    if tokens(en)!=tokens(el):review.append({'id':row[0],'english':en,'reason':'Placeholder mismatch; English fallback'});continue
    if en in catalog and catalog[en]!=el:raise ValueError('Conflicting translation: '+en)
    catalog[en]=el
catalog.update({'Radio':'Κόμβος','Radios':'Κόμβοι','Node':'Κόμβος','Nodes':'Κόμβοι','Group':'Ομάδα'})
for en,el in {'connected':'συνδεδεμένοι','not reported':'δεν αναφέρθηκε','hops':'άλματα','transmission(s)':'εκπομπές','direct receptions.':'απευθείας λήψεις.'}.items():catalog.setdefault(en,el)
(root/'src/main/assets/greek.json').write_text(json.dumps(catalog,ensure_ascii=False,indent=2),encoding='utf-8')
(root/'src/main/assets/greek.js').write_text('/* User-supplied Greek UI translations. */\nwindow.greekTranslations='+json.dumps(catalog,ensure_ascii=False)+';\n',encoding='utf-8')
(root/'build/greek-import-review.json').write_text(json.dumps({'source':str(source),'sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'entries':len(catalog),'rejected':review},ensure_ascii=False,indent=2),encoding='utf-8')
print('Imported',len(catalog),'translations; rejected',len(review),'placeholder mismatches')
