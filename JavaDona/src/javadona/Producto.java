package javadona;

/**
 * Artículo del supermercado (dato inmutable).
 *
 * Todos sus atributos son final y se asignan en el constructor, por lo que
 * el objeto nunca cambia tras su construcción. Por eso puede compartirse
 * entre hilos SIN sincronización: la semántica de los campos final de Java
 * garantiza que cualquier hilo que obtenga la referencia ve los valores correctos.
 */
public class Producto {

    private final String id;
    private final double precio;

    /**
     * @param id     identificador con formato "P-XX" (XX entre 00 y 99)
     * @param precio precio unitario en euros
     */
    public Producto(String id, double precio) {
        this.id = id;
        this.precio = precio;
    }

    public String getId() {
        return id;
    }

    public double getPrecio() {
        return precio;
    }

    @Override
    public String toString() {
        return id;
    }
}
