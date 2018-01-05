package org.semux.gui;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.ParseException;
import org.semux.Launcher;
import org.semux.cli.SemuxOption;
import org.semux.config.AbstractConfig;
import org.semux.message.CLIMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * Check if configuration files exist, if not copy them from defaults
 * Then load SemuxGUI.
 */
public class ApplicationLauncher extends Launcher
{
    private static final Logger logger = LoggerFactory.getLogger(ApplicationLauncher.class);
    public static final String DEFAULT_PROPERTIES = "default.properties";

    public static void main(String[] args) throws ParseException, IOException, InterruptedException
    {
        ApplicationLauncher launcher = new ApplicationLauncher();
        launcher.checkConfiguration(args);

        //launch app
        SemuxGUI.main(args);
    }

    public ApplicationLauncher()
    {
        Option dataDirOption = Option.builder()
            .longOpt(SemuxOption.DATA_DIR.toString())
            .desc(CLIMessages.get("SpecifyDataDir"))
            .hasArg(true).numberOfArgs(1).optionalArg(false).argName("path").type(String.class)
            .build();
        addOption(dataDirOption);
    }

    /**
     * Check that the expected directory structure and configuration file exists
     * If not, create them.
     *
     * @param args args
     * @throws ParseException
     * @throws IOException
     */
    private void checkConfiguration(String[] args) throws ParseException, IOException
    {
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(getOptions(), args);

        String configurationDirPath = getDataDirPath(cmd) + File.separator + "config";

        //check if config directory exists, if not, create directories.
        File configurationDir = new File(configurationDirPath);
        if (!configurationDir.exists() && !configurationDir.mkdirs())
        {
            throw new ParseException("Unable to create config directory.");
        }

        //check if config directory exists, and create if necessary
        createConfigFileIfNotExists(configurationDir);
    }

    private void createConfigFileIfNotExists(File configurationDir) throws ParseException
    {
        // get the configuration properties
        File configuration = new File(configurationDir.getAbsolutePath() + File.separator + AbstractConfig.CONFIG_FILE);
        if (!configuration.exists())
        {
            ClassLoader classLoader = getClass().getClassLoader();
            InputStream defaultProperty = classLoader.getResourceAsStream(DEFAULT_PROPERTIES);

            //copy the default configuration
            try
            {
                Files.copy(defaultProperty, configuration.toPath());
            }
            catch (IOException e)
            {
                logger.error("Unable to copy default properties.", e);
            }
        }
    }

    private String getDataDirPath(CommandLine cmd)
    {
        String dataDirPath = ".";

        //check if datadir is overridden
        if (cmd.hasOption(SemuxOption.DATA_DIR.toString()))
        {
            dataDirPath = cmd.getOptionValue(SemuxOption.DATA_DIR.toString());
        }

        dataDirPath = replaceShellExpansions(dataDirPath);
        return dataDirPath;
    }
}
