/*
 * (C) Copyright 2015 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Thierry Delprat <tdelprat@nuxeo.com>
 */
package org.nuxeo.automation.scripting.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.nuxeo.automation.scripting.api.AutomationScriptingService;
import org.nuxeo.automation.scripting.internals.operation
        .ScriptingOperationTypeImpl;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationDocumentation.Param;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.automation.OperationType;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.test.CoreFeature;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.LocalDeploy;

/**
 * @since 7.2
 */
@RunWith(FeaturesRunner.class)
@Features({ CoreFeature.class })
@RepositoryConfig(cleanup = Granularity.METHOD)
@Deploy({ "org.nuxeo.ecm.automation.core", "org.nuxeo.ecm.automation.features", "org.nuxeo.ecm.platform.query.api",
        "org.nuxeo.ecm.automation.scripting" })
@LocalDeploy({ "org.nuxeo.ecm.automation.scripting.tests:automation-scripting-contrib.xml",
        "org.nuxeo.ecm.automation.scripting.tests:core-types-contrib.xml" })
public class TestScriptRunnerInfrastructure {

    protected static String[] attachments = { "att1", "att2", "att3" };

    @Inject
    CoreSession session;

    @Inject
    AutomationService automationService;

    ByteArrayOutputStream outContent = new ByteArrayOutputStream();

    private PrintStream outStream;

    @Before
    public void setUpStreams() {
        outStream = System.out;
        System.setOut(new PrintStream(outContent));
    }

    @After
    public void cleanUpStreams() throws IOException {
        outContent.close();
        System.setOut(outStream);
    }

    @Test
    public void serviceShouldBeDeclared() {
        AutomationScriptingService scriptingService = Framework.getService(AutomationScriptingService.class);
        assertNotNull(scriptingService);
    }

    @Ignore("NXP-16561")
    @Test
    public void shouldExecuteSimpleScript() throws Exception {
        AutomationScriptingService scriptingService = Framework.getService(AutomationScriptingService.class);
        assertNotNull(scriptingService);

        InputStream stream = this.getClass().getResourceAsStream("/simpleAutomationScript.js");
        assertNotNull(stream);
        scriptingService.run(stream, session);
        assertEquals("Created even Documents\n", outContent.toString());
    }

    @Test
    public void simpleScriptingOperationShouldBeAvailable() throws Exception {

        OperationType type = automationService.getOperation("Scripting" + ".HelloWorld");
        assertNotNull(type);
        assertTrue(type instanceof ScriptingOperationTypeImpl);

        Param[] paramDefs = type.getDocumentation().getParams();
        assertEquals(1, paramDefs.length);

        OperationContext ctx = new OperationContext(session);
        Map<String, Object> params = new HashMap<>();

        params.put("lang", "en");
        ctx.setInput("John");
        Object result = automationService.run(ctx, "Scripting.HelloWorld", params);
        assertEquals("Hello John", result.toString());

        params.put("lang", "fr");
        ctx.setInput("John");
        result = automationService.run(ctx, "Scripting.HelloWorld", params);
        assertEquals("Bonjour John", result.toString());
    }

    @Test
    public void runOperationOnSubTree() throws Exception {

        DocumentModel root = session.getRootDocument();

        for (int i = 0; i < 5; i++) {
            DocumentModel doc = session.createDocumentModel("/", "new" + i, "File");
            session.createDocument(doc);
        }

        session.save();
        DocumentModelList res = session.query("select * from File where  " + "ecm:mixinType = 'HiddenInNavigation'");
        assertEquals(0, res.size());

        OperationContext ctx = new OperationContext(session);
        Map<String, Object> params = new HashMap<>();

        params.put("facet", "HiddenInNavigation");
        params.put("type", "File");
        ctx.setInput(root);
        Object result = automationService.run(ctx, "Scripting.AddFacetInSubTree", params);
        DocumentModelList docs = (DocumentModelList) result;
        assertEquals(5, docs.size());
    }

