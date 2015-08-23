package com.iamprem.goodrive.db;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.ParentReference;
import com.iamprem.goodrive.filesystem.Attributes;

import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Created by prem on 8/23/15.
 */
public class DBWrite {

    private DBWrite(){
        // Private Constructor
    }

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
        long localModified = Files.getLastModifiedTime(diskFile.toPath()).toMillis();
        String mimeType = file.getMimeType();

        Connection con = DBConnection.open();

        Statement stmt = con.createStatement();
        String sql = "INSERT INTO files (id, localname, remotename, localpath, parentid, remotestatus, localstatus, " +
                "localmodified, mimetype) " +
                "VALUES ('"+id+"', '"+localName+"', '"+remoteName+"', '"+localPath+"', '"+parentIds+"', '"+remoteStatus
                +"', '"+localStatus+"',"+localModified+", '"+mimeType+"' );";
        stmt.executeUpdate(sql);
        stmt.close();
        con.close();
    }

}
