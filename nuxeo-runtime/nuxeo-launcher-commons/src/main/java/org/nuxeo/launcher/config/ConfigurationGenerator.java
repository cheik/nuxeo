/*
 * (C) Copyright 2010-2011 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 * $Id$
 */

package org.nuxeo.launcher.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Logger;
import org.apache.log4j.helpers.NullEnumeration;
import org.nuxeo.log4j.Log4JHelper;

/**
 * Builder for server configuration and datasource files from templates and
 * properties.
 *
 * @author jcarsique
 */
public class ConfigurationGenerator {

    private static final Log log = LogFactory.getLog(ConfigurationGenerator.class);

    public static final String NUXEO_HOME = "nuxeo.home";

    public static final String NUXEO_CONF = "nuxeo.conf";

    protected static final String TEMPLATES = "templates";

    protected static final String NUXEO_DEFAULT_CONF = "nuxeo.defaults";

    /**
     * Absolute or relative PATH to the user chosen template
     *
     * @deprecated use {@link #PARAM_TEMPLATES_NAME} instead
     */
    @Deprecated
    public static final String PARAM_TEMPLATE_NAME = "nuxeo.template";

    /**
     * Absolute or relative PATH to the user chosen templates (comma separated
     * list)
     */
    public static final String PARAM_TEMPLATES_NAME = "nuxeo.templates";

    public static final String PARAM_TEMPLATE_DBNAME = "nuxeo.dbtemplate";

    public static final String PARAM_TEMPLATES_NODB = "nuxeo.nodbtemplates";

    public static final String PARAM_TEMPLATES_PARSING_EXTENSIONS = "nuxeo.templates.parsing.extensions";

    /**
     * Absolute or relative PATH to the included templates (comma separated
     * list)
     */
    protected static final String PARAM_INCLUDED_TEMPLATES = "nuxeo.template.includes";

    public static final String PARAM_FORCE_GENERATION = "nuxeo.force.generation";

    public static final String BOUNDARY_BEGIN = "### BEGIN - DO NOT EDIT BETWEEN BEGIN AND END ###";

    public static final String BOUNDARY_END = "### END - DO NOT EDIT BETWEEN BEGIN AND END ###";

    public static final List<String> DB_LIST = Arrays.asList("default",
            "postgresql", "oracle", "mysql", "mssql");

    public static final String PARAM_WIZARD_DONE = "nuxeo.wizard.done";

    public static final String PARAM_WIZARD_RESTART_PARAMS = "wizard.restart.params";

    public static final String PARAM_FAKE_WINDOWS = "org.nuxeo.fake.vindoz";

    public static final String PARAM_LOOPBACK_URL = "nuxeo.loopback.url";

    public static final int MIN_PORT = 1;

    public static final int MAX_PORT = 65535;

    public static final int ADDRESS_PING_TIMEOUT = 1000;

    public static final String PARAM_BIND_ADDRESS = "nuxeo.bind.address";

    public static final String PARAM_HTTP_PORT = "nuxeo.server.http.port";

    public static final String PARAM_STATUS_KEY = "server.status.key";

    public static final String PARAM_CONTEXT_PATH = "org.nuxeo.ecm.contextPath";

    public static final String INSTALL_AFTER_RESTART = "installAfterRestart.log";

    private final File nuxeoHome;

    // User configuration file
    private final File nuxeoConf;

    // Chosen templates
    private final List<File> includedTemplates = new ArrayList<File>();

    // Common default configuration file
    private File nuxeoDefaultConf;

    public boolean isJBoss;

    public boolean isJetty;

    public boolean isTomcat;

    private ServerConfigurator serverConfigurator;

    private boolean forceGeneration;

    private Properties defaultConfig;

    private Properties userConfig;

    private boolean configurable = false;

    private boolean onceGeneration = false;

    private String templates;

    // if PARAM_FORCE_GENERATION=once, set to false; else keep current value
    private boolean setOnceToFalse = true;

    // if PARAM_FORCE_GENERATION=false, set to once; else keep the current value
    private boolean setFalseToOnce = false;

    public boolean isConfigurable() {
        return configurable;
    }

