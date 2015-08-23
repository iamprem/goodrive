package com.iamprem.goodrive.filesystem;

import com.iamprem.goodrive.service.GoogleDriveServices;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

import static java.lang.String.format;

/**
 * Created by prem on 8/17/15.
 */
public class LocalFS {

    /**
     * Create ~/Desktop/GooDrive/ directory
     *
     * @return true if the directory 'home/user/Desktop/GooDrive' is created
     *         successfully
     */

    // TODO: Change Desktop to Home dir
    public static String makeRootDir() {
        java.io.File file = new java.io.File(System.getProperty("user.home") + "/Desktop/GooDrive/");
        if (file.mkdirs()) {
            System.out.println("path created - " + file.getAbsolutePath());
        } else {
            System.out.println(format("path %s already exists", file.getAbsolutePath()));
        }
        Attributes.writeUserDefinedSingle(file.toPath(),"id","root");
        return file.getAbsolutePath();
    }

    public static String makeConfigDir(){
        java.io.File file = new java.io.File(System.getProperty("user.home") + "/.goodrive/");
        if (file.mkdirs()) {
            System.out.println("path created - " + file.getAbsolutePath());
        } else {
            System.out.println(format("path %s already exists", file.getAbsolutePath()));
        }
        return file.getAbsolutePath();
    }

    public static String makeAppPropFile() {
        java.io.File file = new File(GoogleDriveServices.CONFIG_PATH+File.separator+"app.properties");
        try {
            file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file.getAbsolutePath();
    }

    public static boolean isFileExists(String path){
        java.io.File file = new File(path);
        return file.isFile();
    }

    public static boolean isDirExists(String path){
        java.io.File dir = new File(path);
        return dir.isDirectory();
    }

    //Deletes a file or directory
    public static boolean deleteFile(String path){
        try {
            FileUtils.deleteDirectory(new File(path));
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
