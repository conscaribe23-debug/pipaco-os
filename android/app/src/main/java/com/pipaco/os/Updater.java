package com.pipaco.os;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Actualizador del APK (fuera de Play Store).
 * Lee version.json en GitHub Pages, descarga el APK del Release de GitHub,
 * verifica SHA-256 y paquete, y abre el instalador del sistema.
 */
class Updater {
    static final String VERSION_URL = "https://conscaribe23-debug.github.io/pipaco-os/android/version.json";
    private final MainActivity a;
    private final ExecutorService ex = Executors.newSingleThreadExecutor();
    private volatile boolean busy = false, cancel = false;
    private File pendingApk;

    Updater(MainActivity a) { this.a = a; }

    static class Info { int code; String name, url, sha, notes; }

    /** quiet=true: chequeo silencioso (máx. 1 vez al día), solo muestra un aviso si hay versión nueva. */
    void check(final boolean quiet) {
        if (busy) return;
        busy = true;
        final AlertDialog[] wait = new AlertDialog[1];
        if (!quiet) a.runOnUiThread(() -> wait[0] = new AlertDialog.Builder(a).setMessage("Buscando actualización…").setCancelable(false).show());
        ex.execute(() -> {
            Info info = null; String err = null;
            try { info = fetch(); } catch (Exception e) { err = e.getMessage(); }
            final Info fi = info; final String fe = err;
            a.runOnUiThread(() -> {
                busy = false;
                if (wait[0] != null) wait[0].dismiss();
                if (fi != null && quiet) a.getSharedPreferences("pipaco_updater", Context.MODE_PRIVATE).edit()
                        .putString("ultimo", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date())).apply();
                if (fi == null) {
                    if (!quiet) dialogo("Sin conexión", "No se pudo revisar si hay actualizaciones. Revisa tu internet e inténtalo de nuevo." + (fe != null ? "\n\n(" + fe + ")" : ""));
                    return;
                }
                if (fi.code <= BuildConfig.VERSION_CODE) {
                    if (!quiet) dialogo("Tienes la última versión", "PipaCo " + BuildConfig.VERSION_NAME + " está al día. ✅");
                    return;
                }
                if (quiet) {
                    a.js("typeof PWA!=='undefined'&&PWA.avisoApk&&PWA.avisoApk(" + MainActivity.jsStr(fi.name) + ")");
                } else ofrecer(fi);
            });
        });
    }

    void autoCheckOncePerDay() {
        SharedPreferences p = a.getSharedPreferences("pipaco_updater", Context.MODE_PRIVATE);
        String hoy = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        if (hoy.equals(p.getString("ultimo", ""))) return;
        check(true); // se marca el día solo si la revisión se pudo hacer (con internet)
    }

    private Info fetch() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(VERSION_URL + "?t=" + System.currentTimeMillis()).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(15000); c.setUseCaches(false);
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (InputStream in = c.getInputStream()) { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) > 0) bo.write(b, 0, n); }
            JSONObject j = new JSONObject(bo.toString("UTF-8"));
            Info i = new Info();
            i.code = j.getInt("versionCode"); i.name = j.getString("versionName");
            i.url = j.getString("apkUrl"); i.sha = j.getString("sha256").trim().toLowerCase(Locale.US);
            i.notes = j.optString("notes", "");
            if (!i.url.startsWith("https://")) throw new Exception("URL no segura");
            return i;
        } finally { c.disconnect(); }
    }

    private void ofrecer(Info i) {
        new AlertDialog.Builder(a)
                .setTitle("Nueva versión " + i.name)
                .setMessage("Tienes la " + BuildConfig.VERSION_NAME + ".\n\n" + (i.notes.isEmpty() ? "" : "Novedades:\n" + i.notes + "\n\n")
                        + "Tus datos se conservan al actualizar.")
                .setPositiveButton("Descargar e instalar", (d, w) -> descargar(i))
                .setNegativeButton("Ahora no", null)
                .show();
    }

    private void descargar(final Info i) {
        cancel = false;
        float dp = a.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding((int) (24 * dp), (int) (16 * dp), (int) (24 * dp), 0);
        final ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        final TextView txt = new TextView(a);
        txt.setText("Descargando… 0 %");
        box.addView(bar); box.addView(txt);
        final AlertDialog dlg = new AlertDialog.Builder(a).setTitle("Descargando PipaCo " + i.name).setView(box)
                .setCancelable(false).setNegativeButton("Cancelar", (d, w) -> cancel = true).show();
        ex.execute(() -> {
            File dir = new File(a.getCacheDir(), "updates");
            dir.mkdirs();
            File[] viejos = dir.listFiles();
            if (viejos != null) for (File f : viejos) f.delete();
            File out = new File(dir, "PipaCo-" + i.name + ".apk");
            String err = null;
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(i.url).openConnection();
                c.setInstanceFollowRedirects(true); c.setConnectTimeout(15000); c.setReadTimeout(30000);
                if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
                long total = c.getContentLengthLong(), got = 0; int lastPct = -1;
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                try (InputStream in = c.getInputStream(); FileOutputStream fo = new FileOutputStream(out)) {
                    byte[] b = new byte[16384]; int n;
                    while ((n = in.read(b)) > 0) {
                        if (cancel) throw new Exception("cancelado");
                        fo.write(b, 0, n); md.update(b, 0, n); got += n;
                        if (total > 0) {
                            final int pct = (int) (got * 100 / total);
                            if (pct != lastPct) { lastPct = pct; final long g = got; a.runOnUiThread(() -> { bar.setProgress(pct); txt.setText("Descargando… " + pct + " % (" + (g / 1024) + " KB)"); }); }
                        }
                    }
                } finally { c.disconnect(); }
                StringBuilder hex = new StringBuilder();
                for (byte x : md.digest()) hex.append(String.format("%02x", x));
                if (!hex.toString().equals(i.sha)) throw new Exception("el archivo no coincide (SHA-256). No se instaló por seguridad.");
                PackageInfo pi = a.getPackageManager().getPackageArchiveInfo(out.getAbsolutePath(), 0);
                long vc = pi == null ? -1 : (Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode);
                if (pi == null || !a.getPackageName().equals(pi.packageName) || vc <= BuildConfig.VERSION_CODE)
                    throw new Exception("el APK descargado no es una versión nueva de PipaCo.");
            } catch (Exception e) {
                out.delete();
                err = e.getMessage();
            }
            final String fe = err;
            a.runOnUiThread(() -> {
                dlg.dismiss();
                if (fe != null) {
                    if (!"cancelado".equals(fe)) dialogo("No se pudo actualizar", "Error: " + fe + "\n\nRevisa tu internet e inténtalo de nuevo.");
                    return;
                }
                pendingApk = out;
                instalar();
            });
        });
    }

    private void instalar() {
        if (pendingApk == null || !pendingApk.exists()) return;
        if (Build.VERSION.SDK_INT >= 26 && !a.getPackageManager().canRequestPackageInstalls()) {
            new AlertDialog.Builder(a)
                    .setTitle("Permiso para instalar")
                    .setMessage("Para instalar la actualización, activa “Permitir de esta fuente” para PipaCo y vuelve a la app.")
                    .setPositiveButton("Abrir ajustes", (d, w) -> {
                        try { a.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + a.getPackageName()))); }
                        catch (Exception e) { a.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)); }
                    })
                    .setNegativeButton("Cancelar", (d, w) -> pendingApk = null)
                    .show();
            return;
        }
        File f = pendingApk;
        pendingApk = null;
        Uri u = FileProvider.getUriForFile(a, "com.pipaco.os.fileprovider", f);
        Intent it = new Intent(Intent.ACTION_VIEW);
        it.setDataAndType(u, "application/vnd.android.package-archive");
        it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try { a.startActivity(it); }
        catch (Exception e) { dialogo("No se pudo abrir el instalador", String.valueOf(e.getMessage())); }
    }

    /** Llamar desde onResume: si el usuario volvió de Ajustes con el permiso activado, instalar. */
    void onResume() {
        if (pendingApk != null && (Build.VERSION.SDK_INT < 26 || a.getPackageManager().canRequestPackageInstalls())) instalar();
    }

    private void dialogo(String t, String m) {
        new AlertDialog.Builder(a).setTitle(t).setMessage(m).setPositiveButton("OK", null).show();
    }
}
