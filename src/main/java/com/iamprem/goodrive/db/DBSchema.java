package com.iamprem.goodrive.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Created by prem on 8/21/15.
 */
public class DBSchema {

    public static void createTable(Connection conn){

        try {
            Statement stmt = conn.createStatement();
            stmt.setQueryTimeout(10);
            stmt.executeUpdate("drop table if exists filestatus");
            stmt.executeUpdate("create table filestatus (id integer, name string)");
        } catch (SQLException e) {
            e.printStackTrace();
        }

    }

}
