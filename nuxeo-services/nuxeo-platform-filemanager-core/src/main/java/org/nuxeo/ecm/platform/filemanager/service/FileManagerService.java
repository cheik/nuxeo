/*
 * (C) Copyright 2002 - 2006 Nuxeo SARL <http://nuxeo.com> and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 *
 * $Id: Registry.java 2531 2006-09-04 23:01:57Z janguenot $
 */

package org.nuxeo.ecm.platform.filemanager.service;

import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentLocation;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentModelList;
import org.nuxeo.ecm.core.api.DocumentSecurityException;
import org.nuxeo.ecm.core.api.PathRef;
import org.nuxeo.ecm.core.api.VersioningOption;
import org.nuxeo.ecm.core.api.impl.DocumentLocationImpl;
import org.nuxeo.ecm.core.api.impl.DocumentModelListImpl;
import org.nuxeo.ecm.core.api.pathsegment.PathSegmentService;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.platform.filemanager.api.FileManager;
import org.nuxeo.ecm.platform.filemanager.service.extension.CreationContainerListProvider;
import org.nuxeo.ecm.platform.filemanager.service.extension.CreationContainerListProviderDescriptor;
import org.nuxeo.ecm.platform.filemanager.service.extension.FileImporter;
import org.nuxeo.ecm.platform.filemanager.service.extension.FileImporterDescriptor;
import org.nuxeo.ecm.platform.filemanager.service.extension.FolderImporter;
import org.nuxeo.ecm.platform.filemanager.service.extension.FolderImporterDescriptor;
import org.nuxeo.ecm.platform.filemanager.service.extension.UnicityExtension;
import org.nuxeo.ecm.platform.filemanager.service.extension.VersioningDescriptor;
import org.nuxeo.ecm.platform.filemanager.utils.FileManagerUtils;
import org.nuxeo.ecm.platform.mimetype.MimetypeDetectionException;
import org.nuxeo.ecm.platform.mimetype.interfaces.MimetypeRegistry;
import org.nuxeo.ecm.platform.types.TypeManager;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.model.ComponentName;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.model.Extension;

/**
 * FileManager registry service.
 * <p>
 * This is the component to request to perform transformations. See API.
 *
 * @author <a href="mailto:andreas.kalogeropoulos@nuxeo.com">Andreas Kalogeropoulos</a>
 */
public class FileManagerService extends DefaultComponent implements FileManager {

    public static final ComponentName NAME = new ComponentName(
            "org.nuxeo.ecm.platform.filemanager.service.FileManagerService");

    public static final String DEFAULT_FOLDER_TYPE_NAME = "Folder";

    // TODO: OG: we should use an overridable query model instead of hardcoding
    // the NXQL query
    public static final String QUERY = "SELECT * FROM Document WHERE file:content/digest = '%s'";

    public static final int MAX = 15;

    private static final Log log = LogFactory.getLog(FileManagerService.class);

    private final Map<String, FileImporter> fileImporters;

    private final List<FolderImporter> folderImporters;

    private final List<CreationContainerListProvider> creationContainerListProviders;

    private List<String> fieldsXPath = new ArrayList<String>();

    private MimetypeRegistry mimeService;

    private boolean unicityEnabled = false;

    private String digestAlgorithm = "sha-256";

    private boolean computeDigest = false;

    public static final VersioningOption DEF_VERSIONING_OPTION = VersioningOption.MINOR;

    public static final boolean DEF_VERSIONING_AFTER_ADD = false;

    /**
     * @since 5.7
     */
    private VersioningOption defaultVersioningOption = DEF_VERSIONING_OPTION;

    /**
     * @since 5.7
     */
    private boolean versioningAfterAdd = DEF_VERSIONING_AFTER_ADD;

    private TypeManager typeService;

    public FileManagerService() {
        fileImporters = new HashMap<String, FileImporter>();
        folderImporters = new LinkedList<FolderImporter>();
        creationContainerListProviders = new LinkedList<CreationContainerListProvider>();
    }

