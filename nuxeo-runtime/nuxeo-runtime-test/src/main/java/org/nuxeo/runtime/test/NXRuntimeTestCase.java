/*
 * (C) Copyright 2006-2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 * $Id$
 */

package org.nuxeo.runtime.test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jmock.MockObjectTestCase;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.osgi.OSGiAdapter;
import org.nuxeo.runtime.RuntimeService;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.launcher.BundleFile;
import org.nuxeo.runtime.launcher.DirectoryBundleFile;
import org.nuxeo.runtime.launcher.JarBundleFile;
import org.nuxeo.runtime.launcher.StandaloneBundleLoader;
import org.osgi.framework.Bundle;

/**
 * Abstract base class for test cases that require a test runtime service.
 * <p>
 * The runtime service itself is conveniently available as the
 * <code>runtime</code> instance variable in derived classes.
 *
 * @author  <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 *
 */
public abstract class NXRuntimeTestCase extends MockObjectTestCase {

    private static final Log log = LogFactory.getLog(NXRuntimeTestCase.class);

    protected RuntimeService runtime;

    protected URL[] urls; // classpath urls, used for bundles lookup

    protected NXRuntimeTestCase() {
    }

    protected NXRuntimeTestCase(String name) {
        super(name);
    }

    private File workingDir;

    private static int counter = 0;

    private StandaloneBundleLoader bundleLoader;

    private Set<URL> readUrls;

