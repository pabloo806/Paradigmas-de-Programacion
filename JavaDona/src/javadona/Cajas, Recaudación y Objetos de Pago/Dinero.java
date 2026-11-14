package javadona;

/**
 * Utilidad estática para trabajar con dinero en CÉNTIMOS (long).
 *
 * Todos los importes del sistema se guardan como enteros para evitar los
 * errores de redondeo de la coma flotante al sumar. Sin estado: es segura
 * para usarse desde cualquier hilo sin sincronización.
 */
public final class Dinero {

    private static final long CENTIMOS_POR_EURO = 100L;

    private Dinero() {
        // No instanciable
    }

    /** Convierte euros (p. ej. el precio de un Producto) a céntimos, redondeando al más próximo. */
    public static long aCentimos(double euros) {
        return Math.round(euros * CENTIMOS_POR_EURO);
    }

    /**
     * Formatea céntimos como euros con dos decimales y coma decimal.
     * Ejemplo: 3457 -> "34,57 €". Se construye a mano (sin String.format) para
     * no depender de la configuración regional de la máquina.
     */
    public static String formatear(long centimos) {
        long absoluto = Math.abs(centimos);
        long euros = absoluto / CENTIMOS_POR_EURO;
        long resto = absoluto % CENTIMOS_POR_EURO;
        String signo = centimos < 0 ? "-" : "";
        return signo + euros + "," + (resto < 10 ? "0" : "") + resto + " €";
    }
}
