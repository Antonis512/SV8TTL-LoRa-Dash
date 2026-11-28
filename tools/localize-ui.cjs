// One-time source migration: translate literal UI text, never data values or protocol keys.
const fs=require('node:fs'),path=require('node:path');
const {babelParse,traverse}=require(process.env.PLAYWRIGHT_BABEL||path.join(path.dirname(require.resolve('playwright/package.json')),'lib/transform/babelBundle.js'));
const dir=path.resolve('src/main/assets'),dict=JSON.parse(fs.readFileSync(path.join(dir,'greek.json'),'utf8'));
const sinks={node:[2],mapText:[1],contactButton:[1],dialogAction:[1],dialogCard:[0],contactDialog:[0],contactSection:[1],contactInput:[1],contactCheck:[1],contactSelect:[1],mapSelect:[1],mapInput:[1],mapLabel:[1],mapButton:[1],nodeButton:[1],persistentButton:[0],alert:[0],confirm:[0],prompt:[0],mapNotice:[0],contactNotice:[0],createTextNode:[0]};
const returns=new Set(['deliveryLabel','geofenceNodeActionLabel']);
let total=0;
for(const file of fs.readdirSync(dir).filter(f=>f.endsWith('.js')&&!['greek.js','language.js','maplibre-gl.js','pmtiles.js','protomaps-basemaps.js','map-full-style.js','radio-presets.js'].includes(f))){
 const src=fs.readFileSync(path.join(dir,file),'utf8'),ast=babelParse(src,file),edits=new Map();
 function mark(n){
  if(!n)return;
  if(n.type==='StringLiteral'&&dict[n.value.trim()]!==undefined&&n.value.trim())edits.set(n.start,{start:n.start,end:n.end,text:'tr('+src.slice(n.start,n.end)+')'});
  else if(n.type==='TemplateLiteral'){
   const raw=src.slice(n.start+1,n.end-1);
   if(dict[raw]!==undefined)edits.set(n.start,{start:n.start,end:n.end,text:'trTemplate('+JSON.stringify(raw)+',['+n.expressions.map(e=>src.slice(e.start,e.end)).join(',')+'])'});
  }else if(n.type==='BinaryExpression'&&n.operator==='+'){mark(n.left);mark(n.right);}
  else if(n.type==='ConditionalExpression'){mark(n.consequent);mark(n.alternate);}
  else if(n.type==='LogicalExpression'){mark(n.left);mark(n.right);}
 }
 function pairs(n){if(n?.type==='ArrayExpression')for(const e of n.elements)if(e?.type==='ArrayExpression')mark(e.elements[1]);}
 traverse(ast,{
  ArrayExpression(p){if(p.parentPath.isMemberExpression())return;const a=p.node.elements;if(a.length===2&&a[0]?.type==='StringLiteral'&&a[1]?.type==='StringLiteral'&&a[0].value!==a[1].value)mark(a[1]);},
  AssignmentExpression(p){const n=p.node;if(n.left.type==='MemberExpression'&&['textContent','innerText','placeholder','title'].includes(n.left.property.name))mark(n.right);},
  CallExpression(p){const n=p.node,name=n.callee.type==='Identifier'?n.callee.name:n.callee.property?.name;
   for(const i of (Object.hasOwn(sinks,name)?sinks[name]:[]))mark(n.arguments[i]);
   if(name==='setAttribute'&&['title','aria-label','placeholder'].includes(n.arguments[0]?.value))mark(n.arguments[1]);
   if(name==='contactSelect')pairs(n.arguments[3]);
   if(name==='mapSelect')pairs(n.arguments[2]);
  },
  NewExpression(p){if(p.node.callee.name==='Option')mark(p.node.arguments[0]);},
  ReturnStatement(p){if(returns.has(p.getFunctionParent()?.node.id?.name))mark(p.node.argument);}
 });
 if(edits.size){let text=src;for(const e of [...edits.values()].sort((a,b)=>b.start-a.start))text=text.slice(0,e.start)+e.text+text.slice(e.end);fs.writeFileSync(path.join(dir,file),text);console.log(file,edits.size);total+=edits.size;}
}
console.log('Localized UI literal expressions:',total);
