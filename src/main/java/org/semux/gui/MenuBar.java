/**
 * Copyright (c) 2017 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package org.semux.gui;

import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.semux.core.Wallet;
import org.semux.crypto.EdDSA;
import org.semux.crypto.Hex;
import org.semux.gui.dialog.ChangePasswordDialog;
import org.semux.gui.dialog.ConsoleDialog;
import org.semux.gui.dialog.ExportPrivateKeyDialog;
import org.semux.gui.dialog.InputDialog;
import org.semux.message.GUIMessages;
import org.semux.util.SystemUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MenuBar extends JMenuBar implements ActionListener {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(MenuBar.class);
    public static final String HELP_URL = "https://github.com/semuxproject/semux/wiki";

    private transient SemuxGUI gui;
    private JFrame frame;

    public MenuBar(SemuxGUI gui, JFrame frame) {
        this.gui = gui;
        this.frame = frame;

        JMenu menuFile = new JMenu(GUIMessages.get("File"));
        this.add(menuFile);

        JMenuItem itemExit = new JMenuItem(GUIMessages.get("Exit"));
        itemExit.setName("itemExit");
        itemExit.setActionCommand(Action.EXIT.name());
        itemExit.addActionListener(this);
        menuFile.add(itemExit);

        JMenu menuWallet = new JMenu(GUIMessages.get("Wallet"));
        this.add(menuWallet);

        JMenuItem itemChangePassword = new JMenuItem(GUIMessages.get("ChangePassword"));
        itemChangePassword.setName("itemChangePassword");
        itemChangePassword.setActionCommand(Action.CHANGE_PASSWORD.name());
        itemChangePassword.addActionListener(this);
        menuWallet.add(itemChangePassword);

        menuWallet.addSeparator();

        JMenuItem itemRecover = new JMenuItem(GUIMessages.get("RecoverWallet"));
        itemRecover.setName("itemRecover");
        itemRecover.setActionCommand(Action.RECOVER_ACCOUNTS.name());
        itemRecover.addActionListener(this);
        menuWallet.add(itemRecover);

        JMenuItem itemBackupWallet = new JMenuItem(GUIMessages.get("BackupWallet"));
        itemBackupWallet.setName("itemBackupWallet");
        itemBackupWallet.setActionCommand(Action.BACKUP_WALLET.name());
        itemBackupWallet.addActionListener(this);
        menuWallet.add(itemBackupWallet);

        menuWallet.addSeparator();

        JMenuItem itemImportPrivateKey = new JMenuItem(GUIMessages.get("ImportPrivateKey"));
        itemImportPrivateKey.setName("itemImportPrivateKey");
        itemImportPrivateKey.setActionCommand(Action.IMPORT_PRIVATE_KEY.name());
        itemImportPrivateKey.addActionListener(this);
        menuWallet.add(itemImportPrivateKey);

        JMenuItem itemExportPrivateKey = new JMenuItem(GUIMessages.get("ExportPrivateKey"));
        itemExportPrivateKey.setName("itemExportPrivateKey");
        itemExportPrivateKey.setActionCommand(Action.EXPORT_PRIVATE_KEY.name());
        itemExportPrivateKey.addActionListener(this);
        menuWallet.add(itemExportPrivateKey);

        JMenu menuHelp = new JMenu(GUIMessages.get("Help"));
        this.add(menuHelp);

        JMenuItem itemAbout = new JMenuItem(GUIMessages.get("About"));
        itemAbout.setName("itemAbout");
        itemAbout.setActionCommand(Action.ABOUT.name());
        itemAbout.addActionListener(this);
        menuHelp.add(itemAbout);

        JMenuItem itemConsole = new JMenuItem(GUIMessages.get("Console"));
        itemConsole.setName("itemConsole");
        itemConsole.setActionCommand(Action.CONSOLE.name());
        itemConsole.addActionListener(this);
        menuHelp.add(itemConsole);

        JMenuItem itemHelp = new JMenuItem(GUIMessages.get("Help"));
        itemHelp.setName("itemHelp");
        itemHelp.setActionCommand(Action.HELP.name());
        itemHelp.addActionListener(this);
        menuHelp.add(itemHelp);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Action action = Action.valueOf(e.getActionCommand());

        switch (action) {
        case EXIT:
            SystemUtil.exitAsync(0);
            break;
        case RECOVER_ACCOUNTS:
            recoverAccounts();
            break;
        case BACKUP_WALLET:
            backupWallet();
            break;
        case IMPORT_PRIVATE_KEY:
            importPrivateKey();
            break;
        case EXPORT_PRIVATE_KEY:
            exportPrivateKey();
            break;
        case CHANGE_PASSWORD:
            changePassword();
            break;
        case ABOUT:
            about();
            break;
        case HELP:
            help();
            break;
        case CONSOLE:
            console();
            break;
        default:
            break;
        }
    }

    /**
     * Shows the change password dialog.
     */
    protected void changePassword() {
        if (showErroIfLocked()) {
            return;
        }

        ChangePasswordDialog d = new ChangePasswordDialog(gui, frame);
        d.setVisible(true);
    }

    /**
     * Recovers accounts from backup file.
     */
    protected void recoverAccounts() {
        if (showErroIfLocked()) {
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileFilter(new FileNameExtensionFilter(GUIMessages.get("WalletBinaryFormat"), "data"));

        int ret = chooser.showOpenDialog(frame);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            String pwd = new InputDialog(frame, GUIMessages.get("EnterPassword"), true).showAndGet();

            if (pwd != null) {
                Wallet w = new Wallet(file);
                if (!w.unlock(pwd)) {
                    JOptionPane.showMessageDialog(frame, GUIMessages.get("UnlockFailed"));
                    return;
                }

                Wallet wallet = gui.getKernel().getWallet();
                int n = wallet.addAccounts(w.getAccounts());
                wallet.flush();
                JOptionPane.showMessageDialog(frame, GUIMessages.get("ImportSuccess", n));
                gui.getModel().fireUpdateEvent();
            }
        }
    }

    /**
     * Backup the wallet.
     */
    protected void backupWallet() {
        if (showErroIfLocked()) {
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setSelectedFile(new File("wallet.data"));
        chooser.setFileFilter(new FileNameExtensionFilter(GUIMessages.get("WalletBinaryFormat"), "data"));

        int ret = chooser.showSaveDialog(frame);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File dst = chooser.getSelectedFile();
            if (dst.exists()) {
                int answer = JOptionPane.showConfirmDialog(frame, GUIMessages.get("BackupFileExists", dst.getName()));
                if (answer != JOptionPane.OK_OPTION) {
                    return;
                }
            }
            File src = gui.getKernel().getWallet().getFile();
            try {
                Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
                JOptionPane.showMessageDialog(frame, GUIMessages.get("WalletSavedAt", dst.getAbsolutePath()));
            } catch (IOException ex) {
                logger.warn("Failed to save backup file", ex);
                JOptionPane.showMessageDialog(frame, GUIMessages.get("SaveBackupFailed"));
            }
        }
    }

    /**
     * Imports private key into this wallet.
     */
    protected void importPrivateKey() {
        if (showErroIfLocked()) {
            return;
        }

        String pk = new InputDialog(frame, GUIMessages.get("EnterPrivateKey"), false).showAndGet();
        if (pk != null) {
            try {
                Wallet wallet = gui.getKernel().getWallet();
                EdDSA account = new EdDSA(Hex.decode0x(pk));
                if (wallet.addAccount(account)) {
                    wallet.flush();
                    JOptionPane.showMessageDialog(frame, GUIMessages.get("PrivateKeyImportSuccess"));
                    gui.getModel().fireUpdateEvent();
                } else {
                    JOptionPane.showMessageDialog(frame, GUIMessages.get("PrivateKeyAlreadyExists"));
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(frame, GUIMessages.get("PrivateKeyImportFailed"));
            }
        }
    }

    /**
     * Shows the export private key dialog.
     */
    protected void exportPrivateKey() {
        if (showErroIfLocked()) {
            return;
        }

        ExportPrivateKeyDialog d = new ExportPrivateKeyDialog(gui, frame);
        d.setVisible(true);
    }

    /**
     * Shows the console
     */
    private void console() {
        if (showErroIfLocked()) {
            return;
        }
        ConsoleDialog d = new ConsoleDialog(gui, frame);
        d.setVisible(true);
    }

    /**
     * Shows the about dialog.
     */
    protected void about() {
        JOptionPane.showMessageDialog(frame, gui.getKernel().getConfig().getClientId());
    }

    private void help() {
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(new URI(HELP_URL));
            } catch (IOException | URISyntaxException e) {
                logger.error("Unable to parse help url " + HELP_URL);
            }
        }
    }

    /**
     * Displays an error message if the wallet is locked.
     * 
     * @return whether the wallet is locked
     */
    protected boolean showErroIfLocked() {
        Wallet wallet = gui.getKernel().getWallet();

        if (wallet.isLocked()) {
            JOptionPane.showMessageDialog(frame, GUIMessages.get("WalletLocked"));
            return true;
        }

        return false;
    }
}
