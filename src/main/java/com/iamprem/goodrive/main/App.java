package com.iamprem.goodrive.main;

import com.google.api.services.drive.Drive;
import com.iamprem.goodrive.service.Authenticate;
import com.iamprem.goodrive.service.GoogleDriveServices;
import com.iamprem.goodrive.util.AppUtils;

import java.io.IOException;

/**
 * Created by prem on 8/17/15.
 */
public class App {

    public static void main(String[] args) throws IOException {
        // Build a new authorized API client service.
        Drive service = Authenticate.getDriveService();
//        GoogleDriveServices.download(service);
//        AppUtils.setLastSynced(GoogleDriveServices.APP_PROP_PATH);
        GoogleDriveServices.upload(service);
    }
}
