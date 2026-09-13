package org.crafterscr.craftersnpc.entity;

/**
 * Acceso al estado de posición fija añadido a los CNPC.
 *
 * <p>Se mantiene separado de la lógica de animaciones: fijar un NPC solo
 * bloquea su desplazamiento físico. Diálogos, rotación visual y emotes
 * continúan funcionando.</p>
 */
public interface FixedPositionNpc {
    boolean isFixedPosition();

    void setFixedPosition(boolean fixed);
}