    public ConfigurationGenerator() {
        String nuxeoHomePath = System.getProperty(NUXEO_HOME);
        String nuxeoConfPath = System.getProperty(NUXEO_CONF);
        if (nuxeoHomePath != null) {
            nuxeoHome = new File(nuxeoHomePath);
        } else {
            File userDir = new File(System.getProperty("user.dir"));
            if ("bin".equalsIgnoreCase(userDir.getName())) {
                nuxeoHome = userDir.getParentFile();
            } else {
                nuxeoHome = userDir;
            }
        }
        if (nuxeoConfPath != null) {
            nuxeoConf = new File(nuxeoConfPath);
        } else {
            nuxeoConf = new File(nuxeoHome, "bin" + File.separator
                    + "nuxeo.conf");
        }
        nuxeoDefaultConf = new File(nuxeoHome, TEMPLATES + File.separator
                + NUXEO_DEFAULT_CONF);

        // detect server type based on System properties
        isJBoss = System.getProperty("jboss.home.dir") != null;
        isJetty = System.getProperty("jetty.home") != null;
        isTomcat = System.getProperty("tomcat.home") != null;
        if (!isJBoss && !isJetty && !isTomcat) {
            // fallback on jar detection
            isJBoss = new File(nuxeoHome, "bin/run.jar").exists();
            isTomcat = new File(nuxeoHome, "bin/bootstrap.jar").exists();
            String[] files = nuxeoHome.list();
            for (String file : files) {
                if (file.startsWith("nuxeo-runtime-launcher")) {
                    isJetty = true;
                    break;
                }
            }
        }
        if (isJBoss) {
            serverConfigurator = new JBossConfigurator(this);
        } else if (isTomcat) {
            serverConfigurator = new TomcatConfigurator(this);
        } else if (isJetty) {
            serverConfigurator = new JettyConfigurator(this);
        }
        if (Logger.getRootLogger().getAllAppenders() instanceof NullEnumeration) {
            serverConfigurator.initLogs();
        }
        log.info("Nuxeo home:          " + nuxeoHome.getPath());
        log.info("Nuxeo configuration: " + nuxeoConf.getPath());
    }

    /**
     * @see #PARAM_FORCE_GENERATION
     * @param forceGeneration
     */
    public void setForceGeneration(boolean forceGeneration) {
        this.forceGeneration = forceGeneration;
    }

    /**
     * @see #PARAM_FORCE_GENERATION
     * @return true if configuration will be generated from templates
     * @since 5.4.2
     */
    public boolean isForceGeneration() {
        return forceGeneration;
    }

    public Properties getUserConfig() {
        return userConfig;
    }

    /**
     * @since 5.4.2
     */
    public final ServerConfigurator getServerConfigurator() {
        return serverConfigurator;
    }

    /**
     * Runs the configuration files generation.
     */
    public void run() throws ConfigurationException {
        if (init()) {
            if (!serverConfigurator.isConfigured()) {
                log.info("No current configuration, generating files...");
                generateFiles();
            } else if (forceGeneration) {
                log.info("Configuration files generation (nuxeo.force.generation="
                        + userConfig.getProperty(PARAM_FORCE_GENERATION,
                                "false") + ")...");
                generateFiles();
            } else {
                log.info("Server already configured (set nuxeo.force.generation=true to force configuration files generation).");
            }
        }
    }

    /**
     * Initialize configurator, check requirements and load current
     * configuration
     *
     * @return returns true if current install is configurable, else returns
     *         false
     */
    public boolean init() {
        if (serverConfigurator == null) {
            log.warn("Unrecognized server. Considered as already configured.");
            configurable = false;
        } else if (!nuxeoConf.exists()) {
            log.info("Missing " + nuxeoConf);
            configurable = false;
        } else if (userConfig == null) {
            try {
                setBasicConfiguration();
                configurable = true;
            } catch (ConfigurationException e) {
                log.warn("Error reading basic configuration.", e);
                configurable = false;
            }
        } else {
            configurable = true;
        }
        return configurable;
    }

    public void changeTemplates(String newTemplates) {
        try {
            includedTemplates.clear();
            templates = newTemplates;
            setBasicConfiguration();
            configurable = true;
        } catch (ConfigurationException e) {
            log.warn("Error reading basic configuration.", e);
            configurable = false;
        }
    }

    /**
     * Change templates using given database template
     *
     * @param dbTemplate new database template
     * @since 5.4.2
     */
    public void changeDBTemplate(String dbTemplate) {
        changeTemplates(rebuildTemplatesStr(dbTemplate));
    }

