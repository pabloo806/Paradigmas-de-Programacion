package javadona.cliente;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import javadona.Dinero;
import javadona.remoto.EstadoSupermercado;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

/**
 * Ventana del cliente remoto: VISTA PASIVA (Swing escrito a mano, sin .form).
 *
 * No consulta la red ni decide nada: el controlador (ClienteRemoto) le dice qué
 * mostrar. Todos sus métodos deben llamarse en el EDT.
 *
 * El texto del botón de pausa se fija siempre desde el estado que informa el
 * servidor (mostrarBoton), nunca alternándolo en local.
 */
public final class VentanaRemota extends JFrame {

    private static final long serialVersionUID = 1L;

    /** Estados de la conexión con el servidor, con su código de color. */
    public enum EstadoConexion {
        CONECTANDO("CONECTANDO", new Color(255, 214, 102), Color.BLACK,
                "Esperando al servidor…"),
        CONECTADO("CONECTADO", new Color(46, 160, 67), Color.WHITE, ""),
        SIN_RESPUESTA("SIN RESPUESTA", new Color(240, 140, 40), Color.BLACK,
                "El servidor no responde. Se muestran los últimos valores recibidos."),
        SIN_CONEXION("SIN CONEXIÓN", new Color(200, 50, 50), Color.WHITE,
                "Conexión perdida con el servidor. Se muestran los últimos valores conocidos "
                + "(atenuados). Reintentando automáticamente…"),
        FINALIZADA("FINALIZADA", new Color(70, 100, 170), Color.WHITE,
                "La simulación ha finalizado. Se muestran los valores definitivos.");

        private final String texto;
        private final Color fondo;
        private final Color letra;
        private final String aviso;

        private EstadoConexion(String texto, Color fondo, Color letra, String aviso) {
            this.texto = texto;
            this.fondo = fondo;
            this.letra = letra;
            this.aviso = aviso;
        }

        public String getTexto() {
            return texto;
        }
    }

    static final String SIN_DATO = "—";
    static final Color COLOR_VALOR = new Color(25, 25, 25);
    static final Color COLOR_ATENUADO = new Color(165, 165, 165);

    private final JLabel etiquetaConexion = new JLabel();
    private final JLabel etiquetaTienda = new JLabel("Tienda: " + SIN_DATO);
    private final JLabel etiquetaReloj = new JLabel("Tiempo: --:--");
    private final JLabel etiquetaAviso = new JLabel(" ");
    private final JButton botonPausa = new JButton("Detener");

    // Los 13 indicadores
    private final JLabel valorEsperando = crearValor("esperando");
    private final JLabel valorComprando = crearValor("comprando");
    private final JLabel valorCajaTradicional = crearValor("cajaTradicional");
    private final JLabel valorCajaAutopago = crearValor("cajaAutopago");
    private final JLabel valorCochesDentro = crearValor("cochesDentro");
    private final JLabel valorCochesEsperando = crearValor("cochesEsperando");
    private final JLabel valorEncAlmacen = crearValor("encAlmacen");
    private final JLabel valorEncReponiendo = crearValor("encReponiendo");
    private final JLabel valorEncTradicional = crearValor("encTradicional");
    private final JLabel valorEncAutopago = crearValor("encAutopago");
    private final JLabel valorUnidadesEstanterias = crearValor("unidadesEstanterias");
    private final JLabel valorUnidadesAlmacen = crearValor("unidadesAlmacen");
    private final JLabel valorRecaudacion = crearValor("recaudacion");
    private final JLabel[] valores = {
        valorEsperando, valorComprando, valorCajaTradicional, valorCajaAutopago,
        valorCochesDentro, valorCochesEsperando, valorEncAlmacen, valorEncReponiendo,
        valorEncTradicional, valorEncAutopago, valorUnidadesEstanterias,
        valorUnidadesAlmacen, valorRecaudacion};

    public VentanaRemota(String host, int puerto) {
        super("JavaDona - Cliente remoto");
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        etiquetaConexion.setName("conexion");
        etiquetaTienda.setName("tienda");
        etiquetaReloj.setName("reloj");
        etiquetaAviso.setName("aviso");
        botonPausa.setName("botonPausa");
        construirInterfaz(host, puerto);
        // Estado inicial: valores "—", CONECTANDO y botón deshabilitado
        mostrarConexion(EstadoConexion.CONECTANDO);
        mostrarBoton(false, false);
    }

