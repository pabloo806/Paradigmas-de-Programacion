package javadona.remoto;

import java.io.Serializable;

/**
 * Foto del estado del supermercado (DTO inmutable y serializable).
 *
 * Todos los atributos son final: el objeto no cambia tras construirse, así que
 * puede viajar por RMI y compartirse entre hilos sin sincronización. Los valores
 * se leen en varias llamadas independientes al servidor: es una foto coherente
 * para refrescar una pantalla, no una instantánea atómica del sistema.
 *
 * El estado de la tienda va como String (nombre del estado) para que el DTO no
 * dependa de clases de la Parte 1.
 */
public class EstadoSupermercado implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int clientesEsperando;
    private final int clientesComprando;
    private final int clientesCajaTradicional;
    private final int clientesCajaAutopago;
    private final int cochesDentro;
    private final int cochesEsperando;
    private final int encargadosAlmacen;
    private final int encargadosReponiendo;
    private final int encargadosCajaTradicional;
    private final int encargadosCajaAutopago;
    private final int unidadesEstanterias;
    private final int unidadesAlmacen;
    private final long recaudacionCentimos;
    private final boolean pausado;
    private final boolean finalizada;
    private final String estadoTienda;
    private final long tiempoLogicoMs;

    public EstadoSupermercado(int clientesEsperando, int clientesComprando,
                              int clientesCajaTradicional, int clientesCajaAutopago,
                              int cochesDentro, int cochesEsperando,
                              int encargadosAlmacen, int encargadosReponiendo,
                              int encargadosCajaTradicional, int encargadosCajaAutopago,
                              int unidadesEstanterias, int unidadesAlmacen,
                              long recaudacionCentimos,
                              boolean pausado, boolean finalizada,
                              String estadoTienda, long tiempoLogicoMs) {
        this.clientesEsperando = clientesEsperando;
        this.clientesComprando = clientesComprando;
        this.clientesCajaTradicional = clientesCajaTradicional;
        this.clientesCajaAutopago = clientesCajaAutopago;
        this.cochesDentro = cochesDentro;
        this.cochesEsperando = cochesEsperando;
        this.encargadosAlmacen = encargadosAlmacen;
        this.encargadosReponiendo = encargadosReponiendo;
        this.encargadosCajaTradicional = encargadosCajaTradicional;
        this.encargadosCajaAutopago = encargadosCajaAutopago;
        this.unidadesEstanterias = unidadesEstanterias;
        this.unidadesAlmacen = unidadesAlmacen;
        this.recaudacionCentimos = recaudacionCentimos;
        this.pausado = pausado;
        this.finalizada = finalizada;
        this.estadoTienda = estadoTienda;
        this.tiempoLogicoMs = tiempoLogicoMs;
    }

    public int getClientesEsperando() {
        return clientesEsperando;
    }

    public int getClientesComprando() {
        return clientesComprando;
    }

    public int getClientesCajaTradicional() {
        return clientesCajaTradicional;
    }

    public int getClientesCajaAutopago() {
        return clientesCajaAutopago;
    }

    public int getCochesDentro() {
        return cochesDentro;
    }

    public int getCochesEsperando() {
        return cochesEsperando;
    }

    public int getEncargadosAlmacen() {
        return encargadosAlmacen;
    }

    public int getEncargadosReponiendo() {
        return encargadosReponiendo;
    }

    public int getEncargadosCajaTradicional() {
        return encargadosCajaTradicional;
    }

    public int getEncargadosCajaAutopago() {
        return encargadosCajaAutopago;
    }

    public int getUnidadesEstanterias() {
        return unidadesEstanterias;
    }

    public int getUnidadesAlmacen() {
        return unidadesAlmacen;
    }

    public long getRecaudacionCentimos() {
        return recaudacionCentimos;
    }

    public boolean isPausado() {
        return pausado;
    }

    public boolean isFinalizada() {
        return finalizada;
    }

    /** Nombre del estado de la tienda: PRE_APERTURA, ABIERTA o CERRADA. */
    public String getEstadoTienda() {
        return estadoTienda;
    }

    public long getTiempoLogicoMs() {
        return tiempoLogicoMs;
    }
}
