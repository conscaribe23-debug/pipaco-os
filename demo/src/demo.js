/* ================= PipaCo DEMO pública =================
   - Sin login, sin service worker, sin tocar los datos reales:
     todo vive en memoria (DEMO_LS/DEMO_SS) y se vuelve a sembrar al abrir.
   - Las imágenes están en demo/img (ver demo/README.md para reemplazarlas). */
const DEMO={cart:{},tab:'todos',desdeCarrito:false,
 CAT:[
  {key:'pipa',pid:'pipa',canal:'carrito',cat:'detal',img:'img/pipa-entera.webp',nombre:'Pipa entera',desc:'Coco verde fresco, cosechado y llevado al carrito el mismo día. Agua 100% natural, sin azúcar añadida.',unidad:'unidad',badge:'Al detal'},
  {key:'abierta',pid:'pipa_abierta',canal:'carrito',cat:'detal',img:'img/pipa-abierta.webp',nombre:'Pipa abierta lista para tomar',desc:'La abrimos al momento y te la entregamos fría, con pajilla. Perfecta para el camino.',unidad:'unidad',badge:'Al detal'},
  {key:'agua',pid:'agua',canal:'embotellado',cat:'embotellado',img:'img/agua-embotellada.webp',nombre:'Agua de pipa embotellada 500 ml',desc:'Agua de pipa pura, envasada en frío. Mantener refrigerada y consumir fría.',unidad:'botella',badge:'Embotellado'},
  {key:'caja',pid:'caja24',canal:'distribucion',cat:'negocios',img:'img/agua-embotellada.webp',nombre:'Caja de agua de pipa (24 × 500 ml)',desc:'Para cafeterías, minisúpers y hoteles. Entrega en Boquete y alrededores.',unidad:'caja',badge:'Para negocios'},
  {key:'ciento',pid:'ciento',canal:'distribucion',cat:'negocios',img:'img/negocios.webp',nombre:'Ciento de pipas para negocios',desc:'100 pipas para hoteles, restaurantes y eventos, con entrega programada y crédito para clientes frecuentes.',unidad:'ciento',badge:'Para negocios'}],
 TABS:[['todos','Todo'],['detal','Al detal'],['negocios','Para negocios'],['embotellado','Embotellado']],
 precio(it){return precioCanal(P(it.pid),it.canal)},
 semilla(){const s=datosEjemplo();const t=hoy();
  Object.assign(s.config,{negocio:'Pip & Co',propietario:'(demo)',telefono:'',ubicacion:'Boquete, Chiriquí',emisorDir:'Boquete, Chiriquí',emisorTel:'',emisorCorreo:'',facTitulo:'Comprobante de venta',facPrefijo:'DEMO-',facInicio:1,
   facNota:'DEMO – SIN VALIDEZ. Documento de demostración con datos de ejemplo: no es una factura fiscal ni un comprobante real. En Panamá las facturas fiscales se emiten por el Sistema de Factura Electrónica (SFEP) de la DGI.'});
  const agua=s.productos.find(p=>p.id==='agua');if(agua)agua.nombre='Agua de pipa embotellada 500 ml';
  const pipa=s.productos.find(p=>p.id==='pipa');if(pipa)pipa.nombre='Pipa entera (coco verde)';
  s.productos.push(prodBase({id:'pipa_abierta',nombre:'Pipa abierta con pajilla',tipo:'producido',r1p:'pipa',r1c:1,precioCarrito:1.5,precioDistribucion:1.1,precioEmbotellado:1.5,costoExtra:0.03}),
   prodBase({id:'caja24',nombre:'Caja de agua de pipa 24 × 500 ml',tipo:'producido',r1p:'agua',r1c:24,precioCarrito:55,precioDistribucion:42,precioEmbotellado:48}),
   prodBase({id:'ciento',nombre:'Ciento de pipas (100 unidades)',tipo:'producido',r1p:'pipa',r1c:100,precioCarrito:110,precioDistribucion:85,precioEmbotellado:110}));
  const cli={cE1:{nombre:'Restaurante El Ejemplo',razon:'Restaurante El Ejemplo, S.A. (ficticio)',ruc:'0-000-0001',dv:'00',correo:'compras@restaurante.example.com'},
   cE2:{nombre:'Hotel Ejemplo',razon:'Hotel Ejemplo, S.A. (ficticio)',ruc:'0-000-0002',dv:'00',correo:'compras@hotel.example.com',direccion:'Alto Boquete (dirección ficticia)'},
   cE3:{nombre:'Minisúper Prueba',razon:'Minisúper Prueba (ficticio)',ruc:'0-000-0003',dv:'00',correo:'pedidos@minisuper.example.com'}};
  s.clientes.forEach(c=>{Object.assign(c,cli[c.id]||{});c.telefono=''});
  const S0=S;S=s;
  const v=[...s.ventas].filter(x=>x.cid==='cE2').sort((a,b)=>a.fecha<b.fecha?1:-1)[0];
  if(v){const c={id:'demo1',seq:1,numero:compNumero(1),creado:new Date().toISOString(),estado:'emitida',version:1,fecha:v.fecha,cid:'cE2',cliente:cliDeRegistro(CL('cE2')),lineas:[lineaDeVenta(v)],metodo:v.metodo,canal:'distribucion',venc:'',notas:'Ejemplo precargado',cufe:'',numFiscal:'',titulo:s.config.facTitulo,nota:s.config.facNota,emisor:COMP.emisor()};c.cliente.nombre=CL('cE2').razon;v.compId=c.id;s.comprobantes.push(c)}
  S=S0;s.demo=true;return s},
 arrancar(){S=DEMO.semilla();UI.view='catalogo';guardar();
  document.querySelector('.app').style.display='';document.getElementById('bnav').style.display='';
  AUTH.cerrarSesion=()=>toast('En la demo no hay cuentas ni sesión 🙂');A.reset=A.demo=DEMO.reiniciar;
  const g=COMP.guardar;COMP.guardar=function(){const n=S.comprobantes.length;const r=g.apply(this,arguments);if(S.comprobantes.length>n&&DEMO.desdeCarrito){DEMO.cart={};DEMO.desdeCarrito=false;DEMO.barra()}return r};
  iniciar();DEMO.barra()},
 reiniciar(){if(!confirm('¿Reiniciar la demo? Se borra lo que hiciste y vuelven los datos de ejemplo.'))return;S=DEMO.semilla();DEMO.cart={};DEMO.tab='todos';UI.comp=null;cerrarModal();guardar();UI.view='catalogo';location.hash='catalogo';render();DEMO.barra();window.scrollTo(0,0);toast('🔄 Demo reiniciada')},
 banner(){return`<div class="demo">🧪 <b>MODO DEMO – datos de ejemplo, no se guardan</b><span style="flex:1"></span><button class="btn xs" onclick="DEMO.reiniciar()">Reiniciar demo</button></div>`},
 n(){return Object.values(DEMO.cart).reduce((a,b)=>a+b,0)},
 total(){return r2(Object.entries(DEMO.cart).reduce((a,[k,q])=>{const it=DEMO.CAT.find(x=>x.key===k);return a+(it?DEMO.precio(it)*q:0)},0))},
 add(k,d){const q=Math.max(0,(DEMO.cart[k]||0)+d);if(q)DEMO.cart[k]=q;else delete DEMO.cart[k];if(d>0&&q===1){const it=DEMO.CAT.find(x=>x.key===k);toast('🛒 Agregado: '+esc(it.nombre))}if(UI.view==='catalogo')DEMO.refrescarCard(k);DEMO.barra()},
 refrescarCard(k){const el=document.getElementById('act-'+k);if(el)el.innerHTML=DEMO.acciones(k)},
 acciones(k){const q=DEMO.cart[k]||0;return q?`<div class="qty"><button aria-label="Quitar uno" onclick="DEMO.add('${k}',-1)">−</button><span>${q}</span><button aria-label="Agregar uno" onclick="DEMO.add('${k}',1)">+</button></div><span class="sm mu">en el carrito</span>`:`<button class="btn p" style="flex:1" onclick="DEMO.add('${k}',1)">＋ Agregar</button>`},
 barra(){let b=document.getElementById('cartbar');const n=DEMO.n();if(!n||UI.view!=='catalogo'){if(b)b.remove();return}
  if(!b){b=document.createElement('div');b.id='cartbar';b.className='cartbar';document.body.appendChild(b)}
  b.innerHTML=`<span>🛒 ${n} ${n===1?'artículo':'artículos'} · ${$f(DEMO.total())}</span><span style="flex:1"></span><button class="btn" onclick="DEMO.carrito()">Ver</button><button class="btn" onclick="DEMO.generar()">🧾 Generar comprobante</button>`},
 carrito(){const items=Object.entries(DEMO.cart);document.getElementById('modal').innerHTML=`<div class="modal-bg" onclick="if(event.target===this)A.cerrar()"><div class="modal" style="max-width:560px"><h3><span>🛒 Tu carrito</span><button class="btn xs" onclick="A.cerrar()">✕</button></h3>
  ${items.length?items.map(([k,q])=>{const it=DEMO.CAT.find(x=>x.key===k);const p=DEMO.precio(it);return`<div class="row" style="justify-content:space-between;border-bottom:1px solid var(--bd);padding:8px 0;gap:10px"><img src="${it.img}" alt="" style="width:56px;height:42px;object-fit:cover;border-radius:8px"><div style="flex:1"><b>${esc(it.nombre)}</b><div class="sm mu">${$f(p)} / ${it.unidad}</div></div><div class="qty"><button onclick="DEMO.add('${k}',-1);DEMO.carrito()">−</button><span>${q}</span><button onclick="DEMO.add('${k}',1);DEMO.carrito()">+</button></div><b style="min-width:70px;text-align:right">${$f(p*q)}</b></div>`}).join('')+`<div class="row" style="justify-content:space-between;margin-top:10px"><span class="mu">Total (precios de ejemplo)</span><b style="font-size:20px;color:var(--ac)">${$f(DEMO.total())}</b></div>
  <div class="fld" style="margin-top:12px"><label>¿Para quién es el comprobante?</label><select class="inp" id="dcli"><option value="">Consumidor final</option>${S.clientes.map(c=>`<option value="${c.id}">${esc(c.nombre)} (ficticio)</option>`).join('')}</select></div>
  <div class="row" style="margin-top:12px;justify-content:flex-end"><button class="btn" onclick="DEMO.vaciar()">Vaciar</button><button class="btn p" onclick="DEMO.generar(document.getElementById('dcli').value)">🧾 Generar comprobante</button></div>`:'<div class="empty">El carrito está vacío. Agrega productos del catálogo.</div>'}</div></div>`},
 vaciar(){DEMO.cart={};A.cerrar();DEMO.barra();if(UI.view==='catalogo')render()},
 generar(cid){const items=Object.entries(DEMO.cart);if(!items.length){toast('Agrega productos primero',1);return}cid=cid||'';const c=cid&&CL(cid);
  const lineas=items.map(([k,q])=>{const it=DEMO.CAT.find(x=>x.key===k);const pr=DEMO.precio(it);const t=num(P(it.pid)?.itbms);return{origen:'manual',pid:it.pid,desc:it.nombre,cant:q,pn:r2(pr/(1+t/100)),itbms:t,ex:!t,precio:pr}});
  const canal=items.some(([k])=>DEMO.CAT.find(x=>x.key===k).canal==='distribucion')?'distribucion':items.some(([k])=>DEMO.CAT.find(x=>x.key===k).canal==='embotellado')?'embotellado':'carrito';
  UI.comp=compNuevoEstado({cid,cli:c?cliDeRegistro(c):compNuevoEstado().cli,guardarCli:false,metodo:c?.metodoDef||'Efectivo',canal,lineas,notas:'Pedido desde el catálogo (demo)'});
  DEMO.desdeCarrito=true;COMP.render()},
 card(it){const p=DEMO.precio(it);return`<article class="pcard"><div class="img"><img src="${it.img}" alt="${esc(it.nombre)} (imagen ilustrativa)" loading="lazy" width="800" height="600"><span class="badge2">${it.badge}</span></div>
  <div class="body"><h3>${esc(it.nombre)}</h3><div class="d">${esc(it.desc)}</div><div class="pr"><b>${$f(p)}</b><span>/ ${it.unidad} · precio de ejemplo</span></div><div class="act" id="act-${it.key}">${DEMO.acciones(it.key)}</div></div></article>`},
 setTab(t){DEMO.tab=t;render()}
};
V.catalogo=()=>{const items=DEMO.CAT.filter(it=>DEMO.tab==='todos'||it.cat===DEMO.tab);
 return`<section class="hero" style="background-image:url(img/hero.webp)"><div class="in"><h1><span class="coco">🥥</span>Pip &amp; Co</h1><p><b>Agua de pipa fresca de Boquete, Chiriquí.</b> Elige productos, agrégalos al carrito y genera en segundos un <b>comprobante de venta en PDF</b> con los datos de Distribuidora Montaña Verde (RUC 4-769-1592 DV 36) para enviarlo por WhatsApp o correo.</p>
 <div class="cta"><button class="btn p" onclick="document.getElementById('cat').scrollIntoView({behavior:'smooth'})">Ver productos</button><button class="btn" onclick="A.ir('facturar')">📄 Ver comprobantes</button><button class="btn" onclick="A.ir('ventas')">🧾 Ventas</button></div></div></section>
 <div class="tabs" id="cat" role="tablist">${DEMO.TABS.map(([k,l])=>`<button role="tab" class="${k===DEMO.tab?'on':''}" onclick="DEMO.setTab('${k}')">${l}</button>`).join('')}</div>
 <div class="cat-grid">${items.map(DEMO.card).join('')}</div>
 <div class="card" style="margin-top:16px"><h2>¿Cómo funciona la demo?</h2><div class="sm" style="line-height:1.7">1️⃣ Agrega productos al carrito · 2️⃣ Toca <b>🧾 Generar comprobante</b> y revisa los datos · 3️⃣ Emite el comprobante <b>DEMO-0002</b> · 4️⃣ Descárgalo en PDF o envíalo por WhatsApp/correo.<br><span class="mu">Todo es de ejemplo: nada se guarda ni se envía solo; tú eliges el contacto. Los PDF llevan la marca “DEMO – SIN VALIDEZ”.</span></div></div>
 <div class="credits">Imágenes ilustrativas (no son fotos de Pip &amp; Co). Fotos de <a href="https://www.pexels.com/photo/fresh-green-coconuts-at-outdoor-market-30893235/" target="_blank" rel="noopener">Matheus Bertelli</a>, <a href="https://www.pexels.com/photo/green-coconut-fruit-with-drinking-straw-11371113/" target="_blank" rel="noopener">Juliana Finamore</a>, <a href="https://www.pexels.com/photo/glass-of-juice-beside-a-coconut-drink-14089724/" target="_blank" rel="noopener">Augustinus Martinus Noppé</a> y <a href="https://www.pexels.com/photo/green-coconuts-319894/" target="_blank" rel="noopener">Atlantic Ambience</a> en Pexels (<a href="https://www.pexels.com/license/" target="_blank" rel="noopener">Licencia de Pexels</a>). Ilustración de la botella: creada para Pip &amp; Co.<br>Precios de ejemplo. Demo de PipaCo OS · <a href="../">Acceso a la app (requiere cuenta)</a></div>`};
