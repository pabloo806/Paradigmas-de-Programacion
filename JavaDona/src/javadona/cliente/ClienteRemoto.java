package javadona.cliente;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import javadona.cliente.VentanaRemota.EstadoConexion;
import javadona.remoto.EstadoSupermercado;
import javadona.remoto.PublicadorRemoto;
import javadona.remoto.ServicioSupermercado;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Cliente remoto de JavaDona (controlador y main).
 *
 * Argumentos opcionales: args[0] = host del servidor (por defecto "localhost"),
 * args[1] = puerto del registro RMI (por defecto 1099, rango 1..65535).
 *
 * NO BLOQUEAR EL EDT: ninguna llamada RMI se hace en el EDT. El Timer (500 ms)
 * solo DISPARA la consulta: si no hay otra en curso (flag que solo toca el EDT),
 * lanza un hilo daemon efímero que localiza el servicio (si hace falta), llama a
 * obtenerEstado() y devuelve el resultado con SwingUtilities.invokeLater. Como no
 * se lanza otra consulta mientras la anterior no vuelve, no se acumulan hilos
 * aunque la red sea lenta; si una lleva más de 2 s, el tick solo cambia la
 * etiqueta a SIN RESPUESTA.
 *
 * RECONEXIÓN AUTOMÁTICA: ante cualquier fallo se descarta la referencia al
 * servicio (servicio = null). En el siguiente tick (500 ms) el nuevo hilo vuelve
 * a hacer lookup en el registro, así que si el servidor arranca después, se
 * reinicia o la red vuelve, el cliente se reconecta solo, sin intervención.
 *
 * Los campos de estado solo se leen y escriben en el EDT; los hilos reciben lo
 * que necesitan como parámetros finales y devuelven sus resultados con invokeLater.
 */
public final class ClienteRemoto {

    private static final int INTERVALO_MS = 500;
    private static final long UMBRAL_SIN_RESPUESTA_NANOS = 2_000_000_000L; // 2000 ms
    private static final String HOST_POR_DEFECTO = "localhost";
    private static final int PUERTO_POR_DEFECTO = 1099;

    private final String host;
    private final int puerto;
    private final VentanaRemota ventana;
    private final Timer temporizador;

    // Estado: SOLO EDT
    private ServicioSupermercado servicio = null;
    private boolean consultaEnCurso = false;
    private long inicioConsultaNanos = 0L;
    private boolean haConectadoAlgunaVez = false;
    private boolean finalizada = false;
    private boolean ultimoEstadoPausado = false;
    private boolean pausaEnCurso = false;
    private EstadoConexion conexion = EstadoConexion.CONECTANDO;

