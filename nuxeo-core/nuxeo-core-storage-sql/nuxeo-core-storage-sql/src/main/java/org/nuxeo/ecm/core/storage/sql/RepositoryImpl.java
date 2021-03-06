/*
 * Copyright (c) 2006-2011 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Florent Guillaume
 */

package org.nuxeo.ecm.core.storage.sql;

import java.io.IOException;
import java.io.Serializable;
import java.util.Calendar;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.naming.Reference;
import javax.resource.ResourceException;
import javax.resource.cci.ConnectionSpec;
import javax.resource.cci.RecordFactory;
import javax.resource.cci.ResourceAdapterMetaData;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.storage.DefaultFulltextParser;
import org.nuxeo.ecm.core.storage.FulltextParser;
import org.nuxeo.ecm.core.storage.StorageException;
import org.nuxeo.ecm.core.storage.binary.BinaryGarbageCollector;
import org.nuxeo.ecm.core.storage.binary.BinaryManager;
import org.nuxeo.ecm.core.storage.binary.BinaryManagerDescriptor;
import org.nuxeo.ecm.core.storage.binary.BinaryManagerService;
import org.nuxeo.ecm.core.storage.binary.DefaultBinaryManager;
import org.nuxeo.ecm.core.storage.lock.LockManager;
import org.nuxeo.ecm.core.storage.lock.LockManagerService;
import org.nuxeo.ecm.core.storage.sql.RepositoryBackend.MapperKind;
import org.nuxeo.ecm.core.storage.sql.Session.PathResolver;
import org.nuxeo.ecm.core.storage.sql.jdbc.JDBCBackend;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.metrics.MetricsService;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.SharedMetricRegistries;

/**
 * {@link Repository} implementation, to be extended by backend-specific initialization code.
 *
 * @see RepositoryBackend
 */
public class RepositoryImpl implements Repository {

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(RepositoryImpl.class);

    protected final RepositoryDescriptor repositoryDescriptor;

    protected final Class<? extends FulltextParser> fulltextParserClass;

    protected final BinaryManager binaryManager;

    private final RepositoryBackend backend;

    private final Collection<SessionImpl> sessions;

    protected final MetricRegistry registry = SharedMetricRegistries.getOrCreate(MetricsService.class.getName());

    protected final Counter repositoryUp;

    protected final Counter sessionCount;

    private LockManager lockManager;

    /** Propagator of invalidations to all local mappers' caches. */
    private final InvalidationsPropagator cachePropagator;

    private Model model;

    /**
     * Transient id for this repository assigned by the server on first connection. This is not persisted.
     */
    public String repositoryId;

    public RepositoryImpl(RepositoryDescriptor repositoryDescriptor) throws StorageException {
        this.repositoryDescriptor = repositoryDescriptor;
        sessions = new CopyOnWriteArrayList<SessionImpl>();
        cachePropagator = new InvalidationsPropagator("cache-" + this);

        String className = repositoryDescriptor.fulltextParser;
        if (StringUtils.isBlank(className)) {
            className = DefaultFulltextParser.class.getName();
        }
        Class<?> klass;
        try {
            klass = Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new StorageException("Unknown fulltext parser class: " + className, e);
        }
        if (!FulltextParser.class.isAssignableFrom(klass)) {
            throw new StorageException("Invalid fulltext parser class: " + className);
        }
        fulltextParserClass = (Class<? extends FulltextParser>) klass;

        binaryManager = createBinaryManager();
        backend = createBackend();
        repositoryUp = registry.counter(MetricRegistry.name("nuxeo", "repositories", repositoryDescriptor.name,
                "instance-up"));
        repositoryUp.inc();
        sessionCount = registry.counter(MetricRegistry.name("nuxeo", "repositories", repositoryDescriptor.name,
                "sessions"));
        createMetricsGauges();
    }

    protected void createMetricsGauges() {
        String gaugeName = MetricRegistry.name("nuxeo", "repositories", repositoryDescriptor.name, "caches", "size");
        registry.remove(gaugeName);
        registry.register(gaugeName, new Gauge<Long>() {
            @Override
            public Long getValue() {
                return getCacheSize();
            }

        });
        gaugeName = MetricRegistry.name("nuxeo", "repositories", repositoryDescriptor.name, "caches", "pristines");
        registry.remove(gaugeName);
        registry.register(gaugeName, new Gauge<Long>() {
            @Override
            public Long getValue() {
                return getCachePristineSize();
            }
        });
        gaugeName = MetricRegistry.name("nuxeo", "repositories", repositoryDescriptor.name, "caches", "selections");
        ;
        registry.remove(gaugeName);
        registry.register(gaugeName, new Gauge<Long>() {
            @Override
            public Long getValue() {
                return getCacheSelectionSize();
            }
        });
        gaugeName = MetricRegistry.name("nuxeo", "repositories", repositoryDescriptor.name, "caches", "mappers");
        ;
        registry.remove(gaugeName);
        registry.register(gaugeName, new Gauge<Long>() {
            @Override
            public Long getValue() {
                return getCacheMapperSize();
            }
        });
    }

