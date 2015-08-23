package com.iamprem.goodrive.util;


import com.iamprem.goodrive.filesystem.LocalFS;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Properties;

import static java.lang.String.format;

/**
 * Created by prem on 8/17/15.
 */
public class AppUtils {

    public static Properties getProperties(String path) throws IOException {
        Properties prop = new Properties();
        InputStream input = null;
        //check properties file exist
        java.io.File propFile = new java.io.File(path);
        if (propFile.isFile()){
            input = new FileInputStream(propFile);
            prop.load(input);
        } else{
            System.out.println(format("File '%s' not exist!", path));
        }

        return prop;
    }

    public static void setProperties(String path, Properties prop) throws IOException {

        OutputStream output = null;
        if (!LocalFS.isFileExists(path)){
            Files.createFile(Paths.get(path));
        }
        output = new FileOutputStream(path);
        prop.store(output,null);

    }

    public static long getLastSynced(String path) throws IOException {
        Properties prop = getProperties(path);
        try{
            return Long.parseLong(prop.getProperty("LastSynced"));
        } catch (NumberFormatException e){
            return 0;
        }

    }

    public static void setLastSynced(String path) throws IOException {
        Properties prop = new Properties();
        prop.setProperty("LastSynced", String.valueOf(new Date().getTime()));
        setProperties(path, prop);
    }


}