    private HashMap<String, BundleFile> bundles;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        wipeRuntime();
        initUrls();
        if (urls == null) {
            initTestRuntime();
        } else {
            initOsgiRuntime();
        }
    }

    @Override
    protected void tearDown() throws Exception {
        wipeRuntime();
        if (workingDir != null) {
            FileUtils.deleteTree(workingDir);
        }
        readUrls = null;
        bundles = null;
        super.tearDown();
    }

    private synchronized String generateId() {
        long stamp = System.currentTimeMillis();
        counter ++;
        return Long.toHexString(stamp) + '-'
                + System.identityHashCode(System.class) + '.' + counter;
    }

    protected void initOsgiRuntime() throws Exception {
        try {
            workingDir = File.createTempFile("NXOSGITestFramework", generateId());
            workingDir.delete();
        } catch (IOException e) {
            log.error("Could not init working directory", e);
            throw e;
        }
        OSGiAdapter osgi = new OSGiAdapter(workingDir);
        bundleLoader = new StandaloneBundleLoader(osgi,
                NXRuntimeTestCase.class.getClassLoader());
        Thread.currentThread().setContextClassLoader(bundleLoader.getSharedClassLoader());

        bundleLoader.setScanForNestedJARs(false); // for now
        bundleLoader.setExtractNestedJARs(false);

        BundleFile bundleFile = lookupBundle("org.nuxeo.runtime");
        Bundle bundle = new RootRuntimeBundle(osgi, bundleFile,
                bundleLoader.getClass().getClassLoader(), true);
        bundle.start();
        runtime = Framework.getRuntime();
        assertNotNull(runtime);

        // avoid Streaming and Remoting services: useless and can't work
        deployContrib(bundleFile, "OSGI-INF/DeploymentService.xml");
        deployContrib(bundleFile, "OSGI-INF/LoginComponent.xml");
        deployContrib(bundleFile, "OSGI-INF/ServiceManagement.xml");
        deployContrib(bundleFile, "OSGI-INF/EventService.xml");
        deployContrib(bundleFile, "OSGI-INF/DefaultJBossBindings.xml");
    }

    protected void initTestRuntime() throws Exception {
        runtime = new TestRuntime();
        Framework.initialize(runtime);
        deployContrib("org.nuxeo.runtime.test", "EventService.xml");
        deployContrib("org.nuxeo.runtime.test", "DeploymentService.xml");
    }

    protected void initUrls() {
        ClassLoader classLoader = NXRuntimeTestCase.class.getClassLoader();
        if (!(classLoader instanceof URLClassLoader)) {
            log.warn("Unknow classloader type: "
                    + classLoader.getClass().getName() +
                    "\nWon't be able to load OSGI bundles");
            return;
        }
        urls = ((URLClassLoader) classLoader).getURLs();
        StringBuilder sb = new StringBuilder();
        sb.append("URLs on the classpath: ");
        for (URL url:urls) {
            sb.append(url.toString());
            sb.append('\n');
        }
        log.debug(sb.toString());
        readUrls = new HashSet<URL>();
        bundles = new HashMap<String, BundleFile>();
    }

    /**
     * Makes sure there is no previous runtime hanging around.
     * <p>This happens for instance if a previous test had errors in its
     * <code>setUp()</code>, because tearDown has not been called</p>
     *
     * @throws Exception
     */
    protected void wipeRuntime() throws Exception {
        // Make sure there is no active runtime (this might happen if an
        // exception is raised during a previous setUp -> tearDown is not called
        // afterwards).
        runtime = null;
        if (Framework.getRuntime() != null) {
            Framework.shutdown();
        }
    }

    public static URL getResource(String resource) {
        return Thread.currentThread().getContextClassLoader()
                .getResource(resource);
    }

    /**
     * @deprecated use <code>deployContrib()</code> instead
     */
    @Deprecated
    public void deploy(String contrib) {
        deployContrib(contrib);
    }

    protected void deployContrib(URL url) {
        assertEquals(runtime, Framework.getRuntime());
        log.info("Deploying contribution from " + url.toString());
        try {
            runtime.getContext().deploy(url);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Failed to deploy contrib " + url.toString());
        }
    }

    /**
     * Deploys a contribution file by looking for it in the class loader.
     * <p>The first contribution file found by the class loader will be used.
     * You have no guarantee in case of name collisions</p>
     *
     * @deprecated use the less ambiguous {@method deployContrib(bundleName, contrib)}
     * @param contrib the relative path to the contribution file
     */
    @Deprecated
    public void deployContrib(String contrib) {
        URL url = getResource(contrib);
        assertNotNull("Test contribution not found: " + contrib, url);
        deployContrib(url);
    }

    protected void deployContrib(BundleFile bundleFile, String contrib) {
        URL url = bundleFile.getEntry(contrib);
        if (url == null) {
            fail(String.format("Could not find entry %s in bundle '%s",
                    contrib, bundleFile.getURL()));
        }
        deployContrib(url);
    }

    /**
     * Deploys a contribution from a given bundle.
     * <p>
     * The path will be relative to the bundle root.
     * Example:
     * <code>
     * deployContrib("org.nuxeo.ecm.core", "OSGI-INF/CoreExtensions.xml")
     * </code>
     * <p>
     * For compatibility reasons the name of the bundle may be a jar name, but
     * this use is discouraged and deprecated.
     *
     * @param bundle The name of the bundle to peek the contrib in
     * @param contrib The path to contrib in the bundle.
     * @throws Exception
     */
    public void deployContrib(String bundle, String contrib) throws Exception {
        deployContrib(lookupBundle(bundle), contrib);
    }

    /**
     * @deprecated use {@link #undeployContrib(String, String)} instead
     */
    @Deprecated
    public void undeploy(String contrib) {
        undeployContrib(contrib);
    }

    /**
     * @deprecated use {@link #undeployContrib(String, String)} instead
     */
    @Deprecated
    public void undeployContrib(String contrib) {
        URL url = getResource(contrib);
        assertNotNull("Test contribution not found: " + contrib, url);
        deployContrib(url);
    }

    /**
     * Undeploys a contribution from a given bundle.
     * <p>
     * The path will be relative to the bundle root.
     * Example:
     * <code>
     * undeployContrib("org.nuxeo.ecm.core", "OSGI-INF/CoreExtensions.xml")
     * </code>
     *
     * @param bundle the bundle
     * @param contrib the contribution
     * @throws Exception
     */
    public void undeployContrib(String bundle, String contrib) throws Exception {
        BundleFile b = lookupBundle(bundle);
        URL url = b.getEntry(contrib);
        if (url == null) {
            fail(String.format("Could not find entry %s in bundle '%s'",
                    contrib, b.getURL()));
        }
        runtime.getContext().undeploy(url);
    }

    protected void undeployContrib(URL url, String contrib) throws Exception {
        assertEquals(runtime, Framework.getRuntime());
        log.info("Undeploying contribution from " + url.toString());
        try {
            runtime.getContext().undeploy(url);
        } catch (Exception e) {
            e.printStackTrace();
            fail("Failed to undeploy contrib " + url.toString());
        }

    }

    protected static boolean isVersionSuffix(String s) {
        if (s.length() == 0) {
            return true;
        }
        return s.matches("-(\\d+\\.?)+(-SNAPSHOT)?(\\.\\w+)?");
    }

    /**
     * Resolves an URL for bundle deployment code.
     * <p>TODO: Implementation could be finer...</p>
     *
     * @return the resolved url
     */
    protected URL lookupBundleUrl(String bundle) {
        for (URL url: urls) {
            String[] pathElts = url.getPath().split("/");
            for (int i = 0; i < pathElts.length; i++) {
                if (pathElts[i].startsWith(bundle)
                        && isVersionSuffix(pathElts[i].substring(bundle.length()))) {
                    // we want the main version of the bundle
                    boolean isTestVersion = false;
                    for (int j = i+1; j < pathElts.length; j++) {
                        // ok for Eclipse (/test) and Maven (/test-classes)
                        if (pathElts[j].startsWith("test")) {
                            isTestVersion = true;
                            break;
                        }
                    }
                    if (!isTestVersion) {
                        log.info("Resolved " + bundle + " as " + url.toString());
                        return url;
                    }
                }
            }
        }
        throw new RuntimeException("Could not resolve bundle " + bundle);
    }

    /**
     * Deploy a whole OSGI bundle.
     * <p>The lookup is first done on symbolic name, as set in <code>MANIFEST.MF</code>
     * and then falls back to the bundle url (e.g., <code>nuxeo-platform-search-api</code>)
     * for backwards compatibility.
     *
     * @param bundle the symbolic name
     * @throws Exception
     */
    public void deployBundle(String bundle) throws Exception {
        BundleFile bundleFile = lookupBundle(bundle);
        bundleLoader.loadBundle(bundleFile);
        bundleLoader.installBundle(bundleFile);
    }

    protected String readSymbolicName(BundleFile bf) {
        Manifest manifest = bf.getManifest();
        if (manifest == null) {
            return null;
        }
        Attributes attrs = manifest.getMainAttributes();
        String name = (String) attrs.getValue("Bundle-SymbolicName");
        if (name == null) {
            return null;
        }
        String[] sp = name.split(";", 2);
        return sp[0];
    }

    protected BundleFile lookupBundle(String bundleName) throws Exception {
        BundleFile bundleFile = bundles.get(bundleName);
        if (bundleFile != null) {
            return bundleFile;
        }
        for (URL url: urls) {
            if (readUrls.contains(url)) {
                continue;
            }
            File file = new File(url.toURI());
            readUrls.add(url);
            try {
                if (file.isDirectory()) {
                    bundleFile = new DirectoryBundleFile(file);
                } else {
                    bundleFile = new JarBundleFile(file);
                }
            } catch (IOException e) {
                // no manifest => not a bundle
                continue;
            }
            String symbolicName = readSymbolicName(bundleFile);
            if (symbolicName != null) {
                log.info(String.format("Bundle '%s' has URL %s", symbolicName, url));
                bundles.put(symbolicName, bundleFile);
            }
            if (bundleName.equals(symbolicName)) {
                return bundleFile;
            }
        }
        log.warn(String.format("No bundle with symbolic name '%s'; Falling back to deprecated url lookup scheme", bundleName));
        return oldLookupBundle(bundleName);
    }

    @Deprecated
    protected BundleFile oldLookupBundle(String bundle) throws Exception {
        URL url = lookupBundleUrl(bundle);
        File file = new File(url.toURI());
        BundleFile bundleFile;
        if (file.isDirectory()) {
            bundleFile = new DirectoryBundleFile(file);
        } else {
            bundleFile = new JarBundleFile(file);
        }
        log.warn(String.format(
                "URL-based bundle lookup is deprecated. Please use the symbolic name from MANIFEST (%s) instead",
                readSymbolicName(bundleFile))
                );
        return bundleFile;
    }

}