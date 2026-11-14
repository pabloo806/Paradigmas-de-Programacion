package javadona;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Aparcamiento del supermercado (recurso compartido): 50 plazas.
 *
 * Sincronización: Semaphore(50, true). Un permiso = una plaza ocupada desde que
 * el coche recoge el tique hasta que lo escanea en la barrera de salida.
 * El modo justo da el orden de llegada a la barrera. Se usa tryAcquire con
 * plazo (y no tryAcquire() sin argumentos, que ignora la equidad), lo que
 * resuelve a la vez "espera 1 s y, si no hay hueco, abandona".
 *
 * No hay estado mutable propio: tique y tarifa se calculan con datos locales,
 * por lo que no se necesita ningún cerrojo adicional.
 *
 * Contrato: cada tique debe usarse en salir() UNA sola vez.
 */
public class Aparcamiento {

    public static final int CAPACIDAD = 50;

    private static final long ESPERA_MAXIMA_SEGUNDOS = 1L;
    private static final long LIMITE_GRATIS_MS = 5_000L;
    private static final long LIMITE_TARIFA_PLANA_MS = 10_000L;
    private static final long TARIFA_PLANA_CENTIMOS = 100L;
    private static final long TARIFA_SEGUNDO_EXTRA_CENTIMOS = 50L;
    private static final long MS_POR_SEGUNDO = 1_000L;

    private final Semaphore plazas = new Semaphore(CAPACIDAD, true);
    private final GestorHorario gestorHorario;

    public Aparcamiento(GestorHorario gestorHorario) {
        this.gestorHorario = gestorHorario;
    }

    /**
     * Intenta aparcar: espera como máximo 1 s a que haya plaza.
     *
     * @return el tique con la matrícula y la hora lógica de entrada, o null si
     *         no hubo plaza (o si el hilo fue interrumpido)
     */
    public TiqueAparcamiento entrar(String idCliente, String matricula) {
        try {
            if (!plazas.tryAcquire(ESPERA_MAXIMA_SEGUNDOS, TimeUnit.SECONDS)) {
                return null; // timeout: el cliente abandona el aparcamiento
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // restaura el flag
            return null;
        }
        return new TiqueAparcamiento(idCliente, matricula, gestorHorario.getTiempoTranscurridoMs());
    }

    /**
     * Barrera de salida: calcula la tarifa, libera la plaza y devuelve el importe.
     *
     * @return importe en CÉNTIMOS de euro (long, sin errores de coma flotante)
     */
    public long salir(TiqueAparcamiento tique) {
        long estanciaMs = gestorHorario.getTiempoTranscurridoMs() - tique.getTiempoEntradaMs();
        long importeCentimos = calcularTarifaCentimos(estanciaMs);
        plazas.release();
        return importeCentimos;
    }

    /**
     * Tarifa (Tabla 1), en céntimos, para una estancia en milisegundos lógicos:
     *   t < 5 s        -> 0 EUR
     *   5 s <= t <= 10 -> 1 EUR   (t = 5 s exacto se cobra en este tramo)
     *   t > 10 s       -> 1 EUR + 0,50 EUR por cada segundo (o fracción) sobre 10
     */
    public static long calcularTarifaCentimos(long estanciaMs) {
        if (estanciaMs < LIMITE_GRATIS_MS) {
            return 0L;
        }
        if (estanciaMs <= LIMITE_TARIFA_PLANA_MS) {
            return TARIFA_PLANA_CENTIMOS;
        }
        // división entera con redondeo al alza: ceil((t - 10 s) / 1 s)
        long segundosExtra = (estanciaMs - LIMITE_TARIFA_PLANA_MS + MS_POR_SEGUNDO - 1)
                / MS_POR_SEGUNDO;
        return TARIFA_PLANA_CENTIMOS + TARIFA_SEGUNDO_EXTRA_CENTIMOS * segundosExtra;
    }

    public int getPlazasLibres() {
        return plazas.availablePermits();
    }

    public int getCochesDentro() {
        return CAPACIDAD - plazas.availablePermits();
    }

    /** Estimación (la da el semáforo) de coches esperando plaza en la barrera. */
    public int getCochesEsperando() {
        return plazas.getQueueLength();
    }
}