    protected BinaryManager createBinaryManager() throws StorageException {
        try {
            Class<? extends BinaryManager> klass = repositoryDescriptor.binaryManagerClass;
            if (klass == null) {
                klass = DefaultBinaryManager.class;
            }
            BinaryManager binaryManager = klass.newInstance();
            BinaryManagerDescriptor binaryManagerDescriptor = new BinaryManagerDescriptor();
            binaryManagerDescriptor.repositoryName = repositoryDescriptor.name;
            binaryManagerDescriptor.klass = klass;
            binaryManagerDescriptor.key = repositoryDescriptor.binaryManagerKey;
            binaryManagerDescriptor.storePath = repositoryDescriptor.binaryStorePath;
            binaryManager.initialize(binaryManagerDescriptor);
            BinaryManagerService bms = Framework.getLocalService(BinaryManagerService.class);
            bms.addBinaryManager(repositoryDescriptor.name, binaryManager);
            return binaryManager;
        } catch (ReflectiveOperationException | IOException e) {
            throw new StorageException(e);
        }
    }

    protected RepositoryBackend createBackend() throws StorageException {
        Class<? extends RepositoryBackend> backendClass = repositoryDescriptor.backendClass;
        if (backendClass == null) {
            backendClass = JDBCBackend.class;
        }
        try {
            RepositoryBackend backend = backendClass.newInstance();
            backend.initialize(this);
            return backend;
        } catch (StorageException e) {
            throw e;
        } catch (ReflectiveOperationException e) {
            throw new StorageException(e);
        }
    }

    protected Mapper createCachingMapper(Model model, Mapper mapper) throws StorageException {
        try {
            Class<? extends CachingMapper> cachingMapperClass = getCachingMapperClass();
            if (cachingMapperClass == null) {
                return mapper;
            }
            CachingMapper cachingMapper = cachingMapperClass.newInstance();
            cachingMapper.initialize(getName(), model, mapper, cachePropagator,
                    repositoryDescriptor.cachingMapperProperties);
            return cachingMapper;
        } catch (ReflectiveOperationException e) {
            throw new StorageException(e);
        }
    }

    protected Class<? extends CachingMapper> getCachingMapperClass() {
        if (!repositoryDescriptor.getCachingMapperEnabled()) {
            return null;
        }
        Class<? extends CachingMapper> cachingMapperClass = repositoryDescriptor.cachingMapperClass;
        if (cachingMapperClass == null) {
            // default cache
            cachingMapperClass = SoftRefCachingMapper.class;
        }
        return cachingMapperClass;
    }

    public RepositoryDescriptor getRepositoryDescriptor() {
        return repositoryDescriptor;
    }

    public LockManager getLockManager() {
        return lockManager;
    }

    public Model getModel() {
        return model;
    }

    public RepositoryBackend getBackend() {
        return backend;
    }

    public Class<? extends FulltextParser> getFulltextParserClass() {
        return fulltextParserClass;
    }

    /*
     * ----- javax.resource.cci.ConnectionFactory -----
     */

    /**
     * Gets a new connection.
     *
     * @param connectionSpec the parameters to use to connect (unused)
     * @return the session
     * @throws StorageException
     */
    @Override
    public SessionImpl getConnection(ConnectionSpec connectionSpec) throws StorageException {
        return getConnection();
    }

    /**
     * Gets a new connection.
     *
     * @return the session
     * @throws StorageException
     */
    @Override
    public synchronized SessionImpl getConnection() throws StorageException {
        if (Framework.getRuntime().isShuttingDown()) {
            throw new IllegalStateException("Cannot open connection, runtime is shutting down");
        }
        if (model == null) {
            initRepository();
        }
        SessionPathResolver pathResolver = new SessionPathResolver();
        Mapper mapper = backend.newMapper(model, pathResolver, null);
        SessionImpl session = newSession(model, mapper);
        pathResolver.setSession(session);
        sessions.add(session);
        sessionCount.inc();
        return session;
    }

    protected void initRepository() throws StorageException {
        log.debug("Initializing");
        ModelSetup modelSetup = new ModelSetup();
        modelSetup.repositoryDescriptor = repositoryDescriptor;
        backend.initializeModelSetup(modelSetup);
        model = new Model(modelSetup);
        backend.initializeModel(model);
        initLockManager();

        // create the mapper for the cluster node handler
        if (repositoryDescriptor.getClusteringEnabled()) {
            backend.newMapper(model, null, MapperKind.CLUSTER_NODE_HANDLER);
            log.info("Clustering enabled with " + repositoryDescriptor.getClusteringDelay()
                    + " ms delay for repository: " + getName());
        }

        // log once which mapper cache is being used
        Class<? extends CachingMapper> cachingMapperClass = getCachingMapperClass();
        if (cachingMapperClass == null) {
            log.warn("VCS Mapper cache is disabled.");
        } else {
            log.info("VCS Mapper cache using: " + cachingMapperClass.getName());
        }
    }

