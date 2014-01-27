/*
 * Copyright 2010 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.phoenix.end2end;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

import org.apache.phoenix.schema.TableNotFoundException;
import org.apache.phoenix.util.PhoenixRuntime;
import org.apache.phoenix.util.TestUtil;
import org.junit.Test;

/**
 * TODO: derived from BaseClientMangedTimeTest, but not setting SCN
 * @author elilevine
 * @since 2.0
 */
public class TenantSpecificTablesDMLTest extends BaseTenantSpecificTablesTest {
    
    @Test
    public void testBasicUpsertSelect() throws Exception {
        Connection conn = DriverManager.getConnection(PHOENIX_JDBC_TENANT_SPECIFIC_URL);
        try {
            conn.setAutoCommit(false);
            conn.createStatement().executeUpdate("upsert into " + TENANT_TABLE_NAME + " (id, tenant_col) values (1, 'Cheap Sunglasses')");
            conn.createStatement().executeUpdate("upsert into " + TENANT_TABLE_NAME + " (id, tenant_col) values (2, 'Viva Las Vegas')");
            conn.commit();
            
            ResultSet rs = conn.createStatement().executeQuery("select tenant_col from " + TENANT_TABLE_NAME + " where id = 1");
            assertTrue("Expected 1 row in result set", rs.next());
            assertEquals("Cheap Sunglasses", rs.getString(1));
            assertFalse("Expected 1 row in result set", rs.next());
        }
        finally {
            conn.close();
        }
    }
    
