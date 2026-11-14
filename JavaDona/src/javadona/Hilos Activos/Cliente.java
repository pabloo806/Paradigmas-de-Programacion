package javadona;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Cliente del supermercado (hilo). Ciclo de vida:
 *   creación -> desplazamiento -> [aparcamiento] -> tienda -> compra (máx. 2 vueltas)
 *   -> pago en caja -> salida (tienda y aparcamiento) -> línea resumen en el log.
 *
 * Los atributos de estado propio (tique, dentroTienda) solo los toca este hilo,
 * por lo que no necesitan sincronización; los recursos compartidos se protegen
 * en sus propias clases. Los atributos final se publican de forma segura con
 * start() (el constructor se ejecuta en el hilo creador).
 *
 * SALIDA ÚNICA: todos los caminos (normal, tienda cerrada, sin plaza, pago
 * fallido o interrupción) terminan en el finally de run(), que libera aforo y
 * plaza, cobra la tarifa y escribe el resumen. controlPausa.mirar() solo se
 * llama en los puntos seguros aprobados, nunca dentro de una llamada bloqueante.
 */
public class Cliente extends Thread {

    private static final String CONSONANTES = "BCDFGHJKLMNPRSTVWXYZ";
    private static final int MAX_PRODUCTOS_LISTA = 10;
    private static final int MAX_VUELTAS = 2;

    private final String id;
    private final Supermercado supermercado;
    private final Random aleatorio = new Random(); // uno por cliente: sin contención
    private final boolean enCoche;
    private final String matricula; // null si va andando
    private final Justificante justificante;

    private final ControlPausa controlPausa;
    private final Log log;
    private final Tienda tienda;
    private final Aparcamiento aparcamiento;
    private final CajaFuerte cajaFuerte;
    private final CajaTradicional cajaTradicional;
    private final CajaAutopago cajaAutopago;

    // Estado propio del hilo (confinado): solo lo usa run() y sus auxiliares
    private TiqueAparcamiento tique = null;
    private boolean dentroTienda = false;

    public Cliente(int numero, Supermercado supermercado) {
        this.id = String.format("C-%04d", numero);
        this.supermercado = supermercado;
        this.enCoche = aleatorio.nextBoolean(); // 50 %
        this.matricula = enCoche ? generarMatricula() : null;
        int numProductos = 1 + aleatorio.nextInt(MAX_PRODUCTOS_LISTA); // 1..10
        this.justificante = new Justificante(id,
                supermercado.getCatalogo().getListaAleatoria(numProductos));

        this.controlPausa = supermercado.getControlPausa();
        this.log = supermercado.getLog();
        this.tienda = supermercado.getTienda();
        this.aparcamiento = supermercado.getAparcamiento();
        this.cajaFuerte = supermercado.getCajaFuerte();
        this.cajaTradicional = supermercado.getCajaTradicional();
        this.cajaAutopago = supermercado.getCajaAutopago();
    }

    @Override
    public void run() {
        setName(id); // nombre del hilo = su identificador
        try {
            registrarCreacion();
            if (!desplazarse()) {
                return;
            }
            if (enCoche && !aparcar()) {
                return;
            }
            if (!entrarEnTienda()) {
                return; // tienda cerrada: pasa directo al finally
            }
            if (!comprar()) {
                return;
            }
            pasarPorCaja();
        } finally {
            salirDelSupermercado();
        }
    }

    // ------------------------------------------------------------------
    // Fases del ciclo de vida
    // ------------------------------------------------------------------

    private void registrarCreacion() {
        log.registrar("Cliente " + id + " es creado. Lista de la compra: "
                + listar(justificante.getDeseados()) + ". "
                + (enCoche ? "Viene en coche (matrícula " + matricula + ")" : "Viene andando"));
    }

    /** A pie 2-3 s; en coche la mitad (1-1,5 s). */
    private boolean desplazarse() {
        controlPausa.mirar();
        long ms = enCoche ? 1000 + aleatorio.nextInt(501) : 2000 + aleatorio.nextInt(1001);
        return dormir(ms);
    }

    /** Recoge el tique (espera máx. 1 s por plaza) y aparca (1-1,5 s). */
    private boolean aparcar() {
        controlPausa.mirar();
        log.registrar("Cliente " + id + " espera a recoger el tique del aparcamiento");
        tique = aparcamiento.entrar(id, matricula);
        if (tique == null) {
            log.registrar("Cliente " + id + " no encuentra plaza y abandona el aparcamiento");
            return false;
        }
        log.registrar("Cliente " + id + " recoge el tique del aparcamiento (matrícula "
                + tique.getMatricula() + ", hora de llegada: "
                + (tique.getTiempoEntradaMs() / 1000) + " s)");
        if (!dormir(1000 + aleatorio.nextInt(501))) {
            return false;
        }
        log.registrar("Cliente " + id + " termina de aparcar");
        return true;
    }

