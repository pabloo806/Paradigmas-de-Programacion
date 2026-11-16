package javadona.remoto;

import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import javadona.Supermercado;

/**
 * Gestiona el ciclo de vida del servicio RMI: registro embebido, exportación
 * del objeto remoto y cierre limpio.
 *
 *  - Registro en 'puertoRegistro' (por defecto 1099) y objeto remoto en
 *    'puertoRegistro + 1' (por defecto 1100): solo dos puertos que abrir.
 *  - Guarda REFERENCIAS FUERTES al registro y al servicio: si no, el recolector
 *    podría descartarlos y el cliente recibiría NoSuchObjectException.
 *  - Si se indica un host público, se asigna a java.rmi.server.hostname ANTES de
 *    exportar, para que el stub no lleve una dirección interna (p. ej. 127.0.1.1).
 *  - detener() desexporta el objeto remoto y el registro. Un objeto remoto
 *    exportado mantiene vivo un hilo no demonio (RMI Reaper) y la JVM no podría
 *    terminar sola. Es idempotente y también se ejecuta en un shutdown hook, así
 *    que cubre el cierre manual de la ventana (System.exit).
 */
public class PublicadorRemoto {

    public static final String NOMBRE_SERVICIO = "ServicioSupermercado";
    public static final int PUERTO_REGISTRO_POR_DEFECTO = 1099;

    private final Supermercado supermercado;
    private final int puertoRegistro;
    private final int puertoObjeto;
    private final String hostPublico;

    // Estado protegido por el monitor de this
    private Registry registro = null;
    private ServicioSupermercadoImpl servicio = null;
    private boolean detenido = false;

    public PublicadorRemoto(Supermercado supermercado, int puertoRegistro, String hostPublico) {
        this.supermercado = supermercado;
        this.puertoRegistro = puertoRegistro;
        this.puertoObjeto = puertoRegistro + 1;
        this.hostPublico = hostPublico;
    }

    /**
     * Crea el registro, exporta el servicio y lo vincula con rebind.
     * Si algo falla, libera lo que se hubiera creado y relanza la excepción.
     */
    public synchronized void publicar() throws RemoteException {
        if (registro != null || detenido) {
            throw new IllegalStateException("El servicio ya está publicado o detenido");
        }
        if (hostPublico != null && !hostPublico.trim().isEmpty()) {
            System.setProperty("java.rmi.server.hostname", hostPublico.trim());
        }
        try {
            registro = LocateRegistry.createRegistry(puertoRegistro);
            servicio = new ServicioSupermercadoImpl(supermercado, puertoObjeto);
            registro.rebind(NOMBRE_SERVICIO, servicio);
        } catch (RemoteException | RuntimeException e) {
            liberar();
            detenido = false; // un intento fallido no cuenta como detención
            throw e;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                detener();
            }
        }, "rmi-detener"));
    }

    /** Desvincula y desexporta. Idempotente: solo la primera llamada hace algo. */
    public synchronized void detener() {
        if (detenido) {
            return;
        }
        detenido = true;
        liberar();
    }

    public int getPuertoRegistro() {
        return puertoRegistro;
    }

    public int getPuertoObjeto() {
        return puertoObjeto;
    }

    /** Libera lo que haya, capturando los errores de cierre. Llamar con el monitor tomado. */
    private void liberar() {
        detenido = true;
        if (registro != null && servicio != null) {
            try {
                registro.unbind(NOMBRE_SERVICIO);
            } catch (RemoteException | NotBoundException e) {
                // ya desvinculado o registro caído: nada que hacer
            }
        }
        if (servicio != null) {
            try {
                UnicastRemoteObject.unexportObject(servicio, true);
            } catch (NoSuchObjectException e) {
                // ya desexportado
            }
            servicio = null;
        }
        if (registro != null) {
            try {
                UnicastRemoteObject.unexportObject(registro, true);
            } catch (NoSuchObjectException e) {
                // ya desexportado
            }
            registro = null;
        }
    }
}