    // ------------------------------------------------------------------
    // API de la vista (llamar en el EDT)
    // ------------------------------------------------------------------

    /** Muestra el estado de la conexión, su aviso y, si procede, atenúa los valores. */
    public void mostrarConexion(EstadoConexion estado) {
        etiquetaConexion.setText(estado.texto);
        etiquetaConexion.setBackground(estado.fondo);
        etiquetaConexion.setForeground(estado.letra);
        boolean hayAviso = !estado.aviso.isEmpty();
        etiquetaAviso.setText(hayAviso ? estado.aviso : " ");
        etiquetaAviso.setOpaque(hayAviso);
        etiquetaAviso.setBackground(estado.fondo);
        etiquetaAviso.setForeground(estado.letra);
        atenuarValores(estado == EstadoConexion.SIN_CONEXION);
    }

    /** Vuelca la foto recibida del servidor en los indicadores, el reloj y la tienda. */
    public void mostrarEstado(EstadoSupermercado e) {
        valorEsperando.setText(String.valueOf(e.getClientesEsperando()));
        valorComprando.setText(String.valueOf(e.getClientesComprando()));
        valorCajaTradicional.setText(String.valueOf(e.getClientesCajaTradicional()));
        valorCajaAutopago.setText(String.valueOf(e.getClientesCajaAutopago()));
        valorCochesDentro.setText(String.valueOf(e.getCochesDentro()));
        valorCochesEsperando.setText(String.valueOf(e.getCochesEsperando()));
        valorEncAlmacen.setText(String.valueOf(e.getEncargadosAlmacen()));
        valorEncReponiendo.setText(String.valueOf(e.getEncargadosReponiendo()));
        valorEncTradicional.setText(String.valueOf(e.getEncargadosCajaTradicional()));
        valorEncAutopago.setText(String.valueOf(e.getEncargadosCajaAutopago()));
        valorUnidadesEstanterias.setText(String.valueOf(e.getUnidadesEstanterias()));
        valorUnidadesAlmacen.setText(String.valueOf(e.getUnidadesAlmacen()));
        valorRecaudacion.setText(Dinero.formatear(e.getRecaudacionCentimos()));
        etiquetaTienda.setText("Tienda: " + e.getEstadoTienda() + (e.isPausado() ? " (en pausa)" : ""));
        etiquetaReloj.setText("Tiempo: " + formatearTiempo(e.getTiempoLogicoMs()));
    }

    /**
     * Fija el botón de pausa: "Reanudar" si el SERVIDOR está pausado, "Detener" si
     * no. El texto nunca se alterna en local.
     */
    public void mostrarBoton(boolean pausadoEnServidor, boolean habilitado) {
        botonPausa.setText(pausadoEnServidor ? "Reanudar" : "Detener");
        botonPausa.setEnabled(habilitado);
    }

    public void setAccionPausa(ActionListener accion) {
        botonPausa.addActionListener(accion);
    }

    // ------------------------------------------------------------------
    // Construcción de la interfaz
    // ------------------------------------------------------------------

    private void atenuarValores(boolean atenuados) {
        Color color = atenuados ? COLOR_ATENUADO : COLOR_VALOR;
        for (int i = 0; i < valores.length; i++) {
            valores[i].setForeground(color);
        }
    }

