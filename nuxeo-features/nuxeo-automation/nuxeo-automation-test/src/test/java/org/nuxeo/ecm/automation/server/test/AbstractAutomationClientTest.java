/*
 * Copyright (c) 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     bstefanescu
 */
package org.nuxeo.ecm.automation.server.test;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.JsonNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.automation.client.Constants;
import org.nuxeo.ecm.automation.client.RemoteException;
import org.nuxeo.ecm.automation.client.Session;
import org.nuxeo.ecm.automation.client.adapters.DocumentService;
import org.nuxeo.ecm.automation.client.jaxrs.impl.HttpAutomationClient;
import org.nuxeo.ecm.automation.client.jaxrs.spi.JsonMarshalling;
import org.nuxeo.ecm.automation.client.model.Blobs;
import org.nuxeo.ecm.automation.client.model.DateUtils;
import org.nuxeo.ecm.automation.client.model.DocRef;
import org.nuxeo.ecm.automation.client.model.DocRefs;
import org.nuxeo.ecm.automation.client.model.Document;
import org.nuxeo.ecm.automation.client.model.Documents;
import org.nuxeo.ecm.automation.client.model.FileBlob;
import org.nuxeo.ecm.automation.client.model.PaginableDocuments;
import org.nuxeo.ecm.automation.client.model.PropertyList;
import org.nuxeo.ecm.automation.client.model.PropertyMap;
import org.nuxeo.ecm.automation.core.operations.blob.AttachBlob;
import org.nuxeo.ecm.automation.core.operations.blob.CreateZip;
import org.nuxeo.ecm.automation.core.operations.blob.GetDocumentBlob;
import org.nuxeo.ecm.automation.core.operations.blob.GetDocumentBlobs;
import org.nuxeo.ecm.automation.core.operations.document.CreateDocument;
import org.nuxeo.ecm.automation.core.operations.document.DeleteDocument;
import org.nuxeo.ecm.automation.core.operations.document.FetchDocument;
import org.nuxeo.ecm.automation.core.operations.document.GetDocumentChildren;
import org.nuxeo.ecm.automation.core.operations.document.LockDocument;
import org.nuxeo.ecm.automation.core.operations.document.Query;
import org.nuxeo.ecm.automation.core.operations.document.UpdateDocument;
import org.nuxeo.ecm.automation.core.operations.services.DocumentPageProviderOperation;
import org.nuxeo.ecm.automation.server.test.UploadFileSupport.DigestMockInputStream;

import com.google.inject.Inject;

public abstract class AbstractAutomationClientTest {

    protected Document automationTestFolder;

    @Inject
    Session session;

    @Inject
    HttpAutomationClient client;

    @Before
    public void setupTestFolder() throws Exception {
        Document root = (Document) session.newRequest(FetchDocument.ID).set(
                "value", "/").execute();
        assertNotNull(root);
        assertEquals("/", root.getPath());
        automationTestFolder = (Document) session.newRequest(CreateDocument.ID).setInput(
                root).set("type", "Folder").set("name",
                "automation-test-folder").execute();
        assertNotNull(automationTestFolder);
    }

    @After
    public void tearDownTestFolder() throws Exception {
        session.newRequest(DeleteDocument.ID).setInput(automationTestFolder).execute();
    }

    protected File newFile(String content) throws IOException {
        File file = File.createTempFile("automation-test-", ".xml");
        file.deleteOnExit();
        FileUtils.writeFile(file, content);
        return file;
    }

    // ------ Tests comes here --------

    @Test
    public void testInvalidLogin() {
        try {
            client.getSession("foo", "bar");
            fail("login is supposed to fail");
        } catch (RemoteException e) {
            assertEquals(401, e.getStatus());
        }
    }

