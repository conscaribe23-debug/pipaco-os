package com.pipaco.demo;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** PipaCo Demo: la demo web (catálogo + comprobantes DEMO) empaquetada, con prueba de 7 días. Sin login, sin huella, sin actualizador. */
public class DemoActivity extends Activity {
    static final String HOST = "appassets.androidplatform.net";
    static final String START = "https://" + HOST + "/assets/www/index.html";
    static final String WEB_DEMO = "https://conscaribe23-debug.github.io/pipaco-os/demo/";
    static final String CORREO = "conscaribe23@gmail.com";
    /** Número de WhatsApp para contacto (formato internacional sin +, p. ej. "5076xxxxxxx"). Vacío = el usuario elige el contacto. */
    static final String TELEFONO_WHATSAPP = "";
    static final String MENSAJE = "Hola, probé la demo de Pip & Co (PipaCo) y quiero más información.";
    private static final int REQ_STORAGE = 11;

    FrameLayout root;
    WebView web;
    View pantallaFin;
    Trial.Resultado trial;
    private String[] pendingSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#09090b"));
        setContentView(root);
        trial = TrialStore.evaluar(this);
        if (trial.vencida) mostrarFin(); else cargarWeb(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        trial = TrialStore.evaluar(this);
        if (trial.vencida && pantallaFin == null) mostrarFin();
        else if (!trial.vencida && web != null) web.evaluateJavascript("try{render()}catch(e){}", null);
    }