    private MimetypeRegistry getMimeService() throws ClientException {
        if (mimeService == null) {
            mimeService = Framework.getService(MimetypeRegistry.class);
        }
        return mimeService;
    }

    private TypeManager getTypeService() throws ClientException {
        if (typeService == null) {
            typeService = Framework.getService(TypeManager.class);
        }
        return typeService;
    }

    private Blob checkMimeType(Blob blob, String fullname) throws ClientException {
        final String mimeType = blob.getMimeType();
        if (mimeType != null && !mimeType.isEmpty() && !mimeType.equals("application/octet-stream")
                && !mimeType.equals("application/octetstream")) {
            return blob;
        }
        String filename = FileManagerUtils.fetchFileName(fullname);
        try {
            blob = getMimeService().updateMimetype(blob, filename);
        } catch (MimetypeDetectionException e) {
            throw new ClientException(e);
        }
        return blob;
    }

    public DocumentModel createFolder(CoreSession documentManager, String fullname, String path)
            throws ClientException, IOException {

        if (folderImporters.isEmpty()) {
            return defaultCreateFolder(documentManager, fullname, path);
        } else {
            // use the last registered folder importer
            FolderImporter folderImporter = folderImporters.get(folderImporters.size() - 1);
            return folderImporter.create(documentManager, fullname, path, true, getTypeService());
        }
    }

    public DocumentModel defaultCreateFolder(CoreSession documentManager, String fullname, String path)
            throws ClientException {
        return defaultCreateFolder(documentManager, fullname, path, DEFAULT_FOLDER_TYPE_NAME, true);
    }

    public DocumentModel defaultCreateFolder(CoreSession documentManager, String fullname, String path,
            String containerTypeName, boolean checkAllowedSubTypes) throws ClientException {

        // Fetching filename
        String title = FileManagerUtils.fetchFileName(fullname);

        // Looking if an existing Folder with the same filename exists.
        DocumentModel docModel = FileManagerUtils.getExistingDocByTitle(documentManager, path, title);

        if (docModel == null) {
            // check permissions
            PathRef containerRef = new PathRef(path);
            if (!documentManager.hasPermission(containerRef, SecurityConstants.READ_PROPERTIES)
                    || !documentManager.hasPermission(containerRef, SecurityConstants.ADD_CHILDREN)) {
                throw new DocumentSecurityException("Not enough rights to create folder");
            }

            // check allowed sub types
            DocumentModel container = documentManager.getDocument(containerRef);
            if (checkAllowedSubTypes
                    && !getTypeService().isAllowedSubType(containerTypeName, container.getType(), container)) {
                // cannot create document file here
                // TODO: we should better raise a dedicated exception to be
                // catched by the FileManageActionsBean instead of returning
                // null
                return null;
            }

            PathSegmentService pss = Framework.getService(PathSegmentService.class);
            docModel = documentManager.createDocumentModel(containerTypeName);
            docModel.setProperty("dublincore", "title", title);

            // writing changes
            docModel.setPathInfo(path, pss.generatePathSegment(docModel));
            docModel = documentManager.createDocument(docModel);
            documentManager.save();

            log.debug("Created container: " + docModel.getName() + " with type " + containerTypeName);
        }
        return docModel;
    }

    public DocumentModel createDocumentFromBlob(CoreSession documentManager, Blob input, String path,
            boolean overwrite, String fullName) throws ClientException, IOException {

        // check mime type to be able to select the best importer plugin
        input = checkMimeType(input, fullName);

        List<FileImporter> importers = new ArrayList<FileImporter>(fileImporters.values());
        Collections.sort(importers);
        String normalizedMimeType = getMimeService().getMimetypeEntryByMimeType(input.getMimeType()).getNormalized();
        for (FileImporter importer : importers) {
            if (importer.isEnabled() && (importer.matches(normalizedMimeType) || importer.matches(input.getMimeType()))) {
                DocumentModel doc = importer.create(documentManager, input, path, overwrite, fullName, getTypeService());
                if (doc != null) {
                    return doc;
                }
            }
        }
        return null;
    }

