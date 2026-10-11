package com.pipaco.os;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ShareTest {
    private final Uri uri = Uri.parse("content://com.pipaco.os.fileprovider/export/CV-000001.pdf");

    private PackageManager pm(String... instalados) {
        Context c = ApplicationProvider.getApplicationContext();
        PackageManager pm = c.getPackageManager();
        for (String p : instalados) { PackageInfo pi = new PackageInfo(); pi.packageName = p; shadowOf(pm).installPackage(pi); }
        return pm;
    }

    @Test public void whatsappInstaladoVaDirecto() {
        Intent it = MainActivity.intentCompartir(pm("com.whatsapp"), uri, "application/pdf", "Asunto", "Texto", "", "whatsapp");
        assertEquals(Intent.ACTION_SEND, it.getAction());
        assertEquals("com.whatsapp", it.getPackage());
        assertEquals(uri, it.getParcelableExtra(Intent.EXTRA_STREAM));
        assertTrue((it.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0);
        assertEquals("application/pdf", it.getType());
    }

    @Test public void whatsappBusinessSiNoHayNormal() {
        Intent it = MainActivity.intentCompartir(pm("com.whatsapp.w4b"), uri, "application/pdf", "", "", "", "whatsapp");
        assertEquals("com.whatsapp.w4b", it.getPackage());
    }

    @Test public void sinWhatsappUsaSelector() {
        Intent it = MainActivity.intentCompartir(pm(), uri, "application/pdf", "", "t", "", "whatsapp");
        assertEquals(Intent.ACTION_CHOOSER, it.getAction());
        Intent in = it.getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals(Intent.ACTION_SEND, in.getAction());
        assertNull(in.getPackage());
        assertEquals("t", in.getStringExtra(Intent.EXTRA_TEXT));
    }

    @Test public void correoLlevaDestinatarioYAsunto() {
        Intent it = MainActivity.intentCompartir(pm(), uri, "application/pdf", "Comprobante CV-000001", "Hola", "cliente@example.com", "correo");
        assertEquals(Intent.ACTION_CHOOSER, it.getAction());
        Intent in = it.getParcelableExtra(Intent.EXTRA_INTENT);
        assertArrayEquals(new String[]{"cliente@example.com"}, in.getStringArrayExtra(Intent.EXTRA_EMAIL));
        assertEquals("Comprobante CV-000001", in.getStringExtra(Intent.EXTRA_SUBJECT));
        assertEquals(uri, in.getParcelableExtra(Intent.EXTRA_STREAM));
    }

    @Test public void compartirEscribeArchivoYLanzaIntent() {
        MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
        String r = a.compartir("CV/000001.pdf", "application/pdf;x=1", android.util.Base64.encodeToString("%PDF-1.4 x".getBytes(), android.util.Base64.NO_WRAP), "A", "T", "", "");
        assertEquals("OK", r);
        File f = new File(new File(a.getCacheDir(), "export"), "CV_000001.pdf");
        assertTrue(f.exists());
        assertEquals(10, f.length());
        Intent started = shadowOf(a).getNextStartedActivity();
        assertNotNull(started);
        assertEquals(Intent.ACTION_CHOOSER, started.getAction());
        Intent in = started.getParcelableExtra(Intent.EXTRA_INTENT);
        assertEquals("application/pdf", in.getType());
    }
}
