package javadona.remoto;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Contrato del servicio remoto del supermercado (RMI). Es la ÚNICA interfaz que
 * comparten servidor y cliente.
 *
 * Solo dos operaciones: una consulta que devuelve una foto completa del estado
 * (1 viaje de red cada 500 ms en lugar de 13) y una orden de pausa expresada
 * como estado DESEADO (idempotente), no como "alternar".
 */
public interface ServicioSupermercado extends Remote {

    /** Foto del estado actual: los 13 indicadores, la pausa, el fin de simulación y el reloj. */
    EstadoSupermercado obtenerEstado() throws RemoteException;

    /**
     * Detiene (true) o reanuda (false) la simulación. Idempotente: pedir el estado
     * en el que ya está no cambia nada.
     *
     * @return true si la simulación queda pausada
     */
    boolean establecerPausa(boolean pausada) throws RemoteException;
}
