package com.iamprem.goodrive.db;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.ParentReference;
import com.iamprem.goodrive.entity.FilesMeta;
import com.iamprem.goodrive.filesystem.Attributes;
import com.iamprem.goodrive.main.App;

import javax.print.DocFlavor;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Date;
import java.util.List;

/**
 * Created by prem on 8/23/15.
 */
public class DBWrite {

    private DBWrite(){
        // Private Constructor
    }

    // Insert details about the new files to db on the first run
    public static void insertFile(File file, java.io.File diskFile) throws IOException, SQLException {

        String id = file.getId();
        String localName = diskFile.getName();
        String remoteName = file.getTitle();
        String localPath = diskFile.getPath();
        String parentIds = "";
        List<ParentReference> parentList = file.getParents();
        if (parentList.size() == 1){
            parentIds = parentList.get(0).getId();
        } else{
            for (ParentReference parentReference : parentList) {
                parentIds = parentIds + parentReference.getId() + ";";
            }
        }
        String remoteStatus = "Synced";
        String localStatus = "Synced";
        long localModified = file.getModifiedDate().getValue();
        String mimeType = file.getMimeType();

        Connection con = App.conn;

        Statement stmt = con.createStatement();
        String sql = "INSERT INTO files (id, localname, remotename, localpath, parentid, remotestatus, localstatus, " +
                "localmodified, mimetype) " +
                "VALUES ('"+id+"', '"+localName+"', '"+remoteName+"', '"+localPath+"', '"+parentIds+"', '"+remoteStatus
                +"', '"+localStatus+"',"+localModified+", '"+mimeType+"' );";
        stmt.executeUpdate(sql);
        stmt.close();
    }

    //While downloading the latest changes, if a file is created in remote; on downloading file watcher automatically
    //create an entry in db. So we need to update the file id and parent id from the drive file object
    public static void updateFile(File file, java.io.File diskFile) throws IOException, SQLException {

        String id = file.getId();
        String localName = diskFile.getName();
        String remoteName = file.getTitle();
        String localPath = diskFile.getPath();
        String parentIds = "";
        List<ParentReference> parentList = file.getParents();
        if (parentList.size() == 1){
            parentIds = parentList.get(0).getId();
        } else{
            for (ParentReference parentReference : parentList) {
                parentIds = parentIds + parentReference.getId() + ";";
            }
        }
        String remoteStatus = "Synced";
        String localStatus = "Synced";
        long localModified = file.getModifiedDate().getValue();
        String mimeType = file.getMimeType();

        Connection con = App.conn;

        Statement stmt = con.createStatement();
        String sql = "UPDATE files SET id = '"+id+"', parentid = '"+parentIds+"', remotestatus = '"+remoteStatus
                +"', localstatus = '"+localStatus+"', localmodified = "+localModified+", mimetype = '"+mimeType+"' " +
                "WHERE localpath = '"+localPath+"';";
        stmt.executeUpdate(sql);
        stmt.close();
    }

    public static void updateFile(FilesMeta fm) throws SQLException {

        Connection con = App.conn;
        Statement stmt = con.createStatement();
        String uQuery;

        //Update the local status after uploading to remote
        uQuery = "UPDATE files SET id = '"+fm.getId()+"', parentid = '"+fm.getParentId()+"', remotestatus = '"
                +fm.getRemoteStatus()+"', localstatus='"+fm.getLocalStatus()+"', localmodified="+fm.getLocalModified()
                +", mimetype='"+fm.getMimeType()+"' WHERE localpath = '"+fm.getLocalPath()+"' AND localmodified/1000 <= "+
                fm.getLocalModified()+"";
        stmt.executeUpdate(uQuery);

        //Even after the upload local has the new version which needs to be uploaded. So not modifying the local status
        uQuery = "UPDATE files SET id = '"+fm.getId()+"', parentid = '"+fm.getParentId()+"', remotestatus = '"
                +fm.getRemoteStatus()+"', localmodified="+fm.getLocalModified()
                +", mimetype='"+fm.getMimeType()+"' WHERE localpath = '"+fm.getLocalPath()+"' AND localmodified/1000 > "+
                fm.getLocalModified()+"";
        stmt.executeUpdate(uQuery);


    }

    //Update the db entry after downloading the latest file. Downloading file's local and remote status also reset
    // to Synced due to the parallel WatchDir's localstatus change
    public static void updateFileModified(FilesMeta fm) throws SQLException {

        String id = fm.getId();
        Connection con = App.conn;
        Statement stmt = con.createStatement();
        String sql = "UPDATE files SET localmodified = "+fm.getLocalModified()+", localstatus = 'Synced'," +
                "remotestatus = 'Synced' WHERE id = '"+fm.getId()+"' AND remotestatus = 'ENTRY_MODIFY';";
        stmt.executeUpdate(sql);
        stmt.close();
    }

