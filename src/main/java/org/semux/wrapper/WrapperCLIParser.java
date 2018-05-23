/**
 * Copyright (c) 2017-2018 The Semux Developers
 *
 * Distributed under the MIT software license, see the accompanying file
 * LICENSE or https://opensource.org/licenses/mit-license.php
 */
package org.semux.wrapper;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

class WrapperCLIParser {

    final Wrapper.Mode mode;

    final String[] jvmOptions;

    final String[] remainingArgs;

    WrapperCLIParser(String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine = parser.parse(buildOptions(), args, true);

        this.mode = parseMode(commandLine);
        this.jvmOptions = parseJvmOptions(commandLine);
        this.remainingArgs = commandLine.getArgs();
    }

    /**
     * Parses the `--jvm-options` option.
     * 
     * @param commandLine
     * @return
     */
    protected String[] parseJvmOptions(CommandLine commandLine) {
        if (commandLine.hasOption("jvmoptions")) {
            String option = commandLine.getOptionValue("jvmoptions");
            if (!option.isEmpty()) {
                return option.split(" ");
            }
        }

        return new String[0];
    }

    /**
     * Parses the running mode, either `--gui` or `--cli`, defaults to GUI.
     * 
     * @param commandLine
     * @return
     */
    protected Wrapper.Mode parseMode(CommandLine commandLine) {
        return commandLine.hasOption("cli") ? Wrapper.Mode.CLI : Wrapper.Mode.GUI;
    }

    /**
     * Constructs the allowed options.
     * 
     * @return
     */
    protected Options buildOptions() {
        Options options = new Options();

        options.addOption(Option.builder().longOpt("jvmoptions").hasArg(true).type(String.class).build());

        OptionGroup modeOption = new OptionGroup();
        modeOption.setRequired(false);
        Option guiMode = Option.builder().longOpt("gui").hasArg(false).build();
        modeOption.addOption(guiMode);
        Option cliMode = Option.builder().longOpt("cli").hasArg(false).build();
        modeOption.addOption(cliMode);
        options.addOptionGroup(modeOption);

        return options;
    }
}