    // ===================== Web =====================
    void cargarWeb(Bundle state) {
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setTextZoom(100);
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder().setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        web.setWebViewClient(new WebViewClientCompat() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) { return loader.shouldInterceptRequest(r.getUrl()); }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost())) return false;
                abrirFuera(u);
                return true;
            }
        });
        web.addJavascriptInterface(new Puente(), "PipaCoAndroid");
        if (state != null && web.restoreState(state) != null) return;
        web.loadUrl(START);
    }

    @Override protected void onSaveInstanceState(Bundle out) { super.onSaveInstanceState(out); if (web != null) web.saveState(out); }

    @Override public void onBackPressed() { if (web != null && pantallaFin == null && web.canGoBack()) web.goBack(); else super.onBackPressed(); }

    void abrirFuera(Uri u) {
        try {
            Intent i = "intent".equals(u.getScheme()) ? Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME) : new Intent(Intent.ACTION_VIEW, u);
            if (!"mailto".equals(u.getScheme())) i.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "No hay una app para abrir este enlace", Toast.LENGTH_SHORT).show();
        }
    }

    // ===================== Fin de la demo =====================
    int dp(float v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    Button boton(String texto, boolean principal, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(texto);
        b.setAllCaps(false);
        b.setTextSize(16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(principal ? Color.parseColor("#18181b") : Color.parseColor("#fafafa"));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(principal ? Color.parseColor("#fbbf24") : Color.parseColor("#27272a"));
        b.setBackground(g);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    TextView texto(String t, float sp, String color, boolean negrita) {
        TextView v = new TextView(this);
        v.setText(t); v.setTextSize(sp); v.setTextColor(Color.parseColor(color)); v.setGravity(Gravity.CENTER);
        if (negrita) v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setLineSpacing(0, 1.15f);
        return v;
    }

    static Uri uriWhatsApp() {
        String base = TELEFONO_WHATSAPP.isEmpty() ? "https://wa.me/" : "https://wa.me/" + TELEFONO_WHATSAPP.replaceAll("\\D", "");
        return Uri.parse(base + "?text=" + Uri.encode(MENSAJE));
    }

    static Uri uriCorreo() {
        return Uri.parse("mailto:" + CORREO + "?subject=" + Uri.encode("Demo de Pip & Co terminó") + "&body=" + Uri.encode(MENSAJE));
    }

    void mostrarFin() {
        if (web != null) { root.removeView(web); web.destroy(); web = null; }
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(28), dp(40), dp(28), dp(40));
        TextView coco = texto("🥥", 64, "#fafafa", false);
        box.addView(coco);
        TextView t = texto("La demo de Pip & Co terminó", 26, "#fbbf24", true);
        t.setPadding(0, dp(12), 0, dp(10));
        box.addView(t);
        String fecha = new SimpleDateFormat("dd/MM/yyyy", new Locale("es", "PA")).format(new Date(trial.vence()));
        box.addView(texto("Gracias por probar PipaCo: el periodo de prueba de " + Trial.DIAS + " días terminó el " + fecha + ".\n\n"
                + "Si quieres usar PipaCo en tu negocio (catálogo, ventas y comprobantes con tus datos), escríbenos.", 16, "#d4d4d8", false));
        if (trial.relojAtrasado) box.addView(texto("\nNota: la fecha del teléfono parece atrasada.", 13, "#a1a1aa", false));
        box.addView(boton("💬 Escribir por WhatsApp", true, v -> abrirFuera(uriWhatsApp())));
        box.addView(boton("✉️ Escribir a " + CORREO, false, v -> abrirFuera(uriCorreo())));
        box.addView(boton("🌐 Ver la demo web", false, v -> abrirFuera(Uri.parse(WEB_DEMO))));
        box.addView(boton("Salir", false, v -> finish()));
        sv.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pantallaFin = sv;
    }

    // ===================== Archivos =====================
    String guardar(String nombre, String mime, String b64) {
        try {
            if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
            mime = mime.split(";")[0].trim();
            nombre = nombre.replaceAll("[\\\\/:*?\"<>|]", "_");
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, nombre);
                cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PipaCo Demo");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) return "ERROR: sin acceso a Descargas";
                try (OutputStream os = getContentResolver().openOutputStream(uri)) { os.write(bytes); }
                return "Descargas/PipaCo Demo/" + nombre;
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PipaCo Demo");
                dir.mkdirs();
                File f = new File(dir, nombre);
                try (FileOutputStream os = new FileOutputStream(f)) { os.write(bytes); }
                return f.getAbsolutePath();
            }
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQ_STORAGE && pendingSave != null) {
            String[] p = pendingSave; pendingSave = null;
            boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
            String r = ok ? guardar(p[0], p[1], p[2]) : "ERROR: permiso denegado";
            Toast.makeText(this, r.startsWith("ERROR") ? "No se pudo guardar: " + r : "Guardado en " + r, Toast.LENGTH_LONG).show();
        }
    }

    String compartir(String nombre, String mime, String b64, String asunto, String texto, String email, String destino) {
        try {
            if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
            mime = mime.split(";")[0].trim();
            nombre = (nombre == null || nombre.isEmpty() ? "archivo" : nombre).replaceAll("[\\\\/:*?\"<>|]", "_");
            File dir = new File(getCacheDir(), "export");
            dir.mkdirs();
            File[] viejos = dir.listFiles();
            if (viejos != null) for (File v : viejos) if (System.currentTimeMillis() - v.lastModified() > Trial.DIA) v.delete();
            File f = new File(dir, nombre);
            try (FileOutputStream os = new FileOutputStream(f)) { os.write(Base64.decode(b64, Base64.DEFAULT)); }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            final Intent it = intentCompartir(getPackageManager(), uri, mime, asunto, texto, email, destino);
            final String m = mime;
            runOnUiThread(() -> {
                try { startActivity(it); }
                catch (ActivityNotFoundException e) {
                    try { startActivity(intentCompartir(null, uri, m, asunto, texto, email, "")); }
                    catch (Exception e2) { Toast.makeText(this, "No hay apps para compartir", Toast.LENGTH_LONG).show(); }
                }
            });
            return "OK";
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    static Intent intentCompartir(PackageManager pm, Uri uri, String mime, String asunto, String texto, String email, String destino) {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(mime);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.setClipData(ClipData.newRawUri("", uri));
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (asunto != null && !asunto.isEmpty()) send.putExtra(Intent.EXTRA_SUBJECT, asunto);
        if (texto != null && !texto.isEmpty()) send.putExtra(Intent.EXTRA_TEXT, texto);
        if (email != null && !email.isEmpty()) send.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
        if (pm != null && "whatsapp".equals(destino)) {
            for (String pkg : new String[]{"com.whatsapp", "com.whatsapp.w4b"}) {
                try { pm.getPackageInfo(pkg, 0); send.setPackage(pkg); return send; } catch (Exception ignored) {}
            }
        }
        if (pm != null && "correo".equals(destino)) {
            Intent sel = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"));
            if (sel.resolveActivity(pm) != null) {
                send.setSelector(sel);
                Intent ch = Intent.createChooser(send, "Enviar por correo");
                ch.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                return ch;
            }
        }
        Intent ch = Intent.createChooser(send, "Compartir");
        ch.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return ch;
    }

    /** Disponible en JS como window.PipaCoAndroid (mismo puente de archivos que la app real; sin login ni actualizador). */
    class Puente {
        @JavascriptInterface public String saveFile(String nombre, String mime, String b64) {
            if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                pendingSave = new String[]{nombre, mime, b64};
                runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE));
                return "ERROR: concede el permiso de almacenamiento y se guardará";
            }
            String r = guardar(nombre, mime, b64);
            if (!r.startsWith("ERROR")) runOnUiThread(() -> Toast.makeText(DemoActivity.this, "Guardado en " + r, Toast.LENGTH_LONG).show());
            return r;
        }
        @JavascriptInterface public String shareFile(String nombre, String mime, String b64, String asunto, String texto, String email, String destino) {
            return compartir(nombre, mime, b64, asunto, texto, email, destino);
        }
        @JavascriptInterface public String version() { return BuildConfig.VERSION_NAME + " (demo)"; }
        @JavascriptInterface public int demoDiasRestantes() {
            Trial.Resultado r = TrialStore.evaluar(DemoActivity.this);
            trial = r;
            if (r.vencida) runOnUiThread(() -> { if (pantallaFin == null) mostrarFin(); });
            return r.diasRestantes;
        }
        @JavascriptInterface public String demoVence() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(trial.vence())); }
    }
}