    protected void initLockManager() throws StorageException {
        String lockManagerName = getName(); // TODO configure in repo descriptor
        LockManagerService lockManagerService = Framework.getService(LockManagerService.class);
        lockManager = lockManagerService.getLockManager(lockManagerName);
        if (lockManager == null) {
            // no descriptor
            // default to a VCSLockManager
            lockManager = new VCSLockManager(lockManagerName);
        }
        log.info("Repository " + getName() + " using lock manager " + lockManager);
    }

    protected SessionImpl newSession(Model model, Mapper mapper) throws StorageException {
        mapper = createCachingMapper(model, mapper);
        return new SessionImpl(this, model, mapper);
    }

    public static class SessionPathResolver implements PathResolver {

        private Session session;

        protected void setSession(Session session) {
            this.session = session;
        }

        @Override
        public Serializable getIdForPath(String path) throws StorageException {
            Node node = session.getNodeByPath(path, null);
            return node == null ? null : node.getId();
        }
    }

    /*
     * -----
     */

    @Override
    public ResourceAdapterMetaData getMetaData() {
        throw new UnsupportedOperationException();
    }

    @Override
    public RecordFactory getRecordFactory() {
        throw new UnsupportedOperationException();
    }

    /*
     * ----- javax.resource.Referenceable -----
     */

    private Reference reference;

    @Override
    public void setReference(Reference reference) {
        this.reference = reference;
    }

    @Override
    public Reference getReference() {
        return reference;
    }

    /*
     * ----- Repository -----
     */

    @Override
    public synchronized void close() throws StorageException {
        closeAllSessions();
        model = null;
        backend.shutdown();
        binaryManager.close();
        BinaryManagerService bms = Framework.getLocalService(BinaryManagerService.class);
        bms.removeBinaryManager(repositoryDescriptor.name);

        registry.remove(MetricRegistry.name(RepositoryImpl.class, getName(), "cache-size"));
        registry.remove(MetricRegistry.name(PersistenceContext.class, getName(), "cache-size"));
        registry.remove(MetricRegistry.name(SelectionContext.class, getName(), "cache-size"));
    }

    protected synchronized void closeAllSessions() throws StorageException {
        for (SessionImpl session : sessions) {
            if (!session.isLive()) {
                continue;
            }
            session.closeSession();
        }
        sessions.clear();
        sessionCount.dec(sessionCount.getCount());
        if (lockManager != null) {
            lockManager.close();
        }
    }

    /*
     * ----- RepositoryManagement -----
     */

    @Override
    public String getName() {
        return repositoryDescriptor.name;
    }

    @Override
    public int getActiveSessionsCount() {
        return sessions.size();
    }

    @Override
    public int clearCaches() {
        int n = 0;
        for (SessionImpl session : sessions) {
            n += session.clearCaches();
        }
        if (lockManager != null) {
            lockManager.clearCaches();
        }
        return n;
    }

    @Override
    public long getCacheSize() {
        long size = 0;
        for (SessionImpl session : sessions) {
            size += session.getCacheSize();
        }
        return size;
    }

    public long getCacheMapperSize() {
        long size = 0;
        for (SessionImpl session : sessions) {
            size += session.getCacheMapperSize();
        }
        return size;
    }

    @Override
    public long getCachePristineSize() {
        long size = 0;
        for (SessionImpl session : sessions) {
            size += session.getCachePristineSize();
        }
        return size;
    }

    @Override
    public long getCacheSelectionSize() {
        long size = 0;
        for (SessionImpl session : sessions) {
            size += session.getCacheSelectionSize();
        }
        return size;
    }

    @Override
    public void processClusterInvalidationsNext() {
        // TODO pass through or something
    }

    @Override
    public BinaryGarbageCollector getBinaryGarbageCollector() {
        return binaryManager.getGarbageCollector();
    }

    @Override
    public void markReferencedBinaries(BinaryGarbageCollector gc) {
        try {
            SessionImpl conn = getConnection();
            try {
                conn.markReferencedBinaries(gc);
            } finally {
                conn.close();
            }
        } catch (ResourceException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int cleanupDeletedDocuments(int max, Calendar beforeTime) {
        if (!repositoryDescriptor.getSoftDeleteEnabled()) {
            return 0;
        }
        try {
            SessionImpl conn = getConnection();
            try {
                return conn.cleanupDeletedDocuments(max, beforeTime);
            } finally {
                conn.close();
            }
        } catch (ResourceException e) {
            throw new RuntimeException(e);
        }
    }

    /*
     * ----- -----
     */

    // callback by session at close time
    protected void closeSession(SessionImpl session) {
        sessions.remove(session);
        sessionCount.dec();
    }

}