    private void setBasicConfiguration() throws ConfigurationException {
        try {
            // Load default configuration
            defaultConfig = new Properties();
            defaultConfig.load(new FileInputStream(nuxeoDefaultConf));
            userConfig = new Properties(defaultConfig);

            // Add useful system properties
            userConfig.putAll(System.getProperties());

            // If Windows, replace backslashes in paths
            if (System.getProperty("os.name").toLowerCase().startsWith("win")) {
                replaceBackslashes();
            }
            // Load user configuration
            userConfig.load(new FileInputStream(nuxeoConf));
            onceGeneration = "once".equals(userConfig.getProperty(PARAM_FORCE_GENERATION));
            forceGeneration = onceGeneration
                    || Boolean.parseBoolean(userConfig.getProperty(
                            PARAM_FORCE_GENERATION, "false"));

            // Manage directories set from (or set to) system properties
            setDirectoryWithProperty(Environment.NUXEO_DATA_DIR);
            setDirectoryWithProperty(Environment.NUXEO_LOG_DIR);
            setDirectoryWithProperty(Environment.NUXEO_PID_DIR);
            setDirectoryWithProperty(Environment.NUXEO_TMP_DIR);
        } catch (NullPointerException e) {
            throw new ConfigurationException("Missing file", e);
        } catch (FileNotFoundException e) {
            throw new ConfigurationException("Missing file: "
                    + nuxeoDefaultConf + " or " + nuxeoConf, e);
        } catch (IOException e) {
            throw new ConfigurationException("Error reading " + nuxeoConf, e);
        }

        // Override default configuration with specific configuration(s) of
        // the chosen template(s) which can be outside of server filesystem
        try {
            if (templates == null) {
                templates = getUserTemplates();
            }
            extractDatabaseTemplateName();
            includeTemplates(templates);
        } catch (FileNotFoundException e) {
            throw new ConfigurationException("Missing file", e);
        } catch (IOException e) {
            throw new ConfigurationException("Error reading " + nuxeoConf, e);
        }

        Map<String, String> newParametersToSave = evalDynamicProperties();
        if (newParametersToSave != null && !newParametersToSave.isEmpty()) {
            saveConfiguration(newParametersToSave, false, false);
        }
    }

    /**
     * Generate properties which values are based on others
     *
     * @return Map with new parameters to save in {@code nuxeoConf}
     *
     * @throws ConfigurationException
     *
     * @since 5.5
     */
    protected HashMap<String, String> evalDynamicProperties()
            throws ConfigurationException {
        HashMap<String, String> newParametersToSave = new HashMap<String, String>();
        evalLoopbackURL();
        evalServerStatusKey(newParametersToSave);
        return newParametersToSave;
    }

    /**
     * Generate a server status key if not already set
     *
     * @param newParametersToSave
     *
     * @throws ConfigurationException
     *
     * @see #PARAM_STATUS_KEY
     * @since 5.5
     */
    private void evalServerStatusKey(Map<String, String> newParametersToSave)
            throws ConfigurationException {
        if (userConfig.getProperty(PARAM_STATUS_KEY) == null) {
            newParametersToSave.put(PARAM_STATUS_KEY,
                    UUID.randomUUID().toString().substring(0, 8));
        }
    }

    private void evalLoopbackURL() throws ConfigurationException {
        String loopbackURL = userConfig.getProperty(PARAM_LOOPBACK_URL);
        if (loopbackURL != null) {
            log.debug("Using configured loop back url: " + loopbackURL);
            return;
        }
        InetAddress bindAddress = getBindAddress();
        // Address and ports already checked by #checkAddressesAndPorts
        try {
            if (bindAddress.isAnyLocalAddress()) {
                boolean preferIPv6 = "false".equals(System.getProperty("java.net.preferIPv4Stack"))
                        && "true".equals(System.getProperty("java.net.preferIPv6Addresses"));
                bindAddress = preferIPv6 ? Inet6Address.getByName("::1")
                        : Inet4Address.getByName("127.0.0.1");
                log.debug("Bind address is \"ANY\", using local address instead: "
                        + bindAddress);
            }
        } catch (UnknownHostException e) {
            log.error(e);
        }

        String httpPort = userConfig.getProperty(PARAM_HTTP_PORT);
        String contextPath = userConfig.getProperty(PARAM_CONTEXT_PATH);
        // Is IPv6 or IPv4 ?
        if (bindAddress instanceof Inet6Address) {
            loopbackURL = "http://[" + bindAddress.getHostAddress() + "]:"
                    + httpPort + contextPath;
        } else {
            loopbackURL = "http://" + bindAddress.getHostAddress() + ":"
                    + httpPort + contextPath;
        }
        log.debug("Set as loop back URL: " + loopbackURL);
        userConfig.setProperty(PARAM_LOOPBACK_URL, loopbackURL);
    }

