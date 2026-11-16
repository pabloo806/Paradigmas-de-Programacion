package javadona;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Registro de eventos del supermercado (recurso compartido protegido).
 *
 * Escribe en "evolucion_supermercado.txt" una línea por evento con el formato:
 *     yyyy-MM-dd HH:mm:ss.SSS | evento
 *
 * Exclusión mutua: un único ReentrantLock protege el PrintWriter y, sobre todo,
 * la toma de la marca de tiempo. Como la marca se toma DENTRO de la sección
 * crítica, el orden de las líneas en el fichero coincide siempre con el orden
 * cronológico de las marcas. Si se tomara fuera, un hilo podría obtener una
 * marca anterior y escribir después que otro.
 *
 * Jerarquía de recursos: el Log es el ÚLTIMO eslabón; nunca se llama a ningún
 * otro recurso desde dentro de su sección crítica, por lo que no puede
 * participar en un interbloqueo.
 */
public class Log {

    private static final String NOMBRE_FICHERO = "evolucion_supermercado.txt";
    private static final DateTimeFormatter FORMATO_MARCA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final Lock cerrojo = new ReentrantLock();
    private final PrintWriter escritor;
    private boolean cerrado = false;

    /** Abre (y sobrescribe) el fichero de log. */
    public Log() throws IOException {
        this.escritor = new PrintWriter(new BufferedWriter(new FileWriter(NOMBRE_FICHERO, false)));
    }

    /** Registra un evento con su marca de tiempo. Seguro para uso concurrente. */
    public void registrar(String evento) {
        cerrojo.lock();
        try {
            if (cerrado) {
                return;
            }
            // SECCIÓN CRÍTICA: marca de tiempo + escritura son una operación atómica
            String marca = LocalDateTime.now().format(FORMATO_MARCA);
            escritor.println(marca + " | " + evento);
            escritor.flush(); // el fichero siempre refleja lo ocurrido, aunque el programa se corte
        } finally {
            cerrojo.unlock();
        }
    }

    /** Cierra el fichero. Tras cerrar, registrar() ignora nuevos eventos. */
    public void cerrar() {
        cerrojo.lock();
        try {
            if (!cerrado) {
                cerrado = true;
                escritor.close();
            }
        } finally {
            cerrojo.unlock();
        }
    }

    public static String getNombreFichero() {
        return NOMBRE_FICHERO;
    }
}
