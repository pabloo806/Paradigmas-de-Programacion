package javadona;

import java.util.Random;

/**
 * Encargado del supermercado (hilo). Ciclo de vida:
 *   incorporarse a Plantilla -> pre-apertura (completa estanterías) ->
 *   bucle principal (reevaluar rol y ejecutar su tarea) -> darse de baja.
 *
 * controlPausa.mirar() se llama SOLO al inicio de cada iteración de los bucles,
 * momento en que el hilo no retiene ninguna reserva de estantería ni permiso de
 * almacén. Todo recurso adquirido tiene su liberación en un finally.
 * 'siguienteEstanteria' es estado propio del hilo (no necesita sincronización).
 */
public class Encargado extends Thread {

    private static final long SONDEO_MS = 200L;

    private final String id;
    private final Supermercado supermercado;
    private final Random aleatorio = new Random();

    private final ControlPausa controlPausa;
    private final Log log;
    private final GestorHorario gestorHorario;
    private final Tienda tienda;
    private final Almacen almacen;
    private final Plantilla plantilla;
    private final CajaTradicional cajaTradicional;
    private final CajaAutopago cajaAutopago;

    // Estantería por la que empieza el próximo ciclo (aleatoria la primera vez)
    private int siguienteEstanteria;

    public Encargado(int numero, Supermercado supermercado) {
        this.id = "E-" + numero;
        this.supermercado = supermercado;
        this.controlPausa = supermercado.getControlPausa();
        this.log = supermercado.getLog();
        this.gestorHorario = supermercado.getGestorHorario();
        this.tienda = supermercado.getTienda();
        this.almacen = supermercado.getAlmacen();
        this.plantilla = supermercado.getPlantilla();
        this.cajaTradicional = supermercado.getCajaTradicional();
        this.cajaAutopago = supermercado.getCajaAutopago();
        this.siguienteEstanteria = aleatorio.nextInt(Estanteria.NUM_ESTANTERIAS);
    }

    @Override
    public void run() {
        setName(id); // nombre del hilo = su identificador
        plantilla.incorporar(id); // rol inicial: REPOSICION (reparto 10/0/0)
        try {
            log.registrar("Encargado " + id + " empieza su jornada (" + Rol.REPOSICION + ")");

            // Pre-apertura: todos completan estanterías; NO se llama a reevaluar()
            while (!isInterrupted()
                    && gestorHorario.getEstado() == GestorHorario.EstadoTienda.PRE_APERTURA) {
                controlPausa.mirar();
                if (!cicloReposicion()) {
                    dormir(SONDEO_MS); // sondeo: sin espera activa
                }
            }

            // Bucle principal: hasta que la tienda esté cerrada y vacía
            Rol rol = Rol.REPOSICION;
            while (!isInterrupted() && !tienda.haTerminadoJornada()) {
                controlPausa.mirar();
                rol = reevaluarRol(rol);
                switch (rol) {
                    case REPOSICION:
                        if (!cicloReposicion()) {
                            dormir(SONDEO_MS);
                        }
                        break;
                    case CAJA_TRADICIONAL:
                        cajaTradicional.atenderSiguiente(id, CajaTradicional.PLAZO_REEVALUACION_MS);
                        break;
                    case CAJA_AUTOPAGO:
                        cajaAutopago.desbloquearMaquina(id, CajaAutopago.PLAZO_REEVALUACION_MS);
                        break;
                    default:
                        break;
                }
            }
        } finally {
            plantilla.darDeBaja(id);
            log.registrar("Encargado " + id + " termina su jornada");
        }
    }

    /**
     * Lee colas y almacén FUERA de cualquier cerrojo, consulta a Plantilla y
     * registra el cambio de rol (Plantilla no escribe en el log).
     */
    private Rol reevaluarRol(Rol actual) {
        int colaTradicional = cajaTradicional.getClientesEnCola();
        int colaAutopago = cajaAutopago.getClientesEnCola();
        boolean almacenVacio = almacen.estaVacio();
        Rol nuevo = plantilla.reevaluar(id, colaTradicional, colaAutopago, almacenVacio);
        if (nuevo != actual) {
            log.registrar("Encargado " + id + " cambia de " + actual + " a " + nuevo);
        }
        return nuevo;
    }

    /**
     * Un ciclo completo de reposición. NO llama a controlPausa.mirar().
     *
     * @return false si no encontró ninguna estantería que reponer (o fue
     *         interrumpido antes de empezar); true si repuso una
     */
    private boolean cicloReposicion() {
        // 1) Buscar desde 'siguienteEstanteria', en círculo, y reservar la primera útil
        int indice = -1;
        for (int k = 0; k < Estanteria.NUM_ESTANTERIAS; k++) {
            int candidata = (siguienteEstanteria + k) % Estanteria.NUM_ESTANTERIAS;
            if (almacen.getCarro(candidata).estaVacio()) {
                continue; // viaje inútil: no hay nada que traer
            }
            if (supermercado.getEstanteria(candidata).reservarParaReponer()) {
                indice = candidata;
                break;
            }
        }
        if (indice < 0) {
            return false; // vuelta completa sin trabajo
        }

        Estanteria estanteria = supermercado.getEstanteria(indice);
        Carro carro = almacen.getCarro(indice);
        boolean reservada = true;
        try {
            // 2-4) Coger el carro en el almacén (aforo de 3)
            if (!almacen.entrar()) {
                return false; // interrumpido: el finally libera la reserva
            }
            try {
                if (!dormir(1000 + aleatorio.nextInt(1001))) { // 1-2 s
                    return false;
                }
            } finally {
                almacen.salir();
            }

            // 5) Ir a la estantería
            if (!dormir(500)) {
                return false;
            }
            // 6) Reponer (1-2 s) y volcar el carro en la estantería
            if (!dormir(1000 + aleatorio.nextInt(1001))) {
                return false;
            }
            String desglose = estanteria.reponer(carro);
            reservada = false; // reponer() ya liberó la reserva
            siguienteEstanteria = (indice + 1) % Estanteria.NUM_ESTANTERIAS;
            log.registrar("Encargado " + id + " ha repuesto los productos de la Estantería "
                    + estanteria.getNumero()
                    + ". Desglose de cantidades en estantería: " + desglose);

            // 7-8) Volver al almacén y dejar el carro
            if (!dormir(500)) {
                return true;
            }
            if (almacen.entrar()) {
                try {
                    dormir(1000 + aleatorio.nextInt(1001)); // 1-2 s
                } finally {
                    almacen.salir();
                }
            }
            return true;
        } finally {
            if (reservada) {
                estanteria.liberarReserva();
            }
        }
    }

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

    @Override
    public String toString() {
        return id;
    }
}