    /** Visible en el paquete para poder probarlo. Debe crearse en el EDT. */
    ClienteRemoto(String host, int puerto) {
        this.host = host;
        this.puerto = puerto;
        this.ventana = new VentanaRemota(host, puerto);
        this.temporizador = new Timer(INTERVALO_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                alTick();
            }
        });
        this.temporizador.setInitialDelay(0); // primera consulta inmediata
        this.ventana.setAccionPausa(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                alPulsarPausa();
            }
        });
        this.ventana.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cerrar();
            }
        });
    }

    public static void main(String[] args) {
        final String host = args.length > 0 && !args[0].trim().isEmpty()
                ? args[0].trim() : HOST_POR_DEFECTO;
        final int puerto = leerPuerto(args);
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new ClienteRemoto(host, puerto).iniciar();
            }
        });
    }

    /** Muestra la ventana y arranca el refresco. Llamar en el EDT. */
    void iniciar() {
        ventana.setVisible(true);
        temporizador.start();
    }

    VentanaRemota getVentana() {
        return ventana;
    }

    // ------------------------------------------------------------------
    // Consulta periódica (EDT dispara, hilo daemon consulta)
    // ------------------------------------------------------------------

    private void alTick() {
        if (finalizada) {
            return;
        }
        if (consultaEnCurso) {
            // No se lanza otra consulta (no se acumulan hilos). Si la actual tarda
            // demasiado y ya hubo conexión, solo se actualiza el estado visual.
            boolean tardaDemasiado = System.nanoTime() - inicioConsultaNanos > UMBRAL_SIN_RESPUESTA_NANOS;
            if (tardaDemasiado && haConectadoAlgunaVez && conexion == EstadoConexion.CONECTADO) {
                conexion = EstadoConexion.SIN_RESPUESTA;
                ventana.mostrarConexion(conexion);
                ventana.mostrarBoton(ultimoEstadoPausado, false);
            }
            return;
        }
        lanzarConsulta();
    }

    private void lanzarConsulta() {
        consultaEnCurso = true;
        inicioConsultaNanos = System.nanoTime();
        final ServicioSupermercado actual = servicio; // el hilo recibe la referencia como parámetro
        Thread hilo = new Thread(new Runnable() {
            @Override
            public void run() {
                ServicioSupermercado usado = actual;
                EstadoSupermercado estado = null;
                Exception error = null;
                try {
                    if (usado == null) {
                        usado = localizar();
                    }
                    estado = usado.obtenerEstado();
                } catch (Exception e) {
                    error = e; // cualquier fallo lo trata el EDT (así el flag siempre se libera)
                }
                final ServicioSupermercado servicioUsado = usado;
                final EstadoSupermercado foto = estado;
                final Exception fallo = error;
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        alRecibirConsulta(servicioUsado, foto, fallo);
                    }
                });
            }
        }, "consulta-rmi");
        hilo.setDaemon(true);
        hilo.start();
    }

    /** Se ejecuta en el HILO de consulta, nunca en el EDT. */
    private ServicioSupermercado localizar() throws Exception {
        Registry registro = LocateRegistry.getRegistry(host, puerto);
        return (ServicioSupermercado) registro.lookup(PublicadorRemoto.NOMBRE_SERVICIO);
    }

    /** Resultado de una consulta, de vuelta en el EDT. */
    private void alRecibirConsulta(ServicioSupermercado usado, EstadoSupermercado foto, Exception fallo) {
        consultaEnCurso = false;
        if (finalizada) {
            return;
        }
        if (fallo != null) {
            // RemoteException, NotBoundException...: se descarta la referencia y se
            // reintentará en el siguiente tick. Nunca conectó -> CONECTANDO; si ya
            // había conectado -> SIN CONEXIÓN (valores atenuados).
            servicio = null;
            conexion = haConectadoAlgunaVez ? EstadoConexion.SIN_CONEXION : EstadoConexion.CONECTANDO;
            ventana.mostrarConexion(conexion);
            ventana.mostrarBoton(ultimoEstadoPausado, false);
            return;
        }
        servicio = usado;
        haConectadoAlgunaVez = true;
        ultimoEstadoPausado = foto.isPausado();
        ventana.mostrarEstado(foto);
        if (foto.isFinalizada()) {
            finalizada = true;
            conexion = EstadoConexion.FINALIZADA;
            ventana.mostrarConexion(conexion);
            ventana.mostrarBoton(ultimoEstadoPausado, false);
            temporizador.stop(); // ya no hay nada que consultar
        } else {
            conexion = EstadoConexion.CONECTADO;
            ventana.mostrarConexion(conexion);
            ventana.mostrarBoton(ultimoEstadoPausado, !pausaEnCurso);
        }
    }

    // ------------------------------------------------------------------
    // Botón Detener / Reanudar
    // ------------------------------------------------------------------

    /** Envía el estado DESEADO (idempotente) en un hilo aparte; la etiqueta la fija el servidor. */
    private void alPulsarPausa() {
        if (servicio == null || pausaEnCurso || finalizada) {
            return;
        }
        final boolean deseado = !ultimoEstadoPausado;
        final ServicioSupermercado destino = servicio;
        pausaEnCurso = true;
        ventana.mostrarBoton(ultimoEstadoPausado, false); // deshabilitado mientras dure la petición
        Thread hilo = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean resultado = deseado;
                Exception error = null;
                try {
                    resultado = destino.establecerPausa(deseado);
                } catch (Exception e) {
                    error = e;
                }
                final boolean pausado = resultado;
                final Exception fallo = error;
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        alRecibirRespuestaPausa(pausado, fallo);
                    }
                });
            }
        }, "pausa-rmi");
        hilo.setDaemon(true);
        hilo.start();
    }

    private void alRecibirRespuestaPausa(boolean pausadoEnServidor, Exception fallo) {
        pausaEnCurso = false;
        if (finalizada) {
            return;
        }
        if (fallo != null) {
            servicio = null; // el servidor cayó: se reconectará en el siguiente tick
            conexion = EstadoConexion.SIN_CONEXION;
            ventana.mostrarConexion(conexion);
            ventana.mostrarBoton(ultimoEstadoPausado, false);
            return;
        }
        ultimoEstadoPausado = pausadoEnServidor; // estado resultante que informa el servidor
        ventana.mostrarBoton(pausadoEnServidor, conexion == EstadoConexion.CONECTADO);
    }

    // ------------------------------------------------------------------
    // Cierre y argumentos
    // ------------------------------------------------------------------

    private void cerrar() {
        temporizador.stop();
        ventana.dispose();
        System.exit(0);
    }

    private static int leerPuerto(String[] args) {
        if (args.length > 1) {
            try {
                int puerto = Integer.parseInt(args[1].trim());
                if (puerto >= 1 && puerto <= 65535) {
                    return puerto;
                }
            } catch (NumberFormatException e) {
                // se avisa abajo y se usa el valor por defecto
            }
            System.err.println("Aviso: puerto no válido (" + args[1] + "); se usa " + PUERTO_POR_DEFECTO);
        }
        return PUERTO_POR_DEFECTO;
    }
}
