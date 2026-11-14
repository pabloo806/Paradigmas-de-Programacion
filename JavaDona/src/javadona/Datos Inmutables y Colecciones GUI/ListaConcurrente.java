package javadona;

import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;

/**
 * Lista compartida (monitor de colección) que refleja su contenido en un
 * componente de texto Swing (JTextField o JTextArea). Evolución de ListaThreads.
 *
 * Exclusión mutua: todos los métodos que tocan la lista son synchronized, por lo
 * que dos hilos nunca modifican el ArrayList a la vez (que no es thread-safe).
 *
 * Regla de oro Swing: los hilos de simulación NUNCA tocan el componente. Dentro
 * de la sección crítica solo se construye una INSTANTÁNEA del texto (un String
 * inmutable) y se encarga su pintado al EDT con SwingUtilities.invokeLater.
 * Así el EDT jamás lee la lista ni necesita su cerrojo.
 *
 * Orden de los refrescos: invokeLater se llama DENTRO de la sección crítica
 * (es no bloqueante, no hay riesgo de interbloqueo). La cola del EDT es FIFO,
 * así que los textos se pintan en el mismo orden en que se modificó la lista y
 * el último pintado siempre es el estado más reciente.
 *
 * Los elementos se muestran con su toString(), separados por espacios, en orden
 * de inserción (importante para las colas de espera).
 */
public class ListaConcurrente<T> {

    private final List<T> elementos = new ArrayList<T>();
    private final JTextComponent componente; // puede ser null (sin vista)

    /** @param componente donde se muestra la lista; null si no se desea vista */
    public ListaConcurrente(JTextComponent componente) {
        this.componente = componente;
    }

    /** Añade el elemento al final de la lista y refresca la vista. */
    public synchronized void meter(T elemento) {
        elementos.add(elemento);
        refrescarVista();
    }

    /**
     * Quita la primera aparición del elemento y refresca la vista.
     *
     * @return true si el elemento estaba en la lista
     */
    public synchronized boolean sacar(T elemento) {
        boolean eliminado = elementos.remove(elemento);
        if (eliminado) {
            refrescarVista();
        }
        return eliminado;
    }

    public synchronized int getTamano() {
        return elementos.size();
    }

    public synchronized boolean contiene(T elemento) {
        return elementos.contains(elemento);
    }

    /** @return copia de los elementos (instantánea; el llamante puede recorrerla sin cerrojo) */
    public synchronized List<T> getElementos() {
        return new ArrayList<T>(elementos);
    }

    /** Debe llamarse con el cerrojo (this) en poder del hilo. */
    private void refrescarVista() {
        if (componente == null) {
            return;
        }
        StringBuilder texto = new StringBuilder();
        for (T elemento : elementos) {
            texto.append(elemento).append(' ');
        }
        final String instantanea = texto.toString();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                componente.setText(instantanea); // se ejecuta en el EDT
            }
        });
    }
}
