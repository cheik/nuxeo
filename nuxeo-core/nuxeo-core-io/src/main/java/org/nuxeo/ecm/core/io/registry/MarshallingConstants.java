package org.nuxeo.ecm.core.io.registry;

public interface MarshallingConstants {

    // Entity names

    String ENTITY_FIELD_NAME = "entity-type";

    String ENTITY_DOCUMENT = "document";

    String ENTITY_VALIDATION_REPORT = "validation_report";

    String ENTITY_VALIDATION_CONSTRAINT = "validation_constraint";

    // Parameters

    String HEADER_PREFIX = "X-NX";

    String EMBED_PROPERTIES = "embed";

    String ADD_ENRICHERS = "adds";

    String FETCH_PROPERTIES = "fetch";

    /**
     * @deprecated use {@value #EMBED_PARAM} concatenated with {@link #EMBED_PROPERTIES} (example:
     *             embed:props=dublincore)
     */
    @Deprecated
    String DOCUMENT_PROPERTIES_HEADER = "X-NXDocumentProperties";

    /**
     * @deprecated use {@value #EMBED_PARAM} concatenated with {@link #ADD_ENRICHERS} (example: embed:adds=acls)
     */
    @Deprecated
    String NXCONTENT_CATEGORY_HEADER = "X-NXContext-Category";

    // Technical

    String WRAPPED_CONTEXT = "MarshalledEntitiesWrappedContext";

}
