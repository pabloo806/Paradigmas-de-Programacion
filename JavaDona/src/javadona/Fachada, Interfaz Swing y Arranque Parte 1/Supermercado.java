package javadona;

import java.io.IOException;

/**
 * Supermercado: contenedor pasivo y fachada (patrón Facade) del sistema.
 *
 * Instancia y conecta todos los recursos compartidos y ofrece a los hilos
 * (Cliente, Encargado) y al módulo de acceso remoto (Parte 2) una API única.
 * No arranca ningún hilo ni es un hilo.
 *
 * Los getters de consulta devuelven tipos primitivos y son seguros desde
 * cualquier hilo (cada uno toma, como mucho, una sección crítica corta de
 * un único recurso, sin esperas). Los totales compuestos se leen en varias
 * llamadas independientes: son coherentes para refrescar una pantalla, no una
 * instantánea atómica de todo el sistema.
 */
public class Supermercado {

    private final Log log;
    private final Catalogo catalogo;
    private final GestorHorario gestorHorario;
    private final ControlPausa controlPausa;
    private final CajaFuerte cajaFuerte;
    private final Tienda tienda;
    private final Aparcamiento aparcamiento;
    private final Estanteria[] estanterias = new Estanteria[Estanteria.NUM_ESTANTERIAS];
    private final Almacen almacen;
    private final CajaTradicional cajaTradicional;
    private final CajaAutopago cajaAutopago;
    private final Plantilla plantilla;

    private boolean finalizada = false; // protegida por el monitor de this

    /**
     * Construye el sistema en un orden seguro: cada pieza solo usa otras ya creadas.
     *
     * @param vista componentes Swing donde reflejar las listas; null para ejecutar sin ventana
     * @throws IOException si no se puede crear el fichero de log
     */
    public Supermercado(VistaListas vista) throws IOException {
        this.log = new Log();
        this.catalogo = new Catalogo();
        this.gestorHorario = new GestorHorario();
        this.controlPausa = new ControlPausa();
        this.cajaFuerte = new CajaFuerte();

        // Listas en vivo: cada una refleja su contenido en un componente (o en ninguno)
        ListaConcurrente<String> esperaTienda = new ListaConcurrente<String>(
                vista == null ? null : vista.getEsperaTienda());
        ListaConcurrente<String> dentroTienda = new ListaConcurrente<String>(
                vista == null ? null : vista.getDentroTienda());
        ListaConcurrente<String> colaTradicional = new ListaConcurrente<String>(
                vista == null ? null : vista.getColaTradicional());
        ListaConcurrente<String> atendiendoTradicional = new ListaConcurrente<String>(
                vista == null ? null : vista.getAtendiendoTradicional());
        ListaConcurrente<String> colaAutopago = new ListaConcurrente<String>(
                vista == null ? null : vista.getColaAutopago());
        ListaConcurrente<String> pagandoAutopago = new ListaConcurrente<String>(
                vista == null ? null : vista.getPagandoAutopago());
        ListaConcurrente<String> encargadosReponiendo = new ListaConcurrente<String>(
                vista == null ? null : vista.getEncargadosReponiendo());
        ListaConcurrente<String> encargadosTradicional = new ListaConcurrente<String>(
                vista == null ? null : vista.getEncargadosTradicional());
        ListaConcurrente<String> encargadosAutopago = new ListaConcurrente<String>(
                vista == null ? null : vista.getEncargadosAutopago());

        this.tienda = new Tienda(gestorHorario, esperaTienda, dentroTienda);
        this.aparcamiento = new Aparcamiento(gestorHorario);
        for (int i = 0; i < estanterias.length; i++) {
            estanterias[i] = new Estanteria(i, catalogo);
        }
        this.almacen = new Almacen();
        this.cajaTradicional = new CajaTradicional(cajaFuerte, log, colaTradicional, atendiendoTradicional);
        this.cajaAutopago = new CajaAutopago(cajaFuerte, log, colaAutopago, pagandoAutopago);
        this.plantilla = new Plantilla(encargadosReponiendo, encargadosTradicional, encargadosAutopago);
    }

    // ------------------------------------------------------------------
    // Recursos para Cliente y Encargado
    // ------------------------------------------------------------------

    public Catalogo getCatalogo() {
        return catalogo;
    }

    public GestorHorario getGestorHorario() {
        return gestorHorario;
    }

    public ControlPausa getControlPausa() {
        return controlPausa;
    }

    public Log getLog() {
        return log;
    }

    public Tienda getTienda() {
        return tienda;
    }

    public Aparcamiento getAparcamiento() {
        return aparcamiento;
    }

    public Estanteria getEstanteria(int indice) {
        return estanterias[indice];
    }

    public Almacen getAlmacen() {
        return almacen;
    }

    public CajaTradicional getCajaTradicional() {
        return cajaTradicional;
    }

    public CajaAutopago getCajaAutopago() {
        return cajaAutopago;
    }

    public CajaFuerte getCajaFuerte() {
        return cajaFuerte;
    }

    public Plantilla getPlantilla() {
        return plantilla;
    }

    // ------------------------------------------------------------------
    // Pausa coherente (ControlPausa + GestorHorario) sin anidar cerrojos
    // ------------------------------------------------------------------

