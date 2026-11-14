package javadona;

/**
 * Tique que recoge un cliente en la barrera de entrada del aparcamiento
 * (dato inmutable).
 *
 * Atributos final asignados en el constructor: puede pasarse entre hilos y
 * leerse sin sincronización. El tique muestra la matrícula del coche y la hora
 * de llegada. El tiempo es LÓGICO (descuenta las pausas del sistema), obtenido
 * de GestorHorario.getTiempoTranscurridoMs().
 */
public class TiqueAparcamiento {

    private final String idCliente;
    private final String matricula;
    private final long tiempoEntradaMs;

    public TiqueAparcamiento(String idCliente, String matricula, long tiempoEntradaMs) {
        this.idCliente = idCliente;
        this.matricula = matricula;
        this.tiempoEntradaMs = tiempoEntradaMs;
    }

    public String getIdCliente() {
        return idCliente;
    }

    public String getMatricula() {
        return matricula;
    }

    public long getTiempoEntradaMs() {
        return tiempoEntradaMs;
    }

    @Override
    public String toString() {
        return "Tique{cliente=" + idCliente + ", matricula=" + matricula
                + ", entrada=" + tiempoEntradaMs + " ms}";
    }
}
