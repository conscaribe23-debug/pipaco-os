package com.pipaco.trial;

import static org.junit.Assert.*;
import static com.pipaco.trial.Trial.DIA;

import org.junit.Test;

/** Lógica pura del periodo de prueba de 14 días (sin Android). */
public class TrialTest {
    static final long T0 = 1_791_000_000_000L;
    static final long H = 60L * 60 * 1000;

    @Test public void configuracion_14dias() { assertEquals(14, Trial.DIAS); assertEquals(14 * DIA, Trial.DURACION); }

    @Test public void dia0_primeraApertura_quedan14() {
        Trial.Resultado r = Trial.calcular(0, 0, 0, 0, T0 - 5 * 60_000, T0);
        assertTrue(r.primeraVez);
        assertEquals(T0, r.inicio);
        assertFalse(r.vencida);
        assertEquals(14, r.diasRestantes);
    }

    @Test public void dia1_quedan13() { assertEquals(13, Trial.calcular(T0, T0, 0, 0, T0, T0 + DIA).diasRestantes); }

    @Test public void dia13_queda1() {
        Trial.Resultado r = Trial.calcular(T0, T0, T0 + 12 * DIA, T0 + 12 * DIA, T0, T0 + 13 * DIA);
        assertFalse(r.vencida);
        assertEquals(1, r.diasRestantes);
        assertEquals(1, Trial.calcular(T0, T0, 0, 0, T0, T0 + 13 * DIA + 20 * H).diasRestantes);
    }

    @Test public void dia14_vencida() {
        Trial.Resultado r = Trial.calcular(T0, T0, T0, T0, T0, T0 + 14 * DIA);
        assertTrue(r.vencida);
        assertEquals(0, r.diasRestantes);
        assertEquals(1, Trial.calcular(T0, T0, 0, 0, T0, T0 + 14 * DIA - 1).diasRestantes);
        assertFalse(Trial.calcular(T0, T0, 0, 0, T0, T0 + 14 * DIA - 1).vencida);
        assertEquals(T0 + 14 * DIA, r.vence());
    }

    @Test public void relojAtrasado_noDevuelveDias() {
        Trial.Resultado r = Trial.calcular(T0, T0, T0 + 13 * DIA, T0 + 13 * DIA, T0, T0 + 2 * DIA);
        assertTrue(r.relojAtrasado);
        assertEquals(1, r.diasRestantes);
        assertEquals(T0 + 13 * DIA, r.ultimaVista);
    }

    @Test public void relojAtrasado_trasVencer_sigueVencida() {
        Trial.Resultado r = Trial.calcular(T0, T0, T0 + 15 * DIA, T0 + 15 * DIA, T0, T0 + 2 * DIA);
        assertTrue(r.vencida);
        assertTrue(r.relojAtrasado);
        assertTrue(Trial.calcular(T0, T0, T0 + 15 * DIA, 0, T0, T0 - 30 * DIA).vencida);
    }

    @Test public void ajustePequenoDelReloj_noEsAtraso() {
        Trial.Resultado r = Trial.calcular(T0, T0, T0 + DIA, T0 + DIA, T0, T0 + DIA - 2 * 60_000);
        assertFalse(r.relojAtrasado);
        assertFalse(r.vencida);
    }

    @Test public void copiasDistintas_usaInicioMasViejoYVistaMasNueva() {
        Trial.Resultado r = Trial.calcular(T0 + 3 * DIA, T0, T0 + 3 * DIA, T0 + 5 * DIA, T0, T0 + 4 * DIA);
        assertEquals(T0, r.inicio);
        assertEquals(T0 + 5 * DIA, r.ultimaVista);
        assertEquals(9, r.diasRestantes);
        assertEquals(T0, Trial.calcular(0, T0, 0, T0 + DIA, T0, T0 + 2 * DIA).inicio);
    }

    @Test public void datosBorrados_usaFechaDeInstalacion() {
        Trial.Resultado r = Trial.calcular(0, 0, 0, 0, T0, T0 + 20 * DIA);
        assertEquals(T0, r.inicio);
        assertTrue(r.vencida);
        assertEquals(10, Trial.calcular(0, 0, 0, 0, T0, T0 + 4 * DIA).diasRestantes);
        assertEquals(T0 + 3 * H, Trial.calcular(0, 0, 0, 0, T0, T0 + 3 * H).inicio);
    }
}
