/**
 * Copyright (c) 2017 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package org.semux.gui.dialog;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

import javax.swing.*;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.semux.api.ApiHandlerResponse;
import org.semux.api.SemuxAPI;
import org.semux.api.SemuxApiImpl;
import org.semux.gui.SemuxGUI;
import org.semux.message.GUIMessages;

/**
 */
public class ConsoleDialog extends JDialog implements ActionListener {

    private SemuxGUI gui;
    private JTextArea console;
    private JTextField input;
    private SemuxAPI api;
    private ObjectMapper mapper = new ObjectMapper();

    public ConsoleDialog(SemuxGUI gui, JFrame parent) {

        super(null, GUIMessages.get("Console"), Dialog.ModalityType.MODELESS);
        this.gui = gui;

        setName("Console");

        console = new JTextArea();
        console.setEditable(false);

        JScrollPane scroll = new JScrollPane(console);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

        input = new JTextField();
        input.addActionListener(this);

        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(input, BorderLayout.SOUTH);

        this.setSize(800, 600);
        this.setLocationRelativeTo(parent);
        api = new SemuxApiImpl(gui.getKernel());

        console.append(GUIMessages.get("ConsoleHelp"));
        addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                input.requestFocus();
            }
        });
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String command = input.getText();

        console.append("\n");
        console.append("> " + command);
        console.append("\n");

        switch (command) {
        case "help":
            printHelp();
            break;
        default:
            callApi(command);
            break;

        }
        input.setText("");
    }

    /**
     * Use reflection to call methods
     *
     * @param commandString
     */
    private void callApi(String commandString) {
        String[] commandParams = commandString.split(" ");

        String command = commandParams[0];
        // api only takes string parameters;

        int numParams = commandParams.length - 1;
        Class[] classes = new Class[numParams];
        for (int i = 0; i < numParams; i++) {
            classes[i] = String.class;
        }

        try {
            Method method = api.getClass().getMethod(command, classes);
            ApiHandlerResponse response = (ApiHandlerResponse) method.invoke(api,
                    Arrays.copyOfRange(commandParams, 1, commandParams.length));

            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            console.append("\n");
            console.append(mapper.writeValueAsString(response));
            console.append("\n");
        } catch (NoSuchMethodException e) {
            console.append(GUIMessages.get("UnknownMethod", command));
        } catch (InvocationTargetException | IllegalAccessException | JsonProcessingException e) {
            console.append(GUIMessages.get("MethodError", command));
        }
    }

    private void printHelp() {
        Method[] allMethods = SemuxAPI.class.getMethods();
        for (Method method : allMethods) {
            String methodString = getMethodString(method);

            if (methodString != null) {
                console.append(methodString);
            }
        }

        console.append("\n");
    }

    private String getMethodString(Method method) {
        // get the annotation
        Path path = method.getAnnotation(Path.class);
        if (path == null) {
            // not a web method
            return null;
        }
        StringBuilder builder = new StringBuilder();

        builder.append(method.getName());
        for (Parameter parameter : method.getParameters()) {

            builder.append(" ");
            PathParam param = parameter.getAnnotation(PathParam.class);
            builder.append("<");
            if (param != null) {
                builder.append(param.value());
            } else {
                builder.append(parameter.getName());
            }
            builder.append(">");

        }
        builder.append("\n");
        return builder.toString();
    }
}
