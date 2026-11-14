package javadona;

import java.util.concurrent.Semaphore;

/**
 * Almacén del supermercado (recurso compartido): 10 carros, uno por estantería,
 * y un aforo de 3 empleados simultáneos.
 *
 * Sincronización:
 *  - Aforo: Semaphore(3, true). Es capacidad pura con cola FIFO. El encargado
 *    lo adquiere dos veces por ciclo (coger el carro y devolverlo). Los sleep
 *    de esos trayectos van en el hilo Encargado, nunca aquí dentro.
 *  - Stock: cada Carro protege el suyo con su propio monitor. Esta clase no
 *    tiene estado mutable propio, por lo que no necesita más cerrojos.
 */
public class Almacen {

    public static final int AFORO_MAXIMO = 3;

    private final Semaphore aforo = new Semaphore(AFORO_MAXIMO, true);
    private final Carro[] carros = new Carro[Estanteria.NUM_ESTANTERIAS];

    public Almacen() {
        for (int i = 0; i < carros.length; i++) {
            carros[i] = new Carro(i);
        }
    }

    /**
     * Entra en el almacén esperando por orden de llegada si hay 3 empleados dentro.
     *
     * @return true si se ha conseguido entrar (y por tanto hay que llamar a salir());
     *         false si el hilo fue interrumpido y NO tiene permiso. Devolver el
     *         resultado evita que un salir() sin entrar() infle el semáforo.
     */
    public boolean entrar() {
        try {
            aforo.acquire();
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // restaura el flag
            return false;
        }
    }

    /** Sale del almacén y libera un hueco. Solo debe llamarse tras un entrar() que devolvió true. */
    public void salir() {
        aforo.release();
    }

    public Carro getCarro(int indiceEstanteria) {
        return carros[indiceEstanteria];
    }

    /**
     * true cuando no queda ninguna unidad en ningún carro. Recorre los carros de
     * uno en uno (nunca tiene dos monitores a la vez). Es EXACTO aunque no sea una
     * instantánea atómica: las unidades solo disminuyen, así que si todas las
     * lecturas dan 0, en el momento de la última lectura todo estaba a 0.
     */
    public boolean estaVacio() {
        for (int i = 0; i < carros.length; i++) {
            if (!carros[i].estaVacio()) {
                return false;
            }
        }
        return true;
    }

    /** Total de unidades en el almacén (para GUI/RMI). Aproximado si hay reposiciones en curso. */
    public int getUnidadesTotales() {
        int total = 0;
        for (int i = 0; i < carros.length; i++) {
            total += carros[i].getUnidadesTotales();
        }
        return total;
    }

    /** Empleados dentro del almacén ahora mismo (para RMI). */
    public int getEmpleadosDentro() {
        return AFORO_MAXIMO - aforo.availablePermits();
    }
}
