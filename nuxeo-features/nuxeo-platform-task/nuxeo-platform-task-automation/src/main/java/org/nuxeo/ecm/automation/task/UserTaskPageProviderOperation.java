/*
 * (C) Copyright 2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 * Contributors:
 *     Nuxeo - initial API and implementation
 */

package org.nuxeo.ecm.automation.task;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.core.api.Blobs;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.platform.query.api.PageProvider;
import org.nuxeo.ecm.platform.query.api.PageProviderService;
import org.nuxeo.ecm.platform.task.dashboard.DashBoardItem;
import org.nuxeo.ecm.platform.task.providers.UserTaskPageProvider;
import org.nuxeo.ecm.platform.url.api.DocumentViewCodecManager;
import org.nuxeo.runtime.api.Framework;

/**
 * Operation to retrieve the tasks waiting for the current user.
 *
 * @author <a href="mailto:troger@nuxeo.com">Thomas Roger</a>
 * @since 5.5
 */
@Operation(id = UserTaskPageProviderOperation.ID, category = Constants.CAT_SERVICES, label = "UserTaskPageProvider", description = "Returns the tasks waiting for the current user.", addToStudio = false)
public class UserTaskPageProviderOperation extends AbstractTaskOperation {

    public static final String ID = "Workflow.UserTaskPageProvider";

    public static final String USER_TASKS_PAGE_PROVIDER = "user_tasks";

    @Param(name = "language", required = false)
    protected String language;

    @Param(name = "page", required = false)
    protected Integer page;

    @Param(name = "pageSize", required = false)
    protected Integer pageSize;

    @Context
    protected CoreSession session;

    @Context
    protected DocumentViewCodecManager documentViewCodecManager;

    @SuppressWarnings("unchecked")
    @OperationMethod
    public Blob run() {
        Map<String, Serializable> props = new HashMap<String, Serializable>();
        props.put(UserTaskPageProvider.CORE_SESSION_PROPERTY, (Serializable) session);
        PageProviderService pps = Framework.getLocalService(PageProviderService.class);

        Long targetPage = null;
        if (page != null) {
            targetPage = Long.valueOf(page.longValue());
        }
        Long targetPageSize = null;
        if (pageSize != null) {
            targetPageSize = Long.valueOf(pageSize.longValue());
        }
        PageProvider<DashBoardItem> pageProvider = (PageProvider<DashBoardItem>) pps.getPageProvider(
                USER_TASKS_PAGE_PROVIDER, null, targetPageSize, targetPage, props);

        Locale locale = language != null && !language.isEmpty() ? new Locale(language) : Locale.ENGLISH;

        JSONArray processes = new JSONArray();
        for (DashBoardItem dashBoardItem : pageProvider.getCurrentPage()) {
            dashBoardItem.setLocale(locale);
            JSONObject obj = dashBoardItem.asJSON();
            processes.add(obj);
        }

        JSONObject json = new JSONObject();
        json.put("isPaginable", Boolean.TRUE);
        json.put("totalSize", Long.valueOf(pageProvider.getResultsCount()));
        json.put("pageIndex", Long.valueOf(pageProvider.getCurrentPageIndex()));
        json.put("pageSize", Long.valueOf(pageProvider.getPageSize()));
        json.put("pageCount", Long.valueOf(pageProvider.getNumberOfPages()));

        json.put("entries", processes);
        return Blobs.createBlob(json.toString(), "application/json");
    }

}
