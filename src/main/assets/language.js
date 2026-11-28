'use strict';
let appLanguage='en';
const languageStatic=[];
function tr(text){
 if(typeof text!=='string'||appLanguage!=='el')return text;
 const key=text.trim(),translated=Object.prototype.hasOwnProperty.call(window.greekTranslations||{},key)?window.greekTranslations[key]:undefined;
 return !key||translated===undefined?text:text.slice(0,text.indexOf(key))+translated+text.slice(text.indexOf(key)+key.length);
}
function trTemplate(source,values){
 const tokens=source.match(/\$\{[^}]*\}/g)||[],translated=tr(source);
 return translated.replace(/\$\{[^}]*\}/g,token=>String(values[tokens.indexOf(token)]??token));
}
// Only for protocol-produced statistics, never chat messages or user notes.
function trStatistics(text){return String(text||'').split('\n').map(line=>{const split=line.indexOf(':');return split<0?tr(line):tr(line.slice(0,split))+line.slice(split);}).join('\n');}
function captureLanguageLabels(){
 const walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);
 while(walker.nextNode()){
  const n=walker.currentNode;
  if(!n.parentElement.closest('script,style,textarea,[data-language-fixed]')&&n.textContent.trim())languageStatic.push({node:n,source:n.textContent,last:n.textContent});
 }
 for(const n of document.querySelectorAll('[placeholder],[title],[aria-label]')){
  if(n.closest('[data-language-fixed]'))continue;
  for(const attr of ['placeholder','title','aria-label'])if(n.hasAttribute(attr))languageStatic.push({node:n,attr,source:n.getAttribute(attr),last:n.getAttribute(attr)});
 }
}
function applyAppLanguage(language){
 appLanguage=language==='el'?'el':'en';document.documentElement.lang=appLanguage;
 for(const b of languageStatic){
  if(!b.node.isConnected)continue;
  const current=b.attr?b.node.getAttribute(b.attr):b.node.textContent;
  if(current!==b.last)continue;
  b.last=tr(b.source);if(b.attr)b.node.setAttribute(b.attr,b.last);else b.node.textContent=b.last;
 }
 const current=document.getElementById('current-language');if(current)current.textContent=appLanguage==='el'?'Ελληνικά':'English';
}
function openLanguagePicker(){document.querySelector('#language-picker input[value="'+appLanguage+'"]').checked=true;document.getElementById('language-picker').showModal();}
function saveAppLanguage(){const lang=document.querySelector('#language-picker input:checked').value;document.getElementById('language-picker').close();AndroidBridge.setAppLanguage(lang);}
captureLanguageLabels();
