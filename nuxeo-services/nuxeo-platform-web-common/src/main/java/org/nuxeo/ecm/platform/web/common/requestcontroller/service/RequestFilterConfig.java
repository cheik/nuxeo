/*
 * (C) Copyright 2006-2009 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     Nuxeo - initial API and implementation
 *
 * $Id$
 */

package org.nuxeo.ecm.platform.web.common.requestcontroller.service;

import java.io.Serializable;

import javax.servlet.http.HttpServletRequest;

import org.nuxeo.ecm.platform.web.common.requestcontroller.filter.NuxeoRequestControllerFilter;

/**
 * Interface for the {@link NuxeoRequestControllerFilter} config for a given {@link HttpServletRequest}.
 *
 * @author tiry
 */
public interface RequestFilterConfig extends Serializable {

    boolean needSynchronization();

    boolean needTransaction();

    boolean needTransactionBuffered();

    boolean isCached();

    boolean isPrivate();

    String getCacheTime();

}