    @Test
    public void testRemoteErrorHandling() throws Exception {
        // assert document removed
        try {
            session.newRequest(FetchDocument.ID).set("value",
                    "/automation-test-folder/unexisting").execute();
            fail("request is supposed to return 404");
        } catch (RemoteException e) {
            Throwable remoteCause = e.getRemoteCause();
            assertEquals(404, e.getStatus());
            assertThat(remoteCause, is(notNullValue()));
            final StackTraceElement[] remoteStack = remoteCause.getStackTrace();
            assertThat(remoteStack, is(notNullValue()));
            while (remoteCause.getCause() != remoteCause
                    && remoteCause.getCause() != null) {
                remoteCause = remoteCause.getCause();
            }
            Map<String, JsonNode> otherNodes = ((JsonMarshalling.RemoteThrowable) remoteCause).getOtherNodes();
            String className = otherNodes.get("className").getTextValue();
            assertThat(className,
                    is("org.nuxeo.ecm.core.model.NoSuchDocumentException"));
            Boolean rollback = otherNodes.get("rollback").getBooleanValue();
            assertThat(rollback, is(Boolean.TRUE));
        }
    }

    private void checkHasCorrectMultiValues(Document note) {
        assertThat(note, notNullValue());
        PropertyMap properties = note.getProperties();
        assertThat(properties, notNullValue());

        PropertyList sl = properties.getList("mv:sl");
        assertThat(sl, notNullValue());
        List<Object> slValues = sl.list();
        assertThat(slValues, hasItem((Object) "s1"));
        assertThat(slValues, hasItem((Object) "s2"));

        PropertyList ss = properties.getList("mv:ss");
        assertThat(ss, notNullValue());
        List<Object> ssValues = ss.list();
        assertThat(ssValues, hasItem((Object) "s1"));
        assertThat(ssValues, hasItem((Object) "s2"));

        Boolean b = properties.getBoolean("mv:b");
        assertThat(b, is(true));

        PropertyList bl = properties.getList("mv:bl");
        assertThat(bl, notNullValue());
        List<Object> blValues = bl.list();
        assertThat(blValues, hasItem((Object) "true"));
        assertThat(blValues, hasItem((Object) "false"));
        assertThat(bl.getBoolean(0), is(Boolean.TRUE));
        assertThat(bl.getBoolean(1), is(Boolean.FALSE));
    }

    @Test
    public void testGetCreateUpdateAndRemoveDocument() throws Exception {
        // HttpAutomationClient client = new HttpAutomationClient();
        // client.connect("http://localhost:18080/automation");
        // Session cs = client.getSession("Administrator", "Administrator");

        Document folder = (Document) session.newRequest(CreateDocument.ID).setInput(
                automationTestFolder).set("type", "Folder").set("name",
                "myfolder").set("properties", "dc:title=My Folder").execute();

        assertNotNull(folder);
        assertEquals("/automation-test-folder/myfolder", folder.getPath());
        assertEquals("My Folder", folder.getTitle());

        // update folder properties
        folder = (Document) session.newRequest(UpdateDocument.ID).setHeader(
                "X-NXDocumentProperties", "*").setInput(folder).set(
                "properties", "dc:title=My Folder2\ndc:description=test").execute();

        assertNotNull(folder);
        assertEquals("/automation-test-folder/myfolder", folder.getPath());
        assertEquals("My Folder2", folder.getTitle());
        assertEquals("test", folder.getProperties().getString("dc:description"));

        // remove folder
        session.newRequest(DeleteDocument.ID).setInput(folder).execute();

        // assert document removed
        try {
            session.newRequest(FetchDocument.ID).set("value",
                    "/automation-test-folder/myfolder").execute();
            fail("request is suposed to return 404");
        } catch (RemoteException e) {
            assertEquals(404, e.getStatus());
        }
    }

