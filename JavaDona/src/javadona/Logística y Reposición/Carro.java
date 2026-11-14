package javadona;

/**
 * Carro del almacén asociado a una estantería (recurso compartido).
 * Contiene las unidades de reserva de los 10 productos de su estantería
 * (170 de cada uno al empezar).
 *
 * Exclusión mutua: todos los métodos son synchronized. Lo modifica el
 * encargado que tiene reservada la estantería, pero lo LEEN además otros hilos
 * (Almacen.estaVacio desde los encargados, y la GUI/RMI), por lo que el acceso
 * debe estar protegido para garantizar visibilidad y lecturas coherentes.
 *
 * Es un NODO HOJA en la jerarquía de recursos: nunca llama a otro recurso desde
 * su sección crítica, así que no puede participar en un interbloqueo.
 */
public class Carro {

    public static final int UNIDADES_INICIALES_POR_PRODUCTO = 170;

    private final int indiceEstanteria;
    private final int[] unidades = new int[Estanteria.PRODUCTOS_POR_ESTANTERIA];

    public Carro(int indiceEstanteria) {
        this.indiceEstanteria = indiceEstanteria;
        for (int i = 0; i < unidades.length; i++) {
            unidades[i] = UNIDADES_INICIALES_POR_PRODUCTO;
        }
    }

    /**
     * Retira hasta 'cantidad' unidades del producto (posición 0..9 en la estantería).
     *
     * @return las unidades realmente retiradas (puede ser menos si el carro no tiene tantas)
     */
    public synchronized int extraer(int indiceProducto, int cantidad) {
        if (cantidad <= 0) {
            return 0;
        }
        int retiradas = Math.min(cantidad, unidades[indiceProducto]);
        unidades[indiceProducto] -= retiradas;
        return retiradas;
    }

    public synchronized int getUnidadesProducto(int indiceProducto) {
        return unidades[indiceProducto];
    }

    public synchronized int getUnidadesTotales() {
        int total = 0;
        for (int i = 0; i < unidades.length; i++) {
            total += unidades[i];
        }
        return total;
    }

    public synchronized boolean estaVacio() {
        return getUnidadesTotales() == 0; // synchronized es reentrante
    }

    public int getIndiceEstanteria() {
        return indiceEstanteria;
    }
}
