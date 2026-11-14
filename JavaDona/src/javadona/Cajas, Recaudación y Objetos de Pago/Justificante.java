package javadona;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Justificante de compra de un cliente.
 *
 * Objeto CONFINADO al hilo Cliente: solo lo crea, modifica y lee su propio
 * cliente, así que no necesita sincronización. Si algún día se compartiera
 * con otro hilo, habría que protegerlo.
 *
 * Recoge lo que el cliente quería, lo que realmente consiguió, el importe de
 * la compra y el cargo de aparcamiento, todo en céntimos.
 */
public class Justificante {

    private final String idCliente;
    private final List<Producto> deseados;
    private final List<Producto> conseguidos = new ArrayList<Producto>();
    private long compraCentimos = 0L;
    private long aparcamientoCentimos = 0L;

    public Justificante(String idCliente, List<Producto> deseados) {
        this.idCliente = idCliente;
        this.deseados = Collections.unmodifiableList(new ArrayList<Producto>(deseados));
    }

    /** Registra un producto conseguido y suma su precio al importe de la compra. */
    public void anadirConseguido(Producto producto) {
        conseguidos.add(producto);
        compraCentimos += Dinero.aCentimos(producto.getPrecio());
    }

    public void setAparcamientoCentimos(long aparcamientoCentimos) {
        this.aparcamientoCentimos = aparcamientoCentimos;
    }

    public String getIdCliente() {
        return idCliente;
    }

    public List<Producto> getDeseados() {
        return deseados;
    }

    public List<Producto> getConseguidos() {
        return Collections.unmodifiableList(conseguidos);
    }

    public long getCompraCentimos() {
        return compraCentimos;
    }

    public long getAparcamientoCentimos() {
        return aparcamientoCentimos;
    }

    /**
     * Línea resumen final del cliente para el log. Ejemplo:
     * "Cliente C-0105 se va. Lista: P-03, P-17 | Comprados: P-03 | Compra: 34,57 € | Aparcamiento: 1,50 €"
     * Si no hubo cargo de aparcamiento (a pie o estancia corta): "Aparcamiento: sin cargo".
     */
    public String formatearResumen() {
        String parking = aparcamientoCentimos > 0L
                ? Dinero.formatear(aparcamientoCentimos)
                : "sin cargo";
        return "Cliente " + idCliente + " se va. Lista: " + listarIds(deseados)
                + " | Comprados: " + listarIds(conseguidos)
                + " | Compra: " + Dinero.formatear(compraCentimos)
                + " | Aparcamiento: " + parking;
    }

    private static String listarIds(List<Producto> productos) {
        if (productos.isEmpty()) {
            return "ninguno";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < productos.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(productos.get(i).getId());
        }
        return sb.toString();
    }
}
