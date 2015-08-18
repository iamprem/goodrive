package com.iamprem.goodrive.filesystem;

import java.io.File;

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


    public static boolean isFileExists(String path){
        java.io.File file = new File(path);
        return file.isFile();
    }

    public static boolean isDirExists(String path){
        java.io.File dir = new File(path);
        return dir.isDirectory();
    }
}
