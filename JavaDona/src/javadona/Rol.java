package javadona;

/**
 * Zona o rol en el que trabaja un encargado.
 *
 * Enum inmutable: se comparte entre hilos sin sincronización.
 * El ORDEN de declaración (ordinal) lo usa Plantilla para indexar sus
 * tablas de objetivos y prioridades; no debe alterarse.
 */
public enum Rol {

    REPOSICION("Reposición"),
    CAJA_TRADICIONAL("Caja Tradicional"),
    CAJA_AUTOPAGO("Caja Autopago");

    private final String descripcion;

    private Rol(String descripcion) {
        this.descripcion = descripcion;
    }

    /** Texto legible para los mensajes del log. */
    public String getDescripcion() {
        return descripcion;
    }

    @Override
    public String toString() {
        return descripcion;
    }
}
