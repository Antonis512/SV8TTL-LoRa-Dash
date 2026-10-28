// Only structural metadata: never record form values, messages or error messages.
'use strict';
function appDiagnostic(category,detail){try{AndroidBridge.diagnostic(category,detail);}catch(ignore){}}
window.addEventListener('error',event=>appDiagnostic('js-error','line='+event.lineno+' column='+event.colno+' type='+(event.error?.name||'ResourceError')));
window.addEventListener('unhandledrejection',event=>appDiagnostic('js-rejection','type='+(event.reason?.name||typeof event.reason)));
document.addEventListener('click',event=>{const target=event.target.closest('button,select,input,a');if(target)appDiagnostic('ui','click tag='+target.tagName+' id='+target.id+' class='+target.className);},true);
document.addEventListener('change',event=>appDiagnostic('ui','change id='+event.target.id),true);
