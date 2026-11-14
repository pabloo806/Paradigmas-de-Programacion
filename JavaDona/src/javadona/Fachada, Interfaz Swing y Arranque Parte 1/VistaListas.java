package javadona;

import javax.swing.text.JTextComponent;

/**
 * Vista de la simulación: los 9 componentes de texto donde se reflejan las
 * listas en vivo. Permite que Supermercado enlace sus ListaConcurrente con la
 * interfaz sin depender de una ventana concreta (ni de que exista una).
 */
public interface VistaListas {

    JTextComponent getEsperaTienda();

    JTextComponent getDentroTienda();

    JTextComponent getColaTradicional();

    JTextComponent getAtendiendoTradicional();

    JTextComponent getColaAutopago();

    JTextComponent getPagandoAutopago();

    JTextComponent getEncargadosReponiendo();

    JTextComponent getEncargadosTradicional();

    JTextComponent getEncargadosAutopago();
}
