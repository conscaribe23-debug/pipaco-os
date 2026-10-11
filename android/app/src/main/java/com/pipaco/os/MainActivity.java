package com.pipaco.os;

import android.Manifest;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
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

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.FragmentActivity;
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
public class MainActivity extends FragmentActivity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/assets/www/index.html";
    private static final int REQ_FILE = 1, REQ_CAM_PERM = 2, REQ_STORAGE_PERM = 3;

    private WebView web;

    // ---- Bloqueo con huella / PIN del teléfono ----
    private static final long RELOCK_MS = 60_000L;   // volver a pedir tras ~1 min en segundo plano
    private FrameLayout root;
    private LinearLayout cover;
    private TextView coverMsg;
    private Button coverBtn;
    private boolean locked = true, authInProgress = false, contentLoaded = false, noLockNoticeShown = false, autoPrompt = true;
    private long backgroundSince = 0L;
    private Bundle pendingState;
    private Updater updater;
    private boolean autoCheckDone = false;

    void js(String code) { if (web != null) web.evaluateJavascript(code, null); }
    private ValueCallback<Uri[]> fileCb;
    private WebChromeClient.FileChooserParams pendingParams;
    private Uri cameraUri;
    private String[] pendingSave; // nombre, mime, base64 (Android 7–9 sin permiso aún)

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        updater = new Updater(this);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#09090b"));
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#09090b"));
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        cover = crearPantallaBloqueo();
        root.addView(cover, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.setVisibility(View.INVISIBLE);
        setContentView(root);

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
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!autoCheckDone && url != null && url.contains(HOST)) { autoCheckDone = true; updater.autoCheckOncePerDay(); }
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

        // El contenido NO se carga hasta desbloquear.
        pendingState = savedInstanceState;
    }

    // ===================== Bloqueo biométrico =====================
    private LinearLayout crearPantallaBloqueo() {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(Color.parseColor("#09090b"));
        l.setPadding((int) (32 * d), 0, (int) (32 * d), 0);
        l.setClickable(true); // bloquea toques al WebView de abajo
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(ContextCompat.getDrawable(this, R.mipmap.ic_launcher));
        l.addView(ic, new LinearLayout.LayoutParams((int) (96 * d), (int) (96 * d)));
        TextView t = new TextView(this);
        t.setText("PipaCo OS PRO");
        t.setTextColor(Color.parseColor("#f4f4f5"));
        t.setTextSize(22);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, (int) (16 * d), 0, (int) (6 * d));
        l.addView(t);
        coverMsg = new TextView(this);
        coverMsg.setText("🔒 App bloqueada");
        coverMsg.setTextColor(Color.parseColor("#a1a1aa"));
        coverMsg.setTextSize(15);
        coverMsg.setGravity(Gravity.CENTER);
        coverMsg.setPadding(0, 0, 0, (int) (20 * d));
        l.addView(coverMsg);
        coverBtn = new Button(this);
        coverBtn.setText("🔓 Desbloquear");
        coverBtn.setAllCaps(false);
        coverBtn.setTextSize(17);
        coverBtn.setTextColor(Color.parseColor("#111111"));
        coverBtn.setBackgroundColor(Color.parseColor("#fbbf24"));
        coverBtn.setPadding((int) (28 * d), (int) (12 * d), (int) (28 * d), (int) (12 * d));
        coverBtn.setOnClickListener(v -> pedirDesbloqueo());
        l.addView(coverBtn);
        Button salir = new Button(this);
        salir.setText("Salir");
        salir.setAllCaps(false);
        salir.setTextColor(Color.parseColor("#a1a1aa"));
        salir.setBackgroundColor(Color.TRANSPARENT);
        salir.setOnClickListener(v -> finish());
        l.addView(salir);
        return l;
    }

    private boolean pantallaConBloqueo() {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.isDeviceSecure();
    }

    private int autenticadores() {
        return BiometricManager.Authenticators.BIOMETRIC_STRONG
                | BiometricManager.Authenticators.BIOMETRIC_WEAK
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
    }

    private void bloquear() {
        locked = true;
        autoPrompt = true;
        cover.setVisibility(View.VISIBLE);
        web.setVisibility(View.INVISIBLE);
        coverMsg.setText("🔒 App bloqueada");
    }

    private void desbloquear() {
        locked = false;
        authInProgress = false;
        cover.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
        if (!contentLoaded) {
            contentLoaded = true;
            if (pendingState != null) web.restoreState(pendingState);
            else web.loadUrl(START);
            pendingState = null;
        }
    }

    private void pedirDesbloqueo() {
        if (authInProgress || !locked) return;
        // Sin PIN/patrón/contraseña en el teléfono: se abre, pero recomendamos configurarlo.
        if (!pantallaConBloqueo()) {
            desbloquear();
            if (!noLockNoticeShown) {
                noLockNoticeShown = true;
                new AlertDialog.Builder(this)
                        .setTitle("Protege tu teléfono")
                        .setMessage("Tu teléfono no tiene bloqueo de pantalla. Te recomendamos configurar un PIN, patrón o huella en Ajustes → Seguridad para que nadie más pueda abrir PipaCo.")
                        .setPositiveButton("Ir a Ajustes", (dd, w) -> {
                            try { startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)); } catch (Exception ignored) { }
                        })
                        .setNegativeButton("Ahora no", null)
                        .show();
            }
            return;
        }
        BiometricPrompt.PromptInfo.Builder b = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Desbloquear PipaCo")
                .setSubtitle("Usa tu huella o el PIN del teléfono")
                .setConfirmationRequired(false);
        if (Build.VERSION.SDK_INT >= 30) {
            b.setAllowedAuthenticators(autenticadores());
        } else {
            // Android 7–10: huella si hay, si no PIN/patrón/contraseña (KeyguardManager vía androidx.biometric).
            b.setDeviceCredentialAllowed(true);
        }
        BiometricPrompt prompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                        desbloquear();
                    }
                    @Override
                    public void onAuthenticationError(int code, CharSequence msg) {
                        authInProgress = false;
                        if (code == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL && !pantallaConBloqueo()) {
                            pedirDesbloqueo(); // se abre con aviso
                            return;
                        }
                        boolean cancel = code == BiometricPrompt.ERROR_USER_CANCELED
                                || code == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                || code == BiometricPrompt.ERROR_CANCELED;
                        autoPrompt = false; // no volver a abrir solo: el usuario toca Desbloquear o Salir
                        coverMsg.setText(cancel ? "🔒 Toca “Desbloquear” para entrar" : "🔒 " + msg);
                    }
                    @Override
                    public void onAuthenticationFailed() { /* el diálogo deja reintentar */ }
                });
        authInProgress = true;
        try {
            prompt.authenticate(b.build());
        } catch (Exception e) {
            authInProgress = false;
            coverMsg.setText("🔒 No se pudo mostrar el desbloqueo: " + e.getMessage());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (locked && !authInProgress && autoPrompt) pedirDesbloqueo();
        if (!locked) updater.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!locked && !authInProgress && !isChangingConfigurations()) backgroundSince = SystemClock.elapsedRealtime();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!locked && backgroundSince > 0 && SystemClock.elapsedRealtime() - backgroundSince >= RELOCK_MS && pantallaConBloqueo()) {
            bloquear();
        }
        backgroundSince = 0L;
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

    static String jsStr(String s) {
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
        @JavascriptInterface
        public int versionCode() { return BuildConfig.VERSION_CODE; }
        @JavascriptInterface
        public void checkUpdate(boolean quiet) { updater.check(quiet); }
    }

    @Override
    public void onBackPressed() {
        if (locked) { finish(); return; }
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (contentLoaded) web.saveState(out);
    }
}