    /**
     * Test documents input / output
     */
    @Test
    public void testUpdateDocuments() throws Exception {
        // create a folder
        Document folder = (Document) session.newRequest(CreateDocument.ID).setInput(
                automationTestFolder).set("type", "Folder").set("name",
                "docsInput").set("properties", "dc:title=Query Test").execute();
        // create 2 files
        session.newRequest(CreateDocument.ID).setInput(folder).set("type",
                "Note").set("name", "note1").set("properties", "dc:title=Note1").execute();
        session.newRequest(CreateDocument.ID).setInput(folder).set("type",
                "Note").set("name", "note2").set("properties", "dc:title=Note2").execute();

        DocRefs refs = new DocRefs();
        refs.add(new DocRef("/automation-test-folder/docsInput/note1"));
        refs.add(new DocRef("/automation-test-folder/docsInput/note2"));
        Documents docs = (Documents) session.newRequest(UpdateDocument.ID).setHeader(
                Constants.HEADER_NX_SCHEMAS, "*").setInput(refs).set(
                "properties", "dc:description=updated").execute();
        assertEquals(2, docs.size());
        // returned docs doesn't contains all properties.
        // TODO should we return all schemas?

        Document doc = (Document) session.newRequest(FetchDocument.ID).setHeader(
                Constants.HEADER_NX_SCHEMAS, "*").set("value",
                "/automation-test-folder/docsInput/note1").execute();
        assertEquals("updated", doc.getString("dc:description"));

        doc = (Document) session.newRequest(FetchDocument.ID).setHeader(
                Constants.HEADER_NX_SCHEMAS, "*").set("value",
                "/automation-test-folder/docsInput/note2").execute();
        assertEquals("updated", doc.getString("dc:description"));

        String now = DateUtils.formatDate(new Date());
        doc = (Document) session.newRequest(UpdateDocument.ID).setHeader(
                Constants.HEADER_NX_SCHEMAS, "*").setInput(
                new DocRef("/automation-test-folder/docsInput/note1")).set(
                "properties", "dc:valid=" + now).execute();
        // TODO this test will not work if the client date writer and the server
        // date writer
        // are encoding differently the date (for instance the client add the
        // milliseconds field but the server not)
        // should instead compare date objects up to the second field.
        assertThat(doc.getDate("dc:valid"), is(DateUtils.parseDate(now)));
    }

    @Test
    public void testNullProperties() throws Exception {
        Document note = (Document) session.newRequest(CreateDocument.ID).setInput(
                automationTestFolder).set("type", "Note").set("name", "note1").set(
                "properties", "dc:title=Note1").execute();
        note = (Document) session.newRequest(FetchDocument.ID).setHeader(
                Constants.HEADER_NX_SCHEMAS, "*").set("value", note.getPath()).execute();

        PropertyMap props = note.getProperties();
        assertTrue(props.getKeys().contains("dc:source"));
        assertNull(note.getString("dc:source"));
    }

    /**
     * Test documents output - query and get children
     */
    @Test
    public void testQuery() throws Exception {
        // create a folder
        Document folder = (Document) session.newRequest(CreateDocument.ID).setInput(
                automationTestFolder).set("type", "Folder").set("name",
                "queryTest").set("properties", "dc:title=Query Test").execute();
        // create 2 files
        session.newRequest(CreateDocument.ID).setInput(folder).set("type",
                "Note").set("name", "note1").set("properties", "dc:title=Note1").execute();
        session.newRequest(CreateDocument.ID).setInput(folder).set("type",
                "Note").set("name", "note2").set("properties", "dc:title=Note2").execute();

        // now query the two files
        Documents docs = (Documents) session.newRequest(Query.ID).set(
                "query",
                "SELECT * FROM Note WHERE ecm:path STARTSWITH '/automation-test-folder/queryTest' ").execute();
        assertEquals(2, docs.size());
        String title1 = docs.get(0).getTitle();
        String title2 = docs.get(1).getTitle();
        assertTrue(title1.equals("Note1") && title2.equals("Note2")
                || title1.equals("Note2") && title2.equals("Note1"));

        // now get children of /testQuery
        docs = (Documents) session.newRequest(GetDocumentChildren.ID).setInput(
                folder).execute();
        assertEquals(2, docs.size());

        title1 = docs.get(0).getTitle();
        title2 = docs.get(1).getTitle();
        assertTrue(title1.equals("Note1") && title2.equals("Note2")
                || title1.equals("Note2") && title2.equals("Note1"));

    }