    /** Espera su turno de aforo (FIFO). Devuelve false si la tienda ya está cerrada. */
    private boolean entrarEnTienda() {
        controlPausa.mirar();
        log.registrar("Cliente " + id + " espera para entrar en la tienda");
        if (!tienda.entrar(id)) {
            log.registrar("Cliente " + id + " no puede entrar en la tienda (cerrada) y se va");
            return false;
        }
        dentroTienda = true;
        log.registrar("Cliente " + id + " entra en la tienda");
        return true;
    }

    /**
     * Hasta 2 vueltas por la lista: la 2ª solo con los productos pendientes.
     * Cada intento: búsqueda de 250-500 ms y cogerProducto (espera máx. 3 s).
     *
     * @return false si fue interrumpido
     */
    private boolean comprar() {
        List<Producto> pendientes = new ArrayList<Producto>(justificante.getDeseados());
        for (int vuelta = 1; vuelta <= MAX_VUELTAS && !pendientes.isEmpty(); vuelta++) {
            List<Producto> sinConseguir = new ArrayList<Producto>();
            for (Producto producto : pendientes) {
                controlPausa.mirar();
                if (!dormir(250 + aleatorio.nextInt(251))) {
                    return false;
                }
                Estanteria estanteria = supermercado.getEstanteria(indiceEstanteria(producto));
                if (estanteria.cogerProducto(producto.getId())) {
                    justificante.anadirConseguido(producto);
                    log.registrar("Cliente " + id + " ha cogido " + producto.getId());
                } else {
                    sinConseguir.add(producto);
                    log.registrar("Cliente " + id + " no ha conseguido " + producto.getId()
                            + " tras esperar 3 s (vuelta " + vuelta + ")");
                }
            }
            pendientes = sinConseguir;
        }
        return true;
    }

    /** Sin productos no hay nada que pagar; si no, elige caja al azar (una sola vez). */
    private void pasarPorCaja() {
        long importe = justificante.getCompraCentimos();
        if (importe == 0L) {
            log.registrar("Cliente " + id
                    + " no ha conseguido ningún producto y se va sin pasar por caja");
            return;
        }
        controlPausa.mirar();
        boolean tradicional = aleatorio.nextBoolean();
        boolean pagado = tradicional
                ? cajaTradicional.pagar(id, importe)
                : cajaAutopago.pagar(id, importe);
        if (!pagado) {
            log.registrar("Cliente " + id + " abandona la caja sin pagar");
        }
    }

    /**
     * Ruta única de salida (se ejecuta SIEMPRE, desde el finally de run()):
     * 1) libera el aforo (idempotente); 2) si hay tique: vuelve al coche, pasa
     * por la barrera, cobra la tarifa y anota el cargo; 3) escribe el resumen.
     */
    private void salirDelSupermercado() {
        try {
            tienda.salir(id); // idempotente: solo libera si estaba dentro
            if (dentroTienda) {
                dentroTienda = false;
                log.registrar("Cliente " + id + " sale de la tienda");
            }
            if (tique != null) {
                if (!Thread.currentThread().isInterrupted()) {
                    controlPausa.mirar();
                    dormir(1000 + aleatorio.nextInt(501)); // ir a por el coche
                }
                long tarifa = aparcamiento.salir(tique);
                cajaFuerte.ingresarAparcamiento(tarifa);
                justificante.setAparcamientoCentimos(tarifa);
                tique = null; // el tique no puede usarse dos veces
                log.registrar("Cliente " + id + " sale del aparcamiento. "
                        + (tarifa > 0L
                                ? "Tarifa: " + Dinero.formatear(tarifa)
                                : "Sin cargo por aparcamiento"));
            }
        } finally {
            log.registrar(justificante.formatearResumen());
        }
    }

    // ------------------------------------------------------------------
    // Auxiliares
    // ------------------------------------------------------------------

    /** Duerme restaurando el flag si se interrumpe. @return false si fue interrumpido */
    private boolean dormir(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Formato NNNN-LLL: 4 dígitos y 3 consonantes (p. ej. 1234-BCD). */
    private String generarMatricula() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(aleatorio.nextInt(10));
        }
        sb.append('-');
        for (int i = 0; i < 3; i++) {
            sb.append(CONSONANTES.charAt(aleatorio.nextInt(CONSONANTES.length())));
        }
        return sb.toString();
    }

    /** Estantería (0..9) donde está el producto: P-XX -> XX / 10. */
    private static int indiceEstanteria(Producto producto) {
        return Integer.parseInt(producto.getId().substring(2)) / Estanteria.PRODUCTOS_POR_ESTANTERIA;
    }

    private static String listar(List<Producto> productos) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < productos.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(productos.get(i).getId());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return id;
    }
}
