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
    public static void downloadAll(Drive service, String rootId) throws IOException, SQLException {

        Stack<CurrentDirectory> dirLevel = new Stack<CurrentDirectory>();
        dirLevel.push(new CurrentDirectory(rootId, HOME_DIR, "GooDrive"));

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

                        if (diskFile.exists()) {
                            // If there is a directory/file in that path, then
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

                        String dirPath = trimFileName(file, curDir);
                        java.io.File dir = new java.io.File(dirPath);
                        if (dir.exists()){
                            dir = enumDuplicates(service, file, dirPath);
                        } else {
                            dir.mkdirs();
                            System.out.println(file.getTitle() + " - Done!");
                        }

                        CurrentDirectory newDir = new CurrentDirectory(file.getId(), dirPath, file.getTitle());
                        dirLevel.push(newDir);
                        // TODO: Drive can have multiple parents
                        // TODO: After creating the folder if we add files to that, modified date changes. :P Expected!!!
                        Attributes.writeUserDefinedBatch(dir.toPath(), file);
                        Attributes.writeBasic(dir.toPath(), file);
                        DBWrite.insertFile(file, dir);
                    }

                }

            }

        }

    }

    /**
     * Retrieve a list of Change resources from Drive.
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
            request.setStartChangeId(startChangeId+1);
        }
        ChangeList changes = null;
        do {
            try {
                changes = request.execute();
                result.addAll(changes.getItems());
                request.setPageToken(changes.getNextPageToken());

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
                if (localFile.getLocalModified()/1000 < change.getFile().getModifiedDate().getValue()/1000){
                    //Update/delete the file from remote -> local
                    if (change.getDeleted()){
                        LocalFS.deleteFile(localFile.getLocalPath());
                        //TODO update db for remote status to deleted
                    }else {
                        //TODO put this in remote2localUpdate method
                        DBWrite.updateFileRemoteStatus(localFile.getLocalPath(), "ENTRY_MODIFY");
                        File remoteFile = change.getFile();
                        if (remoteFile.getParents().size() > 1){
                            //Handle multiple parents
                            System.err.println("Handle multiple parents. [Skipping download]");
                        } else{
                            //TODO check with the local file's last change time in db - DELETE or UPDATE
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

                                    Attributes.writeUserDefinedBatch(Paths.get(localFile.getLocalPath()), remoteFile);
                                    Attributes.writeBasic(Paths.get(localFile.getLocalPath()), remoteFile);

                                } else{
                                    //Could be a directory
                                    System.err.println("Could be a directory or other special type");
                                    Attributes.writeUserDefinedBatch(Paths.get(localFile.getLocalPath()), remoteFile);
                                    Attributes.writeBasic(Paths.get(localFile.getLocalPath()), remoteFile);
                                }



                            } else{
                                System.err.println("Local and remote parents did not match");

                            }

                            localFile.setLocalModified(change.getModificationDate().getValue());
                            DBWrite.updateFileModified(localFile);
                        }
                    }

                } else if (localFile.getLocalModified() > change.getModificationDate().getValue()){
                    // Update the file from local -> remote
                    // Update the local modified time while pushing to remote
                    //TODO may cause problem on next sync because of local -> remote change counts as a change in remote
                } else{
                    //Both remote and local have same modified time
                    System.out.println("Both remote and local have same modified time... [Not downloading]");
                }
            } else{
                //IF the file is null, this is created in remote and new to download to local
                System.err.println("Download the remotely created file to Local- YET TO IMPLEMENT");
            }

            AppUtils.addProperty(GoogleDriveServices.APP_PROP_PATH,"largestChangeId", change.getId().toString());
            AppUtils.addProperty(GoogleDriveServices.APP_PROP_PATH, "LastSynced", String.valueOf(change.getModificationDate().getValue()));
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
        String extensionPart = (fileName.lastIndexOf(".")>=0?fileName.substring(fileName.lastIndexOf(".")):"");
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
                System.out.println(file.getTitle() + " - Done [Enumerated]!");
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            diskFile.mkdirs();
            System.out.println(file.getTitle() + " - Done [Enumerated]!");
        }
        return diskFile;
    }

    public static String trimFileName(File driveFile, CurrentDirectory curDir){
        String fileName = driveFile.getTitle();
        String filePath;
        if (fileName.length() > 255) {
            String extensionPart = (fileName.lastIndexOf(".")>=0?fileName.substring(fileName.lastIndexOf(".")):"");
            String namePart = fileName.substring(0, 255 - extensionPart.length());
            fileName = namePart + extensionPart;
            filePath = curDir.getPath() + java.io.File.separator + fileName;
        } else {
            filePath = curDir.getPath() + java.io.File.separator + fileName;
        }
        return filePath;
    }

    //Delete files in the remote based on local delete time.
    // # 1
    public static void uploadDeleted(Drive service) throws SQLException {

        ArrayList<FilesMeta> fmList = DBRead.readFileDeleted();

        if (fmList != null){
            for (FilesMeta fm : fmList) {
                File file = null;
                try {
                    file = service.files().get(fm.getId()).execute();
                    if (file.getModifiedDate().getValue()/1000 <= fm.getLocalModified()/1000){
                        //Moves the file to trash
                        trashFile(service, file.getId());
                    } else if (!file.getLabels().getTrashed()){
                        //TODO
                        System.err.println("Remote has latest file. YET TO IMPLEMENT");
                        //Download the latest
                        continue;
                    }
                } catch (IOException e) {
                    System.err.println("File " + fm.getLocalName() + " not found in Remote!");
                }
                DBWrite.deleteFile(fm.getLocalPath());
            }
        }
        DBWrite.deleteFile();
    }

    //# 2
    public static void uploadCreated(Drive service) throws SQLException {

        ArrayList<FilesMeta> fmList = DBRead.readFileCreated();

        if (fmList != null) {
            for (FilesMeta fm : fmList) {
                File file;
                try {
                    if (fm.getId() != null) {
                        file = service.files().get(fm.getId()).execute();
                        if (file.getModifiedDate().getValue()/1000 <= fm.getLocalModified()/1000){
                            file = (fm.getMimeType().equals("application/vnd.google-apps.folder")?
                                    updateFolder(service, file.getId(), fm.getLocalName(),fm.getMimeType(),fm.getLocalPath(),false)
                                    :updateFile(service, file.getId(), fm.getLocalName(),fm.getMimeType(),fm.getLocalPath(),false)
                            );
                            fm.setLocalModified(file.getModifiedDate().getValue());
                            fm.setLocalStatus("Synced");
                            fm.setRemoteStatus("Synced");
                            DBWrite.updateFile(fm);
//                            Attributes.writeBasic(Paths.get(fm.getLocalPath()),file);
//                            Attributes.writeUserDefinedBatch(Paths.get(fm.getLocalPath()),file);
                        } else{
                            System.err.println("Remote has the latest, Let the retriveChanges handle this");
                            System.exit(1);
                        }

                    } else {
                        //New file/folder in local
                        String parent = DBRead.getParentId(fm.getLocalPath());
                        if (fm.getMimeType() != null && fm.getMimeType().equals("application/vnd.google-apps.folder")){
                            file = insertFolder(service,fm.getLocalName(),parent,
                                    fm.getMimeType(),fm.getLocalPath());

                        } else{
                            file = insertFile(service, fm.getLocalName(), parent,
                                    Files.probeContentType(Paths.get(fm.getLocalPath())), fm.getLocalPath());
                        }
                        fm.setId(file.getId());
                        fm.setParentId(file.getParents().get(0).getId());
                        fm.setRemoteStatus("Synced");
                        fm.setLocalStatus("Synced");
                        fm.setLocalModified(file.getModifiedDate().getValue());
                        fm.setMimeType(file.getMimeType());
                        DBWrite.updateFile(fm);
//                        Attributes.writeBasic(Paths.get(fm.getLocalPath()), file);
//                        Attributes.writeUserDefinedBatch(Paths.get(fm.getLocalPath()), file);

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    //# 3
    public static void uploadModified(Drive service) throws SQLException {

        ArrayList<FilesMeta> fmList = DBRead.readFileModified();

        if (fmList != null) {
            for (FilesMeta fm : fmList) {
                File file;
                try {
                    if (fm.getId() != null) {
                        file = service.files().get(fm.getId()).execute();
                        if (file.getModifiedDate().getValue()/1000 <= fm.getLocalModified()/1000){
                            file = (fm.getMimeType().equals("application/vnd.google-apps.folder")?
                                    updateFolder(service, file.getId(), fm.getLocalName(),fm.getMimeType(),fm.getLocalPath(),false)
                                    :updateFile(service, file.getId(), fm.getLocalName(),fm.getMimeType(),fm.getLocalPath(),false)
                            );
                            fm.setLocalModified(file.getModifiedDate().getValue());
                            fm.setLocalStatus("Synced");
                            fm.setRemoteStatus("Synced");
                            DBWrite.updateFile(fm);
//                            Attributes.writeBasic(Paths.get(fm.getLocalPath()),file);
//                            Attributes.writeUserDefinedBatch(Paths.get(fm.getLocalPath()),file);
                        } else{
                            System.err.println("Remote has the latest, Let the retriveChanges handle this");
                            System.exit(1);
                        }

                    } else {
                        //New file/folder in local
                        String parent = DBRead.getParentId(fm.getLocalPath());
                        if (fm.getMimeType() != null && fm.getMimeType().equals("application/vnd.google-apps.folder")){
                            file = insertFolder(service,fm.getLocalName(),parent,
                                    fm.getMimeType(),fm.getLocalPath());

                        } else{
                            file = insertFile(service, fm.getLocalName(), parent,
                                    Files.probeContentType(Paths.get(fm.getLocalPath())), fm.getLocalPath());
                        }
                        fm.setId(file.getId());
                        fm.setParentId(file.getParents().get(0).getId());
                        fm.setRemoteStatus("Synced");
                        fm.setLocalStatus("Synced");
                        fm.setLocalModified(file.getModifiedDate().getValue());
                        fm.setMimeType(file.getMimeType());
                        DBWrite.updateFile(fm);
//                        Attributes.writeBasic(Paths.get(fm.getLocalPath()), file);
//                        Attributes.writeUserDefinedBatch(Paths.get(fm.getLocalPath()), file);

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }


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
                    Attributes.writeUserDefinedBatch(dir, insertedDir);
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
            File file = service.files().get(fileId).execute();
            file.setTitle(newTitle);
            file.setMimeType(newMimeType);

            java.io.File fileContent = new java.io.File(newFilePath);
            FileContent mediaContent = new FileContent(newMimeType, fileContent);

            File updatedFile = service.files().update(fileId, file, mediaContent).execute();

            System.out.println("Updated the File: " + newFilePath);
            return updatedFile;
        } catch (IOException e) {
            System.out.println("An error occurred: " + e);
            return null;
        }
    }

    private static File updateFolder(Drive service, String fileId, String newTitle, String newMimeType,
                                     String newFilePath, boolean newRevision){
        try {
            File file = service.files().get(fileId).execute();
            file.setTitle(newTitle);
            file.setMimeType(newMimeType);

            java.io.File fileContent = new java.io.File(newFilePath);

            File updatedFile = service.files().update(fileId, file).execute();

            System.out.println("Updated the Folder: " + newFilePath);
            return updatedFile;
        } catch (IOException e) {
            System.out.println("An error occurred: " + e);
            return null;
        }
    }

    //Delete file in remote permanently
    private static void deleteFile(Drive service, String fileId) {
        try {
            service.files().delete(fileId).execute();
        } catch (IOException e) {
            System.out.println("An error occurred: " + e);
        }
    }

    /**
     * Move a file to the trash.
     *
     * @param service Drive API service instance.
     * @param fileId ID of the file to trash.
     * @return The updated file if successful, {@code null} otherwise.
     */
    private static File trashFile(Drive service, String fileId) {
        try {
            return service.files().trash(fileId).execute();
        } catch (IOException e) {
            System.out.println("An error occurred: " + e);
        }
        return null;
    }


}
