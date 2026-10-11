package com.pipaco.os;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Looper;
import android.view.View;
import android.webkit.WebView;

import androidx.fragment.app.Fragment;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowBiometricManager;
import org.robolectric.shadows.ShadowKeyguardManager;
import org.robolectric.shadows.ShadowLooper;

import java.lang.reflect.Field;

@RunWith(RobolectricTestRunner.class)
public class LockTest {
    private static Object f(Object o, String n) throws Exception { Field x = MainActivity.class.getDeclaredField(n); x.setAccessible(true); return x.get(o); }

    private MainActivity abrir(boolean seguro, Boolean bio) {
        Context c = org.robolectric.RuntimeEnvironment.getApplication();
        ShadowKeyguardManager sk = shadowOf((KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE));
        sk.setIsDeviceSecure(seguro);
        sk.setIsKeyguardSecure(seguro);
        if (bio != null && android.os.Build.VERSION.SDK_INT >= 29) {
            ShadowBiometricManager sb = (ShadowBiometricManager) org.robolectric.shadow.api.Shadow.extract(c.getSystemService(android.hardware.biometrics.BiometricManager.class));
            sb.setCanAuthenticate(bio);
            if (android.os.Build.VERSION.SDK_INT >= 30) sb.setAuthenticatorType(
                    android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
                    | android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL);
        }
        if (bio != null && android.os.Build.VERSION.SDK_INT < 29) {
            shadowOf(c.getPackageManager()).setSystemFeature(android.content.pm.PackageManager.FEATURE_FINGERPRINT, true);
            FingerprintManager fm = c.getSystemService(FingerprintManager.class);
            shadowOf(fm).setIsHardwareDetected(true);
            shadowOf(fm).setDefaultFingerprints(bio ? 1 : 0);
        }
        ActivityController<MainActivity> ac = Robolectric.buildActivity(MainActivity.class).setup();
        ShadowLooper.idleMainLooper(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
        shadowOf(Looper.getMainLooper()).idle();
        return ac.get();
    }

    private String urlCargada(MainActivity a) throws Exception { return shadowOf((WebView) f(a, "web")).getLastLoadedUrl(); }

    private boolean hayBiometricFragment(MainActivity a) {
        for (Fragment fr : a.getSupportFragmentManager().getFragments())
            if (fr.getClass().getName().startsWith("androidx.biometric")) return true;
        return false;
    }

    @Test @Config(sdk = 34)
    public void api34_conHuella_noCargaContenidoYMuestraPrompt() throws Exception {
        MainActivity a = abrir(true, true);
        assertTrue("debe seguir bloqueada", (Boolean) f(a, "locked"));
        assertNull("no debe cargar la app web antes de desbloquear", urlCargada(a));
        assertEquals(View.VISIBLE, ((View) f(a, "cover")).getVisibility());
        assertTrue("debe mostrarse BiometricPrompt", hayBiometricFragment(a));
        System.out.println("DIAG34: " + a.diagnostico().replace('\n', ' '));
        System.out.println("LOG34: " + a.registro().replace("\n", " | "));
    }

    @Test @Config(sdk = 34)
    public void api34_sinBiometria_usaPinDelTelefonoYSigueBloqueada() throws Exception {
        MainActivity a = abrir(true, false);
        assertTrue((Boolean) f(a, "locked"));
        assertNull(urlCargada(a));
        System.out.println("LOG34b: " + a.registro().replace("\n", " | "));
        Intent next = shadowOf(a).getNextStartedActivity();
        System.out.println("NEXT34b: " + next);
    }

    @Test @Config(sdk = 34)
    public void api34_sinBloqueoDePantalla_abreConAvisoVisible() throws Exception {
        MainActivity a = abrir(false, null);
        assertFalse((Boolean) f(a, "locked"));
        assertNotNull(urlCargada(a));
        AlertDialog d = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull("debe mostrar aviso", d);
        assertTrue(d.isShowing());
    }

    @Test @Config(sdk = 34)
    public void api34_cancelarNoDesbloquea() throws Exception {
        MainActivity a = abrir(true, true);
        Field cb = MainActivity.class.getDeclaredField("prompt"); cb.setAccessible(true);
        // Simula cancelación del usuario vía el callback guardado por androidx.biometric
        Object vm = new androidx.lifecycle.ViewModelProvider(a).get(Class.forName("androidx.biometric.BiometricViewModel").asSubclass(androidx.lifecycle.ViewModel.class));
        java.lang.reflect.Method gcb = vm.getClass().getDeclaredMethod("getClientCallback"); gcb.setAccessible(true);
        androidx.biometric.BiometricPrompt.AuthenticationCallback c = (androidx.biometric.BiometricPrompt.AuthenticationCallback) gcb.invoke(vm);
        c.onAuthenticationError(androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED, "cancelado");
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue((Boolean) f(a, "locked"));
        assertNull(urlCargada(a));
        c.onAuthenticationError(androidx.biometric.BiometricPrompt.ERROR_LOCKOUT, "demasiados intentos");
        shadowOf(Looper.getMainLooper()).idle();
        assertTrue("lockout no debe desbloquear", (Boolean) f(a, "locked"));
        assertNull(urlCargada(a));
        System.out.println("LOGcancel: " + a.registro().replace("\n", " | "));
    }

    @Test @Config(sdk = 27)
    public void api27_huella_temaAppCompatNoFalla() throws Exception {
        MainActivity a = abrir(true, true);
        assertTrue((Boolean) f(a, "locked"));
        assertNull(urlCargada(a));
        assertTrue("debe mostrarse el diálogo de huella (FingerprintDialogFragment)", hayBiometricFragment(a));
        System.out.println("LOG27: " + a.registro().replace("\n", " | ") + " frags=" + a.getSupportFragmentManager().getFragments());
    }
}
