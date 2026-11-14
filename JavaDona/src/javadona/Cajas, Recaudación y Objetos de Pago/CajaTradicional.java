package javadona;

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Cajas tradicionales del supermercado (recurso compartido): UNA cola FIFO
 * atendida por N cajeros (encargados).
 *
 * Sincronización: un único ReentrantLock protege la cola, el contador de
 * clientes en atención y los flags 'cobrado' de los turnos.
 *  - Condition 'hayCliente': los cajeros esperan aquí cuando la cola está vacía.
 *  - Condition POR TURNO: cada cliente espera en la suya; el cajero despierta
 *    solo a quien ha cobrado (signal, no signalAll).
 * El orden FIFO lo garantiza la estructura (LinkedList), no las Condition.
 *
 * REGLA: el sleep de cobro, CajaFuerte, Log y ListaConcurrente se llaman
 * SIEMPRE con el cerrojo de la caja LIBRE. No hay anidamiento de cerrojos.
 */
public class CajaTradicional {

    public static final long TIEMPO_COBRO_MS = 500L;
    public static final long PLAZO_REEVALUACION_MS = 500L;

    private final Lock cerrojo = new ReentrantLock();
    private final Condition hayCliente = cerrojo.newCondition();

    // Estado compartido, protegido por 'cerrojo'
    private final LinkedList<Turno> cola = new LinkedList<Turno>();
    private int clientesAtendiendo = 0;

    private final CajaFuerte cajaFuerte;
    private final Log log;
    private final ListaConcurrente<String> colaEspera;
    private final ListaConcurrente<String> enAtencion;

    public CajaTradicional(CajaFuerte cajaFuerte, Log log,
                           ListaConcurrente<String> colaEspera,
                           ListaConcurrente<String> enAtencion) {
        this.cajaFuerte = cajaFuerte;
        this.log = log;
        this.colaEspera = colaEspera;
        this.enAtencion = enAtencion;
    }

    // ------------------------------------------------------------------
    // Protocolo del CLIENTE
    // ------------------------------------------------------------------

    /**
     * El cliente se pone en la cola y espera a ser cobrado.
     *
     * @return true si ha pagado; false si fue interrumpido antes de que un
     *         cajero lo tomara (se retira de la cola y no paga)
     */
    public boolean pagar(String idCliente, long importeCentimos) {
        // El log va ANTES de encolar: nadie puede cobrarle antes de que "llegue"
        log.registrar("Cliente " + idCliente + " llega a la caja tradicional y espera su turno");
        colaEspera.meter(idCliente); // antes de encolar: el cajero siempre lo saca después de meterlo

        boolean retirado = false;
        boolean interrumpido = false;
        cerrojo.lock();
        try {
            Turno turno = new Turno(idCliente, importeCentimos, cerrojo.newCondition());
            cola.addLast(turno);
            hayCliente.signal(); // avisa a un cajero que esté esperando
            while (!turno.isCobrado()) { // while: se revalida tras cada despertar
                try {
                    turno.getCondicion().await(); // libera el cerrojo mientras espera
                } catch (InterruptedException e) {
                    if (cola.remove(turno)) {
                        // Ningún cajero lo había tomado: se retira sin pagar
                        Thread.currentThread().interrupt();
                        retirado = true;
                        break;
                    }
                    // Un cajero ya lo está cobrando: hay que esperar a que termine
                    interrumpido = true;
                }
            }
        } finally {
            cerrojo.unlock();
        }

        if (retirado) {
            colaEspera.sacar(idCliente);
            return false;
        }
        if (interrumpido) {
            Thread.currentThread().interrupt(); // restaura el flag al terminar el cobro
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Protocolo del CAJERO (encargado)
    // ------------------------------------------------------------------

    /**
     * El cajero atiende al siguiente cliente de la cola. Nunca espera sin plazo:
     * si no llega nadie en 'plazoMs', devuelve false para que el Encargado
     * reevalúe su rol o compruebe si ha terminado la jornada.
     *
     * @return true si ha cobrado a un cliente; false si no había nadie (o fue interrumpido)
     */
    public boolean atenderSiguiente(String idEncargado, long plazoMs) {
        long restanteNanos = TimeUnit.MILLISECONDS.toNanos(plazoMs);
        Turno turno;

        // FASE A: esperar cliente (con plazo) y tomarlo de la cola
        cerrojo.lock();
        try {
            while (cola.isEmpty()) { // primero el estado, luego el plazo
                if (restanteNanos <= 0L) {
                    return false;
                }
                try {
                    restanteNanos = hayCliente.awaitNanos(restanteNanos);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            turno = cola.removeFirst();
            clientesAtendiendo++;
        } finally {
            cerrojo.unlock();
        }

        try {
            // FASE B: todo con el cerrojo LIBRE
            colaEspera.sacar(turno.getIdCliente());
            enAtencion.meter(turno.getIdCliente());
            try {
                Thread.sleep(TIEMPO_COBRO_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // restaura el flag pero TERMINA el cobro
            }
            cajaFuerte.ingresarVentas(turno.getImporteCentimos());
            log.registrar("Encargado " + idEncargado + " ha cobrado "
                    + Dinero.formatear(turno.getImporteCentimos())
                    + " a Cliente " + turno.getIdCliente() + " en caja tradicional");
            enAtencion.sacar(turno.getIdCliente());
        } finally {
            // FASE C: pase lo que pase, el cliente debe ser despertado.
            // Ingreso y log ya están hechos: al continuar el cliente, el dinero consta.
            cerrojo.lock();
            try {
                turno.setCobrado(true);
                clientesAtendiendo--;
                turno.getCondicion().signal(); // solo despierta a su dueño
            } finally {
                cerrojo.unlock();
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Consultas (GUI / RMI / Plantilla)
    // ------------------------------------------------------------------

    /** Clientes esperando turno (exacto, bajo cerrojo). */
    public int getClientesEnCola() {
        cerrojo.lock();
        try {
            return cola.size();
        } finally {
            cerrojo.unlock();
        }
    }

    /** Clientes que están siendo cobrados ahora mismo. */
    public int getClientesAtendiendo() {
        cerrojo.lock();
        try {
            return clientesAtendiendo;
        } finally {
            cerrojo.unlock();
        }
    }

    /** Clientes en la caja tradicional: en cola + siendo atendidos (lectura coherente). */
    public int getClientesEnCaja() {
        cerrojo.lock();
        try {
            return cola.size() + clientesAtendiendo;
        } finally {
            cerrojo.unlock();
        }
    }
}
