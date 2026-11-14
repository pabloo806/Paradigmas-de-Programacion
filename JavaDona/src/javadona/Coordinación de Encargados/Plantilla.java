package javadona;

import java.util.HashMap;
import java.util.Map;

/**
 * Plantilla de encargados (monitor, NODO HOJA): reparte los 10 encargados entre
 * reposición, cajas tradicionales y cajas de autopago de forma atómica.
 *
 * Exclusión mutua: la decisión (mapa de asignaciones, contadores y latch) se
 * toma dentro de bloques synchronized. La comprobación "¿hay déficit?" y el
 * cambio de rol ocurren en la MISMA sección crítica (check-then-act atómico):
 * dos encargados que preguntan a la vez nunca cubren el mismo déficit.
 *
 * NODO HOJA: dentro de su monitor no se llama a Tienda, Almacen, cajas, Log ni
 * ListaConcurrente. Los datos de colas y almacén llegan como argumentos
 * primitivos. Las ListaConcurrente de la GUI se actualizan SIEMPRE tras salir
 * del monitor, por lo que no hay anidamiento de cerrojos. Como cada encargado
 * es un único hilo y solo toca su propio id, el orden de sus actualizaciones
 * se conserva; lo peor posible es que durante microsegundos un id no figure en
 * ninguna lista (efecto solo visual). Los contadores, que son los que usan la
 * lógica y RMI, son siempre exactos.
 *
 * GARANTÍA DEL MÍNIMO DE 2 POR ZONA SIN BLOQUEOS: un encargado solo abandona su
 * zona Z si ve contador[Z] > objetivo[Z]. Mientras haya stock en el almacén,
 * objetivo[Z] >= 2 para toda zona, así que tras salir quedan
 * contador[Z] - 1 >= objetivo[Z] >= 2. Si otro encargado pregunta justo después,
 * ve contador[Z] == objetivo[Z] y se queda. Nadie espera a nadie: la regla es
 * un invariante local de una sección crítica corta, sin wait ni cerrojos
 * adicionales. Con el almacén vacío el mínimo deja de regir (objetivo 0/7/3).
 */
public class Plantilla {

    public static final int TOTAL_ENCARGADOS = 10;
    private static final int OBJETIVO_TRADICIONAL_ALMACEN_VACIO = 7;
    private static final int OBJETIVO_AUTOPAGO_ALMACEN_VACIO = 3;

    /**
     * Destinos candidatos por orden de prioridad, indexados por el ordinal de la
     * zona de origen (REPOSICION, CAJA_TRADICIONAL, CAJA_AUTOPAGO).
     */
    private static final Rol[][] PRIORIDAD_DESTINO = {
        {Rol.CAJA_TRADICIONAL, Rol.CAJA_AUTOPAGO}, // desde REPOSICION
        {Rol.REPOSICION, Rol.CAJA_AUTOPAGO},       // desde CAJA_TRADICIONAL
        {Rol.REPOSICION, Rol.CAJA_TRADICIONAL}     // desde CAJA_AUTOPAGO
    };

    private final ListaConcurrente<String> listaReposicion;
    private final ListaConcurrente<String> listaCajaTradicional;
    private final ListaConcurrente<String> listaCajaAutopago;

    // Estado compartido, protegido por el monitor de this
    private final Map<String, Rol> asignaciones = new HashMap<String, Rol>();
    private int enReposicion = 0;
    private int enCajaTradicional = 0;
    private int enCajaAutopago = 0;
    private boolean almacenVacio = false; // latch: una vez true, no vuelve a false

    public Plantilla(ListaConcurrente<String> listaReposicion,
                     ListaConcurrente<String> listaCajaTradicional,
                     ListaConcurrente<String> listaCajaAutopago) {
        this.listaReposicion = listaReposicion;
        this.listaCajaTradicional = listaCajaTradicional;
        this.listaCajaAutopago = listaCajaAutopago;
    }

    /**
     * Registra al encargado con rol REPOSICION (reparto inicial 10/0/0 en
     * pre-apertura, cuando todos completan las estanterías).
     *
     * @throws IllegalArgumentException si el id ya estaba incorporado
     */
    public void incorporar(String idEncargado) {
        synchronized (this) {
            if (asignaciones.containsKey(idEncargado)) {
                throw new IllegalArgumentException("Encargado ya incorporado: " + idEncargado);
            }
            asignaciones.put(idEncargado, Rol.REPOSICION);
            enReposicion++;
        }
        listaReposicion.meter(idEncargado); // fuera del monitor
    }

