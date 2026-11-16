package javadona;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javadona.remoto.PublicadorRemoto;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Programa principal: arranque, generación escalonada de clientes y cierre
 * ordenado de la simulación JavaDona (Parte 1: concurrencia) con el servicio de
 * acceso remoto RMI de la Parte 2.
 *
 * Argumentos opcionales: args[0] = puerto del registro RMI (por defecto 1099;
 * el objeto remoto usa el siguiente), args[1] = IP o nombre público del servidor
 * (java.rmi.server.hostname), necesario cuando el cliente está en otro equipo.
 *
 * El hilo main es el ÚNICO que crea y arranca Clientes y Encargados, por lo que
 * las listas de hilos que mantiene no necesitan sincronización.
 */
public class ProgramaPrincipal {

    private static final int NUM_ENCARGADOS = 10;
    private static final int INTERVALO_MIN_MS = 500;
    private static final int INTERVALO_VARIABLE_MS = 401; // 500 .. 900 ms
    private static final long PERIODO_GRACIA_MS = 2000L;  // 4 periodos de consulta del cliente remoto

    public static void main(String[] args) {
        final int puertoRegistro = leerPuerto(args);
        final String hostPublico = args.length > 1 ? args[1] : null;
        final VentanaPrincipal[] ventana = new VentanaPrincipal[1];
        final Supermercado[] supermercado = new Supermercado[1];

        // 1) Ventana y Supermercado se crean en el EDT; main espera el resultado
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    ventana[0] = new VentanaPrincipal();
                    try {
                        supermercado[0] = new Supermercado(ventana[0]);
                        ventana[0].conectar(supermercado[0]);
                        ventana[0].setVisible(true);
                    } catch (IOException e) {
                        JOptionPane.showMessageDialog(null,
                                "No se puede crear el fichero de log: " + e.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                        ventana[0].dispose();
                    }
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (InvocationTargetException e) {
            e.getCause().printStackTrace();
            System.exit(1);
        }
        if (supermercado[0] == null) {
            System.exit(1); // sin log no hay simulación
        }
        simular(supermercado[0], ventana[0], puertoRegistro, hostPublico);
    }

    private static void simular(Supermercado supermercado, VentanaPrincipal ventana,
                                int puertoRegistro, String hostPublico) {
        GestorHorario gestorHorario = supermercado.getGestorHorario();
        ControlPausa controlPausa = supermercado.getControlPausa();
        Log log = supermercado.getLog();
        Random aleatorio = new Random();
        List<Encargado> encargados = new ArrayList<Encargado>();
        List<Cliente> clientes = new ArrayList<Cliente>();
        boolean interrumpido = false;

        // Servicio remoto: se publica ANTES de iniciar la simulación, para que un
        // cliente pueda conectar ya durante la pre-apertura. Un fallo no la aborta.
        PublicadorRemoto publicador = new PublicadorRemoto(supermercado, puertoRegistro, hostPublico);
        publicarServicioRemoto(publicador, log);

        try {
            // 2) Reloj lógico y lanzamiento concurrente de los 10 encargados
            gestorHorario.iniciar();
            log.registrar("Comienza la simulación de JavaDona");
            for (int i = 0; i < NUM_ENCARGADOS; i++) {
                Encargado encargado = new Encargado(i, supermercado);
                encargados.add(encargado);
                encargado.start(); // no bloquea: los clientes pueden crearse a la vez
            }

            // 3) Espera a la apertura (respeta las pausas)
            gestorHorario.esperarApertura();
            log.registrar("La tienda abre sus puertas");

            // 4) Generación escalonada de clientes mientras la tienda no cierre
            int numero = 0;
            while (!gestorHorario.estaCerrada()) {
                controlPausa.mirar();
                if (gestorHorario.estaCerrada()) {
                    break; // cerró durante la pausa: no se crean más clientes
                }
                numero++;
                Cliente cliente = new Cliente(numero, supermercado);
                clientes.add(cliente);
                cliente.start();
                Thread.sleep(INTERVALO_MIN_MS + aleatorio.nextInt(INTERVALO_VARIABLE_MS));
            }
            log.registrar("La tienda cierra: no se admiten más clientes");

            // 5) Cierre ordenado: hasta el último coche debe haber salido y pagado su tarifa
            for (Cliente cliente : clientes) {
                cliente.join();
            }
            for (Encargado encargado : encargados) {
                encargado.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            interrumpido = true;
        } finally {
            // 6) Recaudación final y cierre seguro del log (idempotente)
            supermercado.finalizar(interrumpido);
            ventana.mostrarFinalizada();
            // 7) Fin normal: periodo de gracia para que los clientes remotos lean la foto
            //    final (finalizada == true) y después se desexporta el servicio. En el cierre
            //    manual de la ventana (System.exit) lo hace el shutdown hook del publicador.
            if (!interrumpido) {
                esperarGracia();
            }
            publicador.detener();
        }
    }

    /** Publica el servicio RMI. Un fallo se avisa en el log y en stderr, pero no aborta la simulación. */
    private static void publicarServicioRemoto(PublicadorRemoto publicador, Log log) {
        try {
            publicador.publicar();
            log.registrar("Servicio remoto RMI publicado (registro: puerto " + publicador.getPuertoRegistro()
                    + ", objeto remoto: puerto " + publicador.getPuertoObjeto() + ")");
        } catch (RemoteException | RuntimeException e) {
            String aviso = "Aviso: no se pudo publicar el servicio remoto (" + e
                    + "). La simulación continúa sin acceso remoto";
            log.registrar(aviso);
            System.err.println(aviso);
        }
    }

    private static void esperarGracia() {
        try {
            Thread.sleep(PERIODO_GRACIA_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Puerto del registro: args[0] si es válido (1..65534, porque el objeto usa +1); si no, el de por defecto. */
    private static int leerPuerto(String[] args) {
        if (args.length == 0) {
            return PublicadorRemoto.PUERTO_REGISTRO_POR_DEFECTO;
        }
        try {
            int puerto = Integer.parseInt(args[0].trim());
            if (puerto >= 1 && puerto < 65535) {
                return puerto;
            }
        } catch (NumberFormatException e) {
            // se avisa abajo y se usa el valor por defecto
        }
        System.err.println("Aviso: puerto de registro no válido (" + args[0] + "); se usa "
                + PublicadorRemoto.PUERTO_REGISTRO_POR_DEFECTO);
        return PublicadorRemoto.PUERTO_REGISTRO_POR_DEFECTO;
    }
}
