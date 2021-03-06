/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     dmetzler
 */
package org.nuxeo.ecm.restapi.server.jaxrs.directory;

import static org.nuxeo.ecm.restapi.server.jaxrs.directory.DirectorySessionRunner.withDirectorySession;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.directory.Directory;
import org.nuxeo.ecm.directory.DirectoryException;
import org.nuxeo.ecm.directory.Session;
import org.nuxeo.ecm.directory.api.DirectoryService;
import org.nuxeo.ecm.restapi.jaxrs.io.directory.DirectoryEntry;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.exceptions.WebResourceNotFoundException;
import org.nuxeo.ecm.webengine.model.impl.DefaultObject;
import org.nuxeo.runtime.api.Framework;

/**
 * @since 5.7.3
 */
@WebObject(type = "directoryEntry")
public class DirectoryEntryObject extends DefaultObject {

    protected DirectoryEntry entry;

    protected Directory directory;

    @Override
    protected void initialize(Object... args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Directory Entry obhect  takes one parameter");
        }

        entry = (DirectoryEntry) args[0];
        directory = getDirectoryFromEntry(entry);

    }

    @GET
    public DirectoryEntry doGet() {
        return entry;
    }

    @PUT
    public DirectoryEntry doUpdateEntry(final DirectoryEntry entry) {
        checkEditGuards();
        return withDirectorySession(directory, new DirectorySessionRunner<DirectoryEntry>() {

            @Override
            DirectoryEntry run(Session session) throws ClientException {
                DocumentModel docEntry = entry.getDocumentModel();
                session.updateEntry(docEntry);

                String id = (String) docEntry.getPropertyValue(directory.getSchema() + ":" + directory.getIdField());

                return new DirectoryEntry(directory.getName(), session.getEntry(id));

            }
        });

    }

    private void checkEditGuards() {
        ((DirectoryObject) prev).checkEditGuards();
    }

    @DELETE
    public Response doDeleteEntry() {
        checkEditGuards();
        withDirectorySession(directory, new DirectorySessionRunner<DirectoryEntry>() {

            @Override
            DirectoryEntry run(Session session) throws ClientException {
                session.deleteEntry(entry.getDocumentModel());
                return null;
            }
        });

        return Response.ok().status(Status.NO_CONTENT).build();

    }

    private Directory getDirectoryFromEntry(final DirectoryEntry entry) {
        DirectoryService ds = Framework.getLocalService(DirectoryService.class);
        Directory directory;
        try {
            directory = ds.getDirectory(entry.getDirectoryName());
        } catch (DirectoryException e) {
            throw new WebResourceNotFoundException("directory not found");
        }
        return directory;
    }

}
