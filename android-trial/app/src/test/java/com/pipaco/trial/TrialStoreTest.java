package com.pipaco.trial;

import static org.junit.Assert.*;
import static com.pipaco.trial.Trial.DIA;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.view.View;
import android.webkit.WebView;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class TrialStoreTest {
    Context ctx() { return ApplicationProvider.getApplicationContext(); }
    long ahora() { return System.currentTimeMillis(); }

    @Test public void guardaEnPreferenciasYArchivo() {
        long t = ahora();
        Trial.Resultado r = TrialStore.evaluar(ctx(), t);
        assertEquals(14, r.diasRestantes);
        assertEquals(t, ctx().getSharedPreferences(TrialStore.PREFS, 0).getLong(TrialStore.K_INICIO, 0));
        assertEquals(t, TrialStore.leerArchivo(ctx())[0]);
        // segunda apertura 3 días después: mismo inicio
        Trial.Resultado r2 = TrialStore.evaluar(ctx(), t + 3 * DIA);
        assertEquals(t, r2.inicio);
        assertEquals(11, r2.diasRestantes);
    }

    @Test public void borrarPreferencias_seRecuperaDelArchivo() {
        long t = ahora();
        TrialStore.evaluar(ctx(), t);
        ctx().getSharedPreferences(TrialStore.PREFS, 0).edit().clear().commit();
        Trial.Resultado r = TrialStore.evaluar(ctx(), t + 14 * DIA + 1);
        assertEquals(t, r.inicio);
        assertTrue(r.vencida);
        assertEquals(t, ctx().getSharedPreferences(TrialStore.PREFS, 0).getLong(TrialStore.K_INICIO, 0)); // reparado
    }

    @Test public void borrarArchivo_seRecuperaDePreferencias() {
        long t = ahora();
        TrialStore.evaluar(ctx(), t);
        assertTrue(new File(ctx().getFilesDir(), TrialStore.ARCHIVO).delete());
        assertEquals(t, TrialStore.evaluar(ctx(), t + DIA).inicio);
        assertEquals(t, TrialStore.leerArchivo(ctx())[0]); // reparado
    }

    @Test public void relojAtrasadoPersistente() {
        long t = ahora();
        TrialStore.evaluar(ctx(), t);
        TrialStore.evaluar(ctx(), t + 13 * DIA);
        Trial.Resultado r = TrialStore.evaluar(ctx(), t + DIA); // atrasaron el reloj
        assertEquals(1, r.diasRestantes);
        assertTrue(r.relojAtrasado);
    }

    @Test public void activity_dentroDelPlazo_muestraLaWeb() {
        TrialActivity a = Robolectric.buildActivity(TrialActivity.class).setup().get();
        assertNotNull(a.web);
        assertNull(a.pantallaFin);
        assertEquals(14, a.new Puente().trialDiasRestantes());
        assertFalse(a.new Puente().trialVencida());
        assertTrue(a.new Puente().version().contains("prueba"));
    }

    @Test public void activity_vencida_bloqueaConPantallaFin() {
        long viejo = ahora() - 15 * DIA;
        ctx().getSharedPreferences(TrialStore.PREFS, 0).edit().putLong(TrialStore.K_INICIO, viejo).putLong(TrialStore.K_VISTA, viejo).commit();
        TrialActivity a = Robolectric.buildActivity(TrialActivity.class).setup().get();
        assertNull(a.web);
        assertNotNull(a.pantallaFin);
        assertEquals(View.VISIBLE, a.pantallaFin.getVisibility());
        String todo = textos(a.pantallaFin);
        assertTrue(todo, todo.contains("Terminó tu prueba de PipaCo"));
        assertTrue(todo.contains("conscaribe23@gmail.com"));
        assertTrue(todo.contains("WhatsApp"));
        assertTrue(todo.contains("Ver mis datos (solo lectura)"));
        assertTrue(a.new Puente().trialVencida());
        assertEquals(0, a.new Puente().trialDiasRestantes());
    }

    @Test public void vencida_verMisDatos_abreLaWebEnSoloLectura() {
        long viejo = ahora() - 15 * DIA;
        ctx().getSharedPreferences(TrialStore.PREFS, 0).edit().putLong(TrialStore.K_INICIO, viejo).putLong(TrialStore.K_VISTA, viejo).commit();
        TrialActivity a = Robolectric.buildActivity(TrialActivity.class).setup().get();
        a.verMisDatos();
        assertNotNull(a.web);
        assertNull(a.pantallaFin);
        assertTrue(a.verDatos);
        assertTrue(a.new Puente().trialVencida()); // la web sigue en solo lectura
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertNull(a.pantallaFin); // no vuelve a tapar los datos
    }

    @Test public void contacto_uris() {
        assertEquals("mailto", TrialActivity.uriCorreo().getScheme());
        assertTrue(TrialActivity.uriCorreo().toString().startsWith("mailto:conscaribe23@gmail.com?subject="));
        Uri w = TrialActivity.uriWhatsApp();
        assertEquals("wa.me", w.getHost());
        assertNotNull(w.getQueryParameter("text"));
    }

    @Test public void compartirWhatsappDirecto() {
        PackageInfo pi = new PackageInfo(); pi.packageName = "com.whatsapp";
        shadowOf(ctx().getPackageManager()).installPackage(pi);
        Uri u = Uri.parse("content://com.pipaco.trial.fileprovider/export/CV-000002.pdf");
        Intent it = TrialActivity.intentCompartir(ctx().getPackageManager(), u, "application/pdf", "A", "T", "", "whatsapp");
        assertEquals("com.whatsapp", it.getPackage());
        assertEquals(u, it.getParcelableExtra(Intent.EXTRA_STREAM));
    }

    @Test public void compartirEscribeArchivo() {
        TrialActivity a = Robolectric.buildActivity(TrialActivity.class).setup().get();
        String r = a.compartir("CV-000002.pdf", "application/pdf", android.util.Base64.encodeToString("%PDF-1.4".getBytes(), 2), "A", "T", "x@example.com", "correo");
        assertEquals("OK", r);
        assertTrue(new File(new File(a.getCacheDir(), "export"), "CV-000002.pdf").exists());
        assertNotNull(shadowOf(a).getNextStartedActivity());
    }

    static String textos(View v) {
        StringBuilder sb = new StringBuilder();
        if (v instanceof android.widget.TextView) sb.append(((android.widget.TextView) v).getText()).append('\n');
        if (v instanceof android.view.ViewGroup) { android.view.ViewGroup g = (android.view.ViewGroup) v; for (int i = 0; i < g.getChildCount(); i++) sb.append(textos(g.getChildAt(i))); }
        return sb.toString();
    }
}
