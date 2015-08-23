package com.iamprem.goodrive.main;

import com.google.api.services.drive.Drive;
import com.iamprem.goodrive.db.DBConnection;
import com.iamprem.goodrive.db.DBSchema;
import com.iamprem.goodrive.service.Authenticate;
import com.iamprem.goodrive.service.GoogleDriveServices;
import com.iamprem.goodrive.util.AppUtils;
import com.iamprem.goodrive.util.DateUtils;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.Properties;

/**
 * Created by prem on 8/17/15.
 */
public class App {

    public static void main(String[] args) throws IOException, SQLException {
        // Build a new authorized API client service.
        Drive service = Authenticate.getDriveService();
        String lastSyncVal = String.valueOf(new Date().getTime());

        GoogleDriveServices.getRootId(service);
        if (AppUtils.getLastSynced(GoogleDriveServices.APP_PROP_PATH) == 0){
            //First Time
            GoogleDriveServices.downloadAll(service);
        } else{

            //Start download
            GoogleDriveServices.downloadLatest(service, AppUtils.getLastSynced(GoogleDriveServices.APP_PROP_PATH));
            //Start Upload
//            GoogleDriveServices.upload(service);
        }
        //After sync set lastsync time as before the download starts
        AppUtils.addProperty(GoogleDriveServices.APP_PROP_PATH, "LastSynced", lastSyncVal);

        //DB
//        Connection conn = DBConnection.open();
//
//        DBSchema.createTable(conn);
//        DBConnection.close();
    }
}
