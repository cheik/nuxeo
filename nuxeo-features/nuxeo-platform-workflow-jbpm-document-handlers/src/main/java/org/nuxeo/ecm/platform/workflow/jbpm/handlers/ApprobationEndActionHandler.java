/*
 * (C) Copyright 2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 * $Id: ApprobationEndActionHandler.java 19070 2007-05-21 16:05:43Z sfermigier $
 */

package org.nuxeo.ecm.platform.workflow.jbpm.handlers;

import org.jbpm.graph.exe.ExecutionContext;
import org.jbpm.graph.exe.ProcessInstance;
import org.nuxeo.ecm.platform.workflow.jbpm.handlers.api.client.AbstractWorkflowDocumentActionHandler;

/**
 * Action handler invoked when approbation process ends.
 *
 * @author <a href="mailto:ja@nuxeo.com">Julien Anguenot</a>
 *
 */
public class ApprobationEndActionHandler extends
        AbstractWorkflowDocumentActionHandler {

    private static final long serialVersionUID = -6338380183606090792L;

    public void execute(ExecutionContext ec) throws Exception {

        log.debug("Ending process");

        try {
            ProcessInstance pi = getProcessInstance(ec);

            // Try to change life cycle of the bound document to specified
            // destination life cycle.
            String transition = getLifeCycleTransitionToDestinationState(ec);
            if (transition != null) {
                log.debug("Found life cycle transition to follow during review "
                        + "name=" + transition);
                documentFollowTransition(ec, transition);
            } else {
                log.error("Cannot find transition to destination life cycle...");
            }

            // Unbind document to process
            unbindDocumentToProcess(ec);

            // End process instance
            pi.end();

            // Remove WF ACL
            removeWFACL(ec);

            try {
                notifyEvent(ec, null);
            } catch (Exception we) {
                we.printStackTrace();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}