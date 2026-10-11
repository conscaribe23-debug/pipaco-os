/* ================= PipaCo PRUEBA (14 días + límites) =================
   Capa añadida SOLO al APK de prueba por android-trial/web/build_trial.py.
   La configuración viene de android-trial/web/trial-config.json (window.PIPACO_TRIAL_CFG). */
const TRIAL=Object.assign({dias:14,maxVentas:30,maxClientes:5,maxComprobantes:10,maxProductos:10,marcaPdf:'VERSIÓN DE PRUEBA',exportarExcelCsv:false,respaldoJson:false,contabilidadAvanzada:false,datosDeEjemplo:false,correo:'conscaribe23@gmail.com',whatsapp:''},window.PIPACO_TRIAL_CFG||{});
const PRUEBA={
 /** Colecciones con límite (los números salen de trial-config.json). */
 TIPOS:['ventas','clientes','comprobantes','productos'],
 LIM:{ventas:TRIAL.maxVentas,clientes:TRIAL.maxClientes,comprobantes:TRIAL.maxComprobantes,productos:TRIAL.maxProductos},
 NOM:{ventas:'ventas',clientes:'clientes',comprobantes:'comprobantes',productos:'productos'},
 dias(){try{const d=parseInt(PipaCoAndroid.trialDiasRestantes(),10);return isNaN(d)?TRIAL.dias:d}catch(e){return TRIAL.dias}},
 vencida(){try{return !!(window.PipaCoAndroid&&PipaCoAndroid.trialVencida&&PipaCoAndroid.trialVencida())}catch(e){return false}},
 cuenta(){const c={};PRUEBA.TIPOS.forEach(t=>c[t]=(S[t]||[]).length);return c},
 /** ¿Se pueden agregar n registros de este tipo? Si no, muestra el aviso y devuelve false. */
 puede(tipo,n=1){if(PRUEBA.vencida()){PRUEBA.soloLectura();return false}if(PRUEBA.cuenta()[tipo]+n>PRUEBA.LIM[tipo]){PRUEBA.limite(tipo);return false}return true},
 msgLimite(tipo){return`Llegaste al límite de la versión de prueba: ${PRUEBA.LIM[tipo]} ${PRUEBA.NOM[tipo]}.`},
 limite(tipo){PRUEBA.aviso('🚀 '+PRUEBA.msgLimite(tipo),`¡Qué bien que tu negocio se está moviendo! En la versión completa de PipaCo registras ventas, clientes, productos y comprobantes <b>sin límite</b>, exportas a Excel para tu contador, haces respaldos y ves toda la contabilidad. Escríbenos y te ayudamos a pasar tus datos.`)},
 soloLectura(){PRUEBA.aviso(`⏳ Terminó la prueba de ${TRIAL.dias} días`,`Tus datos siguen aquí y puedes <b>consultarlos</b>, pero ya no se pueden agregar ni editar. Pide la versión completa de PipaCo para seguir trabajando con todos tus datos.`)},
 bloqueada(que){PRUEBA.aviso('🔒 '+que,`Disponible en la <b>versión completa</b> de PipaCo. En la versión de prueba puedes usar ventas, inventario, clientes, pedidos, el resumen y los comprobantes.`)},
 urlWa(){const t=encodeURIComponent('Hola, estoy usando PipaCo Prueba y quiero la versión completa.');const n=String(TRIAL.whatsapp||'').replace(/\D/g,'');return`https://wa.me/${n}?text=${t}`},
 urlCorreo(){return`mailto:${TRIAL.correo}?subject=${encodeURIComponent('Quiero la versión completa de PipaCo')}&body=${encodeURIComponent('Hola, estoy usando PipaCo Prueba y quiero la versión completa.')}`},
 botones(){return`<a class="btn p" href="${PRUEBA.urlCorreo()}">✉️ Escribir a ${esc(TRIAL.correo)}</a><a class="btn" href="${PRUEBA.urlWa()}">💬 WhatsApp</a>`},
 aviso(titulo,html){cerrarModal();UI.comp=null;document.getElementById('modal').innerHTML=`<div class="modal-bg" onclick="if(event.target===this)A.cerrar()"><div class="modal" style="max-width:520px"><h3><span>${titulo}</span><button class="btn xs" onclick="A.cerrar()">✕</button></h3><div style="line-height:1.55">${html}</div><div class="row" style="margin-top:14px">${PRUEBA.botones()}<button class="btn" onclick="A.cerrar()">Cerrar</button></div></div></div>`},
 banner(){const d=PRUEBA.dias(),fin=PRUEBA.vencida(),c=PRUEBA.cuenta();
  const uso=PRUEBA.TIPOS.map(t=>`${PRUEBA.NOM[t][0].toUpperCase()+PRUEBA.NOM[t].slice(1)} ${c[t]}/${PRUEBA.LIM[t]}`).join(' · ');
  return fin?`<div class="prueba-ban fin" id="prueba-ban">⏳ <b>Versión de prueba terminada: solo lectura</b><span class="uso">${uso}</span><span style="flex:1"></span><button class="btn xs" onclick="PRUEBA.soloLectura()">Pedir versión completa</button></div>`
   :`<div class="prueba-ban" id="prueba-ban">🧪 <b>Versión de prueba: ${d===1?'queda 1 día':'quedan '+d+' días'}</b><span class="uso">${uso}</span><span style="flex:1"></span><button class="btn xs" onclick="PRUEBA.limite('ventas')">Versión completa</button></div>`},
 card(){const c=PRUEBA.cuenta();const fila=(t)=>`<div class="row" style="justify-content:space-between"><span>${PRUEBA.NOM[t][0].toUpperCase()+PRUEBA.NOM[t].slice(1)}</span><b class="${c[t]>=PRUEBA.LIM[t]?'neg':''}">${c[t]} de ${PRUEBA.LIM[t]}</b></div>`;
  return`<div class="card"><h2>🧪 PipaCo Prueba</h2><div class="sm" style="line-height:1.6">${PRUEBA.vencida()?'<b class="neg">La prueba terminó: modo solo lectura.</b>':`Te ${PRUEBA.dias()===1?'queda <b>1 día</b>':'quedan <b>'+PRUEBA.dias()+' días</b>'} de prueba.`}</div>
  <div style="margin:8px 0">${PRUEBA.TIPOS.map(fila).join('')}</div>
  <div class="sm mu">No incluye: exportar a Excel/CSV, respaldo e importación JSON, contabilidad avanzada. Los PDF llevan la marca “${esc(TRIAL.marcaPdf)}”.</div><div class="row" style="margin-top:10px">${PRUEBA.botones()}</div></div>`}
};

