package com.iamprem.goodrive.db;

import com.iamprem.goodrive.service.GoogleDriveServices;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Created by prem on 8/20/15.
 */
public class DBConnection {

    private static Connection connection = null;

    public static Connection open(){
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:"
                    + GoogleDriveServices.CONFIG_PATH
                    + File.separator
                    + "goodrive.db");
            System.out.println("Connection Opened Successfully!");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return connection;
    }

    public static void createTables(Connection connection) throws SQLException {
        Statement stmt = connection.createStatement();
        String cQuery = "CREATE TABLE IF NOT EXISTS files (" +
                "id text," +
                "localname text," +
                "remotename text," +
                "localpath text," +
                "parentid text," +
                "remotestatus text," +
                "localstatus text," +
                "localmodified int," +
                "mimetype text" +
                ");";
        stmt.executeUpdate(cQuery);
        stmt.close();
    }

    public static void close() {
        try {
            connection.close();
            System.out.println("Connection Closed !");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
