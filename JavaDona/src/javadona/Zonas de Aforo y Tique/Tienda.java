package javadona;

import java.util.concurrent.Semaphore;

/**
 * Tienda del supermercado (recurso compartido): controla el aforo de 150 clientes.
 *
 * Sincronización: Semaphore(150, true). El contador es el aforo y el modo justo
 * (fair) garantiza que los clientes que esperan en la puerta entran por orden
 * de llegada. Los métodos no son synchronized: toda la exclusión mutua la da el
 * semáforo y las ListaConcurrente (que son monitores por sí mismas).
 *
 * ORDEN "CONTAR PRIMERO, COMPROBAR DESPUÉS" (evita una carrera al cierre):
 * si el cliente comprobase "abierta" y LUEGO se contase como dentro, un
 * encargado podría ver "cerrada y dentro == 0" justo en medio, terminar su
 * jornada, y el cliente entraría después sin nadie que le cobre.
 * Como el cierre es MONOTÓNICO (una vez cerrada, no se reabre), basta con:
 *   cliente:   1) se cuenta en 'dentro'   2) comprueba el horario
 *   encargado: 1) lee 'cerrada'           2) lee 'dentro'  (en este orden)
 * Si el encargado ve dentro == 0 tras ver cerrada, todo cliente que se cuente
 * después comprobará el horario después y lo verá cerrado, así que se irá.
 * No hace falta anidar cerrojos.
 */
public class Tienda {

    public static final int AFORO_MAXIMO = 150;

    private final Semaphore aforo = new Semaphore(AFORO_MAXIMO, true);
    private final GestorHorario gestorHorario;
    private final ListaConcurrente<String> esperando; // cola en la puerta
    private final ListaConcurrente<String> dentro;    // clientes dentro comprando

    public Tienda(GestorHorario gestorHorario,
                  ListaConcurrente<String> esperando,
                  ListaConcurrente<String> dentro) {
        this.gestorHorario = gestorHorario;
        this.esperando = esperando;
        this.dentro = dentro;
    }

    /**
     * Intenta entrar en la tienda, esperando por orden de llegada si está llena.
     *
     * @return true si el cliente ha entrado; false si la tienda estaba cerrada
     *         al conseguir turno (o si el hilo fue interrumpido) y debe irse
     */
    public boolean entrar(String idCliente) {
        esperando.meter(idCliente);
        try {
            aforo.acquire(); // bloquea en la cola FIFO del semáforo justo
        } catch (InterruptedException e) {
            esperando.sacar(idCliente);
            Thread.currentThread().interrupt(); // restaura el flag
            return false;
        }
        esperando.sacar(idCliente);

        dentro.meter(idCliente);                 // 1º: contarse
        if (!gestorHorario.estaAbierta()) {      // 2º: comprobar horario
            dentro.sacar(idCliente);
            aforo.release();                     // devuelve el permiso
            return false;
        }
        return true;
    }

    /**
     * Sale de la tienda y libera un hueco de aforo. Solo libera el permiso si el
     * cliente estaba realmente dentro (evita inflar el semáforo por error).
     */
    public void salir(String idCliente) {
        if (dentro.sacar(idCliente)) {
            aforo.release();
        }
    }

    /**
     * Condición de fin de jornada para los encargados: tienda cerrada y vacía.
     * IMPORTANTE: el orden de lectura (primero horario, luego ocupación) es el
     * que hace correcto el razonamiento de la cabecera; no debe invertirse.
     */
    public boolean haTerminadoJornada() {
        return gestorHorario.estaCerrada() && dentro.getTamano() == 0;
    }

    public int getClientesEsperando() {
        return esperando.getTamano();
    }

    public int getClientesDentro() {
        return dentro.getTamano();
    }

    public int getAforoLibre() {
        return aforo.availablePermits();
    }
}
