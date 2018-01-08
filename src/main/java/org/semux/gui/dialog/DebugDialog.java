package org.semux.gui.dialog;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.*;

import org.semux.gui.SemuxGUI;
import org.semux.gui.logging.DebugPanelAppender;
import org.semux.message.GUIMessages;

/**
 */
public class DebugDialog extends JDialog {

    private JTextArea debug = new JTextArea();

    public DebugDialog(SemuxGUI gui, JFrame parent) {
        super(null, GUIMessages.get("Debug"), Dialog.ModalityType.MODELESS);
        setName("Debug");
        setModalityType(Dialog.ModalityType.MODELESS);

        debug = new JTextArea();
        this.setSize(800, 600);
        debug.setEditable(false);

        JScrollPane scroll = new JScrollPane (debug);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

        this.add(scroll);

        this.setSize(800, 600);
        this.setLocationRelativeTo(parent);
        DebugPanelAppender.setEnabled(true, this);
    }

    @Override
    public void setVisible(boolean b) {
        super.setVisible(b);
        DebugPanelAppender.setEnabled(b, this);
    }

    public void writeLine(String line) {
        debug.append(line);
        debug.append("\n");
        debug.setCaretPosition(debug.getDocument().getLength());
    }
}
