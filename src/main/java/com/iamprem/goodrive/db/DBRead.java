package com.iamprem.goodrive.db;

import com.iamprem.goodrive.entity.FilesMeta;
import com.iamprem.goodrive.main.App;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Created by prem on 8/23/15.
 */
public class DBRead {

    private DBRead(){}

    public static FilesMeta readFileById(String id) throws SQLException {

        Connection con = App.conn;
        Statement stmt = con.createStatement();
        String query = "Select * from files where id = '"+id+"';";
        ResultSet rs = stmt.executeQuery(query);

        if (rs.next()){
            String localName = rs.getString("localname");
            String remoteName = rs.getString("remotename");
            String localPath = rs.getString("localpath");
            String parentId = rs.getString("parentid");
            String remoteStatus = rs.getString("remotestatus");
            String localStatus = rs.getString("localstatus");
            String mimeType = rs.getString("mimetype");
            long localModified = rs.getLong("localmodified");

            FilesMeta fm = new FilesMeta(id,localName,remoteName,localPath,parentId,remoteStatus,localStatus,localModified,mimeType);
            return fm;

        } else{
            return null;
        }
    }
}
