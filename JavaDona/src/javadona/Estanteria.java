package javadona;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Estantería del supermercado (recurso compartido): 10 productos consecutivos
 * del catálogo, con un máximo de 20 unidades de cada uno.
 *
 * Sincronización: cada estantería tiene SU PROPIO ReentrantLock y su Condition
 * 'hayStock', de modo que clientes y encargados de estanterías distintas no
 * compiten entre sí. Se usa Condition (y no synchronized/wait) por la espera
 * TEMPORIZADA de 3 s (awaitNanos) que exige el enunciado.
 *
 * Reserva de reposición: el flag 'enReposicion' garantiza que dos encargados no
 * repongan la misma estantería a la vez. La reserva dura todo el ciclo del
 * encargado, pero el CERROJO no: solo se retiene en secciones cortas, para que
 * los clientes puedan seguir cogiendo productos mientras se repone.
 *
 * Jerarquía de recursos: esta clase NUNCA retiene su cerrojo mientras llama a
 * un Carro (ver reponer). No hay anidamiento de cerrojos.
 */
public class Estanteria {

    public static final int NUM_ESTANTERIAS = 10;
    public static final int PRODUCTOS_POR_ESTANTERIA = 10;
    public static final int CAPACIDAD_POR_PRODUCTO = 20;
    private static final long ESPERA_REPOSICION_SEGUNDOS = 3L;

    private final int indice; // 0..9
    private final List<Producto> productos; // inmutable tras el constructor
    private final Lock cerrojo = new ReentrantLock();
    private final Condition hayStock = cerrojo.newCondition();

    // Estado compartido, protegido por 'cerrojo'
    private final int[] stock = new int[PRODUCTOS_POR_ESTANTERIA];
    private boolean enReposicion = false;

    /**
     * @param indice   posición de la estantería, de 0 a 9 (la 0 contiene P-00..P-09)
     * @param catalogo catálogo del que toma sus 10 productos consecutivos
     */
    public Estanteria(int indice, Catalogo catalogo) {
        if (indice < 0 || indice >= NUM_ESTANTERIAS) {
            throw new IllegalArgumentException("Índice de estantería fuera de rango: " + indice);
        }
        this.indice = indice;
        int desde = indice * PRODUCTOS_POR_ESTANTERIA;
        this.productos = Collections.unmodifiableList(new ArrayList<Producto>(
                catalogo.getTodos().subList(desde, desde + PRODUCTOS_POR_ESTANTERIA)));
        for (int i = 0; i < PRODUCTOS_POR_ESTANTERIA; i++) {
            stock[i] = CAPACIDAD_POR_PRODUCTO;
        }
    }

    // ------------------------------------------------------------------
    // Métodos para CLIENTES
    // ------------------------------------------------------------------

    /**
     * Coge una unidad del producto. Si no hay, espera hasta 3 s a que un
     * encargado reponga.
     *
     * Se usa while (no if) y se recalcula el tiempo restante: awaitNanos
     * devuelve una estimación de lo que queda, y tras despertar otro cliente
     * puede haberse llevado la unidad repuesta, así que hay que revalidar.
     * Se usa signalAll (en reponer) porque en la misma Condition esperan
     * clientes de productos distintos.
     *
     * @return true si consiguió el producto; false si se agotó la espera (o fue interrumpido)
     */
    public boolean cogerProducto(String idProducto) {
        int i = indiceLocal(idProducto);
        long restanteNanos = TimeUnit.SECONDS.toNanos(ESPERA_REPOSICION_SEGUNDOS);
        cerrojo.lock();
        try {
            while (stock[i] == 0) {
                if (restanteNanos <= 0L) {
                    return false; // plazo agotado y sigue sin stock
                }
                try {
                    restanteNanos = hayStock.awaitNanos(restanteNanos); // libera el cerrojo mientras espera
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // restaura el flag
                    return false;
                }
            }
            stock[i]--;
            return true;
        } finally {
            cerrojo.unlock();
        }
    }

    // ------------------------------------------------------------------
    // Métodos para ENCARGADOS (reposición exclusiva)
    // ------------------------------------------------------------------

    /**
     * Intenta reservar la estantería para reponerla. La comprobación
     * "no reservada Y algún producto por debajo de 20" y la marca de reserva
     * ocurren en la MISMA sección crítica (atómicas): dos encargados nunca
     * pueden reservar la misma estantería.
     *
     * @return true si la reserva se ha concedido
     */
    public boolean reservarParaReponer() {
        cerrojo.lock();
        try {
            if (enReposicion) {
                return false;
            }
            for (int i = 0; i < PRODUCTOS_POR_ESTANTERIA; i++) {
                if (stock[i] < CAPACIDAD_POR_PRODUCTO) {
                    enReposicion = true;
                    return true;
                }
            }
            return false; // estantería completa: no hace falta reponer
        } finally {
            cerrojo.unlock();
        }
    }

