package com.iamprem.goodrive.db;

import com.iamprem.goodrive.service.GoogleDriveServices;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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
            System.out.println("Connection successful! - "+connection.getSchema());
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return connection;
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
