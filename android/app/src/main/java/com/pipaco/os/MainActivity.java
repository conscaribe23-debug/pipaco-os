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
        prompt = crearPrompt(); // BiometricPrompt se crea en onCreate (requisito de androidx.biometric)
        coverDiag.setText(diagnostico());
        registrar(savedInstanceState == null ? "app abierta (bloqueada)" : "app recreada (bloqueada)");
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
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                currentUrl = url == null ? "" : url;
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

    // ===================== Bloqueo biométrico (2.2.1) =====================
    // Reglas: la app SOLO se abre con (a) éxito de BiometricPrompt, (b) éxito del PIN/patrón del
    // teléfono (KeyguardManager), (c) teléfono SIN bloqueo de pantalla (con aviso visible), o
    // (d) botón explícito "Entrar con correo y contraseña" tras un error (cierra la sesión interna).
    private static final int REQ_KEYGUARD = 7;
    private BiometricPrompt prompt;
    private TextView coverDiag;
    private Button btnPin, btnCorreo;
    private int fallos = 0;
    // "Solo la huella": token de un solo uso que la app web consume tras un desbloqueo nativo.
    private volatile String unlockToken = null;
    private volatile long unlockTokenAt = 0L;
    private volatile String currentUrl = "";
    private static final long TOKEN_TTL_MS = 120_000L;

    private void emitirToken() {
        byte[] b = new byte[32];
        new java.security.SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        unlockToken = sb.toString();
        unlockTokenAt = SystemClock.elapsedRealtime();
    }

    synchronized String consumirToken() {
        String t = unlockToken;
        unlockToken = null;
        if (t == null || locked) return "";
        if (SystemClock.elapsedRealtime() - unlockTokenAt > TOKEN_TTL_MS) return "";
        Uri u = Uri.parse(currentUrl == null ? "" : currentUrl);
        if (!HOST.equals(u.getHost())) return ""; // solo la app incluida en el APK
        return t;
    }

    /** Llamado desde la web (Cerrar sesión / fin del día): vuelve a la pantalla de huella. */
    void bloquearDesdeWeb() {
        if (locked) return;
        if (!pantallaConBloqueo()) { js("AUTH.pantalla()"); return; } // sin bloqueo del teléfono: contraseña de PipaCo
        bloquear("sesión cerrada desde la app");
        autoPrompt = false;
        root.post(this::pedirDesbloqueo);
    }

    private LinearLayout crearPantallaBloqueo() {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setGravity(Gravity.CENTER);
        l.setBackgroundColor(Color.parseColor("#09090b"));
        l.setPadding((int) (28 * d), 0, (int) (28 * d), 0);
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
        coverMsg.setTextColor(Color.parseColor("#e4e4e7"));
        coverMsg.setTextSize(15);
        coverMsg.setGravity(Gravity.CENTER);
        coverMsg.setPadding(0, 0, 0, (int) (18 * d));
        l.addView(coverMsg);
        coverBtn = boton("🔓 Desbloquear", true, d);
        coverBtn.setOnClickListener(v -> reintentar());
        l.addView(coverBtn);
        btnPin = boton("🔢 Usar PIN / patrón del teléfono", false, d);
        btnPin.setOnClickListener(v -> { autoPrompt = false; cancelarPrompt(); usarCredencialTelefono(); });
        btnPin.setVisibility(View.GONE);
        l.addView(btnPin);
        btnCorreo = boton("✉️ Entrar con correo y contraseña de PipaCo", false, d);
        btnCorreo.setOnClickListener(v -> { cancelarPrompt(); desbloquear("correo y contraseña (alternativa)", true, false); });
        btnCorreo.setVisibility(View.GONE);
        l.addView(btnCorreo);
        Button salir = new Button(this);
        salir.setText("Salir");
        salir.setAllCaps(false);
        salir.setTextColor(Color.parseColor("#a1a1aa"));
        salir.setBackgroundColor(Color.TRANSPARENT);
        salir.setOnClickListener(v -> finish());
        l.addView(salir);
        coverDiag = new TextView(this);
        coverDiag.setTextColor(Color.parseColor("#71717a"));
        coverDiag.setTextSize(11);
        coverDiag.setGravity(Gravity.CENTER);
        coverDiag.setPadding(0, (int) (18 * d), 0, 0);
        l.addView(coverDiag);
        return l;
    }

    private Button boton(String txt, boolean principal, float d) {
        Button b = new Button(this);
        b.setText(txt);
        b.setAllCaps(false);
        b.setTextSize(principal ? 17 : 14);
        b.setTextColor(Color.parseColor(principal ? "#111111" : "#f4f4f5"));
        b.setBackgroundColor(Color.parseColor(principal ? "#fbbf24" : "#202024"));
        b.setPadding((int) (22 * d), (int) (12 * d), (int) (22 * d), (int) (12 * d));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (8 * d);
        b.setLayoutParams(lp);
        return b;
    }

    private BiometricPrompt crearPrompt() {
        return new BiometricPrompt(this, ContextCompat.getMainExecutor(this), new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult r) {
                authInProgress = false;
                int tipo = r.getAuthenticationType();
                desbloquear(tipo == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC ? "huella"
                        : tipo == BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL ? "PIN/patrón del teléfono" : "desbloqueo del teléfono (tipo " + tipo + ")", false, true);
            }
            @Override
            public void onAuthenticationError(int code, CharSequence msg) {
                authInProgress = false;
                autoPrompt = false; // nunca reabrir solo en bucle
                registrar("BiometricPrompt error " + code + ": " + msg);
                if (!locked) return;
                if (code == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL && !pantallaConBloqueo()) { pedirDesbloqueo(); return; }
                boolean cancel = code == BiometricPrompt.ERROR_USER_CANCELED || code == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        || code == BiometricPrompt.ERROR_CANCELED;
                if (cancel) { mostrarError("Desbloqueo cancelado (código " + code + "). Toca “Desbloquear” para intentar de nuevo.", false); return; }
                fallos++;
                mostrarError("No se pudo usar la huella (error " + code + ": " + msg + ").", true);
                // Errores irrecuperables (bloqueo por intentos, sensor no disponible, sin huellas…): ir al PIN del teléfono.
                if (code != BiometricPrompt.ERROR_TIMEOUT) usarCredencialTelefono();
            }
            @Override
            public void onAuthenticationFailed() { registrar("huella no reconocida (se puede reintentar)"); }
        });
    }

    private boolean pantallaConBloqueo() {
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.isDeviceSecure();
    }

    private static String nombreCan(int c) {
        switch (c) {
            case BiometricManager.BIOMETRIC_SUCCESS: return "lista";
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED: return "sin registrar";
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE: return "sin sensor";
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE: return "sensor no disponible";
            case BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED: return "requiere actualización";
            case BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED: return "no soportado";
            default: return "desconocido";
        }
    }

    private int canHuella() {
        BiometricManager bm = BiometricManager.from(this);
        try {
            return Build.VERSION.SDK_INT >= 30 ? bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) : bm.canAuthenticate();
        } catch (Throwable e) { return -99; }
    }

    private int canDebil() {
        try { return BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK); }
        catch (Throwable e) { return -99; }
    }

    private int canHuellaOPin() {
        try {
            return BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG
                    | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        } catch (Throwable e) { return -99; }
    }

    String diagnostico() {
        int h = canHuella();
        String s = "PipaCo " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ") · " + Build.MANUFACTURER + " " + Build.MODEL
                + " · Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")"
                + "\nBloqueo de pantalla: " + (pantallaConBloqueo() ? "sí" : "NO")
                + " · Huella: " + nombreCan(h) + " (" + h + ")";
        if (Build.VERSION.SDK_INT >= 30) {
            int w = canDebil(), c = canHuellaOPin();
            s += " · Biometría débil: " + nombreCan(w) + " (" + w + ") · Huella/PIN: " + nombreCan(c) + " (" + c + ")";
        }
        return s;
    }

    private void registrar(String ev) {
        android.content.SharedPreferences p = getSharedPreferences("pipaco_lock", MODE_PRIVATE);
        String linea = new java.text.SimpleDateFormat("dd/MM HH:mm:ss", java.util.Locale.US).format(new java.util.Date()) + " " + ev;
        String prev = p.getString("log", "");
        String all = linea + (prev.isEmpty() ? "" : "\n" + prev);
        String[] ls = all.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(20, ls.length); i++) sb.append(i > 0 ? "\n" : "").append(ls[i]);
        p.edit().putString("log", sb.toString()).apply();
    }

    String registro() { return getSharedPreferences("pipaco_lock", MODE_PRIVATE).getString("log", ""); }

    private void mostrarError(String msg, boolean conAlternativas) {
        coverMsg.setText("🔒 " + msg);
        coverDiag.setText(diagnostico());
        if (pantallaConBloqueo()) btnPin.setVisibility(View.VISIBLE);
        if (conAlternativas || fallos > 0 || !pantallaConBloqueo()) btnCorreo.setVisibility(View.VISIBLE);
    }

    private void bloquear(String motivo) {
        locked = true;
        autoPrompt = true;
        cover.setVisibility(View.VISIBLE);
        web.setVisibility(View.INVISIBLE);
        coverMsg.setText("🔒 App bloqueada");
        coverDiag.setText(diagnostico());
        btnPin.setVisibility(View.GONE);
        btnCorreo.setVisibility(View.GONE);
        registrar("bloqueada: " + motivo);
    }

    private void desbloquear(String metodo, boolean forzarLoginInterno, boolean nativo) {
        if (!locked) return;
        locked = false;
        authInProgress = false;
        fallos = 0;
        registrar("desbloqueada con " + metodo + (nativo ? " (entra directo)" : " (pide contraseña PipaCo)"));
        if (nativo) emitirToken(); else unlockToken = null;
        cover.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
        if (!contentLoaded) {
            contentLoaded = true;
            if (pendingState != null) web.restoreState(pendingState);
            else web.loadUrl(START);
            pendingState = null;
        } else if (forzarLoginInterno) {
            js("try{sessionStorage.removeItem('pipaco_ses_v1');AUTH.pantalla()}catch(e){}");
        } else if (nativo) {
            js("try{typeof AUTH!=='undefined'&&AUTH.nativo&&AUTH.nativo()}catch(e){}");
        }
        Toast.makeText(this, "🔓 Desbloqueado con " + metodo, Toast.LENGTH_SHORT).show();
    }

    private void cancelarPrompt() {
        if (prompt != null && authInProgress) { try { prompt.cancelAuthentication(); } catch (Throwable ignored) { } }
        authInProgress = false;
    }

    private void reintentar() {
        cancelarPrompt(); // si quedó un intento "colgado", se reinicia
        autoPrompt = false;
        pedirDesbloqueo();
    }

    private void pedirDesbloqueo() {
        if (!locked || authInProgress || isFinishing()) return;
        coverDiag.setText(diagnostico());
        // Único caso que abre sin verificar: el teléfono NO tiene PIN/patrón/contraseña (aviso visible).
        if (!pantallaConBloqueo()) {
            final String diag = diagnostico();
            desbloquear("teléfono sin bloqueo de pantalla", false, false);
            if (!noLockNoticeShown) {
                noLockNoticeShown = true;
                new AlertDialog.Builder(this)
                        .setTitle("⚠️ Tu teléfono no tiene bloqueo de pantalla")
                        .setMessage("Por eso PipaCo no puede pedir tu huella y se abrió solo con tu correo y contraseña.\n\nConfigura un PIN, patrón o huella en Ajustes → Seguridad para proteger PipaCo.\n\n" + diag)
                        .setPositiveButton("Ir a Ajustes", (dd, w) -> {
                            try { startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)); } catch (Exception ignored) { }
                        })
                        .setNegativeButton("Ahora no", null)
                        .show();
            }
            return;
        }
        if (getSupportFragmentManager().isStateSaved()) { registrar("aplazado: actividad no lista"); return; }
        BiometricPrompt.PromptInfo.Builder b = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Desbloquear PipaCo")
                .setSubtitle("Usa tu huella o el PIN del teléfono")
                .setConfirmationRequired(true); // nunca aceptar un desbloqueo pasivo (p. ej. rostro) sin confirmar
        if (Build.VERSION.SDK_INT >= 30) {
            int c = canHuellaOPin();
            if (c != BiometricManager.BIOMETRIC_SUCCESS) {
                registrar("canAuthenticate(huella|PIN)=" + c + " → PIN del teléfono");
                usarCredencialTelefono();
                return;
            }
            // Preferir huella "fuerte"; si el teléfono solo tiene biometría "débil", usarla pero con confirmación explícita.
            int bio = canHuella() == BiometricManager.BIOMETRIC_SUCCESS ? BiometricManager.Authenticators.BIOMETRIC_STRONG
                    : canDebil() == BiometricManager.BIOMETRIC_SUCCESS ? BiometricManager.Authenticators.BIOMETRIC_WEAK : 0;
            b.setAllowedAuthenticators(bio | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        } else {
            int h = canHuella();
            if (h != BiometricManager.BIOMETRIC_SUCCESS) {
                registrar("canAuthenticate()=" + h + " → PIN del teléfono");
                usarCredencialTelefono();
                return;
            }
            b.setDeviceCredentialAllowed(true);
        }
        try {
            authInProgress = true;
            registrar("mostrando BiometricPrompt");
            prompt.authenticate(b.build());
        } catch (Throwable e) {
            authInProgress = false;
            fallos++;
            registrar("excepción BiometricPrompt: " + e);
            mostrarError("No se pudo mostrar la huella (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ").", true);
            usarCredencialTelefono();
        }
    }

    /** Respaldo: pantalla de PIN/patrón/contraseña del sistema (KeyguardManager). */
    @SuppressWarnings("deprecation")
    private void usarCredencialTelefono() {
        if (!locked || isFinishing()) return;
        KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        Intent i = km == null ? null : km.createConfirmDeviceCredentialIntent("Desbloquear PipaCo", "Usa el PIN, patrón o contraseña del teléfono");
        if (i == null) {
            if (!pantallaConBloqueo()) { pedirDesbloqueo(); return; }
            mostrarError("No se pudo abrir el PIN del teléfono.", true);
            return;
        }
        try {
            authInProgress = true;
            registrar("mostrando PIN del teléfono (KeyguardManager)");
            startActivityForResult(i, REQ_KEYGUARD);
        } catch (Throwable e) {
            authInProgress = false;
            registrar("excepción KeyguardManager: " + e);
            mostrarError("No se pudo abrir el PIN del teléfono (" + e.getClass().getSimpleName() + ").", true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (locked && !authInProgress && autoPrompt) {
            // Esperar a que la actividad esté realmente visible antes de mostrar el diálogo.
            root.postDelayed(() -> { if (locked && !authInProgress && autoPrompt && !isFinishing()) { autoPrompt = false; pedirDesbloqueo(); } }, 250);
        }
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
            bloquear("volvió tras más de 1 min");
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
        if (req == REQ_KEYGUARD) {
            authInProgress = false;
            if (res == RESULT_OK) desbloquear("PIN/patrón del teléfono", false, true);
            else { registrar("PIN del teléfono cancelado (resultado " + res + ")"); mostrarError("Desbloqueo cancelado. Toca “Desbloquear” para intentar de nuevo.", true); }
            return;
        }
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
        super.onRequestPermissionsResult(req, perms, res);
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
        public String consumeUnlock() { return consumirToken(); }
        @JavascriptInterface
        public void lock() { runOnUiThread(MainActivity.this::bloquearDesdeWeb); }
        @JavascriptInterface
        public String diag() { return diagnostico() + "\n\nRegistro de desbloqueo:\n" + registro(); }
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
