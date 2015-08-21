package com.iamprem.goodrive.service;

import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;
import com.iamprem.goodrive.entity.CurrentDirectory;
import com.iamprem.goodrive.filesystem.Attributes;
import com.iamprem.goodrive.filesystem.LocalFS;
import com.iamprem.goodrive.util.AppUtils;

import javax.naming.directory.AttributeInUseException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.*;

/**
 * Created by prem on 8/17/15.
 */
public class GoogleDriveServices {

    //Static variables
    public static final String HOME_DIR = LocalFS.makeRootDir();
    public static final String CONFIG_PATH = LocalFS.makeConfigDir();
    public static final String APP_PROP_PATH = CONFIG_PATH + java.io.File.separator + "app.properties";
    protected static final List<String> MIMETYPES_SPECIAL = new ArrayList<String>();
    protected static Properties appProperties;

    static {
        MIMETYPES_SPECIAL.add("application/vnd.google-apps.document");
        MIMETYPES_SPECIAL.add("application/vnd.google-apps.drawing");
        MIMETYPES_SPECIAL.add("application/vnd.google-apps.spreadsheet");
        MIMETYPES_SPECIAL.add("application/vnd.google-apps.presentation");
        MIMETYPES_SPECIAL.add("application/vnd.google-apps.form");
        MIMETYPES_SPECIAL.add("application/vnd.google-apps.folder");
        MIMETYPES_SPECIAL.add("application/vnd.google-apps.fusiontable");
        MIMETYPES_SPECIAL.add("application/vnd.google-apps.script");
        MIMETYPES_SPECIAL.add("application/vnd.google-apps.sites");
        MIMETYPES_SPECIAL.add("application/vnd.google-apps.unknown");
        try {
            appProperties = AppUtils.getProperties(APP_PROP_PATH);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    /**
     * Download files and folders from Drive preserving the folder structure
     *
     * TODO: Files with same name will be replaced with the last occurrence
     * while downloading/ Have to handle this like Google Drive windows client
     *
     * @param service
     * @throws IOException
     */
    public static void download(Drive service) throws IOException {
        Stack<CurrentDirectory> dirLevel = new Stack<CurrentDirectory>();
        // Initial Level as 'root'
        dirLevel.push(new CurrentDirectory("root", HOME_DIR, "GooDrive"));

        while (!dirLevel.isEmpty()) {
            CurrentDirectory curDir = dirLevel.pop();
            FileList result = service.files().list().setQ("'" + curDir.getId() + "' in parents").execute();
            List<File> files = result.getItems();

            if (files == null) {
                System.out.println("Empty directory : " + curDir.getTitle());

            } else {
                OutputStream os = null;
                for (File file : files) {

                    if (!MIMETYPES_SPECIAL.contains(file.getMimeType())) {

                        String filePath = trimFileName(file, curDir);
                        java.io.File diskFile = new java.io.File(filePath);
                        Path path = diskFile.toPath();

                        if (diskFile.exists() && diskFile.isFile()) {
                            String id = Attributes.readUserDefined(path, "id");
                            if (id.equals(file.getId())) {

                                String md5CheckSum = Attributes.readUserDefined(path, "md5CheckSum");
                                Long modifiedDate = Files.getLastModifiedTime(path).toMillis();
                                System.out.println("Local : " + modifiedDate + "  Remote : "
                                        + file.getModifiedDate().getValue());

                                if (!md5CheckSum.equals(file.getMd5Checksum())
                                        || Attributes.compareModfDate(file.getModifiedDate().getValue(), modifiedDate)) {
                                    os = new FileOutputStream(diskFile);
                                    InputStream is = service.files().get(file.getId()).executeMediaAsInputStream();
                                    int read = 0;
                                    byte[] bytes = new byte[1024];
                                    while ((read = is.read(bytes)) != -1) {
                                        os.write(bytes, 0, read);
                                    }
                                    os.close();
                                    System.out.println(file.getTitle() + " - Downloaded the latest version!");
                                } else {
                                    // NOT MODIFIED
                                    System.out.println(file.getTitle() + " -- NOT MODIFIED");
                                }

                            } else {
                                // TODO Duplicate file with same name but
                                // different id. Enumerate the file name
                                enumDuplicates(service, file, filePath);
                            }

                        } else if (diskFile.exists() && diskFile.isDirectory()) {
                            // If there is a directory in that path, then
                            // enumerate the file and store.
                            enumDuplicates(service, file, filePath);
                        } else {
                            // No directory or file exist in the same name in
                            // the path
                            os = new FileOutputStream(diskFile);
                            InputStream is = service.files().get(file.getId()).executeMediaAsInputStream();
                            int read = 0;
                            byte[] bytes = new byte[1024];
                            while ((read = is.read(bytes)) != -1) {
                                os.write(bytes, 0, read);
                            }
                            os.close();
                            System.out.println(file.getTitle() + " - Done!");
                        }

                        Attributes.writeUserDefinedBatch(path, file);
                        Attributes.writeBasic(path, file);

                    } else {

                        //TODO : Handle Special MIME TYPES
                        String dirPath = null;
                        // Check filename length is larger than 255, then
                        // truncate it to 255
                        if (file.getTitle().length() > 255) {
                            String namePart = file.getTitle().substring(0, 255);
                            dirPath = curDir.getPath() + java.io.File.separator + namePart;
                        } else {
                            dirPath = curDir.getPath() + java.io.File.separator + file.getTitle();
                        }

                        java.io.File dir = new java.io.File(dirPath);
                        if (!dir.exists()) {
                            dir.mkdirs();
                        }
                        CurrentDirectory newDir = new CurrentDirectory(file.getId(), dirPath, file.getTitle());
                        dirLevel.push(newDir);
                        Path path = dir.toPath();
                        UserDefinedFileAttributeView userView = Files.getFileAttributeView(path,
                                UserDefinedFileAttributeView.class);
                        FileTime ft = FileTime.fromMillis(file.getModifiedDate().getValue());
                        Files.setLastModifiedTime(path, ft);
                        userView.write("id", ByteBuffer.wrap(file.getId().getBytes()));
                        userView.write("mimeType", ByteBuffer.wrap(file.getMimeType().getBytes()));
                        // TODO: Drive can have multiple parents
                        // TODO: After creating the folder if we add files to
                        // that, modified date changes. :P Expected!!!
                        userView.write("parents", ByteBuffer.wrap(file.getParents().toString().getBytes()));
                    }

                }

            }

        }


    }

    /**
     * Takes the files/folders with same name and enumerate it. Google drive can
     * have multiple files with same name, But local file system cannot
     *
     * @param service
     * @param file
     * @param filePath
     * @return
     */

    public static java.io.File enumDuplicates(Drive service, File file, String filePath) {

        java.io.File diskFile = new java.io.File(filePath);
        String pathPrefix = filePath.substring(0, filePath.lastIndexOf(java.io.File.separatorChar)+1);
        String fileName = filePath.substring(filePath.lastIndexOf(java.io.File.separatorChar) + 1);
        String extensionPart = (fileName.lastIndexOf(".")>0?fileName.substring(fileName.lastIndexOf(".")):"");
        String namePart = fileName.substring(0, fileName.length() - extensionPart.length());
        int number = 1;

        while (diskFile.exists()) {
            String numberExtPart = "(" + number + ")"+extensionPart;

            if ((namePart+numberExtPart).length()>255) {
                namePart = namePart.substring(0, namePart.length() - ((namePart+numberExtPart).length()-255));
            }
            fileName = namePart+numberExtPart;
            filePath = pathPrefix + fileName;
            diskFile = new java.io.File(filePath);
            number++;
        }

        if (!file.getMimeType().equals("application/vnd.google-apps.folder")) {
            OutputStream os;
            try {
                os = new FileOutputStream(diskFile);
                InputStream is = service.files().get(file.getId()).executeMediaAsInputStream();
                int read = 0;
                byte[] bytes = new byte[1024];
                while ((read = is.read(bytes)) != -1) {
                    os.write(bytes, 0, read);
                }
                os.close();
                System.out.println(file.getTitle() + " - Done!");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            diskFile.mkdirs();
        }
        return diskFile;
    }

    public static String trimFileName(File driveFile, CurrentDirectory curDir){
        String fileName, filePath;
        if (driveFile.getTitle().length() > 255) {
            String extensionPart = driveFile.getTitle().substring(driveFile.getTitle().lastIndexOf("."));
            String namePart = driveFile.getTitle().substring(0, 255 - extensionPart.length());
            fileName = namePart + extensionPart;
            filePath = curDir.getPath() + java.io.File.separator + fileName;
        } else {
            filePath = curDir.getPath() + java.io.File.separator + driveFile.getTitle();
        }
        return filePath;
    }


    public static void upload(Drive service) throws IOException {

        long lastSynced = AppUtils.getLastSynced(APP_PROP_PATH);

        Path start = Paths.get(HOME_DIR);

        Files.walkFileTree(start, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                //TODO Check its fileID
                String id = Attributes.readUserDefined(dir,"id");
                if (id == null){
                    //TODO insert this dir to remote
                    File insertedDir = insertFolder(service,
                            dir.getFileName().toString(),
                            Attributes.readUserDefined(dir.getParent(), "id"),
                            "application/vnd.google-apps.folder",
                            dir.toString());
                    Attributes.writeBasic(dir,insertedDir);
                    Attributes.writeUserDefinedBatchDir(dir, insertedDir);
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                //TODO Check last modified time is greater than lastsync, then upload

                if (Files.getLastModifiedTime(file).toMillis() >= lastSynced){
                    String id = Attributes.readUserDefined(file,"id");
                    if (id == null){
                        //Create file in remote
                        File insertedFile = insertFile(service,
                                file.getFileName().toString(),
                                Attributes.readUserDefined(file.getParent(), "id"),
                                Files.probeContentType(file),
                                file.toString());
                        Attributes.writeBasic(file,insertedFile);
                        Attributes.writeUserDefinedBatch(file,insertedFile);
                    } else{
                        //Update file
                        File updatedFile = updateFile(service,
                                id,
                                file.getFileName().toString(),
                                Files.probeContentType(file),
                                file.toString(),
                                false);
                        Attributes.writeBasic(file,updatedFile);
                        Attributes.writeUserDefinedBatch(file,updatedFile);
                    }
                }


                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });



    }


    // Upload a file to drive
    private static File insertFile(Drive service, String title, String parentId, String mimeType, String filePath) {
        // File's metadata.
        File body = new File();
        body.setTitle(title);
        body.setMimeType(mimeType);

        // Set the parent folder.
        if (parentId != null && parentId.length() > 0) {
            body.setParents(
                    Arrays.asList(new ParentReference().setId(parentId)));
        }

        // File's content.
        java.io.File fileContent = new java.io.File(filePath);
        FileContent mediaContent = new FileContent(mimeType, fileContent);
        try {
            File file = service.files().insert(body, mediaContent).execute();

            System.out.println("Inserted New File: " + filePath);
            return file;
        } catch (IOException e) {
            System.out.println("An error occured: " + e);
            return null;
        }
    }

    private  static File insertFolder(Drive service, String title, String parentId, String mimeType, String filePath) {
        // File's metadata.
        File body = new File();
        body.setTitle(title);
        body.setMimeType(mimeType);

        // Set the parent folder.
        if (parentId != null && parentId.length() > 0) {
            body.setParents(
                    Arrays.asList(new ParentReference().setId(parentId)));
        }

        try {
            File file = service.files().insert(body).execute();

            System.out.println("Inserted New Folder: " + filePath);
            return file;
        } catch (IOException e) {
            System.out.println("An error occured: " + e);
            return null;
        }
    }

    private static File updateFile(Drive service, String fileId, String newTitle, String newMimeType,
                                   String newFilePath, boolean newRevision) {
        try {
            // First retrieve the file from the API.
            File file = service.files().get(fileId).execute();

            // File's new metadata.
            file.setTitle(newTitle);
            file.setMimeType(newMimeType);

            // File's new content.
            java.io.File fileContent = new java.io.File(newFilePath);
            FileContent mediaContent = new FileContent(newMimeType, fileContent);

            // Send the request to the API.
            File updatedFile = service.files().update(fileId, file, mediaContent).execute();

            System.out.println("Updated New File: " + newFilePath);
            return updatedFile;
        } catch (IOException e) {
            System.out.println("An error occurred: " + e);
            return null;
        }
    }
}
