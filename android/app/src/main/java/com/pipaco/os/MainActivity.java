package com.pipaco.os;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * PipaCo OS PRO para Android: WebView que carga la app incluida en el APK
 * (assets/www) desde https://appassets.androidplatform.net, así funciona sin
 * internet y con contexto seguro (cifrado de la contraseña, localStorage, IndexedDB).
 */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/assets/www/index.html";
    private static final int REQ_FILE = 1, REQ_CAM_PERM = 2, REQ_STORAGE_PERM = 3;

    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private WebChromeClient.FileChooserParams pendingParams;
    private Uri cameraUri;
    private String[] pendingSave; // nombre, mime, base64 (Android 7–9 sin permiso aún)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#09090b"));
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setTextZoom(100);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                return loader.shouldInterceptRequest(req.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (HOST.equals(u.getHost())) return false;
                abrirFuera(u);
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                pendingParams = params;
                if (params.isCaptureEnabled() && Build.VERSION.SDK_INT >= 23
                        && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAM_PERM);
                } else {
                    lanzarSelector(params, true);
                }
                return true;
            }
        });
        web.addJavascriptInterface(new Puente(), "PipaCoAndroid");

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START);
    }

    private void abrirFuera(Uri u) {
        try {
            Intent i;
            if ("intent".equals(u.getScheme())) i = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME);
            else i = new Intent(Intent.ACTION_VIEW, u);
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "No hay una app para abrir este enlace", Toast.LENGTH_SHORT).show();
        }
    }

    private void lanzarSelector(WebChromeClient.FileChooserParams params, boolean camaraPermitida) {
        try {
            Intent galeria = params.createIntent();
            boolean imagen = false;
            for (String t : params.getAcceptTypes()) if (t != null && t.startsWith("image")) imagen = true;
            Intent camara = null;
            if (imagen && camaraPermitida) {
                File dir = new File(getCacheDir(), "camara");
                dir.mkdirs();
                File f = new File(dir, "factura_" + System.currentTimeMillis() + ".jpg");
                cameraUri = FileProvider.getUriForFile(this, "com.pipaco.os.fileprovider", f);
                camara = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                camara.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                camara.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else cameraUri = null;
            Intent lanzar;
            if (camara != null && params.isCaptureEnabled()) lanzar = camara;
            else {
                lanzar = Intent.createChooser(galeria, "Elegir archivo");
                if (camara != null) lanzar.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camara});
            }
            startActivityForResult(lanzar, REQ_FILE);
        } catch (ActivityNotFoundException e) {
            if (fileCb != null) fileCb.onReceiveValue(null);
            fileCb = null;
            Toast.makeText(this, "No se pudo abrir la cámara/galería", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_FILE || fileCb == null) return;
        Uri[] out = null;
        if (res == RESULT_OK) {
            if (data != null && (data.getData() != null || data.getClipData() != null)) {
                out = WebChromeClient.FileChooserParams.parseResult(res, data);
            } else if (cameraUri != null) {
                out = new Uri[]{cameraUri};
            }
        }
        fileCb.onReceiveValue(out);
        fileCb = null;
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        boolean ok = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
        if (req == REQ_CAM_PERM && pendingParams != null) {
            lanzarSelector(pendingParams, ok);
        } else if (req == REQ_STORAGE_PERM && pendingSave != null) {
            final String[] p = pendingSave;
            pendingSave = null;
            String r = ok ? guardar(p[0], p[1], p[2]) : "ERROR: permiso denegado";
            final String js = "toast(" + jsStr(r.startsWith("ERROR") ? "No se pudo guardar: " + r : "💾 Guardado en Descargas: " + p[0]) + (r.startsWith("ERROR") ? ",1)" : ")");
            web.evaluateJavascript(js, null);
        }
    }

    private static String jsStr(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ") + "'";
    }

    /** Guarda en la carpeta Descargas del teléfono. Devuelve la ruta o "ERROR: ...". */
    private String guardar(String nombre, String mime, String b64) {
        try {
            if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
            mime = mime.split(";")[0].trim();
            nombre = nombre.replaceAll("[\\\\/:*?\"<>|]", "_");
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Downloads.DISPLAY_NAME, nombre);
                cv.put(MediaStore.Downloads.MIME_TYPE, mime);
                cv.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/PipaCo");
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) return "ERROR: sin acceso a Descargas";
                try (OutputStream os = getContentResolver().openOutputStream(uri)) { os.write(bytes); }
                return "Descargas/PipaCo/" + nombre;
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "PipaCo");
                dir.mkdirs();
                File f = new File(dir, nombre);
                try (FileOutputStream os = new FileOutputStream(f)) { os.write(bytes); }
                return f.getAbsolutePath();
            }
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /** Disponible en JS como window.PipaCoAndroid */
    private class Puente {
        @JavascriptInterface
        public String saveFile(String nombre, String mime, String b64) {
            if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                pendingSave = new String[]{nombre, mime, b64};
                runOnUiThread(() -> requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE_PERM));
                return "ERROR: concede el permiso de almacenamiento y se guardará";
            }
            String r = guardar(nombre, mime, b64);
            if (!r.startsWith("ERROR")) runOnUiThread(() -> Toast.makeText(MainActivity.this, "Guardado en " + r, Toast.LENGTH_LONG).show());
            return r;
        }
        @JavascriptInterface
        public String version() { return BuildConfig.VERSION_NAME; }
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }
}
