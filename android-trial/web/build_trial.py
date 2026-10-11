#!/usr/bin/env python3
"""Genera el index.html de PipaCo Prueba a partir de la app real (../../index.html), SIN modificarla.
Uso: python3 android-trial/web/build_trial.py [salida]   (por defecto: android-trial/app/src/main/assets/www/index.html)"""
import json, os, sys
D = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(D, '..', '..'))
out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(D, '..', 'app', 'src', 'main', 'assets', 'www', 'index.html')
cfg = json.load(open(os.path.join(D, 'trial-config.json'), encoding='utf-8'))
cfg = {k: v for k, v in cfg.items() if not k.startswith('_')}
s = open(os.path.join(REPO, 'index.html'), encoding='utf-8').read()

def rep(a, b, n=1):
    global s
    c = s.count(a)
    assert c == n, f'esperaba {n} y hay {c}: {a[:80]!r}'
    s = s.replace(a, b)

rep('<link rel="manifest" href="manifest.webmanifest">\n', '')
rep('<title>PipaCo OS PRO</title>', '<title>PipaCo Prueba</title>')
rep('<script>\n', '<script>\nwindow.PIPACO_TRIAL_CFG=' + json.dumps(cfg, ensure_ascii=False) + ';\n')
# Sin service worker ni botón de instalar (es un APK)
rep("document.addEventListener('DOMContentLoaded',()=>{AUTH.gate(iniciar);try{PWA.init()}catch(e){console.warn(e)}});",
    "document.addEventListener('DOMContentLoaded',()=>{AUTH.gate(iniciar)});")
rep(' ${PWA.card()}\n', ' ${PRUEBA.card()}\n')
# PDF: marca de agua centrada "VERSIÓN DE PRUEBA" + pie
rep("watermark(s,color){cur.push(`q ${D.rgb(color)} rg 0.92 0.39 -0.39 0.92 120 230 cm BT /F2 90 Tf 0 0 Td (${PDFC.esc(PDFC.enc(s))}) Tj ET Q`);return D},",
    "watermark(s,color,size=90){const w=PDFC.w(s,size,true),a=Math.PI/180*36,co=Math.cos(a),si=Math.sin(a);const x=306-co*w/2+si*size*0.36,y=396-si*w/2-co*size*0.36;cur.push(`q ${D.rgb(color)} rg ${co.toFixed(4)} ${si.toFixed(4)} ${(-si).toFixed(4)} ${co.toFixed(4)} ${x.toFixed(2)} ${y.toFixed(2)} cm BT /F2 ${size} Tf 0 0 Td (${PDFC.esc(PDFC.enc(s))}) Tj ET Q`);return D},")
rep("if(!compActivo(c))D.watermark('ANULADO','#fecaca');else if(ejemplo)D.watermark('EJEMPLO','#e4e4e7');",
    "if(!compActivo(c))D.watermark('ANULADO','#fecaca');D.watermark(TRIAL.marcaPdf,'#bfdbfe',58);")
rep("Generado con PipaCo OS`,{size:7,align:'center',color:'#a1a1aa'});",
    "Generado con PipaCo — ${TRIAL.marcaPdf}`,{size:7,align:'center',color:'#a1a1aa'});D.text(306,759,TRIAL.marcaPdf+' de PipaCo',{size:8,bold:true,align:'center',color:'#2563eb'});")
# Datos de inicio neutros: cada usuario de la prueba pone los suyos (no se usan los datos personales de Luis/Pip & Co)
rep("const EMISOR_DEF={emisorNombre:'Pip & Co',emisorRazon:'Distribuidora Montaña Verde',emisorRuc:'4-769-1592',emisorDv:'36'};",
    "const EMISOR_DEF={emisorNombre:'',emisorRazon:'',emisorRuc:'',emisorDv:''};")
rep("config:{negocio:'PipaCo',propietario:'Luis Gabriel Delgado',telefono:'',ubicacion:'Boquete, Chiriquí',",
    "config:{negocio:'Mi negocio',propietario:'',telefono:'',ubicacion:'',")
# Pantalla de acceso: cada usuario crea su propio correo/contraseña (sin huella)
rep("AUTH_EMAIL_DEF='conscaribe23@gmail.com';", "AUTH_EMAIL_DEF='';")
rep('font-size:22px">PipaCo OS PRO</h1>', 'font-size:22px">PipaCo Prueba</h1>')
rep("'📱 App Android '+esc(PipaCoAndroid.version())+' · normalmente entras con tu huella; esta contraseña es el respaldo'",
    "'🧪 PipaCo Prueba '+esc(PipaCoAndroid.version())+' · versión de prueba de '+TRIAL.dias+' días'")
css = open(os.path.join(D, 'trial.css'), encoding='utf-8').read()
js = open(os.path.join(D, 'trial.js'), encoding='utf-8').read()
rep('</style>\n</head>', css + '</style>\n</head>')
k = s.rindex('</script>')
s = s[:k] + '\n' + js + '\n' + s[k:]
os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
open(out, 'w', encoding='utf-8').write(s)
import shutil
ico = os.path.join(os.path.dirname(os.path.abspath(out)), 'icons'); os.makedirs(ico, exist_ok=True)
for n in ('apple-touch-icon.png', 'favicon-64.png', 'icon-192.png'):
    shutil.copyfile(os.path.join(REPO, 'icons', n), os.path.join(ico, n))
print('PipaCo Prueba ->', out, len(s), 'bytes', cfg)
