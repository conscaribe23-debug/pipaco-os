#!/usr/bin/env python3
"""Genera demo/index.html a partir de ../index.html (la app real).
Uso: python3 demo/build_demo.py   (desde la raíz del repo o desde demo/)"""
import os, re
D = os.path.dirname(os.path.abspath(__file__))
s = open(os.path.join(D, '..', 'index.html'), encoding='utf-8').read()
css = open(os.path.join(D, 'src', 'demo.css'), encoding='utf-8').read()
js = open(os.path.join(D, 'src', 'demo.js'), encoding='utf-8').read()

def rep(a, b, n=1):
    global s
    c = s.count(a)
    assert c == n, f'esperaba {n} vez/veces y hay {c}: {a[:70]!r}'
    s = s.replace(a, b)

DESC = 'Demo pública de Pip & Co: catálogo de pipas (agua de pipa) de Boquete y generación de comprobantes de venta en PDF para enviar por WhatsApp o correo. Sin registro.'
URL = 'https://conscaribe23-debug.github.io/pipaco-os/demo/'
rep('<link rel="manifest" href="manifest.webmanifest">\n', '')
s = s.replace('href="icons/', 'href="../icons/')
rep('<title>PipaCo OS PRO</title>', f'''<title>Pip & Co – Catálogo y demo de comprobantes</title>
<meta name="description" content="{DESC}">
<link rel="canonical" href="{URL}">
<meta property="og:type" content="website">
<meta property="og:site_name" content="Pip & Co">
<meta property="og:locale" content="es_PA">
<meta property="og:title" content="Pip & Co – Catálogo y demo de comprobantes">
<meta property="og:description" content="{DESC}">
<meta property="og:url" content="{URL}">
<meta property="og:image" content="{URL}img/og.jpg">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:image:alt" content="Pip & Co – agua de pipa de Boquete. Catálogo y demo de comprobantes">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:title" content="Pip & Co – Catálogo y demo de comprobantes">
<meta name="twitter:description" content="{DESC}">
<meta name="twitter:image" content="{URL}img/og.jpg">''')
rep('</style>\n</head>', css + '</style>\n</head>')
# Barra lateral: sin sesión
rep('Funciona sin internet. Datos guardados en este navegador.<br><button class="btn xs" style="margin-top:8px" onclick="AUTH.cerrarSesion()">🔒 Cerrar sesión</button>',
    'Demo pública: datos de ejemplo en memoria; se borran al recargar.<br><button class="btn xs" style="margin-top:8px" onclick="DEMO.reiniciar()">🔄 Reiniciar demo</button>')
rep('<small>OS PRO v2 · Agua de pipa · Boquete</small>', '<small>Demo · Agua de pipa · Boquete</small>')
# Almacenamiento: solo en memoria y con espacio de nombres propio
rep('<script>\n', '''<script>
/* DEMO: almacenamiento solo en memoria (nunca toca localStorage/sessionStorage reales) */
function memStore(){const m=new Map();return{getItem:k=>m.has(k)?m.get(k):null,setItem:(k,v)=>{m.set(k,String(v))},removeItem:k=>{m.delete(k)},clear:()=>m.clear(),key:i=>[...m.keys()][i]??null,get length(){return m.size}}}
const DEMO_LS=memStore(),DEMO_SS=memStore();
''')
body_start = s.index('DEMO_SS=memStore();') + len('DEMO_SS=memStore();')
head, tail = s[:body_start], s[body_start:]
tail = re.sub(r'\blocalStorage\b', 'DEMO_LS', tail)
tail = re.sub(r'\bsessionStorage\b', 'DEMO_SS', tail)
s = head + tail
rep("const KEY='pipaco_os_v2';", "const KEY='pipaco_demo_v1';")
rep("'pipaco_auth_v1'", "'pipaco_demo_auth'")
rep("'pipaco_ses_v1'", "'pipaco_demo_ses'")
s = s.replace("'pipaco_fotos'", "'pipaco_demo_fotos'")
# Arranque sin login ni service worker
rep("document.addEventListener('DOMContentLoaded',()=>{AUTH.gate(iniciar);try{PWA.init()}catch(e){console.warn(e)}});",
    "document.addEventListener('DOMContentLoaded',()=>DEMO.arrancar());")