    /**
     * Read nuxeo.conf, replace backslashes in paths and write new
     * nuxeo.conf
     *
     * @throws ConfigurationException if any error reading or writing
     *             nuxeo.conf
     * @since 5.4.1
     */
    protected void replaceBackslashes() throws ConfigurationException {
        StringBuffer sb = new StringBuffer();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(nuxeoConf));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.matches(".*:\\\\.*")) {
                    line = line.replaceAll("\\\\", "/");
                }
                sb.append(line + System.getProperty("line.separator"));
            }
            reader.close();
        } catch (IOException e) {
            throw new ConfigurationException("Error reading " + nuxeoConf, e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    throw new ConfigurationException(e);
                }
            }
        }
        FileWriter writer = null;
        try {
            writer = new FileWriter(nuxeoConf, false);
            // Copy back file content
            writer.append(sb.toString());
        } catch (IOException e) {
            throw new ConfigurationException("Error writing in " + nuxeoConf, e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    throw new ConfigurationException(e);
                }
            }
        }
    }

    /**
     * @since 5.4.2
     * @param key Directory system key
     * @see Environment
     */
    public void setDirectoryWithProperty(String key) {
        String directory = userConfig.getProperty(key);
        if (directory == null) {
            userConfig.setProperty(key,
                    serverConfigurator.getDirectory(key).getPath());
        } else {
            serverConfigurator.setDirectory(key, directory);
        }
    }

    private String getUserTemplates() {
        String userTemplatesList = userConfig.getProperty(PARAM_TEMPLATES_NAME);
        if (userTemplatesList == null) {
            // backward compliance: manage parameter for a single
            // template
            userTemplatesList = userConfig.getProperty(PARAM_TEMPLATE_NAME);
        }
        if (userTemplatesList == null) {
            log.warn("No template found in configuration! Fallback on 'default'.");
            userTemplatesList = "default";
        }
        return userTemplatesList;
    }

    protected void generateFiles() throws ConfigurationException {
        try {
            serverConfigurator.parseAndCopy(userConfig);
            serverConfigurator.dumpProperties(userConfig);
            log.info("Configuration files generated.");
            // keep true or false, switch once to false
            if (onceGeneration) {
                setOnceToFalse = true;
                writeConfiguration(loadConfiguration());
            }
        } catch (FileNotFoundException e) {
            throw new ConfigurationException("Missing file", e);
        } catch (IOException e) {
            throw new ConfigurationException("Configuration failure", e);
        }
    }

    private StringBuffer loadConfiguration() throws ConfigurationException {
        return loadConfiguration(null);
    }

    private void writeConfiguration(StringBuffer configuration)
            throws ConfigurationException {
        writeConfiguration(configuration, null);
    }

    private void includeTemplates(String templatesList) throws IOException {
        StringTokenizer st = new StringTokenizer(templatesList, ",");
        while (st.hasMoreTokens()) {
            String nextToken = st.nextToken();
            File chosenTemplate = new File(nextToken);
            // is it absolute and existing or relative path ?
            if (!chosenTemplate.exists()
                    || !chosenTemplate.getPath().equals(
                            chosenTemplate.getAbsolutePath())) {
                chosenTemplate = new File(nuxeoDefaultConf.getParentFile(),
                        nextToken);
            }
            if (includedTemplates.contains(chosenTemplate)) {
                log.debug("Already included " + nextToken);
            } else if (chosenTemplate.exists()) {
                File chosenTemplateConf = new File(chosenTemplate,
                        NUXEO_DEFAULT_CONF);
                if (chosenTemplateConf.exists()) {
                    Properties subTemplateConf = new Properties();
                    subTemplateConf.load(new FileInputStream(chosenTemplateConf));
                    String subTemplatesList = subTemplateConf.getProperty(PARAM_INCLUDED_TEMPLATES);
                    if (subTemplatesList != null
                            && subTemplatesList.length() > 0) {
                        includeTemplates(subTemplatesList);
                    }
                    // Load configuration from chosen templates
                    defaultConfig.load(new FileInputStream(chosenTemplateConf));
                    log.info("Include template: " + chosenTemplate.getPath());
                } else {
                    log.debug("No default configuration for template "
                            + nextToken);
                }
                includedTemplates.add(chosenTemplate);
            } else {
                log.error(String.format(
                        "Template '%s' not found with relative or absolute path (%s). "
                                + "Check your %s parameter, and %s for included files.",
                        nextToken, chosenTemplate, PARAM_TEMPLATES_NAME,
                        PARAM_INCLUDED_TEMPLATES));
            }
        }
    }

    public File getNuxeoHome() {
        return nuxeoHome;
    }

    public File getNuxeoDefaultConf() {
        return nuxeoDefaultConf;
    }

    public List<File> getIncludedTemplates() {
        return includedTemplates;
    }

    public static void main(String[] args) throws ConfigurationException {
        new ConfigurationGenerator().run();
    }

    /**
     * Save changed parameters in {@code nuxeo.conf}.
     * This method does not check values in map. Use
     * {@link #saveFilteredConfiguration(Map)} for parameters filtering.
     *
     * @param changedParameters Map of modified parameters
     * @see #saveFilteredConfiguration(Map)
     */
    public void saveConfiguration(Map<String, String> changedParameters)
            throws ConfigurationException {
        // Keep generation true or once; switch false to once
        saveConfiguration(changedParameters, false, true);
    }

    /**
     * Save changed parameters in {@code nuxeo.conf}.
     * This method does not check values in map. Use
     * {@link #saveFilteredConfiguration(Map)} for parameters filtering.
     *
     * @param changedParameters Map of modified parameters
     * @param setGenerationOnceToFalse If generation was on (true or once), then
     *            set it to false or not?
     * @param setGenerationFalseToOnce If generation was off (false), then set
     *            it to once?
     * @see #saveFilteredConfiguration(Map)
     * @since 5.5
     */
    public void saveConfiguration(Map<String, String> changedParameters,
            boolean setGenerationOnceToFalse, boolean setGenerationFalseToOnce)
            throws ConfigurationException {
        this.setOnceToFalse = setGenerationOnceToFalse;
        this.setFalseToOnce = setGenerationFalseToOnce;
        writeConfiguration(loadConfiguration(changedParameters),
                changedParameters);
        for (String key : changedParameters.keySet()) {
            if (changedParameters.get(key) != null) {
                userConfig.setProperty(key, changedParameters.get(key));
            }
        }
    }

    /**
     * Save changed parameters in {@code nuxeo.conf}, filtering parameters with
     * {@link #getChangedParametersMap(Map, Map)} and calculating templates if
     * changedParameters contains a value for {@link #PARAM_TEMPLATE_DBNAME}
     *
     * @param changedParameters Maps of modified parameters
     * @since 5.4.2
     * @see #getChangedParameters(Map)
     */
    public void saveFilteredConfiguration(Map<String, String> changedParameters)
            throws ConfigurationException {
        String newDbTemplate = changedParameters.remove(PARAM_TEMPLATE_DBNAME);
        if (newDbTemplate != null) {
            changedParameters.put(PARAM_TEMPLATES_NAME,
                    rebuildTemplatesStr(newDbTemplate));
        }
        saveConfiguration(getChangedParameters(changedParameters));
    }

    /**
     * Filters given parameters including them only if (there was no previous
     * value and new value is not empty/null) or (there was a previous value and
     * it differs from the new value)
     *
     * @param changedParameters parameters to be filtered
     * @return filtered map
     * @since 5.4.2
     */
    public Map<String, String> getChangedParameters(
            Map<String, String> changedParameters) {
        Map<String, String> filteredChangedParameters = new HashMap<String, String>();
        for (String key : changedParameters.keySet()) {
            String oldParam = userConfig.getProperty(key);
            String newParam = changedParameters.get(key);
            if (newParam != null) {
                newParam = newParam.trim();
            }
            if (oldParam == null && newParam != null && !newParam.isEmpty()
                    || oldParam != null && !oldParam.trim().equals(newParam)) {
                filteredChangedParameters.put(key, newParam);
            }
        }
        return filteredChangedParameters;
    }

    private void writeConfiguration(StringBuffer newContent,
            Map<String, String> changedParameters)
            throws ConfigurationException {
        FileWriter writer = null;
        try {
            writer = new FileWriter(nuxeoConf, false);
            // Copy back file content
            writer.append(newContent.toString());
            if (changedParameters != null && !changedParameters.isEmpty()) {
                // Write changed parameters
                writer.write(BOUNDARY_BEGIN + " " + new Date().toString()
                        + System.getProperty("line.separator"));
                for (String key : changedParameters.keySet()) {
                    writer.write("#" + key + "="
                            + userConfig.getProperty(key, "")
                            + System.getProperty("line.separator"));
                    if (changedParameters.get(key) != null) {
                        writer.write(key + "=" + changedParameters.get(key)
                                + System.getProperty("line.separator"));
                    }
                }
                writer.write(BOUNDARY_END
                        + System.getProperty("line.separator"));
            }
        } catch (IOException e) {
            throw new ConfigurationException("Error writing in " + nuxeoConf, e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    throw new ConfigurationException(e);
                }
            }
        }
    }

    private StringBuffer loadConfiguration(Map<String, String> changedParameters)
            throws ConfigurationException {
        String wizardParam = null, templatesParam = null;
        Integer generationIndex = null, wizardIndex = null, templatesIndex = null;
        if (changedParameters != null) {
            // Will change wizardParam value instead of appending it
            wizardParam = changedParameters.remove(PARAM_WIZARD_DONE);
            // Will change templatesParam value instead of appending it
            templatesParam = changedParameters.remove(PARAM_TEMPLATES_NAME);
        }
        ArrayList<String> newLines = new ArrayList<String>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(nuxeoConf));
            String line;
            boolean onConfiguratorContent = false;
            while ((line = reader.readLine()) != null) {
                if (!onConfiguratorContent) {
                    if (!line.startsWith(BOUNDARY_BEGIN)) {
                        if (line.startsWith(PARAM_FORCE_GENERATION)) {
                            if (setOnceToFalse && onceGeneration) {
                                line = PARAM_FORCE_GENERATION + "=false";
                            }
                            if (setFalseToOnce && !forceGeneration) {
                                line = PARAM_FORCE_GENERATION + "=once";
                            }
                            if (generationIndex == null) {
                                newLines.add(line);
                                generationIndex = newLines.size() - 1;
                            } else {
                                newLines.set(generationIndex, line);
                            }
                        } else if (line.startsWith(PARAM_WIZARD_DONE)) {
                            if (wizardParam != null) {
                                line = PARAM_WIZARD_DONE + "=" + wizardParam;
                            }
                            if (wizardIndex == null) {
                                newLines.add(line);
                                wizardIndex = newLines.size() - 1;
                            } else {
                                newLines.set(wizardIndex, line);
                            }
                        } else if (line.startsWith(PARAM_TEMPLATES_NAME)) {
                            if (templatesParam != null) {
                                line = PARAM_TEMPLATES_NAME + "="
                                        + templatesParam;
                            }
                            if (templatesIndex == null) {
                                newLines.add(line);
                                templatesIndex = newLines.size() - 1;
                            } else {
                                newLines.set(templatesIndex, line);
                            }
                        } else {
                            newLines.add(line);
                        }
                    } else {
                        // What must be written just before the BOUNDARY_BEGIN
                        if (templatesIndex == null && templatesParam != null) {
                            newLines.add(PARAM_TEMPLATES_NAME + "="
                                    + templatesParam);
                            templatesIndex = newLines.size() - 1;
                        }
                        if (wizardIndex == null && wizardParam != null) {
                            newLines.add(PARAM_WIZARD_DONE + "=" + wizardParam);
                            wizardIndex = newLines.size() - 1;
                        }
                        onConfiguratorContent = true;
                    }
                } else {
                    if (!line.startsWith(BOUNDARY_END)) {
                        if (changedParameters == null) {
                            newLines.add(line);
                        } else {
                            int equalIdx = line.indexOf("=");
                            if (line.startsWith("#" + PARAM_TEMPLATES_NAME)
                                    || line.startsWith(PARAM_TEMPLATES_NAME)) {
                                // Backward compliance, it must be ignored
                                continue;
                            }
                            if (line.trim().startsWith("#")) {
                                String key = line.substring(1, equalIdx).trim();
                                String value = line.substring(equalIdx + 1).trim();
                                userConfig.setProperty(key, value);
                            } else {
                                String key = line.substring(0, equalIdx).trim();
                                String value = line.substring(equalIdx + 1).trim();
                                if (!changedParameters.containsKey(key)) {
                                    changedParameters.put(key, value);
                                } else if (!value.equals(changedParameters.get(key))) {
                                    userConfig.setProperty(key, value);
                                }
                            }
                        }
                    } else {
                        onConfiguratorContent = false;
                    }
                }
            }
            reader.close();
        } catch (IOException e) {
            throw new ConfigurationException("Error reading " + nuxeoConf, e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    throw new ConfigurationException(e);
                }
            }
        }
        StringBuffer newContent = new StringBuffer();
        for (int i = 0; i < newLines.size(); i++) {
            newContent.append(newLines.get(i).trim()
                    + System.getProperty("line.separator"));
        }
        return newContent;
    }

    /**
     * Extract a database template from a list of templates.
     * Return the last one if there are multiples
     *
     * @see #rebuildTemplatesStr(String)
     */
    public String extractDatabaseTemplateName() {
        String dbTemplate = "unknown";
        String nodbTemplates = "";
        StringTokenizer st = new StringTokenizer(templates, ",");
        while (st.hasMoreTokens()) {
            String template = st.nextToken();
            if (DB_LIST.contains(template)) {
                dbTemplate = template;
            } else {
                nodbTemplates += template;
            }
        }
        userConfig.put(PARAM_TEMPLATES_NODB, nodbTemplates);
        userConfig.put(PARAM_TEMPLATE_DBNAME, dbTemplate);
        return dbTemplate;
    }

    /**
     * @return nuxeo.conf file used
     */
    public File getNuxeoConf() {
        return nuxeoConf;
    }

    /**
     * Delegate logs initialization to serverConfigurator instance
     *
     * @since 5.4.2
     */
    public void initLogs() {
        serverConfigurator.initLogs();
    }

    /**
     * @return log directory
     * @since 5.4.2
     */
    public File getLogDir() {
        return serverConfigurator.getLogDir();
    }

    /**
     * @return pid directory
     * @since 5.4.2
     */
    public File getPidDir() {
        return serverConfigurator.getPidDir();
    }

    /**
     * @return Data directory
     * @since 5.4.2
     */
    public File getDataDir() {
        return serverConfigurator.getDataDir();
    }

    /**
     * Create needed directories.
     * Check existence of old paths.
     * If old paths have been found and they cannot be upgraded automatically,
     * then upgrading message is logged and error thrown.
     *
     * @throws ConfigurationException If a deprecated directory has been
     *             detected.
     *
     * @since 5.4.2
     */
    public void verifyInstallation() throws ConfigurationException {
        String version = System.getProperty("java.version");
        if (!version.startsWith("1.6") && !version.startsWith("1.7")) {
            String message = "Nuxeo requires Java 6 or 7 (detected " + version
                    + ").";
            if ("nofail".equalsIgnoreCase(System.getProperty("jvmcheck", "fail"))) {
                log.error(message);
            } else {
                throw new ConfigurationException(message);
            }
        }
        ifNotExistsAndIsDirectoryThenCreate(getLogDir());
        ifNotExistsAndIsDirectoryThenCreate(getPidDir());
        ifNotExistsAndIsDirectoryThenCreate(getDataDir());
        ifNotExistsAndIsDirectoryThenCreate(getTmpDir());
        serverConfigurator.checkPaths();
        serverConfigurator.removeExistingLocks();
        checkAddressesAndPorts();
    }

    /**
     * Will check the configured addresses are reachable and Nuxeo required
     * ports are available on those addresses.
     * Server specific implementations should override this method in order to
     * check for server specific ports. {@link #bindAddress} must be set
     * before.
     *
     * @throws ConfigurationException
     *
     * @since 5.5
     */
    public void checkAddressesAndPorts() throws ConfigurationException {
        InetAddress bindAddress = getBindAddress();
        // Sanity check
        if (bindAddress.isMulticastAddress()) {
            throw new ConfigurationException("Multicast address won't work: "
                    + bindAddress);
        }
        checkAddressReachable(bindAddress);
        checkPortAvailable(bindAddress,
                Integer.parseInt(userConfig.getProperty(PARAM_HTTP_PORT)));
    }

    private InetAddress getBindAddress() throws ConfigurationException {
        InetAddress bindAddress;
        try {
            bindAddress = InetAddress.getByName(userConfig.getProperty(PARAM_BIND_ADDRESS));
            log.debug("Configured bind address: " + bindAddress);
        } catch (UnknownHostException e) {
            throw new ConfigurationException(e);
        }
        return bindAddress;
    }

    /**
     * @param address address to check for availability
     * @throws ConfigurationException
     * @since 5.5
     */
    public static void checkAddressReachable(InetAddress address)
            throws ConfigurationException {
        try {
            log.debug("Checking availability of " + address);
            address.isReachable(ADDRESS_PING_TIMEOUT);
        } catch (IOException e) {
            throw new ConfigurationException("Unreachable bind address "
                    + address);
        }
    }

    /**
     * Checks if port is available on given address.
     *
     * @param port port to check for availability
     * @throws ConfigurationException Throws an exception if address is
     *             unavailable.
     * @since 5.5
     */
    public static void checkPortAvailable(InetAddress address, int port)
            throws ConfigurationException {
        if (port < MIN_PORT || port > MAX_PORT) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
        ServerSocket socketTCP = null;
        // DatagramSocket socketUDP = null;
        try {
            log.debug("Checking availability of port " + port + " on address "
                    + address);
            socketTCP = new ServerSocket(port, 0, address);
            socketTCP.setReuseAddress(true);
            // socketUDP = new DatagramSocket(port, address);
            // socketUDP.setReuseAddress(true);
            // return true;
        } catch (IOException e) {
            throw new ConfigurationException("Port is unavailable: " + port
                    + " on address " + address + " (" + e.getMessage() + ")", e);
        } finally {
            // if (socketUDP != null) {
            // socketUDP.close();
            // }
            if (socketTCP != null) {
                try {
                    socketTCP.close();
                } catch (IOException e) {
                    // Do not throw
                }
            }
        }
    }

    /**
     * @return Temporary directory
     */
    public File getTmpDir() {
        return serverConfigurator.getTmpDir();
    }

    private void ifNotExistsAndIsDirectoryThenCreate(File directory) {
        if (!directory.isDirectory()) {
            directory.mkdirs();
        }
    }

    /**
     * @return Log files produced by Log4J configuration without loading this
     *         configuration instead of current active one.
     * @since 5.4.2
     */
    public ArrayList<String> getLogFiles() {
        File log4jConfFile = serverConfigurator.getLogConfFile();
        System.setProperty(Environment.NUXEO_LOG_DIR, getLogDir().getPath());
        return Log4JHelper.getFileAppendersFiles(log4jConfFile);
    }

    /**
     * Check if wizard must and can be ran
     *
     * @return true if configuration wizard is required before starting Nuxeo
     * @since 5.4.2
     */
    public boolean isWizardRequired() {
        return !"true".equalsIgnoreCase(getUserConfig().getProperty(
                PARAM_WIZARD_DONE, "true"))
                && serverConfigurator.isWizardAvailable();
    }

    /**
     * Rebuild a templates string for use in nuxeo.conf
     *
     * @param dbTemplate database template to use instead of current one
     * @return new templates string using given dbTemplate
     * @since 5.4.2
     * @see #extractDatabaseTemplateName()
     * @see {@link #changeDBTemplate(String)}
     * @see {@link #changeTemplates(String)}
     */
    public String rebuildTemplatesStr(String dbTemplate) {
        String nodbTemplates = userConfig.getProperty(ConfigurationGenerator.PARAM_TEMPLATES_NODB);
        if (nodbTemplates == null) {
            extractDatabaseTemplateName();
            nodbTemplates = userConfig.getProperty(ConfigurationGenerator.PARAM_TEMPLATES_NODB);
        }
        String newTemplates = nodbTemplates.isEmpty() ? dbTemplate : dbTemplate
                + "," + nodbTemplates;
        return newTemplates;
    }

    /**
     * @return Nuxeo config directory
     * @since 5.4.2
     */
    public File getConfigDir() {
        return serverConfigurator.getConfigDir();
    }

    /**
     * Ensure the server will start only wizard application, not Nuxeo
     *
     * @since 5.4.2
     */
    public void prepareWizardStart() {
        serverConfigurator.prepareWizardStart();
    }

    /**
     * Ensure the wizard won't be started and nuxeo is ready for use
     *
     * @since 5.4.2
     */
    public void cleanupPostWizard() {
        serverConfigurator.cleanupPostWizard();
    }

    /**
     * @return Nuxeo runtime home
     */
    public File getRuntimeHome() {
        return serverConfigurator.getRuntimeHome();
    }

    /**
     * @since 5.4.2
     * @return true if there's an install in progress
     */
    public boolean isInstallInProgress() {
        return getInstallFile().exists();
    }

    /**
     * @return Install/upgrade file
     * @since 5.4.1
     */
    public File getInstallFile() {
        return new File(serverConfigurator.getDataDir(), INSTALL_AFTER_RESTART);
    }

    /**
     * Add a template to the {@link #PARAM_TEMPLATES_NAME} list if not already
     * present
     *
     * @param template Template to add
     * @throws ConfigurationException
     * @since 5.5
     */
    public void addTemplate(String template) throws ConfigurationException {
        HashMap<String, String> newParametersToSave = new HashMap<String, String>();
        String oldTemplates = userConfig.getProperty(PARAM_TEMPLATES_NAME);
        String[] oldTemplatesSplit = oldTemplates.split(",");
        if (!Arrays.asList(oldTemplatesSplit).contains(template)) {
            String newTemplates = oldTemplates
                    + (oldTemplates.length() > 0 ? "," : "") + template;
            newParametersToSave.put(PARAM_TEMPLATES_NAME, newTemplates);
            saveFilteredConfiguration(newParametersToSave);
            changeTemplates(newTemplates);
        }
    }

    /**
     * Remove a template from the {@link #PARAM_TEMPLATES_NAME} list
     *
     * @param template
     * @throws ConfigurationException
     * @since 5.5
     */
    public void rmTemplate(String template) throws ConfigurationException {
        HashMap<String, String> newParametersToSave = new HashMap<String, String>();
        String oldTemplates = userConfig.getProperty(PARAM_TEMPLATES_NAME);
        List<String> oldTemplatesSplit = Arrays.asList(oldTemplates.split(","));
        if (oldTemplatesSplit.contains(template)) {
            String newTemplates = "";
            boolean firstIem = true;
            for (String templateItem : oldTemplatesSplit) {
                if (!template.equals(templateItem)) {
                    newTemplates += (firstIem ? "" : ",") + templateItem;
                    firstIem = false;
                }
            }
            newParametersToSave.put(PARAM_TEMPLATES_NAME, newTemplates);
            saveFilteredConfiguration(newParametersToSave);
            changeTemplates(newTemplates);
        }
    }

    /**
     * Set a property in nuxeo configuration
     *
     * @param key
     * @param value
     * @throws ConfigurationException
     * @return The old value
     * @since 5.5
     */
    public String setProperty(String key, String value)
            throws ConfigurationException {
        HashMap<String, String> newParametersToSave = new HashMap<String, String>();
        newParametersToSave.put(key, value);
        String oldValue = userConfig.getProperty(key);
        saveFilteredConfiguration(newParametersToSave);
        setBasicConfiguration();
        return oldValue;
    }
}
