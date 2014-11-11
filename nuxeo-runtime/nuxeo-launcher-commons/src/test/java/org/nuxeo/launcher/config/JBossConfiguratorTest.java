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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;

/**
 * @author jcarsique
 *
 */
public class JBossConfiguratorTest extends AbstractConfigurationTest {

    private ConfigurationGenerator configGenerator2;

    String propertyToGenerate;

    String propertyToGenerate2;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        propertyToGenerate = "<config-property name=\""
                + "property\" type=\"java.lang.String\">URL=jdbc:h2:"
                + System.getProperty(Environment.NUXEO_DATA_DIR)
                + File.separator + "h2" + File.separator
                + "testinclude;AUTO_SERVER=true</config-property>";
        // Windows path
        propertyToGenerate2 = "<config-property name=\""
                + "property\" type=\"java.lang.String\">URL=jdbc:h2:"
                + "C:\\nuxeo-dm-jboss\\server\\default\\data\\NXRuntime\\data"
                + File.separator + "h2" + File.separator
                + "testinclude;AUTO_SERVER=true</config-property>";

        FileUtils.copyDirectory(getResourceFile("templates/jboss"), new File(
                nuxeoHome, "templates"));
        System.setProperty("jboss.home.dir", nuxeoHome.getPath());
        configGenerator = new ConfigurationGenerator();