    /**
     * Tests blob input / output.
     */
    @Test
    public void testAttachAndGetFile() throws Exception {
        File file = newFile("<doc>mydoc</doc>");
        String filename = file.getName();
        FileBlob fb = new FileBlob(file);
        // TODO the next line is not working as expected - the file.getName()
        // will be used instead
        // fb.setFileName("test.xml");
        fb.setMimeType("text/xml");
        // create a file
        session.newRequest(CreateDocument.ID).setInput(automationTestFolder).set(
                "type", "File").set("name", "myfile").set("properties",
                "dc:title=My File").execute();

        FileBlob blob = (FileBlob) session.newRequest("Blob.Attach").setHeader(
                Constants.HEADER_NX_VOIDOP, "true").setInput(fb).set(
                "document", "/automation-test-folder/myfile").execute();
        // test that output was avoided using Constants.HEADER_NX_VOIDOP
        assertNull(blob);

        // get the file where blob was attached
        Document doc = (Document) session.newRequest(
                DocumentService.FetchDocument).setHeader(
                Constants.HEADER_NX_SCHEMAS, "*").set("value",
                "/automation-test-folder/myfile").execute();

        PropertyMap map = doc.getProperties().getMap("file:content");
        assertEquals(filename, map.getString("name"));
        assertEquals("text/xml", map.getString("mime-type"));

        // get the data URL
        String path = map.getString("data");
        blob = (FileBlob) session.getFile(path);
        assertNotNull(blob);
        assertEquals(filename, blob.getFileName());
        assertEquals("text/xml", blob.getMimeType());
        assertEquals("<doc>mydoc</doc>",
                IOUtils.toString(blob.getStream(), "utf-8"));
        blob.getFile().delete();

        // now test the GetBlob operation on the same blob
        blob = (FileBlob) session.newRequest(GetDocumentBlob.ID).setInput(doc).set(
                "xpath", "file:content").execute();
        assertNotNull(blob);
        assertEquals(filename, blob.getFileName());
        assertEquals("text/xml", blob.getMimeType());
        assertEquals("<doc>mydoc</doc>",
                IOUtils.toString(blob.getStream(), "utf-8"));
        blob.getFile().delete();
    }