    public DocumentModel updateDocumentFromBlob(CoreSession documentManager, Blob input, String path, String fullName)
            throws ClientException {
        String filename = FileManagerUtils.fetchFileName(fullName);
        DocumentModel doc = FileManagerUtils.getExistingDocByFileName(documentManager, path, filename);
        if (doc != null) {
            doc.setProperty("file", "content", input);

            documentManager.saveDocument(doc);
            documentManager.save();

            log.debug("Updated the document: " + doc.getName());
        }
        return doc;
    }

    public FileImporter getPluginByName(String name) {
        return fileImporters.get(name);
    }

    @Override
    public void registerExtension(Extension extension) {
        if (extension.getExtensionPoint().equals("plugins")) {
            Object[] contribs = extension.getContributions();
            for (Object contrib : contribs) {
                if (contrib instanceof FileImporterDescriptor) {
                    registerFileImporter((FileImporterDescriptor) contrib, extension);
                } else if (contrib instanceof FolderImporterDescriptor) {
                    registerFolderImporter((FolderImporterDescriptor) contrib, extension);
                } else if (contrib instanceof CreationContainerListProviderDescriptor) {
                    registerCreationContainerListProvider((CreationContainerListProviderDescriptor) contrib, extension);
                }
            }
        } else if (extension.getExtensionPoint().equals("unicity")) {
            Object[] contribs = extension.getContributions();
            for (Object contrib : contribs) {
                if (contrib instanceof UnicityExtension) {
                    registerUnicityOptions((UnicityExtension) contrib, extension);
                }
            }
        } else if (extension.getExtensionPoint().equals("versioning")) {
            Object[] contribs = extension.getContributions();
            for (Object contrib : contribs) {
                if (contrib instanceof VersioningDescriptor) {
                    VersioningDescriptor descr = (VersioningDescriptor) contrib;
                    String defver = descr.defaultVersioningOption;
                    if (!StringUtils.isBlank(defver)) {
                        try {
                            defaultVersioningOption = VersioningOption.valueOf(defver.toUpperCase(Locale.ENGLISH));
                        } catch (IllegalArgumentException e) {
                            log.warn(String.format("Illegal versioning option: %s, using %s instead", defver,
                                    DEF_VERSIONING_OPTION));
                            defaultVersioningOption = DEF_VERSIONING_OPTION;
                        }
                    }
                    Boolean veradd = descr.versionAfterAdd;
                    if (veradd != null) {
                        versioningAfterAdd = veradd.booleanValue();
                    }
                }
            }
        } else {
            log.warn(String.format("Unknown contribution %s: ignored", extension.getExtensionPoint()));
        }
    }

    @Override
    public void unregisterExtension(Extension extension) {
        if (extension.getExtensionPoint().equals("plugins")) {
            Object[] contribs = extension.getContributions();

            for (Object contrib : contribs) {
                if (contrib instanceof FileImporterDescriptor) {
                    unregisterFileImporter((FileImporterDescriptor) contrib);
                } else if (contrib instanceof FolderImporterDescriptor) {
                    unregisterFolderImporter((FolderImporterDescriptor) contrib);
                } else if (contrib instanceof CreationContainerListProviderDescriptor) {
                    unregisterCreationContainerListProvider((CreationContainerListProviderDescriptor) contrib);
                }
            }
        } else if (extension.getExtensionPoint().equals("unicity")) {

        } else if (extension.getExtensionPoint().equals("versioning")) {
            // set to default value
            defaultVersioningOption = DEF_VERSIONING_OPTION;
            versioningAfterAdd = DEF_VERSIONING_AFTER_ADD;
        } else {
            log.warn(String.format("Unknown contribution %s: ignored", extension.getExtensionPoint()));
        }
    }

