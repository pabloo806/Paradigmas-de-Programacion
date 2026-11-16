package javadona;

import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Cajas de autopago del supermercado (recurso compartido): una única cola de
 * acceso y hasta 10 clientes pagando a la vez.
 *
 * Sincronización: DOS semáforos y NINGÚN cerrojo propio (no puede haber
 * anidamiento de cerrojos ni interbloqueo dentro de esta clase).
 *  - 'maquinas' = Semaphore(10, true): máquinas OPERATIVAS. Un permiso es una
 *    máquina en uso o bloqueada. Al ser justo, es la cola FIFO de entrada.
 *  - 'bloqueadas' = Semaphore(0): "fichas" de máquinas pendientes de que un
 *    encargado las desbloquee. Señalización productor-consumidor en la que un
 *    permiso liberado antes de que alguien espere NO se pierde.
 *
 * Los permisos de un Semaphore NO tienen propietario: el cliente adquiere el
 * permiso de 'maquinas' y, si la máquina se bloquea, se marcha sin devolverlo;
 * el encargado lo devuelve (release) cuando termina de desbloquearla.
 * Invariante: permisos libres + clientes pagando + máquinas bloqueadas = 10.
 *
 * REGLA: sleep, CajaFuerte, Log y ListaConcurrente se llaman fuera de cualquier
 * sección crítica.
 */
public class CajaAutopago {

    public static final int NUM_MAQUINAS = 10;
    public static final long TIEMPO_PAGO_MS = 1500L;
    public static final long TIEMPO_DESBLOQUEO_MS = 2500L;
    public static final long PLAZO_REEVALUACION_MS = 500L;
    private static final int PROBABILIDAD_BLOQUEO = 33; // porcentaje

    private final Semaphore maquinas = new Semaphore(NUM_MAQUINAS, true);
    private final Semaphore bloqueadas = new Semaphore(0);
    private final Random aleatorio = new Random(); // java.util.Random es seguro entre hilos

    private final CajaFuerte cajaFuerte;
    private final Log log;
    private final ListaConcurrente<String> colaEspera;
    private final ListaConcurrente<String> pagando;

    public CajaAutopago(CajaFuerte cajaFuerte, Log log,
                        ListaConcurrente<String> colaEspera,
                        ListaConcurrente<String> pagando) {
        this.cajaFuerte = cajaFuerte;
        this.log = log;
        this.colaEspera = colaEspera;
        this.pagando = pagando;
    }

    // ------------------------------------------------------------------
    // Protocolo del CLIENTE
    // ------------------------------------------------------------------

    /**
     * El cliente espera una máquina, escanea/paga y se marcha. Si tras el pago
     * la máquina se bloquea (33 %), el cliente NO devuelve el permiso: lo hará
     * el encargado al desbloquearla. El cliente no espera al desbloqueo.
     *
     * @return true si ha pagado; false si fue interrumpido antes de conseguir máquina
     */
    public boolean pagar(String idCliente, long importeCentimos) {
        log.registrar("Cliente " + idCliente + " espera una máquina de autopago");
        colaEspera.meter(idCliente);
        try {
            maquinas.acquire(); // cola FIFO justa
        } catch (InterruptedException e) {
            colaEspera.sacar(idCliente);
            Thread.currentThread().interrupt();
            return false;
        }
        colaEspera.sacar(idCliente);
        pagando.meter(idCliente);

        boolean seBloquea = false;
        try {
            try {
                Thread.sleep(TIEMPO_PAGO_MS); // el cliente "es" la máquina: sin cerrojo
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // restaura el flag pero TERMINA el pago
            }
            cajaFuerte.ingresarVentas(importeCentimos);
            log.registrar("Cliente " + idCliente + " ha pagado "
                    + Dinero.formatear(importeCentimos) + " en una máquina de autopago");
            seBloquea = aleatorio.nextInt(100) < PROBABILIDAD_BLOQUEO;
            if (seBloquea) {
                log.registrar("La máquina de autopago se bloquea tras el pago de Cliente " + idCliente);
            }
        } finally {
            pagando.sacar(idCliente);
            if (seBloquea) {
                bloqueadas.release(); // ficha para el encargado; el permiso de 'maquinas' queda retenido
            } else {
                maquinas.release();   // máquina libre para el siguiente de la cola
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Protocolo del ENCARGADO de autopago
    // ------------------------------------------------------------------

    /**
     * Desbloquea una máquina si hay alguna pendiente. Espera como máximo
     * 'plazoMs' una ficha, así que nunca se cuelga si no hay máquinas
     * bloqueadas (p. ej. al cierre): devuelve false y el Encargado reevalúa
     * su rol o comprueba si ha terminado la jornada.
     *
     * @return true si ha desbloqueado una máquina; false si no había ninguna (o fue interrumpido)
     */
    public boolean desbloquearMaquina(String idEncargado, long plazoMs) {
        try {
            if (!bloqueadas.tryAcquire(plazoMs, TimeUnit.MILLISECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        try {
            log.registrar("Encargado " + idEncargado + " empieza a desbloquear una máquina de autopago");
            try {
                Thread.sleep(TIEMPO_DESBLOQUEO_MS); // fuera de cualquier sección crítica
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } finally {
            log.registrar("Encargado " + idEncargado + " ha desbloqueado una máquina de autopago");
            maquinas.release(); // la máquina vuelve a estar operativa (sea cual sea el motivo de salida)
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Consultas (GUI / RMI / Plantilla)
    // ------------------------------------------------------------------

    /** Clientes esperando máquina. */
    public int getClientesEnCola() {
        return colaEspera.getTamano();
    }

    /** Clientes pagando ahora mismo. */
    public int getClientesPagando() {
        return pagando.getTamano();
    }

    /** Clientes en la caja de autopago: en cola + pagando (dos lecturas, no una instantánea atómica). */
    public int getClientesEnCaja() {
        return colaEspera.getTamano() + pagando.getTamano();
    }

    /** Máquinas bloqueadas que aún esperan a que un encargado empiece a desbloquearlas. */
    public int getMaquinasBloqueadas() {
        return bloqueadas.availablePermits();
    }

    public int getMaquinasLibres() {
        return maquinas.availablePermits();
    }
}