        File nuxeoConf = getResourceFile("configurator/nuxeo.conf2");
        System.setProperty(ConfigurationGenerator.NUXEO_CONF,
                nuxeoConf.getPath());
        configGenerator2 = new ConfigurationGenerator();
    }

    @Test
    public void testConfiguration() throws Exception {
        assertTrue(configGenerator.init());
        assertTrue(configGenerator.isConfigurable());
        log.debug(configGenerator.getIncludedTemplates());
        Properties config = configGenerator.getUserConfig();
        assertEquals("default,testinclude",
                config.getProperty("nuxeo.templates"));
        assertEquals("true", config.getProperty("test.nuxeo.conf"));
        assertEquals("true", config.getProperty("test.nuxeo.defaults"));
        assertEquals("true",
                config.getProperty("test.nuxeo.defaults.template.1"));
        assertEquals("true",
                config.getProperty("test.nuxeo.defaults.template.2"));
        assertEquals("true",
                config.getProperty("test.nuxeo.conf.override.defaults"));
        assertEquals(
                "true",
                config.getProperty("test.nuxeo.conf.override.defaults.template"));
        assertEquals("testinclude", config.getProperty("nuxeo.db.name"));
        assertEquals("sa", config.getProperty("nuxeo.db.user"));
    }

    @Test
    public void testGenerateFiles() throws Exception {
        configGenerator.run();
        log.debug(configGenerator.getIncludedTemplates());
        File configDir = new File(nuxeoHome, "server/"
                + JBossConfigurator.DEFAULT_CONFIGURATION
                + "/deploy/nuxeo.ear/config");
        assertTrue(new File(configDir, "test2").exists());

        File generatedFile = new File(configDir.getParentFile(),
                "datasources/default-repository-ds.xml");
        String generatedProperty = new BufferedReader(new FileReader(
                generatedFile)).readLine();
        assertEquals(generatedProperty, propertyToGenerate, generatedProperty);

        // Check windows path parsing
        generatedFile = new File(configDir.getParentFile(),
                "datasources/default-repository-ds_2.xml");
        generatedProperty = new BufferedReader(new FileReader(generatedFile)).readLine();
        assertEquals(generatedProperty, propertyToGenerate2, generatedProperty);

        // ignored extension must not be parsed
        generatedFile = new File(configDir.getParentFile(),
                "config/extension.ignored");
        generatedProperty = new BufferedReader(new FileReader(generatedFile)).readLine();
        String originalProperty = new BufferedReader(new FileReader(new File(
                nuxeoHome, "templates/common/config/extension.ignored"))).readLine();
        assertEquals(generatedProperty, originalProperty, generatedProperty);

        // properly manage files containing accents
        generatedFile = new File(configDir.getParentFile(),
                "config/file-with-accents.xml");
        generatedProperty = new BufferedReader(new FileReader(generatedFile)).readLine();
        originalProperty = new BufferedReader(new FileReader(new File(
                nuxeoHome, "templates/common/config/file-with-accents.xml"))).readLine();
        assertEquals(generatedProperty, originalProperty, generatedProperty);
    }

    @Test
    public void testGenerateFiles2() throws Exception {
        configGenerator2.run();
        log.debug(configGenerator2.getIncludedTemplates());
        Properties config = configGenerator2.getUserConfig();
        assertEquals("common,testinclude2",
                config.getProperty("nuxeo.templates"));
        assertEquals("true", config.getProperty("test.nuxeo.conf"));
        assertEquals("true", config.getProperty("test.nuxeo.defaults"));
        assertEquals("true",
                config.getProperty("test.nuxeo.defaults.template.1"));
        assertEquals("true",
                config.getProperty("test.nuxeo.defaults.template.2"));
        assertEquals("true",
                config.getProperty("test.nuxeo.conf.override.defaults"));
        assertEquals(
                "true",
                config.getProperty("test.nuxeo.conf.override.defaults.template"));
        assertEquals("testinclude", config.getProperty("nuxeo.db.name"));
        assertEquals("sa", config.getProperty("nuxeo.db.user"));

        File configDir = new File(nuxeoHome, "server/"
                + JBossConfigurator.DEFAULT_CONFIGURATION
                + "/deploy/nuxeo.ear/config");
        assertTrue(new File(configDir, "test2").exists());

        File generatedFile = new File(configDir.getParentFile(),
                "datasources/default-repository-ds.xml");
        String generatedProperty = new BufferedReader(new FileReader(
                generatedFile)).readLine();
        assertEquals(generatedProperty, propertyToGenerate, generatedProperty);
    }

    @Test
    public void testForceGeneration() throws ConfigurationException {
        configGenerator2.run();
        File testFile = new File(nuxeoHome, "server/"
                + JBossConfigurator.DEFAULT_CONFIGURATION
                + "/deploy/nuxeo.ear/config");
        testFile = new File(testFile, "test2");
        testFile.delete();
        configGenerator2.setForceGeneration(true);
        configGenerator2.run();
        assertTrue(testFile.exists());
    }

    @Test
    public void testReplaceBackslashes() throws ConfigurationException,
            IOException {
        File windowsConf = getResourceFile("configurator/windows.conf");
        File newWindowsConf = File.createTempFile("nuxeo", ".conf");
        FileUtils.copyFile(windowsConf, newWindowsConf);
        System.setProperty(ConfigurationGenerator.NUXEO_CONF,
                newWindowsConf.getPath());
        configGenerator = new ConfigurationGenerator();
        configGenerator.replaceBackslashes();
        BufferedReader br = new BufferedReader(new FileReader(newWindowsConf));
        String generatedProperty = br.readLine();
        propertyToGenerate = "nuxeo.log.dir=C:/ProgramData/path with space/nuxeo/log";
        assertEquals(generatedProperty, propertyToGenerate, generatedProperty);
        generatedProperty = br.readLine();
        propertyToGenerate = "some.parameter=d\\u00e9connexion-${nuxeo.log.dir}";
        assertEquals(generatedProperty, propertyToGenerate, generatedProperty);
    }

    @Test
    public void testSaveFilteredConfiguration() throws ConfigurationException {
        String propToSave1 = "nuxeo.notification.eMailSubjectPrefix";
        String valueToSave1 = "[Nuxeo test]";
        String propToSave2 = "mail.smtp.username";
        String valueToSave2 = "tester";
        assertTrue(configGenerator.init());
        assertTrue(configGenerator.isConfigurable());
        HashMap<String, String> parameters = new HashMap<String, String>();
        parameters.put(propToSave1, valueToSave1);
        configGenerator.saveFilteredConfiguration(parameters);

        File nuxeoConf = getResourceFile("configurator/nuxeo.conf");
        System.setProperty(ConfigurationGenerator.NUXEO_CONF,
                nuxeoConf.getPath());
        configGenerator = new ConfigurationGenerator();
        assertTrue(configGenerator.init());
        Properties userConfig = configGenerator.getUserConfig();
        assertEquals("Save fail", valueToSave1,
                userConfig.getProperty(propToSave1));
        parameters.clear();
        parameters.put(propToSave2, valueToSave2);
        configGenerator.saveFilteredConfiguration(parameters);

        configGenerator = new ConfigurationGenerator();
        assertTrue(configGenerator.init());
        userConfig = configGenerator.getUserConfig();
        assertEquals("Save fail", valueToSave2,
                userConfig.getProperty(propToSave2));
        assertEquals("Save fail", valueToSave1,
                userConfig.getProperty(propToSave1));
    }
}