    private Connection nextConnection(String url) throws SQLException {
        Properties props = new Properties(TestUtil.TEST_PROPERTIES);
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(nextTimestamp()));
        return DriverManager.getConnection(getUrl(), props);
    }
    
    @Test
    public void testJoinWithGlobalTable() throws Exception {
        Connection conn = nextConnection(getUrl());
        conn.createStatement().execute("create table foo (k INTEGER NOT NULL PRIMARY KEY)");
        conn.close();

        conn = nextConnection(getUrl());
        conn.createStatement().execute("upsert into foo(k) values(1)");
        conn.commit();
        conn.close();

        conn = DriverManager.getConnection(PHOENIX_JDBC_TENANT_SPECIFIC_URL);
        try {
            conn.setAutoCommit(false);
            conn.createStatement().executeUpdate("upsert into " + TENANT_TABLE_NAME + " (id, tenant_col) values (1, 'Cheap Sunglasses')");
            conn.createStatement().executeUpdate("upsert into " + TENANT_TABLE_NAME + " (id, tenant_col) values (2, 'Viva Las Vegas')");
            conn.commit();
            
            ResultSet rs = conn.createStatement().executeQuery("select tenant_col from " + TENANT_TABLE_NAME + " join foo on k=id");
            assertTrue("Expected 1 row in result set", rs.next());
            assertEquals("Cheap Sunglasses", rs.getString(1));
            assertFalse("Expected 1 row in result set", rs.next());
        }
        finally {
            conn.close();
        }
    }
    
    @Test
    public void testSelectOnlySeesTenantData() throws Exception {
        Connection conn = DriverManager.getConnection(getUrl());
        try {
            conn.setAutoCommit(true);
            conn.createStatement().executeUpdate("delete from " + PARENT_TABLE_NAME);
            conn.createStatement().executeUpdate("upsert into " + PARENT_TABLE_NAME + " (tenant_id, tenant_type_id, id, user) values ('AC/DC', 'abc', 1, 'Bon Scott')");
            conn.createStatement().executeUpdate("upsert into " + PARENT_TABLE_NAME + " (tenant_id, tenant_type_id, id, user) values ('" + TENANT_ID + "', '" + TENANT_TYPE_ID + "', 1, 'Billy Gibbons')");
            conn.createStatement().executeUpdate("upsert into " + PARENT_TABLE_NAME + " (tenant_id, tenant_type_id, id, user) values ('" + TENANT_ID + "', 'def', 1, 'Billy Gibbons')");
            conn.close();
            
            conn = DriverManager.getConnection(PHOENIX_JDBC_TENANT_SPECIFIC_URL);
            ResultSet rs = conn.createStatement().executeQuery("select user from " + TENANT_TABLE_NAME);
            assertTrue("Expected 1 row in result set", rs.next());
            assertEquals("Billy Gibbons", rs.getString(1));
            assertFalse("Expected 1 row in result set", rs.next());
            
            rs = conn.createStatement().executeQuery("select count(*) from " + TENANT_TABLE_NAME);
            assertTrue("Expected 1 row in result set", rs.next());
            assertEquals(1, rs.getInt(1));
            assertFalse("Expected 1 row in result set", rs.next());
        }
        finally {
            conn.close();
        }
    }
    
    @Test
    public void testDeleteOnlyDeletesTenantData() throws Exception {
        Connection conn = DriverManager.getConnection(getUrl());
        try {
            conn.setAutoCommit(true);
            conn.createStatement().executeUpdate("delete from " + PARENT_TABLE_NAME);
            conn.createStatement().executeUpdate("upsert into " + PARENT_TABLE_NAME + " (tenant_id, tenant_type_id, id, user) values ('AC/DC', 'abc', 1, 'Bon Scott')");
            conn.createStatement().executeUpdate("upsert into " + PARENT_TABLE_NAME + " (tenant_id, tenant_type_id, id, user) values ('" + TENANT_ID + "', '" + TENANT_TYPE_ID + "', 1, 'Billy Gibbons')");
            conn.createStatement().executeUpdate("upsert into " + PARENT_TABLE_NAME + " (tenant_id, tenant_type_id, id, user) values ('" + TENANT_ID + "', 'def', 1, 'Billy Gibbons')");
            conn.close();
            
            conn = DriverManager.getConnection(PHOENIX_JDBC_TENANT_SPECIFIC_URL);
            conn.setAutoCommit(true);
            int count = conn.createStatement().executeUpdate("delete from " + TENANT_TABLE_NAME);
            assertEquals("Expected 1 row have been deleted", 1, count);
            
            ResultSet rs = conn.createStatement().executeQuery("select * from " + TENANT_TABLE_NAME);
            assertFalse("Expected no rows in result set", rs.next());
            conn.close();
            
            conn = DriverManager.getConnection(getUrl());
            rs = conn.createStatement().executeQuery("select count(*) from " + PARENT_TABLE_NAME);
            rs.next();
            assertEquals(2, rs.getInt(1));
        }
        finally {
            conn.close();
        }
    }
    
    @Test
    public void testDeleteOnlyDeletesTenantDataWithNoTenantTypeId() throws Exception {
        Connection conn = DriverManager.getConnection(getUrl());
        try {
            conn.setAutoCommit(true);
            conn.createStatement().executeUpdate("delete from " + PARENT_TABLE_NAME_NO_TENANT_TYPE_ID);
            conn.createStatement().executeUpdate("upsert into " + PARENT_TABLE_NAME_NO_TENANT_TYPE_ID + " (tenant_id, id, user) values ('AC/DC', 1, 'Bon Scott')");
            conn.createStatement().executeUpdate("upsert into " + PARENT_TABLE_NAME_NO_TENANT_TYPE_ID + " (tenant_id, id, user) values ('" + TENANT_ID + "', 1, 'Billy Gibbons')");
            conn.createStatement().executeUpdate("upsert into " + PARENT_TABLE_NAME_NO_TENANT_TYPE_ID + " (tenant_id, id, user) values ('" + TENANT_ID + "', 2, 'Billy Gibbons')");
            conn.close();
            
            conn = DriverManager.getConnection(PHOENIX_JDBC_TENANT_SPECIFIC_URL);
            conn.setAutoCommit(true);
            int count = conn.createStatement().executeUpdate("delete from " + TENANT_TABLE_NAME_NO_TENANT_TYPE_ID);
            assertEquals("Expected 2 rows have been deleted", 2, count);
            
            ResultSet rs = conn.createStatement().executeQuery("select * from " + TENANT_TABLE_NAME_NO_TENANT_TYPE_ID);
            assertFalse("Expected no rows in result set", rs.next());
            conn.close();
            
            conn = DriverManager.getConnection(getUrl());
            rs = conn.createStatement().executeQuery("select count(*) from " + PARENT_TABLE_NAME_NO_TENANT_TYPE_ID);
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
        finally {
            conn.close();
        }
    }
    
    @Test
    public void testDeleteAllTenantTableData() throws Exception {
        Properties props = new Properties();
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(nextTimestamp()));
        Connection conn = DriverManager.getConnection(getUrl(), props);
        try {
            conn.setAutoCommit(true);
            conn.createStatement().executeUpdate("delete from " + PARENT_TABLE_NAME);
            conn.createStatement().executeUpdate("upsert into " + PARENT_TABLE_NAME + " (tenant_id, tenant_type_id, id, user) values ('AC/DC', 'abc', 1, 'Bon Scott')");
            conn.createStatement().executeUpdate("upsert into " + PARENT_TABLE_NAME + " (tenant_id, tenant_type_id, id, user) values ('" + TENANT_ID + "', '" + TENANT_TYPE_ID + "', 1, 'Billy Gibbons')");
            conn.createStatement().executeUpdate("upsert into " + PARENT_TABLE_NAME + " (tenant_id, tenant_type_id, id, user) values ('" + TENANT_ID + "', 'def', 1, 'Billy Gibbons')");
            
            conn.close();
            
            props = new Properties();
            props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(nextTimestamp()));
            conn = DriverManager.getConnection(PHOENIX_JDBC_TENANT_SPECIFIC_URL, props);
            conn.createStatement().execute("delete from " + TENANT_TABLE_NAME);
            conn.commit();
            conn.close();
            
            props = new Properties();
            props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(nextTimestamp()));
            conn = DriverManager.getConnection(getUrl(), props);
            ResultSet rs = conn.createStatement().executeQuery("select count(*) from " + PARENT_TABLE_NAME);
            rs.next();
            assertEquals(2, rs.getInt(1));
        }
        finally {
            conn.close();
        }
    }
    
    @Test
    public void testDropTenantTableDeletesNoData() throws Exception {
        Properties props = new Properties();
        props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(nextTimestamp()));
        Connection conn = DriverManager.getConnection(getUrl(), props);
        try {
            conn.setAutoCommit(true);
            conn.createStatement().executeUpdate("delete from " + PARENT_TABLE_NAME_NO_TENANT_TYPE_ID);
            conn.createStatement().executeUpdate("upsert into " + PARENT_TABLE_NAME_NO_TENANT_TYPE_ID + " (tenant_id, id, user) values ('AC/DC', 1, 'Bon Scott')");
            conn.createStatement().executeUpdate("upsert into " + PARENT_TABLE_NAME_NO_TENANT_TYPE_ID + " (tenant_id, id, user) values ('" + TENANT_ID + "', 1, 'Billy Gibbons')");
            conn.createStatement().executeUpdate("upsert into " + PARENT_TABLE_NAME_NO_TENANT_TYPE_ID + " (tenant_id, id, user) values ('" + TENANT_ID + "', 2, 'Billy Gibbons')");
            
            conn.close();
            
            props = new Properties();
            props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(nextTimestamp()));
            conn = DriverManager.getConnection(PHOENIX_JDBC_TENANT_SPECIFIC_URL, props);
            conn.createStatement().execute("drop view " + TENANT_TABLE_NAME_NO_TENANT_TYPE_ID);
            conn.close();
            
            props = new Properties();
            props.setProperty(PhoenixRuntime.CURRENT_SCN_ATTRIB, Long.toString(nextTimestamp()));
            conn = DriverManager.getConnection(getUrl(), props);
            ResultSet rs = conn.createStatement().executeQuery("select count(*) from " + PARENT_TABLE_NAME_NO_TENANT_TYPE_ID);
            rs.next();
            assertEquals(3, rs.getInt(1));
        }
        finally {
            conn.close();
        }
    }
    
    @Test
    public void testUpsertSelectOnlyUpsertsTenantData() throws Exception {
        Connection conn = DriverManager.getConnection(getUrl());
        try {
            conn.setAutoCommit(true);
            conn.createStatement().executeUpdate("delete from " + PARENT_TABLE_NAME);
            conn.createStatement().executeUpdate("upsert into " + PARENT_TABLE_NAME + " (tenant_id, tenant_type_id, id, user) values ('AC/DC', 'aaa', 1, 'Bon Scott')");
            conn.createStatement().executeUpdate("upsert into " + PARENT_TABLE_NAME + " (tenant_id, tenant_type_id, id, user) values ('" + TENANT_ID + "', '" + TENANT_TYPE_ID + "', 1, 'Billy Gibbons')");
            conn.createStatement().executeUpdate("upsert into " + PARENT_TABLE_NAME + " (tenant_id, tenant_type_id, id, user) values ('" + TENANT_ID + "', 'def', 2, 'Billy Gibbons')");
            conn.close();
            
            conn = DriverManager.getConnection(PHOENIX_JDBC_TENANT_SPECIFIC_URL);
            conn.setAutoCommit(true);
            int count = conn.createStatement().executeUpdate("upsert into " + TENANT_TABLE_NAME + "(id, user) select id+100, user from " + TENANT_TABLE_NAME);
            assertEquals("Expected 1 row to have been inserted", 1, count);
            
            ResultSet rs = conn.createStatement().executeQuery("select count(*) from " + TENANT_TABLE_NAME);
            rs.next();
            assertEquals(2, rs.getInt(1));
        }
        finally {
            conn.close();
        }
    }
    
    @Test
    public void testUpsertSelectOnlyUpsertsTenantDataWithDifferentTenantTable() throws Exception {
        createTestTable(PHOENIX_JDBC_TENANT_SPECIFIC_URL, "CREATE VIEW ANOTHER_TENANT_TABLE ( " + 
            "tenant_col VARCHAR) AS SELECT * FROM PARENT_TABLE WHERE tenant_type_id = 'def'", null, nextTimestamp(), false);
        
        Connection conn = DriverManager.getConnection(getUrl());
        try {
            conn.setAutoCommit(true);
            conn.createStatement().executeUpdate("delete from " + PARENT_TABLE_NAME);
            conn.createStatement().executeUpdate("upsert into " + PARENT_TABLE_NAME + " (tenant_id, tenant_type_id, id, user) values ('AC/DC', 'aaa', 1, 'Bon Scott')");
            conn.createStatement().executeUpdate("upsert into " + PARENT_TABLE_NAME + " (tenant_id, tenant_type_id, id, user) values ('" + TENANT_ID + "', '" + TENANT_TYPE_ID + "', 1, 'Billy Gibbons')");
            conn.createStatement().executeUpdate("upsert into " + PARENT_TABLE_NAME + " (tenant_id, tenant_type_id, id, user) values ('" + TENANT_ID + "', 'def', 2, 'Billy Gibbons')");
            conn.close();
            
            conn = DriverManager.getConnection(PHOENIX_JDBC_TENANT_SPECIFIC_URL);
            conn.setAutoCommit(true);
            int count = conn.createStatement().executeUpdate("upsert into " + TENANT_TABLE_NAME + "(id, user) select id+100, user from ANOTHER_TENANT_TABLE where id=2");
            assertEquals("Expected 1 row to have been inserted", 1, count);
            
            ResultSet rs = conn.createStatement().executeQuery("select count(*) from " + TENANT_TABLE_NAME);
            rs.next();
            assertEquals(2, rs.getInt(1));
        }
        finally {
            conn.close();
        }
    }
    
    @Test
    public void testUpsertValuesOnlyUpsertsTenantData() throws Exception {
        Connection conn = DriverManager.getConnection(PHOENIX_JDBC_TENANT_SPECIFIC_URL);
        try {
            conn.setAutoCommit(true);
            int count = conn.createStatement().executeUpdate("upsert into " + TENANT_TABLE_NAME + " (id, user) values (1, 'Bon Scott')");
            assertEquals("Expected 1 row to have been inserted", 1, count);
            
            ResultSet rs = conn.createStatement().executeQuery("select count(*) from " + TENANT_TABLE_NAME);
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
        finally {
            conn.close();
        }
    }
    
    @Test
    public void testBaseTableCannotBeUsedInStatementsInMultitenantConnections() throws Exception {
        Connection conn = DriverManager.getConnection(PHOENIX_JDBC_TENANT_SPECIFIC_URL);
        try {
            try {
                conn.createStatement().execute("select * from " + PARENT_TABLE_NAME);
                fail();
            }
            catch (TableNotFoundException expected) {};   
        }
        finally {
            conn.close();
        }
    }
    
    @Test
    public void testTenantTableCannotBeUsedInStatementsInNonMultitenantConnections() throws Exception {
        Connection conn = DriverManager.getConnection(getUrl());
        try {
            try {
                conn.createStatement().execute("select * from " + TENANT_TABLE_NAME);
                fail();
            }
            catch (TableNotFoundException expected) {};   
        }
        finally {
            conn.close();
        }
    }
}
