package javadona;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Repositorio de los 100 productos del supermercado (P-00 ... P-99).
 *
 * Es de SOLO LECTURA una vez construido: las colecciones internas se rellenan
 * en el constructor y se exponen únicamente como vistas no modificables. Como
 * nadie las modifica después, varios hilos pueden consultarlas a la vez sin
 * cerrojos (los campos final se publican de forma segura al terminar el
 * constructor; el catálogo debe crearse ANTES de arrancar ningún hilo).
 *
 * Único estado mutable: el generador Random. java.util.Random es seguro para
 * uso concurrente (su semilla se actualiza de forma atómica), así que no
 * necesita protección adicional.
 */
public class Catalogo {

    public static final int NUM_PRODUCTOS = 100;
    private static final int PRECIO_MIN_CENTIMOS = 99;    // 0.99 EUR
    private static final int PRECIO_MAX_CENTIMOS = 1999;  // 19.99 EUR

    private final List<Producto> productos;
    private final Map<String, Producto> productosPorId;
    private final Random aleatorio = new Random();

    /** Genera los 100 productos con precio aleatorio entre 0.99 y 19.99 EUR. */
    public Catalogo() {
        List<Producto> lista = new ArrayList<Producto>(NUM_PRODUCTOS);
        Map<String, Producto> mapa = new HashMap<String, Producto>();
        for (int i = 0; i < NUM_PRODUCTOS; i++) {
            String id = String.format("P-%02d", i);
            // Se sortean céntimos enteros (99..1999 ambos incluidos) para que el
            // precio tenga exactamente 2 decimales y luego no haya errores al cobrar
            int centimos = PRECIO_MIN_CENTIMOS
                    + aleatorio.nextInt(PRECIO_MAX_CENTIMOS - PRECIO_MIN_CENTIMOS + 1);
            Producto producto = new Producto(id, centimos / 100.0);
            lista.add(producto);
            mapa.put(id, producto);
        }
        this.productos = Collections.unmodifiableList(lista);
        this.productosPorId = Collections.unmodifiableMap(mapa);
    }

    /** @return el producto con ese id, o null si no existe */
    public Producto getProducto(String id) {
        return productosPorId.get(id);
    }

    /** @return vista no modificable de los 100 productos, en orden P-00 ... P-99 */
    public List<Producto> getTodos() {
        return productos;
    }

    public int getTamano() {
        return productos.size();
    }

    /**
     * Devuelve n productos DISTINTOS elegidos al azar (lista de la compra).
     * Trabaja sobre una copia local, por lo que dos hilos que la llamen a la
     * vez no interfieren entre sí. El orden devuelto es el orden de búsqueda.
     *
     * @throws IllegalArgumentException si n está fuera de [0, 100]
     */
    public List<Producto> getListaAleatoria(int n) {
        if (n < 0 || n > productos.size()) {
            throw new IllegalArgumentException("n debe estar entre 0 y " + productos.size());
        }
        List<Producto> copia = new ArrayList<Producto>(productos);
        Collections.shuffle(copia, aleatorio);
        return new ArrayList<Producto>(copia.subList(0, n));
    }
}
