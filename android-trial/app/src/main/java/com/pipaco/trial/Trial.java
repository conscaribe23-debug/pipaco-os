package com.pipaco.trial;

/**
 * Lógica pura (sin Android) del periodo de prueba (14 días por defecto, ver web/trial-config.json).
 *
 * Reglas:
 * - El periodo empieza en la PRIMERA apertura (o en la fecha de instalación si se borraron los datos de la app).
 * - Si dos copias guardadas difieren (SharedPreferences y archivo de respaldo) se usa el inicio MÁS ANTIGUO
 *   y la "última vez vista" MÁS RECIENTE.
 * - Reloj atrasado: el tiempo efectivo es max(ahora, última vez vista), así que atrasar el reloj del
 *   teléfono nunca devuelve días. La "última vez vista" nunca baja.
 */
public final class Trial {
    public static final long DIA = 24L * 60 * 60 * 1000;
    /** Días de prueba: vienen de android-trial/web/trial-config.json (campo "dias") vía BuildConfig. */
    public static final int DIAS = BuildConfig.DIAS_PRUEBA;
    public static final long DURACION = DIAS * DIA;
    /** Si no hay datos guardados pero la app se instaló hace más de esto, se asume que borraron los datos. */
    public static final long UMBRAL_DATOS_BORRADOS = DIA;
    /** Diferencia hacia atrás a partir de la cual se considera "reloj atrasado" (para avisar). */
    public static final long TOLERANCIA_RELOJ = 10L * 60 * 1000;

    public static final class Resultado {
        public final long inicio, ultimaVista, efectivo;
        public final boolean vencida, relojAtrasado, primeraVez;
        public final int diasRestantes;
        Resultado(long inicio, long ultimaVista, long efectivo, boolean vencida, boolean relojAtrasado, boolean primeraVez, int diasRestantes) {
            this.inicio = inicio; this.ultimaVista = ultimaVista; this.efectivo = efectivo; this.vencida = vencida;
            this.relojAtrasado = relojAtrasado; this.primeraVez = primeraVez; this.diasRestantes = diasRestantes;
        }
        public long vence() { return inicio + DURACION; }
    }

    private Trial() {}

    static long minPositivo(long a, long b) { if (a <= 0) return b > 0 ? b : 0; if (b <= 0) return a; return Math.min(a, b); }

    /**
     * @param inicioPrefs  inicio guardado en SharedPreferences (0 = no hay)
     * @param inicioArchivo inicio guardado en el archivo de respaldo (0 = no hay)
     * @param vistaPrefs   última vez vista en SharedPreferences (0 = no hay)
     * @param vistaArchivo última vez vista en el archivo (0 = no hay)
     * @param instalada    PackageInfo.firstInstallTime (0 = desconocido)
     * @param ahora        System.currentTimeMillis()
     */
    public static Resultado calcular(long inicioPrefs, long inicioArchivo, long vistaPrefs, long vistaArchivo, long instalada, long ahora) {
        long inicio = minPositivo(inicioPrefs, inicioArchivo);
        long vista = Math.max(Math.max(vistaPrefs, vistaArchivo), 0);
        boolean primera = inicio <= 0;
        if (primera) {
            // Sin datos: primera apertura... o borraron los datos de la app (entonces vale la fecha de instalación).
            inicio = (instalada > 0 && ahora - instalada > UMBRAL_DATOS_BORRADOS) ? instalada : ahora;
        }
        vista = Math.max(vista, inicio);
        boolean atrasado = ahora < vista - TOLERANCIA_RELOJ;
        long efectivo = Math.max(ahora, vista);
        long transcurrido = efectivo - inicio;
        boolean vencida = transcurrido >= DURACION;
        long resta = DURACION - transcurrido;
        int dias = vencida ? 0 : (int) Math.min(DIAS, (resta + DIA - 1) / DIA);
        return new Resultado(inicio, efectivo, efectivo, vencida, atrasado, primera, dias);
    }
}
