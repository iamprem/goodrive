package com.iamprem.goodrive.filesystem;

import com.google.api.services.drive.model.File;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserDefinedFileAttributeView;

/**
 * Created by prem on 8/17/15.
 */
public class Attributes {
    private Attributes() {
        // To restrict creating object for this class
    }

    public static void readBasic(Path path, String attrName) {
        //TODO
    }

    /**
     * Read the user defined attribute from the file
     *
     * @param path - Path object of the files
     * @param attrName - Name of the user defined attribute
     * @return a value of the attribute or null
     */
    public static String readUserDefined(Path path, String attrName) {

        UserDefinedFileAttributeView view = Files.getFileAttributeView(path, UserDefinedFileAttributeView.class);
        ByteBuffer buf;
        String result = null;
        try {
            buf = ByteBuffer.allocate(view.size(attrName));
            view.read(attrName, buf);
            buf.flip();
            result  = Charset.defaultCharset().decode(buf).toString();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return result;
    }

    public static void writeBasic(Path path, File file) {
        FileTime ft = FileTime.fromMillis(file.getModifiedDate().getValue());
        try {
            Files.setLastModifiedTime(path, ft);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static void writeUserDefinedBatch(Path path, File file) {
        UserDefinedFileAttributeView view = Files.getFileAttributeView(path, UserDefinedFileAttributeView.class);
        try {
            view.write("id", ByteBuffer.wrap(file.getId().getBytes()));
            view.write("md5CheckSum", ByteBuffer.wrap(file.getMd5Checksum().getBytes()));
            view.write("mimeType", ByteBuffer.wrap(file.getMimeType().getBytes()));
            view.write("parents", ByteBuffer.wrap(file.getParents().toString().getBytes()));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    /**
     * Write single user defined attribute to the file
     *
     * @param path - Path object from the file location
     * @param file - Drive's File object, remote copy
     * @param attrName - Name of the attribute that should be set
     * @param attrValue - Value of the attribute that should be set
     */
    public static void writeUserDefinedSingle(Path path, File file, String attrName, String attrValue) {
        UserDefinedFileAttributeView view = Files.getFileAttributeView(path, UserDefinedFileAttributeView.class);
        try {
            view.write(attrName, ByteBuffer.wrap(attrValue.getBytes()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void writeUserDefinedSingle(Path path, String attrName, String attrValue) {
        UserDefinedFileAttributeView view = Files.getFileAttributeView(path, UserDefinedFileAttributeView.class);
        try {
            view.write(attrName, ByteBuffer.wrap(attrValue.getBytes()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Compare lastModifiedDate of the file from Drive to the lastModifiedDate of file from Local
     * @param remoteModfDate
     * @param localModfDate
     * @return true if remote is modified recently than local
     */
    public static boolean compareModfDate(long remoteModfDate, long localModfDate){

        if (remoteModfDate/1000 > localModfDate/1000) {
            return true;
        }
        return false;
    }

}
