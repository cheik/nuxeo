/*
 * Copyright (c) 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 */

package org.nuxeo.ecm.automation.jsf.operations;

import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.Constants;
import org.nuxeo.ecm.automation.core.annotations.Context;
import org.nuxeo.ecm.automation.core.annotations.Operation;
import org.nuxeo.ecm.automation.core.annotations.OperationMethod;
import org.nuxeo.ecm.automation.core.annotations.Param;

/**
 * Sets the outcome for JSF navigation.
 *
 * @author <a href="mailto:td@nuxeo.com">Thierry Delprat</a>
 * @since 5.4.2
 */
@Operation(id = SetOutcome.ID, category = Constants.CAT_UI, requires = Constants.SEAM_CONTEXT, label = "Set JSF Outcome", description = "Set the 'Outcome' context variable that represent a JSF outcome string. This outcome can be used by the next operations that need an outcome. It preserves the current input (e.g returns back the same input).", aliases = { "Seam.SetOutcome" })
public class SetOutcome {

    public static final String ID = "WebUI.SetJSFOutcome";

    @Context
    protected OperationContext ctx;

    @Param(name = "outcome")
    protected String outcome;

    @OperationMethod
    public void run() {
        ctx.put(SeamOperation.OUTCOME, outcome);
    }

}
