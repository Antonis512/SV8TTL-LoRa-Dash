const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
class Element {
  constructor(tag){this.tag=tag;this.attrs={};this.children=[];this.style={};this.namespaceURI='http://www.w3.org/2000/svg';}
  setAttribute(key,value){this.attrs[key]=value;}
  append(...children){this.children.push(...children);}
}
let rows=[];
const context={document:{createElementNS:(ns,tag)=>new Element(tag)},node:(tag,cls,text)=>Object.assign(new Element(tag),{textContent:text}),AndroidBridge:{usageHistory:()=>JSON.stringify(rows)}};
vm.createContext(context);vm.runInContext(fs.readFileSync('src/main/assets/usage-history.js','utf8'),context);
function paths(data){rows=data;const result=new Element('div');context.drawUsageGraph(result,[{value:'mc:test',parentElement:{textContent:'MC test'}}],[0,3600000]);return result.children[0].children.filter(x=>x.tag==='path').map(x=>x.attrs.d);}
let result=paths([[0,10,20,1,0],[60000,11,21,1,60000],[120000,12,22,2,120000],[180000,13,23,2,180000]]);
assert.equal((result[0].match(/ M/g)||[]).length,2,'Short reconnect breaks the line');
assert.equal((result[0].match(/ L/g)||[]).length,2,'Continuous samples remain joined');
result=paths([[0,0,0,1,0],[60000,null,5,1,60000],[120000,2,6,1,120000]]);
assert.equal((result[0].match(/ M/g)||[]).length,2,'Missing metric creates gap');
assert.equal((result[1].match(/ L/g)||[]).length,2,'Other metric remains continuous');
result=paths([[0,10,20,'',0],[1800000,11,21,'',1800000]]);
assert(!result[0].includes(' L'),'Long legacy gaps are not joined');
console.log('Utilization graph gap checks passed');
