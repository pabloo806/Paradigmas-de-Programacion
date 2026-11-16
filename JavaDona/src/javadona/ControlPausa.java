package javadona;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Mecanismo de pausa cooperativa (patrón "Paso" de la sesión 3).
 *
 * Los hilos llaman a mirar() en puntos seguros de su ciclo de vida. Si el
 * sistema está pausado, se bloquean en la Condition hasta que se reanude.
 *
 * REGLA DE USO: mirar() debe llamarse SIN tener en posesión ningún otro
 * cerrojo ni permiso de semáforo, para no bloquear recursos de terceros
 * mientras el hilo está parado (evita interbloqueos y inanición).
 *
 * Esta clase es independiente de GestorHorario: quien pause el sistema
 * (la clase Supermercado) debe invocar pausar()/reanudar() en ambas, una tras
 * otra y nunca anidadas, para respetar la regla de no anidar cerrojos.
 */
public class ControlPausa {

    private final Lock cerrojo = new ReentrantLock();
    private final Condition reanudado = cerrojo.newCondition();
    private boolean pausado = false;

    /**
     * Detiene al hilo llamante mientras el sistema esté pausado.
     * Exclusión mutua: el cerrojo protege la lectura/escritura de 'pausado'.
     * await() libera el cerrojo atómicamente, así que no bloquea a quien reanuda.
     * Se usa while (y no if) para revalidar la condición tras cada despertar.
     */
    public void mirar() {
        cerrojo.lock();
        try {
            while (pausado) {
                try {
                    reanudado.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // conserva la interrupción
                    return;
                }
            }
        } finally {
            cerrojo.unlock();
        }
    }

    /** Pone el sistema en pausa. Los hilos se detendrán en su próximo mirar(). */
    public void pausar() {
        cerrojo.lock();
        try {
            pausado = true;
        } finally {
            cerrojo.unlock();
        }
    }

    /** Reanuda el sistema y despierta a TODOS los hilos detenidos. */
    public void reanudar() {
        cerrojo.lock();
        try {
            pausado = false;
            reanudado.signalAll();
        } finally {
            cerrojo.unlock();
        }
    }

    /**
     * Alterna el estado (útil para un único botón Pausar/Reanudar).
     * La comprobación y el cambio ocurren en la misma sección crítica.
     *
     * @return true si tras la llamada el sistema queda pausado
     */
    public boolean alternar() {
        cerrojo.lock();
        try {
            pausado = !pausado;
            if (!pausado) {
                reanudado.signalAll();
            }
            return pausado;
        } finally {
            cerrojo.unlock();
        }
    }

    public boolean estaPausado() {
        cerrojo.lock();
        try {
            return pausado;
        } finally {
            cerrojo.unlock();
        }
    }
}
