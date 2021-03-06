/*
 * (C) Copyright 2013 Nuxeo SA (http://nuxeo.com/) and others.
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
 *     Thomas Roger
 */

package org.nuxeo.ecm.webapp.bulkedit;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.VersioningOption;
import org.nuxeo.ecm.core.api.model.PropertyNotFoundException;
import org.nuxeo.runtime.model.ComponentInstance;
import org.nuxeo.runtime.model.DefaultComponent;

/**
 * Default implementation of {@link BulkEditService}.
 *
 * @since 5.7.3
 */
public class BulkEditServiceImpl extends DefaultComponent implements BulkEditService {

    public static final String VERSIONING_EP = "versioning";

    public static final VersioningOption DEFAULT_VERSIONING_OPTION = VersioningOption.MINOR;

    private static final Log log = LogFactory.getLog(BulkEditServiceImpl.class);

    protected VersioningOption defaultVersioningOption = DEFAULT_VERSIONING_OPTION;

    @Override
    public void updateDocuments(CoreSession session, DocumentModel sourceDoc, List<DocumentModel> targetDocs)
            throws ClientException {
        List<String> propertiesToCopy = getPropertiesToCopy(sourceDoc);
        if (propertiesToCopy.isEmpty()) {
            return;
        }

        for (DocumentModel targetDoc : targetDocs) {

            for (String propertyToCopy : propertiesToCopy) {
                try {
                    checkIn(targetDoc);
                    targetDoc.setPropertyValue(propertyToCopy, sourceDoc.getPropertyValue(propertyToCopy));
                } catch (PropertyNotFoundException e) {
                    String message = "%s property does not exist on %s";
                    log.warn(String.format(message, propertyToCopy, targetDoc));
                }
            }
        }
        session.saveDocuments(targetDocs.toArray(new DocumentModel[targetDocs.size()]));
    }

    /**
     * Extracts the properties to be copied from {@code sourceDoc}. The properties are stored in the ContextData of
     * {@code sourceDoc}: the key is the xpath property, the value is {@code true} if the property has to be copied,
     * {@code false otherwise}.
     */
    protected List<String> getPropertiesToCopy(DocumentModel sourceDoc) {
        List<String> propertiesToCopy = new ArrayList<String>();
        for (Map.Entry<String, Serializable> entry : sourceDoc.getContextData().entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(BULK_EDIT_PREFIX)) {
                String[] properties = key.replace(BULK_EDIT_PREFIX, "").split(" ");
                Serializable value = entry.getValue();
                if (value instanceof Boolean && (Boolean) value) {
                    for (String property : properties) {
                        if (!property.startsWith(CONTEXT_DATA)) {
                            propertiesToCopy.add(property);
                        }
                    }
                }
            }
        }
        return propertiesToCopy;
    }

    protected void checkIn(DocumentModel doc) throws ClientException {
        if (defaultVersioningOption != null && defaultVersioningOption != VersioningOption.NONE) {
            if (doc.isCheckedOut()) {
                doc.checkIn(defaultVersioningOption, null);
            }
        }
    }

    @Override
    public void registerContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (VERSIONING_EP.equals(extensionPoint)) {
            VersioningDescriptor desc = (VersioningDescriptor) contribution;
            String defaultVer = desc.getDefaultVersioningOption();
            if (!StringUtils.isBlank(defaultVer)) {
                try {
                    defaultVersioningOption = VersioningOption.valueOf(defaultVer.toUpperCase(Locale.ENGLISH));
                } catch (IllegalArgumentException e) {
                    log.warn(String.format("Illegal versioning option: %s, using %s instead", defaultVer,
                            DEFAULT_VERSIONING_OPTION));
                    defaultVersioningOption = DEFAULT_VERSIONING_OPTION;
                }
            }
        }
    }

    @Override
    public void unregisterContribution(Object contribution, String extensionPoint, ComponentInstance contributor) {
        if (VERSIONING_EP.equals(extensionPoint)) {
            defaultVersioningOption = DEFAULT_VERSIONING_OPTION;
        }
    }

}
