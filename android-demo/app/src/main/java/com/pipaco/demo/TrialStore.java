package com.pipaco.demo;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;

/** Guarda el inicio del periodo de prueba en SharedPreferences + un archivo de respaldo (ambos privados de la app). */
public final class TrialStore {
    static final String PREFS = "demo_trial";
    static final String K_INICIO = "inicio", K_VISTA = "ultimaVista";
    static final String ARCHIVO = "demo_trial.dat";

    private TrialStore() {}

    static long instalada(Context c) {
        try { return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).firstInstallTime; } catch (Exception e) { return 0; }
    }

    static long[] leerArchivo(Context c) {
        try {
            File f = new File(c.getFilesDir(), ARCHIVO);
            if (!f.exists()) return new long[]{0, 0};
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            try (FileInputStream in = new FileInputStream(f)) { byte[] buf = new byte[256]; int n; while ((n = in.read(buf)) > 0) bo.write(buf, 0, n); }
            String[] p = new String(bo.toByteArray(), StandardCharsets.UTF_8).trim().split(",");
            return new long[]{Long.parseLong(p[0].trim()), p.length > 1 ? Long.parseLong(p[1].trim()) : 0};
        } catch (Exception e) { return new long[]{0, 0}; }
    }

    static void escribirArchivo(Context c, long inicio, long vista) {
        try (FileOutputStream os = new FileOutputStream(new File(c.getFilesDir(), ARCHIVO))) {
            os.write((inicio + "," + vista).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
    }

    /** Lee, calcula, y vuelve a guardar en ambos lugares (repara el que falte). */
    public static Trial.Resultado evaluar(Context c, long ahora) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long[] a = leerArchivo(c);
        Trial.Resultado r = Trial.calcular(sp.getLong(K_INICIO, 0), a[0], sp.getLong(K_VISTA, 0), a[1], instalada(c), ahora);
        sp.edit().putLong(K_INICIO, r.inicio).putLong(K_VISTA, r.ultimaVista).commit();
        escribirArchivo(c, r.inicio, r.ultimaVista);
        return r;
    }

    public static Trial.Resultado evaluar(Context c) { return evaluar(c, System.currentTimeMillis()); }
}
