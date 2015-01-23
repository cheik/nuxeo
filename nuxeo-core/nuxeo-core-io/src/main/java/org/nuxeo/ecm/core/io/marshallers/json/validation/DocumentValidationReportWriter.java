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

import static org.nuxeo.ecm.core.io.registry.MarshallingConstants.ENTITY_VALIDATION_REPORT;
import static org.nuxeo.ecm.core.io.registry.reflect.Instanciations.SINGLETON;
import static org.nuxeo.ecm.core.io.registry.reflect.Priorities.REFERENCE;

import java.io.IOException;
import java.lang.reflect.Type;

import javax.ws.rs.core.MediaType;

import org.nuxeo.ecm.core.api.validation.ConstraintViolation;
import org.nuxeo.ecm.core.api.validation.ConstraintViolation.PathNode;
import org.nuxeo.ecm.core.api.validation.DocumentValidationReport;
import org.nuxeo.ecm.core.io.marshallers.json.AbstractJsonWriter;
import org.nuxeo.ecm.core.io.marshallers.json.NxJsonGenerator;
import org.nuxeo.ecm.core.io.registry.MarshallerRegistry;
import org.nuxeo.ecm.core.io.registry.Writer;
import org.nuxeo.ecm.core.io.registry.reflect.Setup;
import org.nuxeo.ecm.core.schema.types.constraints.Constraint;

/**
 * Nuxeo {@link Writer} which is able to marshall {@link DocumentValidationReport} as JSON.
 * <p>
 * This {@link Writer} delegates marshalling of {@link Constraint} to {@link MarshallerRegistry} which provides a
 * {@link Writer} to manage them.
 * </p>
 *
 * @since 7.2
 */
@Setup(mode = SINGLETON, priority = REFERENCE)
public class DocumentValidationReportWriter extends AbstractJsonWriter<DocumentValidationReport> {

    @Override
    public void write(DocumentValidationReport report, Class<?> clazz, Type genericType, MediaType mediatype,
            NxJsonGenerator jg) throws IOException {
        jg.writeStartObject();
        jg.writeEntityTypeField(ENTITY_VALIDATION_REPORT);
        jg.writeBooleanField("has_error", report.hasError());
        jg.writeNumberField("number", report.numberOfErrors());
        // constraint violations
        jg.writeArrayFieldStart("violations");
        for (ConstraintViolation violation : report.asList()) {
            jg.writeStartObject();
            // violation message
            String message = violation.getMessage(ctx.getLocale());
            jg.writeStringField("message", message);
            // invalid value
            Object invalidValue = violation.getInvalidValue();
            if (invalidValue == null) {
                jg.writeNullField("invalid_value");
            } else {
                jg.writeStringField("invalid_value", invalidValue.toString());
            }
            // violated constraint
            Constraint constraint = violation.getConstraint();
            writeEntityField("constraint", constraint, jg);
            // violation place
            jg.writeArrayFieldStart("path");
            for (PathNode node : violation.getPath()) {
                jg.writeStartObject();
                jg.writeStringField("field_name", node.getField().getName().getPrefixedName());
                jg.writeBooleanField("is_list_item", node.isListItem());
                if (node.isListItem()) {
                    jg.writeNumberField("index", node.getIndex());
                }
                jg.writeEndObject();
            }
            jg.writeEndArray();
            jg.writeEndObject();
        }
        jg.writeEndArray();
        jg.writeEndObject();
        jg.flush();
    }

}