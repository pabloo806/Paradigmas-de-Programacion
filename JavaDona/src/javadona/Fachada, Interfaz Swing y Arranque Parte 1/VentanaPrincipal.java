package javadona;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.text.JTextComponent;

/**
 * Ventana principal de la simulación (Swing escrito a mano, sin .form).
 *
 * Regla de oro Swing: TODO el acceso a los componentes ocurre en el EDT.
 *  - Las 9 listas en vivo las actualiza ListaConcurrente con invokeLater.
 *  - Los contadores y el reloj se refrescan con un javax.swing.Timer (se ejecuta
 *    en el EDT) cada 250 ms, llamando a los getters de Supermercado. Esos
 *    getters solo toman secciones críticas cortas, sin esperas: el EDT nunca se
 *    bloquea esperando a un hilo de simulación.
 *  - El botón de pausa llama a Supermercado.alternarPausa(), que no bloquea.
 * Los campos 'supermercado' y 'finalizada' solo se leen y escriben en el EDT.
 */
public final class VentanaPrincipal extends JFrame implements VistaListas {

    private static final long serialVersionUID = 1L;
    private static final int REFRESCO_MS = 250;

    private final Timer temporizador;
    private transient Supermercado supermercado; // solo EDT (no se serializa)
    private boolean finalizada = false;  // solo EDT

    // Cabecera
    private final JLabel etiquetaEstado = new JLabel("Estado: -");
    private final JLabel etiquetaTiempo = new JLabel("Tiempo: 00:00");
    private final JLabel etiquetaRecaudacion = new JLabel("Recaudación: 0,00 €");
    private final JButton botonPausa = new JButton("Pausar");

    // Aparcamiento
    private final JLabel etiquetaPlazas = new JLabel("Plazas libres: -");
    private final JLabel etiquetaCochesDentro = new JLabel("Coches dentro: -");
    private final JLabel etiquetaCochesEsperando = new JLabel("Coches esperando: -");

    // Tienda
    private final JLabel etiquetaAforo = new JLabel("Dentro: -");
    private final JLabel etiquetaComprando = new JLabel("Comprando: -");
    private final JTextArea areaEsperaTienda = crearArea();
    private final JTextArea areaDentroTienda = crearArea();

    // Cajas
    private final JLabel etiquetaCajaTradicional = new JLabel("Caja tradicional: -");
    private final JLabel etiquetaCajaAutopago = new JLabel("Caja autopago: -");
    private final JLabel etiquetaBloqueadas = new JLabel("Máquinas bloqueadas: -");
    private final JTextArea areaColaTradicional = crearArea();
    private final JTextArea areaAtendiendoTradicional = crearArea();
    private final JTextArea areaColaAutopago = crearArea();
    private final JTextArea areaPagandoAutopago = crearArea();

    // Encargados
    private final JLabel etiquetaEncReponiendo = new JLabel("Reponiendo: -");
    private final JLabel etiquetaEncTradicional = new JLabel("En caja tradicional: -");
    private final JLabel etiquetaEncAutopago = new JLabel("En caja autopago: -");
    private final JLabel etiquetaEncAlmacen = new JLabel("En el almacén: -");
    private final JTextArea areaEncReponiendo = crearArea();
    private final JTextArea areaEncTradicional = crearArea();
    private final JTextArea areaEncAutopago = crearArea();

    // Existencias
    private final JLabel[] etiquetasEstanterias = new JLabel[Estanteria.NUM_ESTANTERIAS];
    private final JLabel etiquetaTotalEstanterias = new JLabel("Total estanterías: -");
    private final JLabel etiquetaTotalAlmacen = new JLabel("Almacén: -");