    private void registerUnicityOptions(UnicityExtension unicityExtension, Extension extension) {
        if (unicityExtension.getAlgo() != null) {
            digestAlgorithm = unicityExtension.getAlgo();
        }
        if (unicityExtension.getEnabled() != null) {
            unicityEnabled = unicityExtension.getEnabled().booleanValue();
        }
        if (unicityExtension.getFields() != null) {
            fieldsXPath = unicityExtension.getFields();
        } else {
            fieldsXPath.add("file:content");
        }
        if (unicityExtension.getComputeDigest() != null) {
            computeDigest = unicityExtension.getComputeDigest().booleanValue();
        }
    }

    private void registerFileImporter(FileImporterDescriptor pluginExtension, Extension extension) {
        String name = pluginExtension.getName();
        if (name == null) {
            log.error("Cannot register file importer without a name");
            return;
        }

        String className = pluginExtension.getClassName();
        if (fileImporters.containsKey(name)) {
            log.info("Overriding file importer plugin " + name);
            FileImporter oldPlugin = fileImporters.get(name);
            FileImporter newPlugin;
            try {
                newPlugin = className != null ? (FileImporter) extension.getContext().loadClass(className).newInstance()
                        : oldPlugin;
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            if (pluginExtension.isMerge()) {
                newPlugin = mergeFileImporters(oldPlugin, newPlugin, pluginExtension);
            } else {
                newPlugin = fillImporterWithDescriptor(newPlugin, pluginExtension);
            }
            fileImporters.put(name, newPlugin);
            log.info("Registered file importer " + name);
        } else if (className != null) {
            FileImporter plugin;
            try {
                plugin = (FileImporter) extension.getContext().loadClass(className).newInstance();
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            plugin = fillImporterWithDescriptor(plugin, pluginExtension);
            fileImporters.put(name, plugin);
            log.info("Registered file importer " + name);
        } else {
            log.info("Unable to register file importer " + name + ", className is null or plugin is not yet registered");
        }
    }

    private FileImporter mergeFileImporters(FileImporter oldPlugin, FileImporter newPlugin, FileImporterDescriptor desc) {
        List<String> filters = desc.getFilters();
        if (filters != null && !filters.isEmpty()) {
            List<String> oldFilters = oldPlugin.getFilters();
            oldFilters.addAll(filters);
            newPlugin.setFilters(oldFilters);
        }
        newPlugin.setName(desc.getName());
        String docType = desc.getDocType();
        if (docType != null) {
            newPlugin.setDocType(docType);
        }
        newPlugin.setFileManagerService(this);
        newPlugin.setEnabled(desc.isEnabled());
        Integer order = desc.getOrder();
        if (order != null) {
            newPlugin.setOrder(desc.getOrder());
        }
        return newPlugin;
    }

    private FileImporter fillImporterWithDescriptor(FileImporter fileImporter, FileImporterDescriptor desc) {
        List<String> filters = desc.getFilters();
        if (filters != null && !filters.isEmpty()) {
            fileImporter.setFilters(filters);
        }
        fileImporter.setName(desc.getName());
        fileImporter.setDocType(desc.getDocType());
        fileImporter.setFileManagerService(this);
        fileImporter.setEnabled(desc.isEnabled());
        fileImporter.setOrder(desc.getOrder());
        return fileImporter;
    }

    private void unregisterFileImporter(FileImporterDescriptor pluginExtension) {
        String name = pluginExtension.getName();
        fileImporters.remove(name);
        log.info("unregistered file importer: " + name);
    }

    private void registerFolderImporter(FolderImporterDescriptor folderImporterDescriptor, Extension extension) {

        String name = folderImporterDescriptor.getName();
        String className = folderImporterDescriptor.getClassName();

        FolderImporter folderImporter;
        try {
            folderImporter = (FolderImporter) extension.getContext().loadClass(className).newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        folderImporter.setName(name);
        folderImporter.setFileManagerService(this);
        folderImporters.add(folderImporter);
        log.info("registered folder importer: " + name);
    }

    private void unregisterFolderImporter(FolderImporterDescriptor folderImporterDescriptor) {
        String name = folderImporterDescriptor.getName();
        FolderImporter folderImporterToRemove = null;
        for (FolderImporter folderImporter : folderImporters) {
            if (name.equals(folderImporter.getName())) {
                folderImporterToRemove = folderImporter;
            }
        }
        if (folderImporterToRemove != null) {
            folderImporters.remove(folderImporterToRemove);
        }
        log.info("unregistered folder importer: " + name);
    }

    private void registerCreationContainerListProvider(
            CreationContainerListProviderDescriptor ccListProviderDescriptor, Extension extension) {

        String name = ccListProviderDescriptor.getName();
        String[] docTypes = ccListProviderDescriptor.getDocTypes();
        String className = ccListProviderDescriptor.getClassName();

        CreationContainerListProvider provider;
        try {
            provider = (CreationContainerListProvider) extension.getContext().loadClass(className).newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        provider.setName(name);
        provider.setDocTypes(docTypes);
        if (creationContainerListProviders.contains(provider)) {
            // equality and containment tests are based on unique names
            creationContainerListProviders.remove(provider);
        }
        // add the new provider at the beginning of the list
        creationContainerListProviders.add(0, provider);
        log.info("registered creationContaineterList provider: " + name);
    }

    private void unregisterCreationContainerListProvider(
            CreationContainerListProviderDescriptor ccListProviderDescriptor) {
        String name = ccListProviderDescriptor.getName();
        CreationContainerListProvider providerToRemove = null;
        for (CreationContainerListProvider provider : creationContainerListProviders) {
            if (name.equals(provider.getName())) {
                providerToRemove = provider;
                break;
            }
        }
        if (providerToRemove != null) {
            creationContainerListProviders.remove(providerToRemove);
        }
        log.info("unregistered creationContaineterList provider: " + name);
    }

    public List<DocumentLocation> findExistingDocumentWithFile(CoreSession documentManager, String path, String digest,
            Principal principal) throws ClientException {
        String nxql = String.format(QUERY, digest);
        DocumentModelList documentModelList = documentManager.query(nxql, MAX);
        List<DocumentLocation> docLocationList = new ArrayList<DocumentLocation>(documentModelList.size());
        for (DocumentModel documentModel : documentModelList) {
            docLocationList.add(new DocumentLocationImpl(documentModel));
        }
        return docLocationList;
    }

    public boolean isUnicityEnabled() {
        return unicityEnabled;
    }

    public boolean isDigestComputingEnabled() {
        return computeDigest;
    }

    public List<String> getFields() {
        return fieldsXPath;
    }

    public DocumentModelList getCreationContainers(Principal principal, String docType) {
        DocumentModelList containers = new DocumentModelListImpl();
        RepositoryManager repositoryManager = Framework.getLocalService(RepositoryManager.class);
        for (String repositoryName : repositoryManager.getRepositoryNames()) {
            try (CoreSession session = CoreInstance.openCoreSession(repositoryName, principal)) {
                containers.addAll(getCreationContainers(session, docType));
            }
        }
        return containers;
    }

    public DocumentModelList getCreationContainers(CoreSession documentManager, String docType) {
        for (CreationContainerListProvider provider : creationContainerListProviders) {
            if (provider.accept(docType)) {
                return provider.getCreationContainerList(documentManager, docType);
            }
        }
        return new DocumentModelListImpl();
    }

    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }

    @Override
    public VersioningOption getVersioningOption() {
        return defaultVersioningOption;
    }

    @Override
    public boolean doVersioningAfterAdd() {
        return versioningAfterAdd;
    }

}
