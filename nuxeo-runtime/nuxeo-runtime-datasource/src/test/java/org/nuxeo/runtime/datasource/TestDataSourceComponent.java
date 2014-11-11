/*
 * (C) Copyright 2009 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Florent Guillaume
 */

package org.nuxeo.runtime.datasource;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import javax.naming.NamingException;
import javax.sql.DataSource;

import org.nuxeo.common.jndi.NamingContextFactory;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.runtime.api.DataSourceHelper;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.jtajca.NuxeoContainer;
import org.nuxeo.runtime.test.NXRuntimeTestCase;
import org.nuxeo.runtime.transaction.TransactionHelper;

public class TestDataSourceComponent extends NXRuntimeTestCase {

    /** This directory will be deleted and recreated. */
    private static final String DIRECTORY = "target/test/h2";

    /** Property used in the datasource URL. */
    private static final String PROP_NAME = "ds.test.home";

    @Override
    public void setUp() throws Exception {
        NamingContextFactory.setAsInitial();
        super.setUp();
        File dir = new File(DIRECTORY);
        FileUtils.deleteTree(dir);
        dir.mkdirs();
        Framework.getProperties().put(PROP_NAME, dir.getPath());

        deployBundle("org.nuxeo.runtime.datasource");
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
       NamingContextFactory.revertSetAsInitial();
    }

    public void testJNDIName() throws Exception {
        assertEquals("java:comp/env/jdbc/foo",
                DataSourceHelper.getDataSourceJNDIName("foo"));
    }

    protected static void checkDataSourceOk() throws Exception {
        DataSource ds = DataSourceHelper.getDataSource("foo");
        Connection conn = ds.getConnection();
        Statement st = conn.createStatement();
        ResultSet rs = st.executeQuery("SELECT 123");
        assertNotNull(rs);
        assertTrue(rs.next());
        assertEquals(123, rs.getInt(1));
        st.close();
        conn.close();
    }

    public void testNonXANoTM() throws Exception {
        deployContrib("org.nuxeo.runtime.datasource.tests",
                "OSGI-INF/datasource-contrib.xml");
        checkDataSourceOk();
    }

    public void testNonXA() throws Exception {
        NuxeoContainer.install();
        try {
            deployContrib("org.nuxeo.runtime.datasource.tests",
                    "OSGI-INF/datasource-contrib.xml");
            checkDataSourceOk();
        } finally {
            NuxeoContainer.uninstall();
        }
    }

    public void testXANoTM() throws Exception {
        deployContrib("org.nuxeo.runtime.datasource.tests",
                "OSGI-INF/xadatasource-contrib.xml");
        DataSource ds = DataSourceHelper.getDataSource("foo");
        try {
            ds.getConnection();
            fail("Should fail for XA with no TM");
        } catch (RuntimeException e) {
            Throwable t = e.getCause();
            String m = t == null ? e.getMessage() : t.getMessage();
            assertEquals("TransactionManager not found in JNDI", m);
        }
    }

    public void testXA() throws Exception {
        NuxeoContainer.install();
        try {
            deployContrib("org.nuxeo.runtime.datasource.tests",
                    "OSGI-INF/xadatasource-contrib.xml");
            TransactionHelper.startTransaction();
            try {
                checkDataSourceOk();
            } finally {
                TransactionHelper.commitOrRollbackTransaction();
            }
        } finally {
            NuxeoContainer.uninstall();
        }
    }

}
