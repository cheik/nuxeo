/*
 * (C) Copyright 2006-2011 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     bstefanescu
 *
 * $Id$
 */

package org.nuxeo.osgi.application;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

import org.nuxeo.osgi.BundleFile;
import org.nuxeo.osgi.DirectoryBundleFile;
import org.nuxeo.osgi.JarBundleFile;
import org.osgi.framework.BundleException;

/**
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 *
 */
public class ClassPath implements ClassPathScanner.Callback {

    protected final List<BundleFile> bundles;
    protected final List<BundleFile> jars;
    protected final List<BundleFile> nestedJars;
    protected final SharedClassLoader loader;
    protected final File nestedJARsDir;

    public ClassPath(SharedClassLoader loader, File nestedJARsDir) {
        bundles = new ArrayList<BundleFile>();
        jars = new ArrayList<BundleFile>();
        nestedJars = new ArrayList<BundleFile>();
        this.loader = loader;
        this.nestedJARsDir = nestedJARsDir;
        nestedJARsDir.mkdirs();
    }

    public List<BundleFile> getBundles() {
        return bundles;
    }

    public List<BundleFile> getJars() {
        return jars;
    }

    public List<BundleFile> getNestedJars() {
        return nestedJars;
    }

    public void scan(List<File> files, boolean scanForNestedJARs, String[] blacklist) {
        new ClassPathScanner(this, scanForNestedJARs, blacklist).scan(files);
    }

    @Override
    public File handleBundle(BundleFile bf) {
        bundles.add(bf);
        loader.addURL(bf.getURL());
        return nestedJARsDir;
    }

    @Override
    public File handleJar(BundleFile bf) {
        jars.add(bf);
        loader.addURL(bf.getURL());
        return nestedJARsDir;
    }

    @Override
    public void handleNestedJar(BundleFile bf) {
        nestedJars.add(bf);
        loader.addURL(bf.getURL());
    }

    public void store(File file) throws BundleException {
        Throwable error = null;
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(file));
            for (BundleFile bf : bundles) {
                writer.append(bf.getFile().getAbsolutePath());
                writer.newLine();
            }
            writer.append("#");
            writer.newLine();
            for (BundleFile bf : jars) {
                writer.append(bf.getFile().getAbsolutePath());
                writer.newLine();
            }
            writer.append("#");
            writer.newLine();
            for (BundleFile bf : nestedJars) {
                writer.append(bf.getFile().getAbsolutePath());
                writer.newLine();
            }
        } catch (Throwable e) {
            error = e;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception e) {
                    error = e;
                }
            }
            if (error != null) {
                file.delete();
                throw new BundleException("failed to write cache file", error);
            }
        }
    }

    public void restore(File file) throws BundleException {
        Throwable error = null;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            List<BundleFile> list = bundles;
            String line = null;
            while (true) {
                line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (line.startsWith("#")) {
                    if (list == bundles) {
                        list = jars;
                    } else if (list == jars) {
                        list = nestedJars;
                    }
                    continue;
                }
                BundleFile bf = null;
                File f = new File(line.trim());
                if (f.isDirectory()) {
                    bf = new DirectoryBundleFile(f);
                } else {
                    bf = new JarBundleFile(f);
                }
                loader.addURL(bf.getURL());
                list.add(bf);
            }
        } catch (Throwable t) {
            error = t;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    error = e;
                }
            }
            if (error != null) {
                throw new BundleException("Failed to load runtime application from cache info", error);
            }
        }
    }

}
