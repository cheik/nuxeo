/*
 * (C) Copyright 2006-2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     bstefanescu
 */
package org.nuxeo.runtime.reload;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.common.Environment;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.common.utils.ZipUtils;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.api.SharedResourceLoader;
import org.nuxeo.runtime.model.ComponentContext;
import org.nuxeo.runtime.model.DefaultComponent;
import org.nuxeo.runtime.services.event.Event;
import org.nuxeo.runtime.services.event.EventService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.packageadmin.PackageAdmin;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
public class ReloadComponent extends DefaultComponent implements ReloadService {

    private static final Log log = LogFactory.getLog(ReloadComponent.class);

    public static final String RELOAD_TOPIC = "org.nuxeo.runtime.reload";

    public static final String FLUSH_EVENT_ID = "flush";
    
    public static final String RELOAD_EVENT_ID = "reload";

    protected static Bundle bundle;

    public static BundleContext getBundleContext() {
        return bundle.getBundleContext();
    }

    public static Bundle getBundle() {
        return bundle;
    }

    @Override
    public void activate(ComponentContext context) throws Exception {
        super.activate(context);
        bundle = context.getRuntimeContext().getBundle();
    }

    @Override
    public void deactivate(ComponentContext context) throws Exception {
        super.deactivate(context);
        bundle = null;
    }

    @Override
    public void flushJaasCache() throws Exception {
        EventService eventService = Framework.getLocalService(EventService.class);
        eventService.sendEvent(new Event("usermanager", "user_changed", this,
                "Deployer")); // the data argument is optional
    }

    @Override
    public void reloadRepository() throws Exception {
        Framework.getLocalService(EventService.class).sendEvent(
                new Event(RELOAD_TOPIC, "reloadRepositories", this, null));
    }

    @Override
    public void flush() throws Exception {
        flushJaasCache();
        EventService eventService = Framework.getLocalService(EventService.class);
        eventService.sendEvent(new Event(RELOAD_TOPIC, FLUSH_EVENT_ID, this,
                null));
    }

    @Override
    public void reload() throws Exception  {
        reloadProperties();     
        EventService eventService = Framework.getLocalService(EventService.class);
        eventService.sendEvent(new Event(RELOAD_TOPIC, RELOAD_EVENT_ID, this,
                null));
    }
    /**
     * Add a JAR to the application classloader - experimental.
     */
    @Override
    public void addJar(File file) throws Exception {
        MutableClassLoaderDelegate mcl = new MutableClassLoaderDelegate(
                ReloadComponent.class.getClassLoader());
        mcl.addURL(file.toURI().toURL());
    }

    /**
     * Remove a JAR from the application classloader - experimental.
     */
    @Override
    public void removeJar(File file) throws Exception {
        // TODO
    }

    public String deployBundle(File file, boolean reloadResourceClasspath)
            throws Exception {
        // TODO this will remove from classpath other bundles deployed at
        // runtime and server was not restarted since.
        String path = file.getAbsolutePath();
        if (reloadResourceClasspath) {
            reloadResourceClassPath(Collections.singletonList(path));
        }
        installWebResources(file); // run fragment processor if needed
        Bundle newBundle = getBundleContext().installBundle(path);
        if (newBundle == null) {
            throw new IllegalArgumentException(
                    "Could not find a valid bundle at path: " + path);
        }
        newBundle.start();
        return newBundle.getSymbolicName();
    }
    
    @Override
    public String deployBundle(File file) throws Exception {
        return deployBundle(file, false);
    }

    public void undeployBundle(String name) throws Exception {
        BundleContext ctx = getBundleContext();
        ServiceReference ref = ctx.getServiceReference(PackageAdmin.class.getName());
        PackageAdmin srv = (PackageAdmin) ctx.getService(ref);
        try {
            for (Bundle b : srv.getBundles(name, null)) {
                b.stop();
                b.uninstall();
            }
        } finally {
            ctx.ungetService(ref);
        }
    }

    @Override
    public void reloadProperties() throws Exception {
        Framework.getRuntime().reloadProperties();
    }

    /**
     * Rebuild the framework resource class loader and add to it the given file
     * paths.
     * <p>
     * The already added paths are removed from the class loader.
     */
    public static void reloadResourceClassPath(Collection<String> files)
            throws Exception {
        Framework.reloadResourceLoader();
        SharedResourceLoader loader = Framework.getResourceLoader();
        for (String path : files) {
            URL url = new File(path).toURI().toURL();
            loader.addURL(url);
        }
    }

    public void installWebResources(File file) throws Exception {
        log.info("running fragment processor");
        // we cannot use DeploymentPreprocessor since the initial preprocessing
        // will be overridden
        if (file.isDirectory()) {
            File war = new File(file, "web");
            war = new File(war, "nuxeo.war");
            if (war.isDirectory()) {
                FileUtils.copyTree(war, getAppDir());
            } else {
                // compatibility mode with studio 1.5 - see NXP-6186
                war = new File(file, "nuxeo.war");
                if (war.isDirectory()) {
                    FileUtils.copyTree(war, getAppDir());
                }
            }
        } else if (file.isFile()) { // a jar
            File war = getWarDir();
            ZipUtils.unzip("web/nuxeo.war", file, war);
            // compatibility mode with studio 1.5 - see NXP-6186
            ZipUtils.unzip("nuxeo.war", file, war);
        }
    }

    public static File getAppDir() {
        return Environment.getDefault().getConfig().getParentFile();
    }

    public static File getWarDir() {
        return new File(getAppDir(), "nuxeo.war");
    }

    @Override
    public void reloadSeamComponents() throws Exception {
        Framework.getLocalService(EventService.class).sendEvent(
                new Event(RELOAD_TOPIC, "reloadSeamComponents", this, null));
    }

}