    /**
     * Pausa o reanuda toda la simulación. ControlPausa.alternar() es atómico y es
     * la FUENTE DE VERDAD; después se RECONCILIA el reloj lógico con su estado.
     * Si otro actor (p. ej. el cliente remoto) alterna a la vez, el bucle aplica
     * siempre el estado vigente y termina cuando coincide con el de ControlPausa,
     * así que el reloj nunca queda en el estado contrario. Como pausar() y
     * reanudar() del gestor son idempotentes, reaplicar es inocuo. No se retiene
     * ningún cerrojo mientras se llama al otro recurso: no hay anidamiento.
     *
     * @return true si la simulación queda pausada
     */
    public boolean alternarPausa() {
        controlPausa.alternar();
        sincronizarReloj();
        return controlPausa.estaPausado();
    }

    public boolean isPausado() {
        return controlPausa.estaPausado();
    }

    private void sincronizarReloj() {
        boolean estado = controlPausa.estaPausado();
        while (true) {
            if (estado) {
                gestorHorario.pausar();
            } else {
                gestorHorario.reanudar();
            }
            boolean actual = controlPausa.estaPausado();
            if (actual == estado) {
                return; // el reloj coincide con el estado vigente
            }
            estado = actual; // alguien alternó entretanto: aplicar el nuevo estado
        }
    }

    // ------------------------------------------------------------------
    // Consultas en tiempo real (GUI y Parte 2: acceso remoto)
    // ------------------------------------------------------------------

    public int getClientesEsperandoTienda() {
        return tienda.getClientesEsperando();
    }

    public int getClientesDentroTienda() {
        return tienda.getClientesDentro();
    }

    /** Clientes dentro de la tienda que aún no están en ninguna caja (nunca negativo). */
    public int getClientesComprando() {
        int enCajas = cajaTradicional.getClientesEnCaja() + cajaAutopago.getClientesEnCaja();
        return Math.max(0, tienda.getClientesDentro() - enCajas);
    }

    public int getClientesCajaTradicional() {
        return cajaTradicional.getClientesEnCaja();
    }

    public int getClientesCajaAutopago() {
        return cajaAutopago.getClientesEnCaja();
    }

    public int getMaquinasBloqueadas() {
        return cajaAutopago.getMaquinasBloqueadas();
    }

    public int getCochesDentroAparcamiento() {
        return aparcamiento.getCochesDentro();
    }

    public int getCochesEsperandoAparcamiento() {
        return aparcamiento.getCochesEsperando();
    }

    public int getPlazasLibres() {
        return aparcamiento.getPlazasLibres();
    }

    public int getEncargadosEnAlmacen() {
        return almacen.getEmpleadosDentro();
    }

    public int getEncargadosReponiendo() {
        return plantilla.getEncargadosReponiendo();
    }

    public int getEncargadosCajaTradicional() {
        return plantilla.getEncargadosCajaTradicional();
    }

    public int getEncargadosCajaAutopago() {
        return plantilla.getEncargadosCajaAutopago();
    }

    public int getUnidadesEstanteria(int indice) {
        return estanterias[indice].getUnidadesTotales();
    }

    /** Suma de las 10 estanterías (lecturas sucesivas, no una instantánea atómica). */
    public int getUnidadesEstanterias() {
        int total = 0;
        for (int i = 0; i < estanterias.length; i++) {
            total += estanterias[i].getUnidadesTotales();
        }
        return total;
    }

    public int getUnidadesAlmacen() {
        return almacen.getUnidadesTotales();
    }

    public long getRecaudacionCentimos() {
        return cajaFuerte.getTotalCentimos();
    }

    public long getVentasCentimos() {
        return cajaFuerte.getVentasCentimos();
    }

    public long getAparcamientoCentimos() {
        return cajaFuerte.getAparcamientoCentimos();
    }

    public long getTiempoLogicoMs() {
        return gestorHorario.getTiempoTranscurridoMs();
    }

    public GestorHorario.EstadoTienda getEstadoTienda() {
        return gestorHorario.getEstado();
    }

    // ------------------------------------------------------------------
    // Finalización
    // ------------------------------------------------------------------

    public boolean isFinalizada() {
        synchronized (this) {
            return finalizada;
        }
    }

    /**
     * Escribe la línea final de recaudación y cierra el log. IDEMPOTENTE: solo la
     * primera llamada hace algo. El flag se decide en un synchronized mínimo y la
     * escritura del log ocurre FUERA del monitor (no se anidan cerrojos).
     *
     * @param interrumpida true si el usuario cerró la ventana antes de terminar
     */
    public void finalizar(boolean interrumpida) {
        synchronized (this) {
            if (finalizada) {
                return;
            }
            finalizada = true;
        }
        try {
            long ventas = cajaFuerte.getVentasCentimos();
            long parking = cajaFuerte.getAparcamientoCentimos();
            String desglose = Dinero.formatear(ventas + parking) + " (ventas: "
                    + Dinero.formatear(ventas) + ", aparcamiento: " + Dinero.formatear(parking) + ")";
            if (interrumpida) {
                log.registrar("Simulación interrumpida por el usuario. Recaudación parcial: " + desglose);
            } else {
                log.registrar("Recaudación total: " + desglose);
            }
        } finally {
            log.cerrar();
        }
    }
}
