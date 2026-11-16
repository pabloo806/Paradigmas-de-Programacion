package javadona.remoto;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import javadona.Supermercado;

/**
 * Implementación del servicio remoto: delega en la fachada Supermercado.
 *
 * NO necesita sincronización propia: el runtime RMI atiende cada llamada en su
 * propio hilo, y esos hilos solo invocan getters de Supermercado que ya son
 * thread-safe (cada uno toma, como mucho, una sección crítica corta de un único
 * recurso). El único estado de esta clase es una referencia final.
 *
 * NO debe pasar por ControlPausa.mirar(): los hilos de RMI son infraestructura
 * de red, no actores de la simulación. Si se detuvieran en una pausa, el cliente
 * remoto dejaría de recibir datos justo cuando más le interesa observar, y no
 * podría reanudar la simulación.
 */
public final class ServicioSupermercadoImpl extends UnicastRemoteObject
        implements ServicioSupermercado {

    private static final long serialVersionUID = 1L;

    private final transient Supermercado supermercado; // no se serializa: el objeto remoto no viaja

    /**
     * @param supermercado fachada del sistema
     * @param puertoObjeto puerto TCP fijo del objeto remoto (así solo hay dos puertos que abrir)
     */
    public ServicioSupermercadoImpl(Supermercado supermercado, int puertoObjeto)
            throws RemoteException {
        super(puertoObjeto);
        this.supermercado = supermercado;
    }

    /** Construye una foto NUEVA en cada llamada. */
    @Override
    public EstadoSupermercado obtenerEstado() throws RemoteException {
        // 'finalizada' se lee PRIMERO. Si se leyera al final, podría verse true con
        // valores leídos antes del cierre y el cliente se quedaría con una foto final
        // obsoleta. Leída la primera, true implica que el resto es ya definitivo.
        boolean finalizada = supermercado.isFinalizada();
        return new EstadoSupermercado(
                supermercado.getClientesEsperandoTienda(),
                supermercado.getClientesComprando(),
                supermercado.getClientesCajaTradicional(),
                supermercado.getClientesCajaAutopago(),
                supermercado.getCochesDentroAparcamiento(),
                supermercado.getCochesEsperandoAparcamiento(),
                supermercado.getEncargadosEnAlmacen(),
                supermercado.getEncargadosReponiendo(),
                supermercado.getEncargadosCajaTradicional(),
                supermercado.getEncargadosCajaAutopago(),
                supermercado.getUnidadesEstanterias(),
                supermercado.getUnidadesAlmacen(),
                supermercado.getRecaudacionCentimos(),
                supermercado.isPausado(),
                finalizada,
                supermercado.getEstadoTienda().name(),
                supermercado.getTiempoLogicoMs());
    }

    @Override
    public boolean establecerPausa(boolean pausada) throws RemoteException {
        return supermercado.establecerPausa(pausada);
    }
}