    //Before downloading the latest file from drive, setting the remotestatus to ENTRY_MODIFY to handle the
    //WatchDir's consequence on downloading the file.
    public static void updateFileRemoteStatus(String localPath, String remoteStatus) throws SQLException {
        Connection con = App.conn;
        Statement stmt = con.createStatement();
        String sql = "UPDATE files SET remotestatus = '"+remoteStatus+"' WHERE localpath = '"+localPath+"';";
        stmt.executeUpdate(sql);
        stmt.close();
    }

    //Only for ENTRY_MODIFY or ENTRY_DELETE
    public static void updateFileLocalStatus(String localPath, String localStatus) throws SQLException {

        Connection con = App.conn;
        Statement stmt = con.createStatement();
        String sql = "UPDATE files SET localstatus = '"+localStatus+"', localmodified = '"+new Date().getTime()
                +"' WHERE localpath = '"+localPath+"';";
        stmt.executeUpdate(sql);
        stmt.close();
    }

    //Update deleted or created file/sub-files if already present in db or create if not!
    public static void updateFileTreeLocalStatus(String localPath, String localStatus) throws SQLException, IOException {

        Connection con = App.conn;
        Statement stmt = con.createStatement();
        String sql = "SELECT mimetype FROM files WHERE localpath = '"+localPath+"';";
        ResultSet rs = stmt.executeQuery(sql);

        if (rs.next() && rs.getString("mimetype").equals("application/vnd.google-apps.folder")){

            if (localStatus.equals("ENTRY_CREATE")){

                Files.walkFileTree(Paths.get(localPath), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                            throws IOException {
                        try {
                            insertFile(dir.toString(), dir.getFileName().toString(),"ENTRY_CREATE");
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                        return FileVisitResult.CONTINUE;
                    }
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        try {
                            insertFile(file.toString(), file.getFileName().toString(), "ENTRY_CREATE");
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });

            } else if (localStatus.equals("ENTRY_DELETE")){
                String sql1 = "SELECT localpath FROM files WHERE localpath LIKE '"+localPath+java.io.File.separator+"%';";
                ResultSet rs1 = stmt.executeQuery(sql1);
                if (rs1.next()){
                    do {
                        updateFileLocalStatus(rs1.getString("localpath"),localStatus);
                    }while(rs1.next());
                }
                updateFileLocalStatus(localPath, localStatus);
            }

        } else{
            insertFile(localPath,Paths.get(localPath).getFileName().toString(),localStatus);
        }

        stmt.close();
    }


    // Mostly localStatus would be ENTRY_CREATE
    public static void insertFile(String localPath, String localName, String localStatus) throws SQLException {

        String remoteName = localName;
        String mimeType;
        Connection con = App.conn;
        Statement stmt = con.createStatement();

        String sql1 = "SELECT * FROM files WHERE localpath = '"+localPath+"';";
        String sql;
        ResultSet rs = stmt.executeQuery(sql1);

        if (rs.next()){
            updateFileLocalStatus(localPath, localStatus);
        } else{
            if (Files.isDirectory(Paths.get(localPath))){
                mimeType = "application/vnd.google-apps.folder";
                sql = "INSERT INTO files (id, localname, remotename, localpath, parentid, remotestatus, localstatus, " +
                        "localmodified, mimetype) " +
                        "VALUES (null, '"+localName+"', '"+remoteName+"', '"+localPath+"', null, null, '"+localStatus
                        +"',"+new Date().getTime()+", '"+mimeType+"' );";
            } else{
                sql = "INSERT INTO files (id, localname, remotename, localpath, parentid, remotestatus, localstatus, " +
                        "localmodified, mimetype) " +
                        "VALUES (null, '"+localName+"', '"+remoteName+"', '"+localPath+"', null, null, '"+localStatus
                        +"',"+new Date().getTime()+", null );";
            }
            stmt.executeUpdate(sql);
        }
        stmt.close();
    }



    public static void deleteFile(String localpath) throws SQLException {

        Connection con = App.conn;
        Statement stmt = con.createStatement();
        String sql = "DELETE FROM files WHERE localpath = '"+localpath+"';";
        stmt.executeUpdate(sql);
        System.out.println("Deleted in DB and REMOTE: "+localpath );
        stmt.close();

    }

    //Delete temp files
    public static void deleteFile() throws SQLException {

        Connection con = App.conn;
        Statement stmt = con.createStatement();
        String sql = "DELETE FROM files WHERE id IS NULL AND localstatus = 'ENTRY_DELETE';";
        stmt.executeUpdate(sql);
        System.out.println("Deleted temp files from DB");
        stmt.close();

    }

}
