# PipaCo — Demo pública (Pip & Co)

URL: https://conscaribe23-debug.github.io/pipaco-os/demo/

Demo sin login ni registro: catálogo de productos de Pip & Co + generación de
comprobantes de venta en PDF (marca de agua **DEMO – SIN VALIDEZ**, numeración `DEMO-0001`)
y envío por WhatsApp / correo / compartir del sistema.

## Cómo se genera
`demo/index.html` **no se edita a mano**: se genera desde la app real (`../index.html`):

```
python3 demo/build_demo.py
```

- `src/demo.js`: catálogo, carrito, datos de ejemplo (clientes ficticios, p. ej. "Hotel Ejemplo").
- `src/demo.css`: estilos del catálogo.
- El script quita el login, el manifest y el service worker, y cambia el almacenamiento a
  **memoria** (`DEMO_LS`/`DEMO_SS`, claves `pipaco_demo_*`): nunca toca `pipaco_os_v2`,
  `pipaco_auth_v1` ni `pipaco_ses_v1`. Al recargar o tocar "Reiniciar demo" vuelven los datos de ejemplo.
- El `sw.js` de la app principal ignora todo lo que está bajo `/demo/`.

Después de cambiar la app (`index.html`) vuelve a correr el script y publica.

## Imágenes (reemplazables)
Todas en `demo/img/`. Para usar fotos reales de Pip & Co, reemplaza el archivo **con el mismo nombre**
(recomendado: WebP o JPG 800×600, 4:3; el hero 1200×520; og 1200×630):

| Archivo | Uso | Origen / licencia |
|---|---|---|
| `pipa-entera.webp` | Pipa entera | Foto de Matheus Bertelli, Pexels — https://www.pexels.com/photo/fresh-green-coconuts-at-outdoor-market-30893235/ |
| `pipa-abierta.webp` | Pipa abierta con pajilla | Foto de Juliana Finamore, Pexels — https://www.pexels.com/photo/green-coconut-fruit-with-drinking-straw-11371113/ |
| `agua-embotellada.webp` | Agua embotellada 500 ml y caja ×24 | Ilustración propia creada para Pip & Co (SVG renderizado), sin restricciones |
| `negocios.webp` | Ciento para negocios (hoteles/restaurantes) | Foto de Augustinus Martinus Noppé, Pexels — https://www.pexels.com/photo/glass-of-juice-beside-a-coconut-drink-14089724/ |
| `hero.webp` | Portada | Foto de Atlantic Ambience, Pexels — https://www.pexels.com/photo/green-coconuts-319894/ |
| `og.jpg` | Vista previa al compartir el enlace (og:image) | Composición propia con `hero` + ilustración |

Licencia de Pexels (https://www.pexels.com/license/): uso gratuito, comercial incluido, sin atribución obligatoria
(se da crédito en el pie de la demo). No se enlazan en caliente: están copiadas y optimizadas en este repo.
Se marcan como "Imágenes ilustrativas" en la página.

Precios: los de ejemplo de la app (pipa $1.25, abierta $1.50, botella $2.50, caja ×24 $42, ciento $85),
marcados como "precio de ejemplo". Se cambian en `src/demo.js` (función `semilla`).