    /**
     * Test blobs input / output
     */
    @Test
    public void testGetBlobs() throws Exception {
        // create a note
        Document note = (Document) session.newRequest(CreateDocument.ID).setInput(
                automationTestFolder).set("type", "Note").set("name", "blobs").set(
                "properties", "dc:title=Blobs Test").execute();
        // attach 2 files to that note
        File file1 = newFile("<doc>mydoc1</doc>");
        File file2 = newFile("<doc>mydoc2</doc>");
        String filename1 = file1.getName();
        String filename2 = file2.getName();
        FileBlob fb1 = new FileBlob(file1);
        fb1.setMimeType("text/xml");
        FileBlob fb2 = new FileBlob(file2);
        fb2.setMimeType("text/xml");
        // TODO attachblob cannot set multiple blobs at once.
        Blobs blobs = new Blobs();
        // blobs.add(fb1);
        // blobs.add(fb2);
        FileBlob blob = (FileBlob) session.newRequest(AttachBlob.ID).setHeader(
                Constants.HEADER_NX_VOIDOP, "true").setInput(fb1).set(
                "document", "/automation-test-folder/blobs").set("xpath",
                "files:files").execute();
        // test that output was avoided using Constants.HEADER_NX_VOIDOP
        assertNull(blob);

        // attach second blob
        blob = (FileBlob) session.newRequest(AttachBlob.ID).setHeader(
                Constants.HEADER_NX_VOIDOP, "true").setInput(fb2).set(
                "document", "/automation-test-folder/blobs").set("xpath",
                "files:files").execute();
        // test that output was avoided using Constants.HEADER_NX_VOIDOP
        assertNull(blob);

        // now retrieve the note with full schemas
        note = (Document) session.newRequest(DocumentService.FetchDocument).setHeader(
                Constants.HEADER_NX_SCHEMAS, "*").set("value",
                "/automation-test-folder/blobs").execute();

        PropertyList list = note.getProperties().getList("files:files");
        assertEquals(2, list.size());

        PropertyMap map = list.getMap(0).getMap("file");
        assertEquals(filename1, map.getString("name"));
        assertEquals("text/xml", map.getString("mime-type"));

        // get the data URL
        String path = map.getString("data");
        blob = (FileBlob) session.getFile(path);
        assertNotNull(blob);
        assertEquals(filename1, blob.getFileName());
        assertEquals("text/xml", blob.getMimeType());
        assertEquals("<doc>mydoc1</doc>",
                IOUtils.toString(blob.getStream(), "utf-8"));
        blob.getFile().delete();

        // the same for the second file
        map = list.getMap(1).getMap("file");
        assertEquals(filename2, map.getString("name"));
        assertEquals("text/xml", map.getString("mime-type"));

        // get the data URL
        path = map.getString("data");
        blob = (FileBlob) session.getFile(path);
        assertNotNull(blob);
        assertEquals(filename2, blob.getFileName());
        assertEquals("text/xml", blob.getMimeType());
        assertEquals("<doc>mydoc2</doc>",
                IOUtils.toString(blob.getStream(), "utf-8"));
        blob.getFile().delete();

        // now test the GetDocumentBlobs operation on the note document
        blobs = (Blobs) session.newRequest(GetDocumentBlobs.ID).setInput(note).set(
                "xpath", "files:files").execute();
        assertNotNull(blob);
        assertEquals(2, blobs.size());

        // test first blob
        blob = (FileBlob) blobs.get(0);
        assertEquals(filename1, blob.getFileName());
        assertEquals("text/xml", blob.getMimeType());
        assertEquals("<doc>mydoc1</doc>",
                IOUtils.toString(blob.getStream(), "utf-8"));
        blob.getFile().delete();

        // test the second one
        blob = (FileBlob) blobs.get(1);
        assertEquals(filename2, blob.getFileName());
        assertEquals("text/xml", blob.getMimeType());
        assertEquals("<doc>mydoc2</doc>",
                IOUtils.toString(blob.getStream(), "utf-8"));
        blob.getFile().delete();
    }

    /**
     * Upload blobs to create a zip and download it.
     */
    @Test
    public void testUploadBlobs() throws Exception {
        File file1 = newFile("<doc>mydoc1</doc>");
        File file2 = newFile("<doc>mydoc2</doc>");
        String filename1 = file1.getName();
        String filename2 = file2.getName();
        FileBlob fb1 = new FileBlob(file1);
        fb1.setMimeType("text/xml");
        FileBlob fb2 = new FileBlob(file2);
        fb2.setMimeType("text/xml");
        Blobs blobs = new Blobs();
        blobs.add(fb1);
        blobs.add(fb2);

        FileBlob zip = (FileBlob) session.newRequest(CreateZip.ID).set(
                "filename", "test.zip").setInput(blobs).execute();
        assertNotNull(zip);

        ZipFile zf = new ZipFile(zip.getFile());
        ZipEntry entry1 = zf.getEntry(filename1);
        assertNotNull(entry1);

        ZipEntry entry2 = zf.getEntry(filename2);
        assertNotNull(entry2);
        zip.getFile().delete();
    }

    @Test
    @Ignore
    public void testUploadSmallFile() throws Exception {
        DigestMockInputStream source = new DigestMockInputStream(100);
        FileInputStream in = new UploadFileSupport(session,
                automationTestFolder.getPath()).testUploadFile(source);
        assertTrue(source.checkDigest(in));
    }