    /**
     * Cancela una reserva sin reponer (p. ej. el encargado descubre en el
     * almacén que no quedan unidades, o es interrumpido). No altera el stock,
     * por lo que no hay nada que señalar a los clientes.
     */
    public void liberarReserva() {
        cerrojo.lock();
        try {
            enReposicion = false;
        } finally {
            cerrojo.unlock();
        }
    }

    /**
     * Rellena cada producto hasta 20 unidades con lo que haya en el carro,
     * libera la reserva y despierta a los clientes que esperaban stock.
     *
     * DISEÑO SIN ANIDAR CERROJOS: el carro tiene su propio monitor (lo leen
     * también la GUI y Almacen.estaVacio desde otros hilos), así que NO se
     * toca mientras se retiene el cerrojo de la estantería. Se hace en 3 fases:
     *   1) [cerrojo estantería] calcular lo que falta de cada producto
     *   2) [sin cerrojo estantería] extraer del carro (monitor del carro)
     *   3) [cerrojo estantería] añadir lo extraído, liberar reserva, signalAll
     * Es correcto porque, mientras la estantería está reservada, nadie más la
     * repone; entre las fases 1 y 3 el stock solo puede BAJAR (clientes), de
     * modo que nunca se supera el máximo de 20.
     *
     * @return desglose del stock actualizado, p. ej. "Producto P-00x20, Producto P-01x18, ..."
     * @throws IllegalStateException si la estantería no estaba reservada
     * @throws IllegalArgumentException si el carro no corresponde a esta estantería
     */
    public String reponer(Carro carro) {
        if (carro.getIndiceEstanteria() != indice) {
            throw new IllegalArgumentException("El carro " + carro.getIndiceEstanteria()
                    + " no corresponde a la estantería " + indice);
        }
        int[] faltantes = new int[PRODUCTOS_POR_ESTANTERIA];

        // Fase 1
        cerrojo.lock();
        try {
            if (!enReposicion) {
                throw new IllegalStateException("Estantería no reservada para reponer");
            }
            for (int i = 0; i < PRODUCTOS_POR_ESTANTERIA; i++) {
                faltantes[i] = CAPACIDAD_POR_PRODUCTO - stock[i];
            }
        } finally {
            cerrojo.unlock();
        }

        // Fase 2 (fuera del cerrojo de la estantería)
        int[] extraidas = new int[PRODUCTOS_POR_ESTANTERIA];
        for (int i = 0; i < PRODUCTOS_POR_ESTANTERIA; i++) {
            if (faltantes[i] > 0) {
                extraidas[i] = carro.extraer(i, faltantes[i]);
            }
        }

        // Fase 3
        cerrojo.lock();
        try {
            for (int i = 0; i < PRODUCTOS_POR_ESTANTERIA; i++) {
                stock[i] += extraidas[i];
            }
            enReposicion = false;
            hayStock.signalAll(); // los clientes revalidan su condición en el while
            return construirDesglose();
        } finally {
            cerrojo.unlock();
        }
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    /** Total de unidades en la estantería (para GUI/RMI). */
    public int getUnidadesTotales() {
        cerrojo.lock();
        try {
            int total = 0;
            for (int i = 0; i < PRODUCTOS_POR_ESTANTERIA; i++) {
                total += stock[i];
            }
            return total;
        } finally {
            cerrojo.unlock();
        }
    }

    public int getIndice() {
        return indice;
    }

    /** Número de estantería para mostrar (1..10). */
    public int getNumero() {
        return indice + 1;
    }

    // ------------------------------------------------------------------
    // Auxiliares privados
    // ------------------------------------------------------------------

    /** Posición (0..9) del producto dentro de esta estantería. Sin cerrojo: 'productos' es inmutable. */
    private int indiceLocal(String idProducto) {
        for (int i = 0; i < productos.size(); i++) {
            if (productos.get(i).getId().equals(idProducto)) {
                return i;
            }
        }
        throw new IllegalArgumentException("El producto " + idProducto
                + " no está en la estantería " + getNumero());
    }

    /** Debe llamarse con el cerrojo en poder del hilo. */
    private String construirDesglose() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PRODUCTOS_POR_ESTANTERIA; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("Producto ").append(productos.get(i).getId()).append('x').append(stock[i]);
        }
        return sb.toString();
    }
}
