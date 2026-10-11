package com.pipaco.trial;

import static org.junit.Assert.*;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** El HTML empaquetado lleva la capa de prueba y los límites de web/trial-config.json, y nada de la app real que no corresponda. */
public class ConfigTest {
    static String html() throws Exception {
        File f = new File("src/main/assets/www/index.html");
        if (!f.exists()) f = new File("app/src/main/assets/www/index.html");
        assertTrue("falta " + f.getAbsolutePath(), f.exists());
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test public void limitesDesdeConfig() throws Exception {
        assertEquals(30, BuildConfig.MAX_VENTAS);
        assertEquals(5, BuildConfig.MAX_CLIENTES);
        assertEquals(10, BuildConfig.MAX_COMPROBANTES);
        assertEquals(10, BuildConfig.MAX_PRODUCTOS);
        assertEquals("conscaribe23@gmail.com", BuildConfig.CORREO);
        String h = html();
        assertTrue(h.contains("\"maxVentas\": " + BuildConfig.MAX_VENTAS));
        assertTrue(h.contains("\"maxClientes\": " + BuildConfig.MAX_CLIENTES));
        assertTrue(h.contains("\"maxComprobantes\": " + BuildConfig.MAX_COMPROBANTES));
        assertTrue(h.contains("\"maxProductos\": " + BuildConfig.MAX_PRODUCTOS));
        assertTrue(h.contains("\"dias\": " + Trial.DIAS));
        assertTrue(h.contains("\"marcaPdf\": \"VERSIÓN DE PRUEBA\""));
    }

    @Test public void capaDePruebaYSinPwaNiDatosPersonales() throws Exception {
        String h = html();
        assertTrue(h.contains("const PRUEBA="));
        assertTrue(h.contains("D.watermark(TRIAL.marcaPdf"));
        assertTrue(h.contains("${PRUEBA.card()}"));
        assertFalse(h.contains("PWA.init()"));
        assertFalse(h.contains("rel=\"manifest\""));
        assertFalse(h.contains("4-769-1592"));
        assertFalse(h.contains("Luis Gabriel"));
        // mismas claves de almacenamiento que la app real
        assertTrue(h.contains("KEY='pipaco_os_v2'"));
    }
}
