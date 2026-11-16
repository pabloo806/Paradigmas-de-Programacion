package javadona;

/**
 * Gestor del horario de la tienda (monitor).
 *
 * Mantiene un RELOJ LÓGICO que descuenta el tiempo pasado en pausa, de forma
 * que la tienda no "cierre" mientras la simulación está detenida.
 *
 * Estados:
 *   PRE_APERTURA: de 0 a 10 s
 *   ABIERTA:      de 10 s a 180 s (10 s + 170 s)
 *   CERRADA:      a partir de 180 s
 *
 * Exclusión mutua: todos los métodos que leen o modifican el estado del reloj
 * son synchronized sobre this. Las secciones críticas son cortas y NO hay
 * esperas dentro de ellas (las esperas se hacen fuera, con sleep).
 */
public class GestorHorario {

    /** Estados posibles de la tienda. */
    public enum EstadoTienda { PRE_APERTURA, ABIERTA, CERRADA }

    private static final long DURACION_PRE_APERTURA_MS = 10_000L;
    private static final long DURACION_JORNADA_MS = 170_000L;
    private static final long INTERVALO_SONDEO_MS = 100L;
    private static final long NANOS_POR_MS = 1_000_000L;

    private boolean iniciado = false;
    private boolean pausado = false;
    private long instanteInicioNanos = 0L;
    private long instantePausaNanos = 0L;
    private long tiempoPausadoNanos = 0L;

    /** Arranca el reloj lógico. Si ya estaba iniciado, no hace nada. */
    public synchronized void iniciar() {
        if (!iniciado) {
            iniciado = true;
            instanteInicioNanos = System.nanoTime();
        }
    }

    /** Congela el reloj lógico. Es idempotente. */
    public synchronized void pausar() {
        if (iniciado && !pausado) {
            pausado = true;
            instantePausaNanos = System.nanoTime();
        }
    }

    /** Reanuda el reloj, acumulando el tiempo que ha estado pausado. */
    public synchronized void reanudar() {
        if (iniciado && pausado) {
            tiempoPausadoNanos += System.nanoTime() - instantePausaNanos;
            pausado = false;
        }
    }

    /** Milisegundos de tiempo lógico transcurridos (sin contar las pausas). */
    public synchronized long getTiempoTranscurridoMs() {
        if (!iniciado) {
            return 0L;
        }
        long referencia = pausado ? instantePausaNanos : System.nanoTime();
        return (referencia - instanteInicioNanos - tiempoPausadoNanos) / NANOS_POR_MS;
    }

    /** Estado actual de la tienda, calculado a partir del tiempo lógico. */
    public synchronized EstadoTienda getEstado() {
        long t = getTiempoTranscurridoMs(); // ReentrantLock implícito de synchronized: reentrante
        if (t < DURACION_PRE_APERTURA_MS) {
            return EstadoTienda.PRE_APERTURA;
        }
        if (t < DURACION_PRE_APERTURA_MS + DURACION_JORNADA_MS) {
            return EstadoTienda.ABIERTA;
        }
        return EstadoTienda.CERRADA;
    }

    public boolean estaAbierta() {
        return getEstado() == EstadoTienda.ABIERTA;
    }

    public boolean estaCerrada() {
        return getEstado() == EstadoTienda.CERRADA;
    }

    /** Milisegundos lógicos que faltan para abrir (0 si ya abrió). */
    public synchronized long getMsHastaApertura() {
        return Math.max(0L, DURACION_PRE_APERTURA_MS - getTiempoTranscurridoMs());
    }

    /** Milisegundos lógicos que faltan para cerrar (0 si ya cerró). */
    public synchronized long getMsHastaCierre() {
        long t = getTiempoTranscurridoMs();
        return Math.max(0L, DURACION_PRE_APERTURA_MS + DURACION_JORNADA_MS - t);
    }

    /**
     * Bloquea al hilo llamante hasta que la tienda abra. Respeta las pausas:
     * sondea el tiempo lógico, por lo que si se pausa, la espera se alarga.
     * El sleep se hace FUERA de la sección crítica para no bloquear el monitor.
     */
    public void esperarApertura() throws InterruptedException {
        while (getEstado() == EstadoTienda.PRE_APERTURA) {
            Thread.sleep(Math.min(INTERVALO_SONDEO_MS, Math.max(1L, getMsHastaApertura())));
        }
    }
}