/* ---- Envolver acciones (sin tocar el código original) ---- */
(function(){
 const ro=()=>{if(PRUEBA.vencida()){PRUEBA.soloLectura();return true}return false};
 const envolver=(obj,nombre,antes)=>{const f=obj[nombre];if(typeof f!=='function')return;obj[nombre]=function(...a){if(antes.apply(this,a)===false)return;return f.apply(this,a)}};
 // Solo lectura al vencer
 ['editar','borrar','pagarFactura','contar','cfg','prod','reset'].forEach(n=>envolver(A,n,()=>!ro()));
 envolver(A,'nuevo',(col)=>{if(ro())return false;if(col==='ventas'&&!PRUEBA.puede('ventas'))return false;if(col==='clientes'&&!PRUEBA.puede('clientes'))return false;if(col==='productos'&&!PRUEBA.puede('productos'))return false});
 envolver(A,'vender',()=>PRUEBA.puede('ventas'));
 envolver(A,'entregar',()=>PRUEBA.puede('ventas'));
 envolver(COMP,'abrir',(id)=>{if(ro())return false;if(!id&&!PRUEBA.puede('comprobantes'))return false});
 envolver(COMP,'anular',()=>!ro());
 envolver(COMP,'entregarYAgregar',()=>PRUEBA.puede('ventas'));
 envolver(COMP,'logo',(inp)=>{if(ro()){inp.value='';return false}});
 envolver(COMP,'quitarLogo',()=>!ro());
 envolver(COMP,'guardar',()=>{const st=UI.comp;if(!st)return;if(ro())return false;
  const nuevasVentas=st.lineas.filter(l=>l.origen==='manual'&&!l.ventaId&&num(l.cant)>0).length;
  if(!st.id&&!PRUEBA.puede('comprobantes'))return false;
  if(nuevasVentas&&!PRUEBA.puede('ventas',nuevasVentas))return false;
  if(st.cid==='__nuevo'&&st.guardarCli&&!PRUEBA.puede('clientes'))return false});
 // Formularios: aviso amable si se llega al límite al guardar
 const vLim=(col,tipo)=>{const sc=SCHEMAS[col];const f=sc.validar;sc.validar=function(v,old){if(PRUEBA.vencida()){setTimeout(PRUEBA.soloLectura,0);return'Prueba terminada: solo lectura'}if(!old&&PRUEBA.cuenta()[tipo]>=PRUEBA.LIM[tipo]){setTimeout(()=>PRUEBA.limite(tipo),0);return PRUEBA.msgLimite(tipo)}return f?f.apply(this,arguments):undefined}};
 vLim('ventas','ventas');vLim('clientes','clientes');vLim('productos','productos');
 Object.keys(SCHEMAS).filter(c=>!['ventas','clientes','productos'].includes(c)).forEach(c=>{const sc=SCHEMAS[c];const f=sc.validar;sc.validar=function(v,old){if(PRUEBA.vencida()){setTimeout(PRUEBA.soloLectura,0);return'Prueba terminada: solo lectura'}
  if(c==='pedidos'&&v.estado==='Entregado'&&!(old&&old.ventaId)&&PRUEBA.cuenta().ventas>=PRUEBA.LIM.ventas){setTimeout(()=>PRUEBA.limite('ventas'),0);return PRUEBA.msgLimite('ventas')}return f?f.apply(this,arguments):undefined}});
 // Funciones de la versión completa
 if(!TRIAL.exportarExcelCsv){['excel','csvVentas','csvGastos','csvLibro'].forEach(n=>{A[n]=()=>PRUEBA.bloqueada('Exportar a Excel / CSV')});COMP.excel=()=>PRUEBA.bloqueada('Exportar comprobantes a Excel')}
 if(!TRIAL.respaldoJson){A.exportJSON=()=>PRUEBA.bloqueada('Respaldo completo (JSON)');A.importJSON=(inp)=>{if(inp)inp.value='';PRUEBA.bloqueada('Importar respaldo')}}
 if(!TRIAL.datosDeEjemplo)A.demo=()=>PRUEBA.aviso('🧪 Datos de ejemplo','En la versión de prueba trabajas con tus propios datos. Para ver un ejemplo completo abre la <a href="https://conscaribe23-debug.github.io/pipaco-os/demo/">demo web de Pip & Co</a>.');
 // Al vencer no se generan pedidos recurrentes nuevos
 const _gen=generarRecurrentes;generarRecurrentes=function(){return PRUEBA.vencida()?0:_gen.apply(this,arguments)};
 // Respaldo final: nada que supere los límites (o que se haga en solo lectura) llega a guardarse
 const _guardar=guardar;guardar=function(){
  let prev=null;try{prev=JSON.parse(localStorage.getItem(KEY)||'null')}catch(e){}
  if(PRUEBA.vencida()){if(prev){S=cargar();_calc=null}return}
  for(const t of PRUEBA.TIPOS){const antes=prev&&Array.isArray(prev[t])?prev[t].length:0;if(S[t].length>PRUEBA.LIM[t]&&S[t].length>antes){S=cargar();_calc=null;PRUEBA.limite(t);return}}
  return _guardar.apply(this,arguments)};
 // Contabilidad avanzada bloqueada: solo resumen básico
 if(!TRIAL.contabilidadAvanzada){V.contabilidad=()=>{const k=calc();const m=UI.mes;const a=m+'-01',b=finMes(m);const vm=k.ventas.filter(v=>v.fecha>=a&&v.fecha<=b);const tv=vm.reduce((s,v)=>s+v.total,0);const tg=S.gastos.filter(g=>g.fecha>=a&&g.fecha<=b).reduce((s,g)=>s+num(g.monto),0);const cxc=Object.values(k.cli).reduce((s,c)=>s+c.saldo,0);
  return`<div class="top"><h1>📒 Contabilidad</h1><input type="month" class="inp" style="width:auto" value="${m}" onchange="A.mes(this.value)"></div>
  <div class="card"><h2>Resumen básico — ${fmtMes(m)}</h2><div class="grid g4"><div class="kpi"><div class="l">Ventas</div><div class="v">${$f(tv)}</div><div class="s">${vm.length} registro(s)</div></div><div class="kpi"><div class="l">Gastos</div><div class="v">${$f(tg)}</div></div><div class="kpi"><div class="l">Ventas − gastos</div><div class="v ${tv-tg>=0?'pos':'neg'}">${$f(tv-tg)}</div></div><div class="kpi"><div class="l">Te deben (clientes)</div><div class="v">${$f(cxc)}</div></div></div></div>
  <div class="card lock-card"><h2>🔒 Disponible en la versión completa</h2><ul class="sm" style="line-height:1.5;padding-left:18px">
   <li>📖 Libro diario (partida doble, cuadre Debe = Haber)</li><li>📊 Estado de resultados (utilidad bruta y neta)</li><li>🏦 Balance general y balance de comprobación</li><li>🧾 Reporte de ITBMS del mes (cobrado vs. pagado)</li><li>⬇️ Excel para el contador y CSV</li><li>💾 Respaldo e importación de datos</li></ul>
   <div class="row" style="margin-top:10px">${PRUEBA.botones()}</div></div>`}}
 // Banner en todas las pantallas
 const _render=render;render=function(){const r=_render.apply(this,arguments);const m=document.getElementById('main');if(m&&!document.getElementById('prueba-ban'))m.insertAdjacentHTML('afterbegin',PRUEBA.banner());return r};
})();
