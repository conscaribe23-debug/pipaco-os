package com.pipaco.demo;

import static org.junit.Assert.*;
import static com.pipaco.demo.Trial.DIA;

import org.junit.Test;

/** Lógica pura del periodo de prueba (sin Android). */
public class TrialTest {
    static final long T0 = 1_791_000_000_000L; // un instante cualquiera
    static final long H = 60L * 60 * 1000;

    @Test public void dia0_primeraApertura_quedan7() {
        Trial.Resultado r = Trial.calcular(0, 0, 0, 0, T0 - 5 * 60_000, T0);
        assertTrue(r.primeraVez);
        assertEquals(T0, r.inicio);
        assertFalse(r.vencida);
        assertEquals(7, r.diasRestantes);
    }

    @Test public void dia6_queda1() {
        Trial.Resultado r = Trial.calcular(T0, T0, T0 + 5 * DIA, T0 + 5 * DIA, T0, T0 + 6 * DIA);
        assertFalse(r.vencida);
        assertEquals(1, r.diasRestantes);
        // a media tarde del día 6 todavía queda 1 día (se redondea hacia arriba)
        assertEquals(1, Trial.calcular(T0, T0, 0, 0, T0, T0 + 6 * DIA + 20 * H).diasRestantes);
    }

    @Test public void dia7_vencida() {
        Trial.Resultado r = Trial.calcular(T0, T0, T0, T0, T0, T0 + 7 * DIA);
        assertTrue(r.vencida);
        assertEquals(0, r.diasRestantes);
        assertTrue(Trial.calcular(T0, T0, 0, 0, T0, T0 + 7 * DIA - 1).diasRestantes == 1);
        assertFalse(Trial.calcular(T0, T0, 0, 0, T0, T0 + 7 * DIA - 1).vencida);
    }

    @Test public void relojAtrasado_noDevuelveDias() {
        // Se vio la app el día 6 y luego atrasan el reloj al día 1: sigue quedando 1 día, no 6.
        Trial.Resultado r = Trial.calcular(T0, T0, T0 + 6 * DIA, T0 + 6 * DIA, T0, T0 + 1 * DIA);
        assertTrue(r.relojAtrasado);
        assertEquals(1, r.diasRestantes);
        assertEquals(T0 + 6 * DIA, r.ultimaVista); // la última vista nunca baja
    }

    @Test public void relojAtrasado_trasVencer_sigueVencida() {
        Trial.Resultado r = Trial.calcular(T0, T0, T0 + 8 * DIA, T0 + 8 * DIA, T0, T0 + 2 * DIA);
        assertTrue(r.vencida);
        assertTrue(r.relojAtrasado);
        // atrasar a ANTES de la instalación tampoco revive la demo
        assertTrue(Trial.calcular(T0, T0, T0 + 8 * DIA, 0, T0, T0 - 30 * DIA).vencida);
    }

    @Test public void ajustePequenoDelReloj_noEsAtraso() {
        Trial.Resultado r = Trial.calcular(T0, T0, T0 + DIA, T0 + DIA, T0, T0 + DIA - 2 * 60_000);
        assertFalse(r.relojAtrasado);
        assertFalse(r.vencida);
    }

    @Test public void copiasDistintas_usaInicioMasViejoYVistaMasNueva() {
        // Alguien borró/editó una de las dos copias
        Trial.Resultado r = Trial.calcular(T0 + 3 * DIA, T0, T0 + 3 * DIA, T0 + 5 * DIA, T0, T0 + 4 * DIA);
        assertEquals(T0, r.inicio);
        assertEquals(T0 + 5 * DIA, r.ultimaVista);
        assertEquals(2, r.diasRestantes);
        // Solo hay archivo de respaldo (borraron las preferencias)
        assertEquals(T0, Trial.calcular(0, T0, 0, T0 + DIA, T0, T0 + 2 * DIA).inicio);
    }

    @Test public void datosBorrados_usaFechaDeInstalacion() {
        // Sin nada guardado pero instalada hace 10 días => vencida (borrar datos de la app no reinicia la demo)
        Trial.Resultado r = Trial.calcular(0, 0, 0, 0, T0, T0 + 10 * DIA);
        assertEquals(T0, r.inicio);
        assertTrue(r.vencida);
        // Instalada hace 3 horas y abierta por primera vez => empieza ahora
        assertEquals(T0 + 3 * H, Trial.calcular(0, 0, 0, 0, T0, T0 + 3 * H).inicio);
    }
}
