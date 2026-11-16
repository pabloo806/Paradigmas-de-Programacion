package javadona;

import java.util.concurrent.locks.Condition;

/**
 * Turno de un cliente en la cola de la CajaTradicional.
 *
 * Los atributos idCliente, importeCentimos y condicion son inmutables. El flag
 * 'cobrado' es estado compartido entre el cliente (que espera) y el cajero
 * (que lo marca): SOLO debe leerse o escribirse con el cerrojo de la
 * CajaTradicional en poder del hilo, que es el que protege este objeto.
 *
 * La Condition pertenece a ese mismo cerrojo (se crea con cerrojo.newCondition())
 * y tiene un único esperador, el cliente dueño del turno. Por eso el cajero
 * puede usar signal() en vez de signalAll() y despertar solo a quien toca.
 */
public class Turno {

    private final String idCliente;
    private final long importeCentimos;
    private final Condition condicion;
    private boolean cobrado = false;

    public Turno(String idCliente, long importeCentimos, Condition condicion) {
        this.idCliente = idCliente;
        this.importeCentimos = importeCentimos;
        this.condicion = condicion;
    }

    public String getIdCliente() {
        return idCliente;
    }

    public long getImporteCentimos() {
        return importeCentimos;
    }

    public Condition getCondicion() {
        return condicion;
    }

    /** Llamar solo con el cerrojo de la caja tomado. */
    public boolean isCobrado() {
        return cobrado;
    }

    /** Llamar solo con el cerrojo de la caja tomado. */
    public void setCobrado(boolean cobrado) {
        this.cobrado = cobrado;
    }
}
