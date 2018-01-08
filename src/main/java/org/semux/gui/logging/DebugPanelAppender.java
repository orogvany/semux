package org.semux.gui.logging;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.semux.gui.dialog.DebugDialog;

/**
 */
public class DebugPanelAppender<E extends LoggingEvent>  extends AppenderBase<E> {

    private static boolean enabled;
    private static DebugDialog dialog;

    public DebugPanelAppender() {
    }

    public static void setEnabled(boolean enabled, DebugDialog dialog) {
        DebugPanelAppender.enabled = enabled;
        DebugPanelAppender.dialog = dialog;
    }

    @Override
    protected void append(E e) {
        if(enabled)
        {
            dialog.writeLine(e.getFormattedMessage());
        }
    }
}
