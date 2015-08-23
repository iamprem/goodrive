package com.iamprem.goodrive.service;

import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.*;
import com.google.api.services.drive.model.File;
import com.iamprem.goodrive.db.DBRead;
import com.iamprem.goodrive.db.DBWrite;
import com.iamprem.goodrive.entity.CurrentDirectory;
import com.iamprem.goodrive.entity.FilesMeta;
import com.iamprem.goodrive.filesystem.Attributes;
import com.iamprem.goodrive.filesystem.LocalFS;
import com.iamprem.goodrive.util.AppUtils;
import com.iamprem.goodrive.util.DateUtils;

import javax.naming.directory.AttributeInUseException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.sql.SQLException;
import java.util.*;

/**
 * Created by prem on 8/17/15.
 */
public class GoogleDriveServices {

    //Static variables
    public static final String HOME_DIR = LocalFS.makeRootDir();
    public static final String CONFIG_PATH = LocalFS.makeConfigDir();
    public static final String APP_PROP_PATH = LocalFS.makeAppPropFile();
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

    public static String getRootId(Drive service){

        try {
            File file = service.files().get("root").execute();
            AppUtils.addProperty(APP_PROP_PATH, "rootid", file.getId());
            return file.getId();
        }catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String getLargestChangeId(Drive service) throws IOException {

        Drive.Changes.List request = service.changes().list();
        request.setStartChangeId((long)1);
        ChangeList changes = request.execute();
        AppUtils.addProperty(APP_PROP_PATH, "largestChangeId", changes.getLargestChangeId().toString());
        return changes.getLargestChangeId().toString();
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
    public static void downloadAll(Drive service) throws IOException, SQLException {
        Stack<CurrentDirectory> dirLevel = new Stack<CurrentDirectory>();
        // Initial Level as 'root'
        dirLevel.push(new CurrentDirectory("root", HOME_DIR, "GooDrive"));

        while (!dirLevel.isEmpty()) {
            List<File> files = new ArrayList<>();
            CurrentDirectory curDir = dirLevel.pop();
            Drive.Files.List request = service.files().list().setQ("'" + curDir.getId() + "' in parents");

            do {
                FileList result = request.execute();
                files.addAll(result.getItems());
                request.setPageToken(result.getNextPageToken());
            } while (request.getPageToken() != null &&
                    request.getPageToken().length() > 0);

            if (files == null || files.size() == 0) {

                System.out.println("Empty directory : " + curDir.getTitle());

            } else {
                OutputStream os = null;
                for (File file : files) {

                    if (!MIMETYPES_SPECIAL.contains(file.getMimeType())) {

                        String filePath = trimFileName(file, curDir);
                        java.io.File diskFile = new java.io.File(filePath);

                        if (diskFile.exists() && diskFile.isFile()) {
                            Path path = diskFile.toPath();
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
                                diskFile = enumDuplicates(service, file, filePath);
                            }

                        } else if (diskFile.exists() && diskFile.isDirectory()) {
                            // If there is a directory in that path, then
                            // enumerate the file and store.
                            diskFile = enumDuplicates(service, file, filePath);
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

                        Attributes.writeUserDefinedBatch(diskFile.toPath(), file);
                        Attributes.writeBasic(diskFile.toPath(), file);
                        DBWrite.insertFile(file, diskFile);

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
                        DBWrite.insertFile(file, dir);
                    }

                }

            }

        }

    }

    /**
     * Retrieve a list of Change resources.
     *
     * @param service Drive API service instance.
     * @param startChangeId ID of the change to start retrieving subsequent changes from or {@code null}.
     * @return List of Change resources.
     */
    public static ArrayList<Change> retrieveAllChanges(Drive service,
                                                   Long startChangeId) throws IOException {
        ArrayList<Change> result = new ArrayList<Change>();
        Drive.Changes.List request = service.changes().list();

        if (startChangeId != null) {
            request.setStartChangeId(800L);
        }
        ChangeList changes = null;
        do {
            try {
                changes = request.execute();
                result.addAll(changes.getItems());
                request.setPageToken(changes.getNextPageToken());
                //TODO should change the property after down syncing the changes
                //AppUtils.addProperty(APP_PROP_PATH, "largestChangeId", changes.getLargestChangeId().toString());

            } catch (IOException e) {
                e.printStackTrace();
                request.setPageToken(null);
            }
        } while (request.getPageToken() != null &&
                request.getPageToken().length() > 0);

        return result;
    }


    public static void downloadLatest(Drive service, ArrayList<Change> changeList, long largestChangeId) throws IOException, SQLException {

        for (Change change : changeList) {

            FilesMeta localFile = DBRead.readFileById(change.getFileId());

            if (localFile != null){
                if (localFile.getLocalModified() < change.getModificationDate().getValue()){
                    //Update/delete the file from remote -> local
                    if (change.getDeleted()){
                        LocalFS.deleteFile(localFile.getLocalPath());
                        //TODO update db for remote status to deleted
                    }else {
                        //TODO put this in remote2localUpdate method
                        File remoteFile = change.getFile();
                        if (remoteFile.getParents().size() > 1){
                            //Handle multiple parents
                            System.err.println("Handle multiple parents");
                        } else{
                            String parent = remoteFile.getParents().get(0).getId();
                            if (parent.equals(localFile.getParentId())){

                                if (!MIMETYPES_SPECIAL.contains(remoteFile.getMimeType())){
                                    OutputStream os = new FileOutputStream(localFile.getLocalPath());
                                    InputStream is = service.files().get(remoteFile.getId()).executeMediaAsInputStream();
                                    int read = 0;
                                    byte[] bytes = new byte[1024];
                                    while ((read = is.read(bytes)) != -1) {
                                        os.write(bytes, 0, read);
                                    }
                                    os.close();
                                    System.out.println(remoteFile.getTitle() + " - Downloaded the latest version!");

                                } else{
                                    //Could be a directory
                                    System.err.println("Could be a directory or other special type");
                                }



                            } else{
                                System.err.println("Local and remote parents did not match");

                            }
//                            Attributes.writeUserDefinedBatch(Paths.get(localFile.getLocalPath()), remoteFile);
//                            Attributes.writeBasic(Paths.get(localFile.getLocalPath()), remoteFile);
                            localFile.setLocalModified(change.getModificationDate().getValue());
                            DBWrite.updateFile(localFile);

                        }
                    }

                } else if (localFile.getLocalModified() > change.getModificationDate().getValue()){
                    // Update the file from local -> remote
                    // Update the local modified time while pushing to remote
                    //TODO may cause problem on next sync because of local -> remote change counts as a change in remote
                } else{
                    //Both remote and local have same modified time
                }
            } else{
                //IF the file is null, this is created in remote and new to download to local

            }
            largestChangeId = change.getId();

        }

        /*
        String lastSyncString = DateUtils.long2UTCString(AppUtils.getLastSynced(GoogleDriveServices.APP_PROP_PATH));
        Stack<CurrentDirectory> dirLevel = new Stack<CurrentDirectory>();
        // Initial Level as 'root'
        dirLevel.push(new CurrentDirectory("root", HOME_DIR, "GooDrive"));

        while (!dirLevel.isEmpty()) {
            CurrentDirectory curDir = dirLevel.pop();
            FileList result = service.files().list().setQ("'" + curDir.getId() + "' in parents and modifiedDate > '"+lastSyncString+"'").execute();
            List<File> files = result.getItems();

            if (files == null || files.size() == 0) {
                System.out.println("No latest modification in : " + curDir.getTitle());

            } else {
                OutputStream os = null;
                for (File file : files) {

                    if (!MIMETYPES_SPECIAL.contains(file.getMimeType())) {

                        String filePath = trimFileName(file, curDir);
                        java.io.File diskFile = new java.io.File(filePath);

                        if (diskFile.exists() && diskFile.isFile()) {
                            Path path = diskFile.toPath();
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
                                diskFile = enumDuplicates(service, file, filePath);
                            }

                        } else if (diskFile.exists() && diskFile.isDirectory()) {
                            // If there is a directory in that path, then
                            // enumerate the file and store.
                            diskFile = enumDuplicates(service, file, filePath);
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

                        Attributes.writeUserDefinedBatch(diskFile.toPath(), file);
                        Attributes.writeBasic(diskFile.toPath(), file);

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

        } */
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
        if (lastSynced == 0) return;

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