    private void construirInterfaz(String host, int puerto) {
        getContentPane().setLayout(new BorderLayout(8, 8));

        // Barra superior: servidor, conexión, tienda, reloj y botón
        JLabel servidor = new JLabel("Servidor: " + host + ":" + puerto);
        servidor.setFont(servidor.getFont().deriveFont(Font.BOLD));
        etiquetaConexion.setOpaque(true);
        etiquetaConexion.setHorizontalAlignment(SwingConstants.CENTER);
        etiquetaConexion.setFont(etiquetaConexion.getFont().deriveFont(Font.BOLD));
        etiquetaConexion.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        etiquetaTienda.setFont(etiquetaTienda.getFont().deriveFont(Font.BOLD));
        etiquetaReloj.setFont(etiquetaReloj.getFont().deriveFont(Font.BOLD));
        JPanel datos = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 6));
        datos.add(servidor);
        datos.add(etiquetaConexion);
        datos.add(etiquetaTienda);
        datos.add(etiquetaReloj);
        JPanel barra = new JPanel(new BorderLayout());
        barra.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 12));
        barra.add(datos, BorderLayout.CENTER);
        botonPausa.setPreferredSize(new Dimension(120, 32));
        barra.add(botonPausa, BorderLayout.EAST);
        getContentPane().add(barra, BorderLayout.NORTH);

        // Centro: los 13 indicadores agrupados por categoría
        JPanel tienda = crearPanel("Tienda",
                new String[] {"Clientes esperando a entrar", "Clientes comprando"},
                new JLabel[] {valorEsperando, valorComprando});
        JPanel cajas = crearPanel("Cajas",
                new String[] {"Clientes en caja tradicional", "Clientes en autopago"},
                new JLabel[] {valorCajaTradicional, valorCajaAutopago});
        JPanel aparcamiento = crearPanel("Aparcamiento",
                new String[] {"Coches dentro", "Coches esperando"},
                new JLabel[] {valorCochesDentro, valorCochesEsperando});
        JPanel encargados = crearPanel("Encargados",
                new String[] {"En el almacén", "Reponiendo", "En caja tradicional", "En caja autopago"},
                new JLabel[] {valorEncAlmacen, valorEncReponiendo, valorEncTradicional, valorEncAutopago});
        JPanel inventario = crearPanel("Inventario y recaudación",
                new String[] {"Unidades en estanterías", "Unidades en almacén", "Recaudación actual"},
                new JLabel[] {valorUnidadesEstanterias, valorUnidadesAlmacen, valorRecaudacion});

        JPanel centro = new JPanel(new GridBagLayout());
        anadir(centro, tienda, 0, 0, 1);
        anadir(centro, cajas, 1, 0, 1);
        anadir(centro, aparcamiento, 2, 0, 1);
        anadir(centro, encargados, 0, 1, 1);
        anadir(centro, inventario, 1, 1, 2);
        getContentPane().add(centro, BorderLayout.CENTER);

        // Sur: aviso de conexión
        etiquetaAviso.setHorizontalAlignment(SwingConstants.CENTER);
        etiquetaAviso.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        etiquetaAviso.setFont(etiquetaAviso.getFont().deriveFont(Font.BOLD));
        getContentPane().add(etiquetaAviso, BorderLayout.SOUTH);

        setSize(920, 520);
        setMinimumSize(new Dimension(760, 460));
        setLocationRelativeTo(null);
    }

    private static JLabel crearValor(String nombre) {
        JLabel valor = new JLabel(SIN_DATO);
        valor.setName(nombre);
        valor.setHorizontalAlignment(SwingConstants.RIGHT);
        valor.setFont(valor.getFont().deriveFont(Font.BOLD, 20f));
        valor.setForeground(COLOR_VALOR);
        return valor;
    }

    private static JPanel crearPanel(String titulo, String[] nombres, JLabel[] valoresPanel) {
        JPanel panel = new JPanel(new GridLayout(0, 2, 10, 10));
        panel.setBorder(BorderFactory.createTitledBorder(titulo));
        for (int i = 0; i < nombres.length; i++) {
            panel.add(new JLabel(nombres[i]));
            panel.add(valoresPanel[i]);
        }
        return panel;
    }

    private static void anadir(JPanel contenedor, JPanel panel, int columna, int fila, int ancho) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = columna;
        c.gridy = fila;
        c.gridwidth = ancho;
        c.weightx = 1.0;
        c.weighty = 1.0;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(4, 6, 4, 6);
        contenedor.add(panel, c);
    }

    /** Milisegundos -> "MM:SS". */
    private static String formatearTiempo(long ms) {
        long segundos = Math.max(0L, ms) / 1000;
        long minutos = segundos / 60;
        long resto = segundos % 60;
        return (minutos < 10 ? "0" : "") + minutos + ":" + (resto < 10 ? "0" : "") + resto;
    }
}
