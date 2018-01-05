/**
 * Copyright (c) 2017 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package org.semux;

import java.io.File;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.semux.cli.SemuxOption;
import org.semux.config.Config;
import org.semux.config.Constants;
import org.semux.config.DevNetConfig;
import org.semux.config.MainNetConfig;
import org.semux.config.TestNetConfig;
import org.semux.log.LoggerConfigurator;

public abstract class Launcher {

    protected static final String DEVNET = "devnet";
    protected static final String TESTNET = "testnet";
    protected static final String MAINNET = "mainnet";
    public static final String USER_HOME = "user.home";

    private Options options = new Options();

    private String network = MAINNET;
    private int coinbase = 0;
    private String password = null;
    private String dataDir = Constants.DEFAULT_DATA_DIR;

    /**
     * Creates an instance of {@link Config} based on the given `--network` option.
     * <p>
     * Defaults to MainNet.
     *
     * @return the configuration
     */
    public Config getConfig() {
        switch (getNetwork()) {
        case TESTNET:
            return new TestNetConfig(getDataDir());
        case DEVNET:
            return new DevNetConfig(getDataDir());
        default:
            return new MainNetConfig(getDataDir());
        }
    }

    /**
     * Returns the network.
     *
     * @return
     */
    public String getNetwork() {
        return network;
    }

    /**
     * Returns the data directory.
     *
     * @return
     */
    public String getDataDir() {
        return dataDir;
    }

    /**
     * Returns the coinbase.
     *
     * @return
     */
    public int getCoinbase() {
        return coinbase;
    }

    /**
     * Returns the provided password if any.
     *
     * @return
     */
    public String getPassword() {
        return password;
    }

    /**
     * Parses options from the given arguments.
     *
     * @param args
     * @return
     * @throws ParseException
     */
    protected CommandLine parseOptions(String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        return parser.parse(getOptions(), args);
    }

    /**
     * Set up customized logger configuration.
     *
     * @param args
     * @throws ParseException
     */
    protected void setupLogger(String[] args) throws ParseException {
        CommandLine cmd = parseOptions(args);
        LoggerConfigurator.configure(
                new File(cmd.hasOption(SemuxOption.DATA_DIR.name()) ? cmd.getOptionValue(SemuxOption.DATA_DIR.name())
                        : Constants.DEFAULT_DATA_DIR));
    }
    /**
     * support shell expansions of ~
     * @param directoryPath
     * @return
     */
    protected String replaceShellExpansions(String directoryPath)
    {
        if (directoryPath.startsWith("~" + File.separator))
        {
            directoryPath = System.getProperty(USER_HOME) + directoryPath.substring(1);
        }
        return directoryPath;
    }

    /**
     * Returns all supported options.
     *
     * @return
     */
    protected Options getOptions() {
        return options;
    }

    /**
     * Adds a supported option.
     *
     * @param option
     */
    protected void addOption(Option option) {
        options.addOption(option);
    }

    /**
     * Sets the network.
     *
     * @param network
     */
    protected void setNetwork(String network) {
        this.network = network;
    }

    /**
     * Sets the data directory.
     *
     * @param dataDir
     */
    protected void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    /**
     * Sets the coinbase.
     *
     * @param coinbase
     */
    protected void setCoinbase(int coinbase) {
        this.coinbase = coinbase;
    }

    /**
     * Sets the password.
     *
     * @param password
     */
    protected void setPassword(String password) {
        this.password = password;
    }

}