    public VentanaPrincipal() {
        super("JavaDona - Simulación del supermercado");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        construirInterfaz();

        temporizador = new Timer(REFRESCO_MS, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refrescar();
            }
        });

        botonPausa.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (supermercado != null && !finalizada) {
                    supermercado.alternarPausa();
                    refrescar(); // respuesta visual inmediata: texto del botón y estado
                }
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cerrarVentana();
            }
        });
    }

    // ------------------------------------------------------------------
    // Ciclo de vida (se llaman en el EDT)
    // ------------------------------------------------------------------

    /** Vincula la ventana con el sistema y arranca el refresco. Llamar en el EDT. */
    public void conectar(Supermercado supermercado) {
        this.supermercado = supermercado;
        refrescar();
        temporizador.start();
    }

    /**
     * Muestra el estado final detenido. Puede llamarse desde cualquier hilo:
     * la actualización se encarga al EDT. Deja un último refresco con los
     * valores definitivos y detiene el temporizador.
     */
    public void mostrarFinalizada() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                finalizada = true;
                refrescar();
                temporizador.stop();
                botonPausa.setEnabled(false);
            }
        });
    }

    // ------------------------------------------------------------------
    // Refresco (EDT)
    // ------------------------------------------------------------------

    private void refrescar() {
        Supermercado s = supermercado;
        if (s == null) {
            return;
        }
        etiquetaTiempo.setText("Tiempo: " + formatearTiempo(s.getTiempoLogicoMs()));
        etiquetaRecaudacion.setText("Recaudación: " + Dinero.formatear(s.getRecaudacionCentimos()));
        actualizarEstadoYBoton(s);

        etiquetaPlazas.setText("Plazas libres: " + s.getPlazasLibres() + " / " + Aparcamiento.CAPACIDAD);
        etiquetaCochesDentro.setText("Coches dentro: " + s.getCochesDentroAparcamiento());
        etiquetaCochesEsperando.setText("Coches esperando: " + s.getCochesEsperandoAparcamiento());

        etiquetaAforo.setText("Dentro: " + s.getClientesDentroTienda() + " / " + Tienda.AFORO_MAXIMO
                + "  (esperando: " + s.getClientesEsperandoTienda() + ")");
        etiquetaComprando.setText("Comprando: " + s.getClientesComprando());

        etiquetaCajaTradicional.setText("Caja tradicional: " + s.getClientesCajaTradicional() + " clientes");
        etiquetaCajaAutopago.setText("Caja autopago: " + s.getClientesCajaAutopago() + " clientes");
        etiquetaBloqueadas.setText("Máquinas bloqueadas: " + s.getMaquinasBloqueadas());

        etiquetaEncReponiendo.setText("Reponiendo: " + s.getEncargadosReponiendo());
        etiquetaEncTradicional.setText("En caja tradicional: " + s.getEncargadosCajaTradicional());
        etiquetaEncAutopago.setText("En caja autopago: " + s.getEncargadosCajaAutopago());
        etiquetaEncAlmacen.setText("En el almacén: " + s.getEncargadosEnAlmacen());

        for (int i = 0; i < etiquetasEstanterias.length; i++) {
            etiquetasEstanterias[i].setText("E" + (i + 1) + ": " + s.getUnidadesEstanteria(i));
        }
        etiquetaTotalEstanterias.setText("Total estanterías: " + s.getUnidadesEstanterias());
        etiquetaTotalAlmacen.setText("Almacén: " + s.getUnidadesAlmacen());
    }

    private void actualizarEstadoYBoton(Supermercado s) {
        boolean pausado = s.isPausado();
        String estado;
        if (finalizada) {
            estado = "SIMULACIÓN FINALIZADA";
        } else {
            estado = describir(s.getEstadoTienda()) + (pausado ? " (en pausa)" : "");
        }
        etiquetaEstado.setText("Estado: " + estado);
        botonPausa.setText(pausado ? "Reanudar" : "Pausar");
    }

    private static String describir(GestorHorario.EstadoTienda estado) {
        switch (estado) {
            case PRE_APERTURA:
                return "PRE-APERTURA";
            case ABIERTA:
                return "ABIERTA";
            default:
                return "CERRADA";
        }
    }

    /** Milisegundos -> "MM:SS". */
    private static String formatearTiempo(long ms) {
        long segundos = ms / 1000;
        long minutos = segundos / 60;
        long resto = segundos % 60;
        return (minutos < 10 ? "0" : "") + minutos + ":" + (resto < 10 ? "0" : "") + resto;
    }

    // ------------------------------------------------------------------
    // Cierre manual de la ventana
    // ------------------------------------------------------------------

    private void cerrarVentana() {
        boolean activa = supermercado != null && !finalizada && !supermercado.isFinalizada();
        if (activa) {
            int opcion = JOptionPane.showConfirmDialog(this,
                    "La simulación sigue en marcha. ¿Desea cerrarla ahora?",
                    "Cerrar JavaDona", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (opcion != JOptionPane.YES_OPTION) {
                return;
            }
            supermercado.finalizar(true); // línea de interrupción y cierre seguro del log
        }
        temporizador.stop();
        dispose();
        System.exit(0);
    }

    // ------------------------------------------------------------------
    // Construcción de la interfaz
    // ------------------------------------------------------------------

    private void construirInterfaz() {
        getContentPane().setLayout(new BorderLayout(8, 8));

        // Cabecera: estado, tiempo, recaudación y botón de pausa
        JLabel titulo = new JLabel("JAVADONA");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 18f));
        etiquetaEstado.setFont(etiquetaEstado.getFont().deriveFont(Font.BOLD));
        etiquetaTiempo.setFont(etiquetaTiempo.getFont().deriveFont(Font.BOLD));
        etiquetaRecaudacion.setFont(etiquetaRecaudacion.getFont().deriveFont(Font.BOLD));
        JPanel datos = new JPanel(new FlowLayout(FlowLayout.LEFT, 24, 6));
        datos.add(titulo);
        datos.add(etiquetaEstado);
        datos.add(etiquetaTiempo);
        datos.add(etiquetaRecaudacion);
        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 12));
        cabecera.add(datos, BorderLayout.CENTER);
        cabecera.add(botonPausa, BorderLayout.EAST);
        getContentPane().add(cabecera, BorderLayout.NORTH);

        // Centro: las 4 zonas
        JPanel zonaAparcamiento = crearZona("Aparcamiento",
                new JLabel[] {etiquetaPlazas, etiquetaCochesDentro, etiquetaCochesEsperando},
                new JScrollPane[] {});
        JPanel zonaTienda = crearZona("Tienda",
                new JLabel[] {etiquetaAforo, etiquetaComprando},
                new JScrollPane[] {
                    crearLista("Esperando para entrar", areaEsperaTienda),
                    crearLista("Dentro de la tienda", areaDentroTienda)});
        JPanel zonaCajas = crearZona("Cajas",
                new JLabel[] {etiquetaCajaTradicional, etiquetaCajaAutopago, etiquetaBloqueadas},
                new JScrollPane[] {
                    crearLista("Cola caja tradicional", areaColaTradicional),
                    crearLista("Atendiendo (tradicional)", areaAtendiendoTradicional),
                    crearLista("Cola autopago", areaColaAutopago),
                    crearLista("Pagando (autopago)", areaPagandoAutopago)});
        JPanel zonaEncargados = crearZona("Encargados",
                new JLabel[] {etiquetaEncReponiendo, etiquetaEncTradicional,
                    etiquetaEncAutopago, etiquetaEncAlmacen},
                new JScrollPane[] {
                    crearLista("Reponiendo", areaEncReponiendo),
                    crearLista("En caja tradicional", areaEncTradicional),
                    crearLista("En caja autopago", areaEncAutopago)});

        JPanel centro = new JPanel(new GridBagLayout());
        anadirZona(centro, zonaAparcamiento, 0, 0.6);
        anadirZona(centro, zonaTienda, 1, 1.0);
        anadirZona(centro, zonaCajas, 2, 1.0);
        anadirZona(centro, zonaEncargados, 3, 1.0);
        getContentPane().add(centro, BorderLayout.CENTER);

        // Sur: existencias
        JPanel sur = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 4));
        sur.setBorder(BorderFactory.createTitledBorder("Existencias (unidades)"));
        for (int i = 0; i < etiquetasEstanterias.length; i++) {
            etiquetasEstanterias[i] = new JLabel("E" + (i + 1) + ": -");
            sur.add(etiquetasEstanterias[i]);
        }
        sur.add(etiquetaTotalEstanterias);
        sur.add(etiquetaTotalAlmacen);
        getContentPane().add(sur, BorderLayout.SOUTH);

        setSize(1320, 780);
        setMinimumSize(new Dimension(1000, 600));
        setLocationRelativeTo(null);
    }

    private static JTextArea crearArea() {
        JTextArea area = new JTextArea(3, 12);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return area;
    }

    private static JScrollPane crearLista(String titulo, JTextArea area) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createTitledBorder(titulo));
        return scroll;
    }

    private static JPanel crearZona(String titulo, JLabel[] etiquetas, JScrollPane[] listas) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createTitledBorder(titulo));
        JPanel arriba = new JPanel(new GridLayout(0, 1, 2, 2));
        for (int i = 0; i < etiquetas.length; i++) {
            arriba.add(etiquetas[i]);
        }
        panel.add(arriba, BorderLayout.NORTH);
        if (listas.length > 0) {
            JPanel centro = new JPanel(new GridLayout(0, 1, 4, 4));
            for (int i = 0; i < listas.length; i++) {
                centro.add(listas[i]);
            }
            panel.add(centro, BorderLayout.CENTER);
        }
        return panel;
    }

    private static void anadirZona(JPanel contenedor, JPanel zona, int columna, double pesoX) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = columna;
        c.gridy = 0;
        c.weightx = pesoX;
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(2, 4, 2, 4);
        contenedor.add(zona, c);
    }

    // ------------------------------------------------------------------
    // VistaListas
    // ------------------------------------------------------------------

    @Override
    public JTextComponent getEsperaTienda() {
        return areaEsperaTienda;
    }

    @Override
    public JTextComponent getDentroTienda() {
        return areaDentroTienda;
    }

    @Override
    public JTextComponent getColaTradicional() {
        return areaColaTradicional;
    }

    @Override
    public JTextComponent getAtendiendoTradicional() {
        return areaAtendiendoTradicional;
    }

    @Override
    public JTextComponent getColaAutopago() {
        return areaColaAutopago;
    }

    @Override
    public JTextComponent getPagandoAutopago() {
        return areaPagandoAutopago;
    }

    @Override
    public JTextComponent getEncargadosReponiendo() {
        return areaEncReponiendo;
    }

    @Override
    public JTextComponent getEncargadosTradicional() {
        return areaEncTradicional;
    }

    @Override
    public JTextComponent getEncargadosAutopago() {
        return areaEncAutopago;
    }
}
