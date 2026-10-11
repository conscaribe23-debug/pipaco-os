// PipaCo Prueba: límites, solo lectura, exportaciones bloqueadas, marca PDF
const puppeteer=require('puppeteer-core');const sleep=ms=>new Promise(r=>setTimeout(r,ms));const URL='http://localhost:8771/index.html';
(async()=>{const b=await puppeteer.launch({executablePath:'/usr/bin/google-chrome',headless:'new',args:['--no-sandbox']});const R={};const errs=[];let fails=0;
const ok=(n,c,info)=>{console.log((c?'PASS ':'FAIL ')+n+(info!==undefined?' → '+JSON.stringify(info):''));if(!c)fails++};
const p=await b.newPage();await p.setViewport({width:412,height:900});p.on('pageerror',e=>errs.push('pageerror '+e.message));p.on('console',m=>{if(m.type()==='error')errs.push(m.text())});p.on('dialog',d=>d.accept());
await p.evaluateOnNewDocument(()=>{window.__saved=[];window.__dias=14;window.__venc=false;window.PipaCoAndroid={version(){return '1.0.0'},saveFile(n,m,b){window.__saved.push({n,m,b});return 'Download/'+n},shareFile(n,m,b){window.__saved.push({n,m,b});return 'OK'},trialDiasRestantes(){return window.__dias},trialVencida(){return window.__venc},trialVence(){return '2026-10-25'}}});
await p.goto(URL,{waitUntil:'networkidle0'});
ok('pantalla crear acceso propia (correo vacío)',await p.evaluate(()=>!!document.getElementById('au_p2')&&au_e.value===''&&document.body.innerText.includes('PipaCo Prueba')&&!document.body.innerText.includes('entras con tu huella')));
ok('AUTH.apk() falso (sin biometría)',await p.evaluate(()=>!AUTH.apk()));
await p.type('#au_e','cliente@negocio.example');await p.type('#au_p','Clave-12345');await p.type('#au_p2','Clave-12345');await p.click('#authf button[type=submit]');await sleep(1200);
ok('entra a la app con datos vacíos',await p.evaluate(()=>S.ventas.length===0&&S.clientes.length===0&&S.comprobantes.length===0));
ok('banner quedan 14 días',await p.evaluate(()=>document.getElementById('prueba-ban')?.innerText.includes('quedan 14 días')&&document.getElementById('prueba-ban').innerText.includes('Productos 3/10')));
ok('sin service worker / manifest',await p.evaluate(()=>!document.querySelector('link[rel=manifest]')&&!navigator.serviceWorker?.controller));
// Clientes: 5 por formulario
const cliForm=async(n)=>{await p.evaluate(()=>A.ir('clientes'));await p.evaluate(()=>A.nuevo('clientes'));await sleep(100);const abierto=await p.evaluate(()=>!!document.querySelector('#modal .modal')&&typeof FORM!=='undefined'&&FORM&&FORM.col==='clientes');if(!abierto)return false;
 await p.evaluate((n)=>{const i=document.querySelector('#mform #f_nombre');i.value=n;i.dispatchEvent(new Event('input'))},n);await p.evaluate(()=>guardarForm());await sleep(150);return true};
for(let i=1;i<=5;i++)await cliForm('Cliente '+i);
ok('5 clientes creados',await p.evaluate(()=>S.clientes.length),undefined);
const sexto=await cliForm('Cliente 6');
ok('6º cliente bloqueado con aviso',await p.evaluate(()=>S.clientes.length===5&&document.body.innerText.includes('límite de la versión de prueba: 5 clientes')),{abrioForm:sexto,n:await p.evaluate(()=>S.clientes.length)});
await p.evaluate(()=>A.cerrar());
// Productos: 3 de fábrica + 7 nuevos = 10; el 11º bloqueado
const prodForm=async(n)=>{await p.evaluate(()=>A.ir('inventario'));await p.evaluate(()=>A.nuevo('productos'));await sleep(100);const abierto=await p.evaluate(()=>!!document.querySelector('#mform')&&typeof FORM!=='undefined'&&FORM&&FORM.col==='productos');if(!abierto)return false;
 await p.evaluate((n)=>{const i=document.querySelector('#mform #f_nombre');i.value=n;i.dispatchEvent(new Event('input'))},n);await p.evaluate(()=>guardarForm());await sleep(150);return true};
ok('arranca con 3 productos',await p.evaluate(()=>S.productos.length===3));
for(let i=1;i<=7;i++)await prodForm('Producto '+i);
ok('10 productos creados',await p.evaluate(()=>S.productos.length===10),await p.evaluate(()=>S.productos.length));
const prod11=await prodForm('Producto 11');
ok('11º producto bloqueado con aviso',await p.evaluate(()=>S.productos.length===10&&document.body.innerText.includes('límite de la versión de prueba: 10 productos')),{abrioForm:prod11});
await p.evaluate(()=>A.cerrar());
ok('respaldo guardar(): producto extra revertido',await p.evaluate(()=>{S.productos.push({...S.productos[0],id:'px'});guardar();return S.productos.length===10&&JSON.parse(localStorage.getItem(KEY)).productos.length===10}));
await p.evaluate(()=>A.cerrar());
// Ventas: registrar 30 por la pantalla de venta rápida
await p.evaluate(()=>A.ir('ventas'));
const vendidas=await p.evaluate(()=>{let n=0;for(let i=0;i<31;i++){const antes=S.ventas.length;UI.venta=UI.venta||{};UI.venta.pid=S.productos[0].id;UI.venta.cant=1;UI.venta.precio=1;UI.venta.metodo='Efectivo';UI.venta.canal=UI.venta.canal||'carrito';A.vender();if(S.ventas.length>antes)n++}return n});
ok('máximo 30 ventas',vendidas===30&&await p.evaluate(()=>S.ventas.length===30),vendidas);
ok('aviso de límite de ventas',await p.evaluate(()=>document.body.innerText.includes('30 ventas')));
await p.evaluate(()=>A.cerrar());
ok('venta 31 por formulario bloqueada',await p.evaluate(()=>{A.nuevo('ventas');return S.ventas.length===30&&!(typeof FORM!=='undefined'&&document.querySelector('#modal')?.innerText.includes('Guardar')&&FORM&&FORM.col==='ventas')}));
await p.evaluate(()=>A.cerrar());
ok('respaldo guardar(): push directo revertido',await p.evaluate(()=>{S.ventas.push({...S.ventas[0],id:'x'});guardar();return S.ventas.length===30&&JSON.parse(localStorage.getItem(KEY)).ventas.length===30}));
await p.evaluate(()=>A.cerrar());
// Comprobantes: 10, a partir de ventas existentes
const comps=await p.evaluate(async()=>{let n=0;for(let i=0;i<11;i++){A.cerrar();const v=S.ventas[i];COMP.abrir(null,{ventaIds:[v.id],cid:''});if(!UI.comp)continue;const antes=S.comprobantes.length;COMP.guardar();if(S.comprobantes.length>antes)n++}return n});
ok('máximo 10 comprobantes',comps===10&&await p.evaluate(()=>S.comprobantes.length===10),comps);
ok('aviso de límite comprobantes',await p.evaluate(()=>document.body.innerText.includes('10 comprobantes')));
await p.evaluate(()=>A.cerrar());
// PDF con marca de agua
await p.evaluate(()=>{window.__saved=[];COMP.pdf(S.comprobantes[0].id)});await sleep(500);
const pdf=await p.evaluate(()=>{const s=window.__saved[0];if(!s)return null;return {n:s.n,txt:atob(s.b).slice(0,200000)}});
ok('PDF generado',!!pdf,pdf&&pdf.n);
ok('PDF con marca VERSIÓN DE PRUEBA',!!pdf&&/VERSI.N DE PRUEBA/.test(pdf.txt));
// Exportaciones bloqueadas
for(const [n,f] of [['A.excel',()=>A.excel('todo')],['A.csvVentas',()=>A.csvVentas()],['A.exportJSON',()=>A.exportJSON()],['COMP.excel',()=>COMP.excel()]]){
 const r=await p.evaluate((src)=>{window.__saved=[];A.cerrar();(0,eval)('('+src+')')();return{saved:window.__saved.length,lock:document.body.innerText.includes('versión completa')}},f.toString());ok(n+' bloqueado',r.saved===0&&r.lock,r)}
ok('importJSON bloqueado',await p.evaluate(()=>{const before=JSON.stringify(S);A.importJSON({value:'x',files:[new File(['{"ventas":[]}'],'r.json')]});return JSON.stringify(S)===before&&document.body.innerText.includes('versión completa')}));
await p.evaluate(()=>A.cerrar());
// Contabilidad bloqueada
await p.evaluate(()=>A.ir('contabilidad'));await sleep(100);
ok('contabilidad avanzada bloqueada',await p.evaluate(()=>{const t=document.getElementById('main').innerText;return t.includes('Disponible en la versión completa')&&t.includes('Resumen básico')&&!t.includes('Debe\t')}),await p.evaluate(()=>document.getElementById('main').innerText.slice(0,300)));
await p.evaluate(()=>A.ir('config'));await sleep(100);
ok('tarjeta PipaCo Prueba en config',await p.evaluate(()=>document.body.innerText.includes('30 de 30')&&document.body.innerText.includes('Productos')&&document.body.innerText.includes('conscaribe23@gmail.com')));
await p.screenshot({path:'/workspace/pwatest/trial_config.png'});
await p.evaluate(()=>A.ir('inicio'));await sleep(100);await p.screenshot({path:'/workspace/pwatest/trial_inicio.png'});
// Día 13 → queda 1 día
await p.evaluate(()=>{window.__dias=1;render()});ok('banner queda 1 día',await p.evaluate(()=>document.getElementById('prueba-ban').innerText.includes('queda 1 día')));
// Vencida: solo lectura
await p.evaluate(()=>{window.__dias=0;window.__venc=true;render()});
ok('banner solo lectura',await p.evaluate(()=>document.getElementById('prueba-ban').innerText.includes('solo lectura')));
const snap=await p.evaluate(()=>localStorage.getItem(KEY));
const ro=await p.evaluate(()=>{const r={};A.cerrar();A.nuevo('gastos');r.formGastos=!!(document.querySelector('#modal .modal')&&!document.body.innerText.includes('Terminó la prueba'));A.cerrar();
 A.borrar&&A.borrar('ventas',S.ventas[0].id);A.cerrar();A.editar('clientes',S.clientes[0].id);r.editCli=!document.body.innerText.includes('Terminó la prueba');A.cerrar();
 S.ventas.push({...S.ventas[0],id:'y'});S.gastos.push({id:'g',fecha:hoy(),monto:5});guardar();r.n=S.ventas.length;return r});
ok('al vencer no se puede crear/editar',!ro.formGastos&&!ro.editCli&&ro.n===30,ro);
ok('datos intactos en solo lectura',await p.evaluate(()=>localStorage.getItem(KEY))===snap);
await p.evaluate(()=>A.ir('ventas'));ok('puede ver los datos',await p.evaluate(()=>document.body.innerText.includes('Cliente 1')||document.querySelectorAll('#main tr,#main .item').length>5));
await p.screenshot({path:'/workspace/pwatest/trial_vencida.png'});
ok('sin errores JS',errs.length===0,errs);
await b.close();console.log(fails?`${fails} FALLOS`:'TODO OK');process.exit(fails?1:0)})();
