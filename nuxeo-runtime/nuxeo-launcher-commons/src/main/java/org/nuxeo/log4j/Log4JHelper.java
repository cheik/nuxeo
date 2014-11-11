/*
 * (C) Copyright 2011 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Julien Carsique
 *
 */

package org.nuxeo.log4j;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Enumeration;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Appender;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Hierarchy;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.DefaultRepositorySelector;
import org.apache.log4j.spi.Filter;
import org.apache.log4j.spi.LoggerRepository;
import org.apache.log4j.spi.RootLogger;
import org.apache.log4j.varia.LevelRangeFilter;
import org.apache.log4j.xml.DOMConfigurator;

/**
 * Provides helper methods for working with log4j
 *
 * @author jcarsique
 * @since 5.4.2
 */
public class Log4JHelper {
    private static final Log log = LogFactory.getLog(Log4JHelper.class);

    public static final String CONSOLE_APPENDER_NAME = "CONSOLE";

    protected static final String FULL_PATTERN_LAYOUT = "%d{HH:mm:ss,SSS} %-5p [%C{1}] %m%n";

    protected static final String LIGHT_PATTERN_LAYOUT = "%m%n";

    /**
     * Returns list of files produced by {@link FileAppender}s defined in a
     * given {@link LoggerRepository}. There's no need for the log4j
     * configuration corresponding to this repository of being active.
     *
     * @param loggerRepository {@link LoggerRepository} to browse looking for
     *            {@link FileAppender}
     * @return {@link FileAppender}s configured in loggerRepository
     */
    public static ArrayList<String> getFileAppendersFiles(
            LoggerRepository loggerRepository) {
        ArrayList<String> logFiles = new ArrayList<String>();
        for (@SuppressWarnings("unchecked")
        Enumeration<Appender> appenders = loggerRepository.getRootLogger().getAllAppenders(); appenders.hasMoreElements();) {
            Appender appender = appenders.nextElement();
            if (appender instanceof FileAppender) {
                FileAppender fileAppender = (FileAppender) appender;
                logFiles.add(fileAppender.getFile());
            }
        }
        return logFiles;
    }

    /**
     * Creates a {@link LoggerRepository} initialized with given log4j
     * configuration file without making this configuration active.
     *
     * @param log4jConfigurationFile XML Log4J configuration file to load.
     * @return {@link LoggerRepository} initialized with log4jConfigurationFile
     */
    public static LoggerRepository getNewLoggerRepository(
            File log4jConfigurationFile) {
        LoggerRepository loggerRepository = null;
        try {
            loggerRepository = new DefaultRepositorySelector(new Hierarchy(
                    new RootLogger(Level.DEBUG))).getLoggerRepository();
            new DOMConfigurator().doConfigure(
                    log4jConfigurationFile.toURI().toURL(), loggerRepository);
            log.debug("Log4j configuration " + log4jConfigurationFile
                    + " successfully loaded.");
        } catch (MalformedURLException e) {
            log.error("Could not load " + log4jConfigurationFile, e);
        }
        return loggerRepository;
    }

    /**
     * @see #getFileAppendersFiles(LoggerRepository)
     * @param log4jConfigurationFile
     * @return {@link FileAppender}s defined in log4jConfigurationFile
     */
    public static ArrayList<String> getFileAppendersFiles(
            File log4jConfigurationFile) {
        return getFileAppendersFiles(getNewLoggerRepository(log4jConfigurationFile));
    }

    /**
     * @since 5.5
     * @param category Log4J category for which to switch debug log level
     * @param debug set debug log level to true or false
     */
    public static void setDebug(String category, boolean debug) {
        Appender consoleAppender = Logger.getLogger(category).getAppender(
                CONSOLE_APPENDER_NAME);
        if (debug) {
            Logger.getLogger(category).setLevel(Level.DEBUG);
            if (consoleAppender != null) {
                consoleAppender.setLayout(new PatternLayout(FULL_PATTERN_LAYOUT));
            }
            log.info("Log level set to DEBUG for: " + category);
        } else {
            if (consoleAppender != null) {
                consoleAppender.setLayout(new PatternLayout(
                        LIGHT_PATTERN_LAYOUT));
            }
            Logger.getLogger(category).setLevel(Level.INFO);
            log.info("Log level reset to INFO for: " + category);
        }
    }

    /**
     * Set "quiet" mode: set log level to WARN for the given Log4J appender.
     *
     * @param appenderName Log4J appender to switch to WARN
     * @since 5.5
     */
    public static void setQuiet(String appenderName) {
        Appender appender = Logger.getRootLogger().getAppender(appenderName);
        if (appender == null) {
            return;
        }
        Filter filter = appender.getFilter();
        while (filter != null && !(filter instanceof LevelRangeFilter)) {
            filter = filter.getNext();
        }
        if (filter != null) {
            LevelRangeFilter levelRangeFilter = (LevelRangeFilter) filter;
            levelRangeFilter.setLevelMin(Level.WARN);
            log.debug("Log level filter set to WARN for appender "
                    + appenderName);
        }
    }

}
