/**
 * Copyright (c) 2017 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package org.semux.gui.panel;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.border.LineBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.semux.Kernel;
import org.semux.config.Config;
import org.semux.config.Constants;
import org.semux.core.Blockchain;
import org.semux.core.BlockchainImpl.ValidatorStats;
import org.semux.core.PendingManager;
import org.semux.core.Transaction;
import org.semux.core.TransactionType;
import org.semux.core.state.Delegate;
import org.semux.core.state.DelegateState;
import org.semux.crypto.Hex;
import org.semux.gui.Action;
import org.semux.gui.SemuxGUI;
import org.semux.gui.SwingUtil;
import org.semux.gui.dialog.DelegateDialog;
import org.semux.gui.model.WalletAccount;
import org.semux.gui.model.WalletDelegate;
import org.semux.gui.model.WalletModel;
import org.semux.message.GUIMessages;
import org.semux.util.Bytes;
import org.semux.util.SystemUtil;
import org.semux.util.exception.UnreachableException;

public class DelegatesPanel extends JPanel implements ActionListener {

    private static final long serialVersionUID = 1L;

    private transient WalletModel model;

    private transient Kernel kernel;
    private transient Config config;

    private JTable table;
    private DelegatesTableModel tableModel;

    JComboBox<Item> selectFrom;

    private JTextField textVote;
    private JTextField textUnvote;
    private JTextField textName;
    private JLabel labelSelectedDelegate;

    public DelegatesPanel(SemuxGUI gui, JFrame frame) {
        this.model = gui.getModel();
        this.model.addListener(this);

        this.kernel = gui.getKernel();
        this.config = kernel.getConfig();

        tableModel = new DelegatesTableModel();
        table = new JTable(tableModel);
        table.setName("DelegatesTable");
        table.setBackground(Color.WHITE);
        table.setFillsViewportHeight(true);
        table.setGridColor(Color.LIGHT_GRAY);
        table.setRowHeight(25);
        table.getTableHeader().setPreferredSize(new Dimension(10000, 24));
        SwingUtil.setColumnWidths(table, 600, 0.07, 0.2, 0.25, 0.15, 0.15, 0.08, 0.1);
        SwingUtil.setColumnAlignments(table, false, false, false, true, true, true, true);

        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !table.getSelectionModel().isSelectionEmpty()) {
                updateSelectedDelegateLabel();
            }
        });

        table.addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent me) {
                JTable sourceTable = (JTable) me.getSource();
                Point p = me.getPoint();
                int row = sourceTable.rowAtPoint(p);
                if (me.getClickCount() == 2 && row != -1) {
                    WalletDelegate d = tableModel.getRow(sourceTable.convertRowIndexToModel(row));
                    if (d != null) {
                        DelegateDialog dialog = new DelegateDialog(gui, frame, d);
                        dialog.setVisible(true);
                    }
                }
            }
        });

        // customized table sorter
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(table.getModel());
        sorter.setComparator(0, SwingUtil.NUMBER_COMPARATOR);
        sorter.setComparator(3, SwingUtil.NUMBER_COMPARATOR);
        sorter.setComparator(4, SwingUtil.NUMBER_COMPARATOR);
        sorter.setComparator(6, SwingUtil.PERCENTAGE_COMPARATOR);
        table.setRowSorter(sorter);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(new LineBorder(Color.LIGHT_GRAY));

        JPanel votePanel = new JPanel();
        votePanel.setBorder(new LineBorder(Color.LIGHT_GRAY));

        JPanel delegateRegistrationPanel = new JPanel();
        delegateRegistrationPanel.setBorder(new LineBorder(Color.LIGHT_GRAY));

        JLabel label = new JLabel(GUIMessages
                .get("DelegateRegistrationNoteHtml", SwingUtil.formatValue(config.minDelegateBurnAmount()),
                        SwingUtil.formatValue(config.minTransactionFee())));
        label.setForeground(Color.DARK_GRAY);

        selectFrom = new JComboBox<>();
        selectFrom.setActionCommand(Action.SELECT_ACCOUNT.name());
        selectFrom.addActionListener(this);

        // @formatter:off
        GroupLayout groupLayout = new GroupLayout(this);
        groupLayout.setHorizontalGroup(
            groupLayout.createParallelGroup(Alignment.TRAILING)
                .addGroup(groupLayout.createSequentialGroup()
                    .addComponent(scrollPane, GroupLayout.DEFAULT_SIZE, 640, Short.MAX_VALUE)
                    .addGap(18)
                    .addGroup(groupLayout.createParallelGroup(Alignment.TRAILING, false)
                        .addComponent(votePanel, GroupLayout.DEFAULT_SIZE, 210, Short.MAX_VALUE)
                        .addComponent(selectFrom, GroupLayout.DEFAULT_SIZE, 210, Short.MAX_VALUE)
                        .addComponent(label, GroupLayout.DEFAULT_SIZE, 210, Short.MAX_VALUE)
                        .addComponent(delegateRegistrationPanel, GroupLayout.DEFAULT_SIZE, 210, Short.MAX_VALUE)))
        );
        groupLayout.setVerticalGroup(
            groupLayout.createParallelGroup(Alignment.LEADING)
                .addGroup(groupLayout.createSequentialGroup()
                    .addComponent(selectFrom, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
                    .addGap(18)
                    .addComponent(votePanel, GroupLayout.PREFERRED_SIZE, 150, GroupLayout.PREFERRED_SIZE)
                    .addGap(18)
                    .addComponent(delegateRegistrationPanel, GroupLayout.PREFERRED_SIZE, 100, GroupLayout.PREFERRED_SIZE)
                    .addGap(18)
                    .addComponent(label)
                    .addContainerGap(174, Short.MAX_VALUE))
                .addComponent(scrollPane, GroupLayout.DEFAULT_SIZE, 512, Short.MAX_VALUE)
        );
        setLayout(groupLayout); 
        // @formatter:on

        textVote = SwingUtil.textFieldWithCopyPastePopup();
        textVote.setName("textVote");
        textVote.setToolTipText(GUIMessages.get("NumVotes"));
        textVote.setColumns(10);
        textVote.setActionCommand(Action.VOTE.name());
        textVote.addActionListener(this);

        JButton btnVote = SwingUtil.createDefaultButton(GUIMessages.get("Vote"), this, Action.VOTE);
        btnVote.setName("btnVote");

        textUnvote = SwingUtil.textFieldWithCopyPastePopup();
        textUnvote.setName("testUnvote");
        textUnvote.setToolTipText(GUIMessages.get("NumVotes"));
        textUnvote.setColumns(10);
        textUnvote.setActionCommand(Action.UNVOTE.name());
        textUnvote.addActionListener(this);

        JButton btnUnvote = SwingUtil.createDefaultButton(GUIMessages.get("Unvote"), this, Action.UNVOTE);
        btnUnvote.setName("btnUnvote");

        labelSelectedDelegate = new JLabel(GUIMessages.get("PleaseSelectDelegate"));
        labelSelectedDelegate.setName("SelectedDelegateLabel");
        labelSelectedDelegate.setForeground(Color.DARK_GRAY);
        labelSelectedDelegate.setHorizontalAlignment(JLabel.LEFT);

        // @formatter:off
        GroupLayout groupLayout2 = new GroupLayout(votePanel);
        groupLayout2.setHorizontalGroup(
            groupLayout2.createParallelGroup(Alignment.LEADING)
                .addGroup(groupLayout2.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(labelSelectedDelegate))
                .addGroup(groupLayout2.createSequentialGroup()
                    .addContainerGap()
                    .addGroup(groupLayout2.createParallelGroup(Alignment.LEADING)
                        .addComponent(textUnvote, 0, 0, Short.MAX_VALUE)
                        .addComponent(textVote, GroupLayout.DEFAULT_SIZE, 90, Short.MAX_VALUE))
                    .addPreferredGap(ComponentPlacement.UNRELATED)
                    .addGroup(groupLayout2.createParallelGroup(Alignment.TRAILING, false)
                        .addComponent(btnVote, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnUnvote, GroupLayout.DEFAULT_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addContainerGap())
        );
        groupLayout2.setVerticalGroup(
            groupLayout2.createParallelGroup(Alignment.LEADING)
                .addGroup(groupLayout2.createSequentialGroup()
                    .addGap(16)
                    .addGroup(groupLayout2.createParallelGroup(Alignment.BASELINE)
                        .addComponent(labelSelectedDelegate))
                    .addGap(16)
                    .addGroup(groupLayout2.createParallelGroup(Alignment.BASELINE)
                        .addComponent(btnVote)
                        .addComponent(textVote, GroupLayout.PREFERRED_SIZE, 25, GroupLayout.PREFERRED_SIZE))
                    .addGap(16)
                    .addGroup(groupLayout2.createParallelGroup(Alignment.BASELINE)
                        .addComponent(textUnvote, GroupLayout.PREFERRED_SIZE, 25, GroupLayout.PREFERRED_SIZE)
                        .addComponent(btnUnvote)))
        );
        votePanel.setLayout(groupLayout2);
        // @formatter:on

        JButton btnDelegate = SwingUtil
                .createDefaultButton(GUIMessages.get("RegisterAsDelegate"), this, Action.DELEGATE);
        btnDelegate.setName("btnDelegate");
        btnDelegate.setToolTipText(
                GUIMessages.get("RegisterAsDelegateToolTip", SwingUtil.formatValue(config.minDelegateBurnAmount())));

        textName = SwingUtil.textFieldWithCopyPastePopup();

        textName.setToolTipText(GUIMessages.get("DelegateName"));
        textName.setName("textName");

        textName.setColumns(10);
        textName.setActionCommand(Action.DELEGATE.name());
        textName.addActionListener(this);

        // @formatter:off
        GroupLayout groupLayout3 = new GroupLayout(delegateRegistrationPanel);
        groupLayout3.setHorizontalGroup(
            groupLayout3.createParallelGroup(Alignment.TRAILING)
                .addGroup(groupLayout3.createSequentialGroup()
                    .addContainerGap()
                    .addGroup(groupLayout3.createParallelGroup(Alignment.LEADING)
                        .addComponent(textName, GroupLayout.DEFAULT_SIZE, 174, Short.MAX_VALUE)
                        .addComponent(btnDelegate, Alignment.TRAILING, GroupLayout.DEFAULT_SIZE, 174, Short.MAX_VALUE))
                    .addContainerGap())
         );
        groupLayout3.setVerticalGroup(
            groupLayout3.createParallelGroup(Alignment.LEADING)
                .addGroup(groupLayout3.createSequentialGroup()
                    .addGap(16)
                    .addComponent(textName, GroupLayout.PREFERRED_SIZE, 25, GroupLayout.PREFERRED_SIZE)
                    .addGap(16)
                    .addComponent(btnDelegate))
        );
        delegateRegistrationPanel.setLayout(groupLayout3);
        // @formatter:on

        refreshAccounts();
        refreshDelegates();
    }

    class DelegatesTableModel extends AbstractTableModel {

        private static final long serialVersionUID = 1L;

        private final String[] columnNames = { GUIMessages.get("Rank"), GUIMessages.get("Name"),
                GUIMessages.get("Address"), GUIMessages.get("Votes"), GUIMessages.get("VotesFromMe"),
                GUIMessages.get("Status"), GUIMessages.get("Rate") };

        private transient List<WalletDelegate> delegates;

        public DelegatesTableModel() {
            this.delegates = Collections.emptyList();
        }

        public void setData(List<WalletDelegate> delegates) {
            this.delegates = delegates;
            this.fireTableDataChanged();
        }

        public WalletDelegate getRow(int row) {
            if (row >= 0 && row < delegates.size()) {
                return delegates.get(row);
            }

            return null;
        }

        @Override
        public int getRowCount() {
            return delegates.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            WalletDelegate d = delegates.get(row);

            switch (column) {
            case 0:
                return SwingUtil.formatNumber(row + 1);
            case 1:
                return d.getNameString();
            case 2:
                return Hex.PREF + d.getAddressString();
            case 3:
                return SwingUtil.formatVote(d.getVotes());
            case 4:
                return SwingUtil.formatVote(d.getVotesFromMe());
            case 5:
                return d.isValidator(kernel) ? "V" : "S";
            case 6:
                return SwingUtil.formatPercentage(d.getRate());
            default:
                return null;
            }
        }
    }

    @Override
    public synchronized void actionPerformed(ActionEvent e) {
        Action action = Action.valueOf(e.getActionCommand());

        switch (action) {
        case REFRESH:
            refreshAccounts();
            refreshDelegates();
            break;
        case SELECT_ACCOUNT:
            refreshDelegates();
            break;
        case VOTE:
        case UNVOTE:
            voteOrUnvote(action);
            break;
        case DELEGATE:
            delegate();
            break;
        default:
            throw new UnreachableException();
        }
    }

    /**
     * Refreshes account list.
     */
    protected void refreshAccounts() {
        List<WalletAccount> list = model.getAccounts();

        // quit if no update
        boolean match = selectFrom.getItemCount() == list.size();
        if (match) {
            for (int i = 0; i < list.size(); i++) {
                if (!Arrays.equals(selectFrom.getItemAt(i).account.getAddress(), list.get(i).getAddress())) {
                    match = false;
                    break;
                }
            }
        }

        if (!match) {
            // record selected account
            Item selected = (Item) selectFrom.getSelectedItem();

            // update account list
            selectFrom.removeAllItems();
            for (int i = 0; i < list.size(); i++) {
                selectFrom.addItem(new Item(list.get(i), i));
            }

            // recover selected account
            if (selected != null) {
                for (int i = 0; i < list.size(); i++) {
                    if (Arrays.equals(list.get(i).getAddress(), selected.account.getAddress())) {
                        selectFrom.setSelectedIndex(i);
                        break;
                    }
                }
            }
        }
    }

    /**
     * Refreshes delegate list.
     */
    protected void refreshDelegates() {
        List<WalletDelegate> delegates = model.getDelegates();
        delegates.sort((d1, d2) -> {
            int c = Long.compare(d2.getVotes(), d1.getVotes());
            return c != 0 ? c : d1.getNameString().compareTo(d2.getNameString());
        });

        WalletAccount acc = getSelectedAccount();
        if (acc != null) {
            byte[] voter = acc.getKey().toAddress();
            Blockchain chain = kernel.getBlockchain();
            DelegateState ds = chain.getDelegateState();
            for (WalletDelegate wd : delegates) {
                long vote = ds.getVote(voter, wd.getAddress());
                wd.setVotesFromMe(vote);

                ValidatorStats s = chain.getValidatorStats(wd.getAddress());
                wd.setNumberOfBlocksForged(s.getBlocksForged());
                wd.setNumberOfTurnsHit(s.getTurnsHit());
                wd.setNumberOfTurnsMissed(s.getTurnsMissed());
            }
        }

        /*
         * update table model
         */
        Delegate d = getSelectedDelegate();
        tableModel.setData(delegates);

        if (d != null) {
            for (int i = 0; i < delegates.size(); i++) {
                if (Arrays.equals(d.getAddress(), delegates.get(i).getAddress())) {
                    table.setRowSelectionInterval(table.convertRowIndexToView(i), table.convertRowIndexToView(i));
                    break;
                }
            }
        }
    }

    /**
     * Handles vote or unvote.
     * 
     * @param action
     */
    protected void voteOrUnvote(Action action) {
        WalletAccount a = getSelectedAccount();
        WalletDelegate d = getSelectedDelegate();
        String v = action.equals(Action.VOTE) ? textVote.getText() : textUnvote.getText();
        long value;
        try {
            value = SwingUtil.parseValue(v);
        } catch (ParseException ex) {
            JOptionPane.showMessageDialog(this, GUIMessages.get("EnterValidNumberOfVotes"));
            return;
        }
        long fee = config.minTransactionFee();

        if (a == null) {
            JOptionPane.showMessageDialog(this, GUIMessages.get("SelectAccount"));
        } else if (d == null) {
            JOptionPane.showMessageDialog(this, GUIMessages.get("SelectDelegate"));
        } else if (value <= 0) {
            JOptionPane.showMessageDialog(this, GUIMessages.get("EnterValidNumberOfVotes"));
        } else {
            if (action == Action.VOTE) {
                if (value + fee > a.getAvailable()) {
                    JOptionPane.showMessageDialog(this,
                            GUIMessages.get("InsufficientFunds", SwingUtil.formatValue(value + fee)));
                    return;
                }

                if (a.getAvailable() - fee - value < fee) {
                    int ret = JOptionPane.showConfirmDialog(this, GUIMessages.get("NotEnoughBalanceToUnvote"),
                            GUIMessages.get("ConfirmDelegateRegistration"), JOptionPane.YES_NO_OPTION);
                    if (ret != JOptionPane.YES_OPTION) {
                        return;
                    }
                }
            } else {
                if (fee > a.getAvailable()) {
                    JOptionPane.showMessageDialog(this,
                            GUIMessages.get("InsufficientFunds", SwingUtil.formatValue(fee)));
                    return;
                }

                if (value > a.getLocked()) {
                    JOptionPane.showMessageDialog(this,
                            GUIMessages.get("InsufficientLockedFunds", SwingUtil.formatValue(value)));
                    return;
                }
            }

            PendingManager pendingMgr = kernel.getPendingManager();

            byte networkId = kernel.getConfig().networkId();
            TransactionType type = action.equals(Action.VOTE) ? TransactionType.VOTE : TransactionType.UNVOTE;
            byte[] fromAddress = a.getKey().toAddress();
            byte[] toAddress = d.getAddress();
            long nonce = pendingMgr.getNonce(fromAddress);
            long timestamp = System.currentTimeMillis();
            byte[] data = {};
            Transaction tx = new Transaction(networkId, type, toAddress, value, fee, nonce, timestamp, data);
            tx.sign(a.getKey());

            sendTransaction(pendingMgr, tx);
        }
    }

    /**
     * Handles delegate registration.
     */
    protected void delegate() {
        WalletAccount a = getSelectedAccount();
        String name = textName.getText();
        if (a == null) {
            JOptionPane.showMessageDialog(this, GUIMessages.get("SelectAccount"));
        } else if (!name.matches("[_a-z0-9]{3,16}")) {
            JOptionPane.showMessageDialog(this, GUIMessages.get("AccountNameError"));
        } else if (a.getAvailable() < config.minDelegateBurnAmount() + config.minTransactionFee()) {
            JOptionPane.showMessageDialog(this, GUIMessages.get("InsufficientFunds",
                    SwingUtil.formatValue(config.minDelegateBurnAmount() + config.minTransactionFee())));
        } else {
            // confirm system requirements
            if ((config.networkId() == Constants.MAIN_NET_ID && !SystemUtil.bench()) && JOptionPane
                    .showConfirmDialog(this, GUIMessages.get("ComputerNotQualified"),
                            GUIMessages.get("ConfirmDelegateRegistration"),
                            JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                return;
            }

            // confirm burning amount
            if (JOptionPane.showConfirmDialog(this,
                    GUIMessages.get("DelegateRegistrationInfo", SwingUtil.formatValue(config.minDelegateBurnAmount())),
                    GUIMessages.get("ConfirmDelegateRegistration"),
                    JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                return;
            }

            PendingManager pendingMgr = kernel.getPendingManager();

            byte networkId = kernel.getConfig().networkId();
            TransactionType type = TransactionType.DELEGATE;
            byte[] to = Bytes.EMPTY_ADDRESS;
            long value = config.minDelegateBurnAmount();
            long fee = config.minTransactionFee();
            long nonce = pendingMgr.getNonce(a.getAddress());
            long timestamp = System.currentTimeMillis();
            byte[] data = Bytes.of(name);
            Transaction tx = new Transaction(networkId, type, to, value, fee, nonce, timestamp, data).sign(a.getKey());

            sendTransaction(pendingMgr, tx);
        }
    }

    /**
     * Adds a transaction to the pending manager.
     * 
     * @param pendingMgr
     * @param tx
     */
    protected void sendTransaction(PendingManager pendingMgr, Transaction tx) {
        PendingManager.ProcessTransactionResult result = pendingMgr.addTransactionSync(tx);
        if (result.error == null) {
            JOptionPane.showMessageDialog(this, GUIMessages.get("TransactionSent", 30),
                    GUIMessages.get("SuccessDialogTitle"), JOptionPane.INFORMATION_MESSAGE);
            clear();
        } else {
            JOptionPane.showMessageDialog(this, GUIMessages.get("TransactionFailed", result.error.toString()),
                    GUIMessages.get("ErrorDialogTitle"), JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Updates the selected delegate label.
     */
    protected void updateSelectedDelegateLabel() {
        Delegate d = getSelectedDelegate();
        if (d != null) {
            labelSelectedDelegate.setText(GUIMessages.get("SelectedDelegate", d.getNameString()));
        }
    }

    /**
     * Returns the selected account.
     * 
     * @return
     */
    protected WalletAccount getSelectedAccount() {
        int idx = selectFrom.getSelectedIndex();
        return (idx == -1) ? null : model.getAccounts().get(idx);
    }

    /**
     * Returns the selected delegate.
     * 
     * @return
     */
    protected WalletDelegate getSelectedDelegate() {
        int row = table.getSelectedRow();
        return (row == -1) ? null : tableModel.getRow(table.convertRowIndexToModel(row));
    }

    /**
     * Clears all input fields
     */
    protected void clear() {
        textVote.setText("");
        textUnvote.setText("");
        textName.setText("");
    }

    /**
     * Represents an item in the account drop list.
     */
    protected static class Item {
        WalletAccount account;
        String name;

        public Item(WalletAccount a, int idx) {
            this.account = a;
            this.name = GUIMessages.get("AccountNumShort", idx) + ", " + SwingUtil.formatValue(account.getAvailable());
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

}