rep(' ${AUTH.card()}\n ${PWA.card()}\n', ' <div class="card"><h2>🧪 Demo</h2><div class="sm mu">Esta es la demo pública: sin cuentas, sin instalación y sin guardar nada. La app real de Pip & Co requiere acceso con correo y contraseña.</div><div class="row" style="margin-top:8px"><button class="btn" onclick="DEMO.reiniciar()">🔄 Reiniciar demo</button></div></div>\n')
# Banner fijo de demo
i = s.index("const demo=S.demo?`"); j = s.index(":'';", i) + 4
s = s[:i] + "const demo=DEMO.banner();" + s[j:]
# Navegación: el catálogo es la portada
rep("const VIEWS=[['inicio','🏠','Inicio'],", "const VIEWS=[['catalogo','🥥','Catálogo'],['inicio','🏠','Inicio'],")
i = s.index("const BNAV=["); j = s.index("];", i) + 2
s = s[:i] + "const BNAV=[['catalogo','🥥','Catálogo'],['facturar','📄','Comprobantes'],['ventas','🧾','Ventas'],['inicio','🏠','Inicio'],['mas','☰','Más']];" + s[j:]
rep("const UI={view:'inicio',", "const UI={view:'catalogo',")
rep(";document.title=(S.config.negocio||'PipaCo')+' OS PRO';", ";document.title='Pip & Co – Catálogo y demo de comprobantes';if(typeof DEMO!=='undefined')setTimeout(DEMO.barra,0);")
# Numeración DEMO-0001 y marca de agua / pie
rep("return String(S.config.facPrefijo??'')+String(seq).padStart(6,'0')", "return String(S.config.facPrefijo??'')+String(seq).padStart(4,'0')")
rep("watermark(s,color){cur.push(`q ${D.rgb(color)} rg 0.92 0.39 -0.39 0.92 120 230 cm BT /F2 90 Tf 0 0 Td (${PDFC.esc(PDFC.enc(s))}) Tj ET Q`);return D},",
    "watermark(s,color,size=90){const w=PDFC.w(s,size,true),a=Math.PI/180*36,co=Math.cos(a),si=Math.sin(a);const x=306-co*w/2+si*size*0.36,y=396-si*w/2-co*size*0.36;cur.push(`q ${D.rgb(color)} rg ${co.toFixed(4)} ${si.toFixed(4)} ${(-si).toFixed(4)} ${co.toFixed(4)} ${x.toFixed(2)} ${y.toFixed(2)} cm BT /F2 ${size} Tf 0 0 Td (${PDFC.esc(PDFC.enc(s))}) Tj ET Q`);return D},")
rep("if(!compActivo(c))D.watermark('ANULADO','#fecaca');else if(ejemplo)D.watermark('EJEMPLO','#e4e4e7');",
    "if(!compActivo(c))D.watermark('ANULADO – DEMO','#fecaca',64);else D.watermark('DEMO – SIN VALIDEZ','#fde68a',60);")
rep("Generado con PipaCo OS`,{size:7,align:'center',color:'#a1a1aa'});",
    "DEMO – SIN VALIDEZ · documento de demostración (PipaCo OS)`,{size:7,align:'center',color:'#a1a1aa'});D.text(306,759,'DEMO – SIN VALIDEZ: datos de ejemplo. No es una factura ni un comprobante real.',{size:8,bold:true,align:'center',color:'#dc2626'});")
# Capa de la demo (catálogo, carrito, semilla)
k = s.rindex('</script>')
s = s[:k] + '\n' + js + '\n' + s[k:]
assert 'serviceWorker.register' in s  # existe en PWA pero nunca se llama
assert 'PWA.init()' not in s
open(os.path.join(D, 'index.html'), 'w', encoding='utf-8').write(s)
print('demo/index.html', len(s), 'bytes')
