/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     nchapurlatn <nc@nuxeo.com>
 */

package org.nuxeo.ecm.core.io.marshallers.json.validation;

import static org.nuxeo.ecm.core.io.registry.MarshallingConstants.ENTITY_VALIDATION_CONSTRAINT;
import static org.nuxeo.ecm.core.io.registry.reflect.Instanciations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.REFERENCE;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.Map;

import javax.ws.rs.core.MediaType;

import org.nuxeo.ecm.core.io.marshallers.json.AbstractJsonWriter;
import org.nuxeo.ecm.core.io.marshallers.json.NxJsonGenerator;
import org.nuxeo.ecm.core.io.registry.Writer;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint.Description;

/**
 * Nuxeo {@link Writer} which is able to marshall {@link Constraint} as JSON.
 *
 * @since 7.2
 */
@Setup(mode = SINGLETON, priority = REFERENCE)
public class ConstraintWriter extends AbstractJsonWriter<Constraint> {

    @Override
    public void write(Constraint constraint, Class<?> clazz, Type genericType, MediaType mediatype, NxJsonGenerator jg)
            throws IOException {
        Description description = constraint.getDescription();
        jg.writeStartObject();
        jg.writeEntityTypeField(ENTITY_VALIDATION_CONSTRAINT);
        jg.writeStringField("name", description.getName());
        // constraint parameters
        jg.writeObjectFieldStart("parameters");
        for (Map.Entry<String, Serializable> param : description.getParameters().entrySet()) {
            jg.writeStringField(param.getKey(), param.getValue().toString());
        }
        jg.writeEndObject();
        jg.writeEndObject();
        jg.flush();
    }

}