    @Test
    public void queriesArePaginable() throws Exception {
        // craete 1 folder
        Document folder = (Document) session.newRequest(CreateDocument.ID).setInput(
                automationTestFolder).set("type", "Folder").set("name",
                "docsInput").set("properties", "dc:title=Query Test").execute();
        Document[] notes = new Document[15];
        // create 15 notes
        for (int i = 0; i < 14; i++) {
            notes[i] = (Document) session.newRequest(CreateDocument.ID).setInput(
                    folder).set("type", "Note").set("name", "note" + i).set(
                    "properties", "dc:title=Note" + i).execute();
        }

        Documents docs = (Documents) session.newRequest(Query.ID).set("query",
                "SELECT * from Document WHERE ecm:path STARTSWITH '/automation-test-folder/'").execute();

        PaginableDocuments cursor = (PaginableDocuments) session.newRequest(
                DocumentPageProviderOperation.ID).set("query",
                "SELECT * from Document WHERE ecm:path STARTSWITH '/automation-test-folder/'").set(
                "pageSize", 2).execute();
        final int pageSize = cursor.getPageSize();
        final int pageCount = cursor.getPageCount();
        final int totalSize = cursor.getTotalSize();
        assertThat(cursor.size(), is(2));
        int size = docs.size();
        assertThat(totalSize, is(size));
        assertThat(pageSize, is(2));
        assertThat(pageCount, is(size / 2 + size % 2));
        assertThat(cursor.getTotalSize(), greaterThanOrEqualTo((pageCount - 1)
                * pageSize));
    }

    @Test
    public void testSetArrayProperty() throws Exception {
        PropertyMap props = new PropertyMap();
        props.set("dc:title", "My Test Folder");
        props.set("dc:description", "test");
        props.set("dc:subjects", "a,b,c\\,d");
        Document folder = (Document) session.newRequest(CreateDocument.ID).setHeader(
                "X-NXDocumentProperties", "*").setInput(automationTestFolder).set(
                "type", "Folder").set("name", "myfolder2").set("properties",
                props).execute();

        assertEquals("My Test Folder", folder.getString("dc:title"));
        assertEquals("test", folder.getString("dc:description"));
        PropertyList ar = (PropertyList) folder.getProperties().get(
                "dc:subjects");
        assertEquals(3, ar.size());
        assertEquals("a", ar.getString(0));
        assertEquals("b", ar.getString(1));
        assertEquals("c,d", ar.getString(2));
    }

    @Test
    public void testBadAccess() throws Exception {
        try {
            session.newRequest(FetchDocument.ID).set("value",
                    "/automation-test-folder/foo").execute();
        } catch (RemoteException e) {
            return;
        }
        fail("no exception caught");
    }

    @Test
    public void testLock() throws Exception {
        Document folder = (Document) session.newRequest(CreateDocument.ID).setInput(
                automationTestFolder).set("type", "Folder").set("name",
                "myfolder").set("properties", "dc:title=My Folder").execute();

        // Getting the document
        Document doc = (Document) session.newRequest(FetchDocument.ID).setHeader(
                Constants.HEADER_NX_SCHEMAS, "*").set("value", folder.getPath()).execute();

        assertNull(doc.getLock());

        session.newRequest(LockDocument.ID).setHeader(
                Constants.HEADER_NX_VOIDOP, "*").setInput(doc).execute();

        doc = (Document) session.newRequest(FetchDocument.ID).setHeader(
                Constants.HEADER_NX_SCHEMAS, "*").set("value", doc.getPath()).execute();

        assertNotNull(doc.getLock());
        assertEquals("Administrator", doc.getLockOwner());
        assertNotNull(doc.getLockCreated());
    }

    @Test
    public void testEncoding() throws Exception {
        // Latin vowels with various French accents (avoid non-ascii literals in
        // java source code to avoid issues when working with developers who do
        // not configure there editor charset to UTF-8).
        String title = "\u00e9\u00e8\u00ea\u00eb\u00e0\u00e0\u00e4\u00ec\u00ee\u00ef\u00f9\u00fb\u00f9";
        Document folder = (Document) session.newRequest(CreateDocument.ID).setInput(
                automationTestFolder).set("type", "Folder").set("name",
                "myfolder").set("properties", "dc:title=" + title).execute();

        folder = (Document) session.newRequest(FetchDocument.ID).setHeader(
                Constants.HEADER_NX_SCHEMAS, "*").set("value", folder.getPath()).execute();

        assertEquals(folder.getTitle(), title);
    }

}