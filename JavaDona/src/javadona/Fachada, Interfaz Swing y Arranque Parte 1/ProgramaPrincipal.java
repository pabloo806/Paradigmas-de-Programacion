package javadona;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Programa principal: arranque, generación escalonada de clientes y cierre
 * ordenado de la simulación JavaDona (Parte 1: programación concurrente).
 *
 * El hilo main es el ÚNICO que crea y arranca Clientes y Encargados, por lo que
 * las listas de hilos que mantiene no necesitan sincronización.
 */
public class ProgramaPrincipal {

    private static final int NUM_ENCARGADOS = 10;
    private static final int INTERVALO_MIN_MS = 500;
    private static final int INTERVALO_VARIABLE_MS = 401; // 500 .. 900 ms

    public static void main(String[] args) {
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
        simular(supermercado[0], ventana[0]);
    }

    private static void simular(Supermercado supermercado, VentanaPrincipal ventana) {
        GestorHorario gestorHorario = supermercado.getGestorHorario();
        ControlPausa controlPausa = supermercado.getControlPausa();
        Log log = supermercado.getLog();
        Random aleatorio = new Random();
        List<Encargado> encargados = new ArrayList<Encargado>();
        List<Cliente> clientes = new ArrayList<Cliente>();
        boolean interrumpido = false;

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
        }
    }
}
