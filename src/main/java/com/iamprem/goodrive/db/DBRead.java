package com.iamprem.goodrive.db;

import com.iamprem.goodrive.entity.FilesMeta;
import com.iamprem.goodrive.main.App;

import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

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

    public static FilesMeta readFileByLocalPath(String localPath) throws SQLException {

        Connection con = App.conn;
        Statement stmt = con.createStatement();
        String query = "Select * from files where localpath = '"+localPath+"';";
        ResultSet rs = stmt.executeQuery(query);
        if (rs.next()){
            String id = rs.getString("id");
            String localName = rs.getString("localname");
            String remoteName = rs.getString("remotename");
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

    public static String getParentId(String childLocalPath) throws SQLException {

        String parent = Paths.get(childLocalPath).getParent().toString();
        FilesMeta fm = readFileByLocalPath(parent);
        if (fm != null){
            if (fm.getId() != null){
                return fm.getId();
            } else{
                System.err.println("Parent could be created newly. Serious ERROR!");
                return null;
            }
        } else {
            System.err.println("Parent entry not found in DB");
            return null;
        }

    }


    //Read filelist from DB by ENTRY_DELETE and return them iff they are also in remote
    //Because we just need to delete the files that are already in the remote
    public static ArrayList<FilesMeta> readFileDeleted() throws SQLException {

        Connection con = App.conn;
        ArrayList<FilesMeta> fmList = new ArrayList<>();
        Statement stmt = con.createStatement();
        String query = "SELECT * FROM files WHERE localstatus = 'ENTRY_DELETE' AND id IS NOT NULL;";
        ResultSet rs = stmt.executeQuery(query);

        if (rs.next()){
            do {
                String id = rs.getString("id");
                String localName = rs.getString("localname");
                String remoteName = rs.getString("remotename");
                String localPath = rs.getString("localpath");
                String parentId = rs.getString("parentid");
                String remoteStatus = rs.getString("remotestatus");
                String localStatus = rs.getString("localstatus");
                String mimeType = rs.getString("mimetype");
                long localModified = rs.getLong("localmodified");
                FilesMeta fm = new FilesMeta(id,localName,remoteName,localPath,parentId,remoteStatus,localStatus,localModified,mimeType);
                fmList.add(fm);

            } while (rs.next());

            stmt.close();
            return fmList;

        } else{
            System.out.println("DB has no ENTRY_DELETE");
            stmt.close();
            return null;
        }
    }

    //Read all modified file even if they doesn't have remote copy(Files could be ENTRY_MODIFY before they even uploaded
    // to the remote.
    // TODO SHould run after pushing the created files/folders with status ENTRY_DELETE
    public static ArrayList<FilesMeta> readFileModified() throws SQLException {

        Connection con = App.conn;
        ArrayList<FilesMeta> fmList = new ArrayList<>();
        Statement stmt = con.createStatement();
        String query = "SELECT * FROM files WHERE localstatus = 'ENTRY_MODIFY' ORDER BY LENGTH(localpath) ASC;";
        //TODO can sort the result based on TIME
        ResultSet rs = stmt.executeQuery(query);

        if (rs.next()){
            do{
                String id = rs.getString("id");
                String localName = rs.getString("localname");
                String remoteName = rs.getString("remotename");
                String localPath = rs.getString("localpath");
                String parentId = rs.getString("parentid");
                String remoteStatus = rs.getString("remotestatus");
                String localStatus = rs.getString("localstatus");
                String mimeType = rs.getString("mimetype");
                long localModified = rs.getLong("localmodified");
                FilesMeta fm = new FilesMeta(id,localName,remoteName,localPath,parentId,remoteStatus,localStatus,localModified,mimeType);
                fmList.add(fm);

            }while(rs.next());
            stmt.close();
            return fmList;

        } else{
            System.out.println("DB has no ENTRY_MODIFY");
            stmt.close();
            return null;
        }
    }


    //Read all newly created file in local from DB which has ENTRY_CREATE. NOTE: New file could also have ENTRY_MODIFY status
    // if they are modified after creation. This method is targeted for newly created unmodified files/FOLDERS(important)
    // to create the folder structure before pushing the ENTRY_MODIFY.
    public static ArrayList<FilesMeta> readFileCreated() throws SQLException {
        Connection con = App.conn;
        ArrayList<FilesMeta> fmList = new ArrayList<>();
        Statement stmt = con.createStatement();
        String query = "SELECT * FROM files WHERE localstatus = 'ENTRY_CREATE' ORDER BY LENGTH(localpath) ASC;";
        //TODO can sort the result based on TIME
        ResultSet rs = stmt.executeQuery(query);

        if (rs.next()){
            do{
                String id = rs.getString("id");
                String localName = rs.getString("localname");
                String remoteName = rs.getString("remotename");
                String localPath = rs.getString("localpath");
                String parentId = rs.getString("parentid");
                String remoteStatus = rs.getString("remotestatus");
                String localStatus = rs.getString("localstatus");
                String mimeType = rs.getString("mimetype");
                long localModified = rs.getLong("localmodified");
                FilesMeta fm = new FilesMeta(id,localName,remoteName,localPath,parentId,remoteStatus,localStatus,localModified,mimeType);
                fmList.add(fm);

            }while(rs.next());
            stmt.close();
            return fmList;

        } else{
            System.out.println("DB has no ENTRY_CREATE");
            stmt.close();
            return null;
        }
    }
}