    @Test
    public void simpleScriptingOperationsInChain() throws Exception {

        OperationContext ctx = new OperationContext(session);
        Map<String, Object> params = new HashMap<>();

        ctx.setInput("John");
        Object result = automationService.run(ctx, "Scripting.ChainedHello", params);
        assertEquals("Hello Bonjour John", result.toString());

    }

    @Test
    public void testMultiThread() throws InterruptedException {
        OperationContext ctx = new OperationContext(session);
        Map<String, Object> params = new HashMap<>();
        ctx.setInput("John");
        Thread t = new Thread(() -> {
            try {
                ctx.setInput("Vlad");
                Object result = automationService.run(ctx, "Scripting.ChainedHello", params);
                assertEquals("Hello Bonjour Vlad", result.toString());
            } catch (Exception e) {
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                Object result = automationService.run(ctx, "Scripting.ChainedHello", params);
                assertEquals("Hello Bonjour John", result.toString());
            } catch (Exception e) {
            }
        });
        t.start();
        t2.start();
        t.join();
        t2.join();
    }

    @Test
    public void testOperationCtx() throws OperationException {
        OperationContext ctx = new OperationContext(session);
        Map<String, Object> params = new HashMap<>();
        ctx.put("test", "odd");
        DocumentModel result = (DocumentModel) automationService.run(ctx, "Scripting.TestOperationCtx", params);
        assertEquals("odd", result.getPropertyValue("dc:nature"));
    }

    @Test
    public void testOperationWithBlob() throws IOException, OperationException {
        // upload file blob
        File fieldAsJsonFile = FileUtils.getResourceFileFromContext("creationFields.json");
        Blob fb = Blobs.createBlob(fieldAsJsonFile);
        fb.setMimeType("image/jpeg");

        OperationContext ctx = new OperationContext(session);
        ctx.setInput(fb);
        Map<String, Object> params = new HashMap<>();
        params.put("document", "/newDoc");
        DocumentModel result = (DocumentModel) automationService.run(ctx, "Scripting.TestBlob", params);
        assertEquals("creationFields.json", ((Blob) result.getPropertyValue("file:content")).getFilename());
        assertEquals("title:creationFields.json\n", outContent.toString());
    }

    @Test
    public void testComplexProperties() throws IOException, OperationException {
        // Fill the document properties
        Map<String, Object> creationProps = new HashMap<>();
        creationProps.put("ds:tableName", "MyTable");
        creationProps.put("ds:attachments", attachments);

        // send the fields representation as json
        File fieldAsJsonFile = FileUtils.getResourceFileFromContext("creationFields.json");
        assertNotNull(fieldAsJsonFile);
        String fieldsDataAsJSon = FileUtils.readFile(fieldAsJsonFile);
        fieldsDataAsJSon = fieldsDataAsJSon.replaceAll("\n", "");
        fieldsDataAsJSon = fieldsDataAsJSon.replaceAll("\r", "");
        creationProps.put("ds:fields", fieldsDataAsJSon);
        creationProps.put("dc:title", "testDoc");

        OperationContext ctx = new OperationContext(session);
        Map<String, Object> params = new HashMap<>();
        params.put("properties", toString(creationProps));
        params.put("type", "DataSet");
        params.put("name", "testDoc");
        DocumentModel result = (DocumentModel) automationService.run(ctx, "Scripting.TestComplexProperties", params);
        assertEquals("whatever", ((Map) ((List) result.getPropertyValue("ds:fields")).get(0)).get("sqlTypeHint"));
    }

    public String toString(Map<String, Object> creationProps) {
        StringBuilder buf = new StringBuilder();
        for (Map.Entry<String, Object> entry : creationProps.entrySet()) {
            Object v = entry.getValue();
            if (v != null) {
                if (v.getClass() == String.class) {
                    buf.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
                }
            } else {
                buf.append(entry.getKey()).append("=").append("\n");
            }
        }
        return buf.toString();
    }
}