    /**
     * El encargado pide reevaluar su rol. Como máximo hay UN cambio por llamada.
     * Los parámetros son primitivos para no llamar a otros monitores desde aquí.
     *
     * @param clientesColaTradicional clientes en la cola de la caja tradicional
     * @param clientesColaAutopago    clientes en la cola de autopago (sin contar los que pagan)
     * @param almacenEstaVacio        lectura actual de Almacen.estaVacio()
     * @return el rol que debe desempeñar el encargado a partir de ahora
     * @throws IllegalArgumentException si el encargado no está incorporado
     */
    public Rol reevaluar(String idEncargado, int clientesColaTradicional,
                         int clientesColaAutopago, boolean almacenEstaVacio) {
        Rol anterior;
        Rol nuevo;
        synchronized (this) {
            anterior = asignaciones.get(idEncargado);
            if (anterior == null) {
                throw new IllegalArgumentException("Encargado desconocido: " + idEncargado);
            }
            almacenVacio |= almacenEstaVacio;
            int[] objetivos = calcularObjetivos(clientesColaTradicional, clientesColaAutopago);

            nuevo = anterior;
            // Solo se mueve quien ve EXCESO en su zona (ver cabecera: mínimo de 2)
            if (contadorDe(anterior) > objetivos[anterior.ordinal()]) {
                Rol destino = buscarDestino(anterior, objetivos);
                if (destino != null) {
                    asignaciones.put(idEncargado, destino);
                    ajustarContador(anterior, -1);
                    ajustarContador(destino, +1);
                    nuevo = destino;
                }
            }
        }
        // Fuera del monitor: actualizar las listas de la GUI
        if (nuevo != anterior) {
            listaDe(anterior).sacar(idEncargado);
            listaDe(nuevo).meter(idEncargado);
        }
        return nuevo;
    }

    /** Da de baja al encargado al terminar su jornada. Es idempotente. */
    public void darDeBaja(String idEncargado) {
        Rol actual;
        synchronized (this) {
            actual = asignaciones.remove(idEncargado);
            if (actual == null) {
                return;
            }
            ajustarContador(actual, -1);
        }
        listaDe(actual).sacar(idEncargado); // fuera del monitor
    }

    // ------------------------------------------------------------------
    // Getters para GUI / RMI (contadores exactos)
    // ------------------------------------------------------------------

    /** Encargados asignados a reposición (incluye los que van o vienen del almacén). */
    public synchronized int getEncargadosReponiendo() {
        return enReposicion;
    }

    public synchronized int getEncargadosCajaTradicional() {
        return enCajaTradicional;
    }

    public synchronized int getEncargadosCajaAutopago() {
        return enCajaAutopago;
    }

    // ------------------------------------------------------------------
    // Auxiliares privados. Los que leen el estado se llaman SIEMPRE con el
    // monitor de this ya tomado.
    // ------------------------------------------------------------------

    /** Objetivos por zona, indexados por Rol.ordinal(). */
    private int[] calcularObjetivos(int clientesTradicional, int clientesAutopago) {
        int[] objetivos = new int[Rol.values().length];
        if (almacenVacio) {
            objetivos[Rol.REPOSICION.ordinal()] = 0;
            objetivos[Rol.CAJA_TRADICIONAL.ordinal()] = OBJETIVO_TRADICIONAL_ALMACEN_VACIO;
            objetivos[Rol.CAJA_AUTOPAGO.ordinal()] = OBJETIVO_AUTOPAGO_ALMACEN_VACIO;
        } else {
            int tradicional = objetivoTradicional(clientesTradicional);
            int autopago = objetivoAutopago(clientesAutopago);
            objetivos[Rol.CAJA_TRADICIONAL.ordinal()] = tradicional;
            objetivos[Rol.CAJA_AUTOPAGO.ordinal()] = autopago;
            objetivos[Rol.REPOSICION.ordinal()] = TOTAL_ENCARGADOS - tradicional - autopago;
        }
        return objetivos;
    }

    /** Tabla 2 con rangos cerrados: <5 -> 2; 5..10 -> 3; 11..15 -> 4; >15 -> 5. */
    private static int objetivoTradicional(int clientes) {
        if (clientes < 5) {
            return 2;
        }
        if (clientes <= 10) {
            return 3;
        }
        if (clientes <= 15) {
            return 4;
        }
        return 5;
    }

    /** Autopago: 10 o menos clientes en cola -> 2; más de 10 -> 3. */
    private static int objetivoAutopago(int clientes) {
        return clientes <= 10 ? 2 : 3;
    }

    /** Primera zona con déficit según la prioridad del origen, o null si no hay ninguna. */
    private Rol buscarDestino(Rol origen, int[] objetivos) {
        Rol[] candidatos = PRIORIDAD_DESTINO[origen.ordinal()];
        for (int i = 0; i < candidatos.length; i++) {
            if (contadorDe(candidatos[i]) < objetivos[candidatos[i].ordinal()]) {
                return candidatos[i];
            }
        }
        return null;
    }

    private int contadorDe(Rol rol) {
        switch (rol) {
            case REPOSICION:
                return enReposicion;
            case CAJA_TRADICIONAL:
                return enCajaTradicional;
            default:
                return enCajaAutopago;
        }
    }

    private void ajustarContador(Rol rol, int delta) {
        switch (rol) {
            case REPOSICION:
                enReposicion += delta;
                break;
            case CAJA_TRADICIONAL:
                enCajaTradicional += delta;
                break;
            default:
                enCajaAutopago += delta;
                break;
        }
    }

    /** Lista de GUI de una zona. Los campos son final: no requiere el monitor. */
    private ListaConcurrente<String> listaDe(Rol rol) {
        switch (rol) {
            case REPOSICION:
                return listaReposicion;
            case CAJA_TRADICIONAL:
                return listaCajaTradicional;
            default:
                return listaCajaAutopago;
        }
    }
}
