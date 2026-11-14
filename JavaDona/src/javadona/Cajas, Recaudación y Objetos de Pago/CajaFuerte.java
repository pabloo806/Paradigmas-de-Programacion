package javadona;

/**
 * Caja fuerte del supermercado (monitor): recaudación total en céntimos.
 *
 * Dos orígenes de ingresos, como distingue el enunciado: ventas cobradas en
 * las cajas y tarifas de aparcamiento.
 *
 * Exclusión mutua: todos los métodos son synchronized, así que cada ingreso
 * (una suma sobre un long, que NO es atómica por sí sola) y cada consulta se
 * ejecutan de forma indivisible. Las secciones críticas son mínimas.
 *
 * Es un NODO HOJA en la jerarquía de recursos: nunca llama a otro recurso
 * desde dentro de su monitor, por lo que no puede participar en un interbloqueo.
 */
public class CajaFuerte {

    private long ventasCentimos = 0L;
    private long aparcamientoCentimos = 0L;

    /** Ingresa el importe de una compra cobrada en caja. */
    public synchronized void ingresarVentas(long centimos) {
        validar(centimos);
        ventasCentimos += centimos;
    }

    /** Ingresa la tarifa de aparcamiento cobrada en la barrera de salida. */
    public synchronized void ingresarAparcamiento(long centimos) {
        validar(centimos);
        aparcamientoCentimos += centimos;
    }

    public synchronized long getVentasCentimos() {
        return ventasCentimos;
    }

    public synchronized long getAparcamientoCentimos() {
        return aparcamientoCentimos;
    }

    /** Recaudación total (ventas + aparcamiento), leída de forma coherente. */
    public synchronized long getTotalCentimos() {
        return ventasCentimos + aparcamientoCentimos;
    }

    private static void validar(long centimos) {
        if (centimos < 0L) {
            throw new IllegalArgumentException("El importe no puede ser negativo: " + centimos);
        }
    }
}
