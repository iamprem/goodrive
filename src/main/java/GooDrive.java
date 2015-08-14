/**
 * Created by Prem on 8/8/15.
 */
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.*;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.UserDefinedFileAttributeView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Stack;

public class GooDrive {
	
	
	
	/** Application name. */
	protected static final String APPLICATION_NAME = "GooDrive";

	/** GooDrive home folder */
	protected static final String HOME_DIR = makeRootDir();

	/** Directory to store user credentials for this application. */
	protected static final java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"),
			".credentials/drive-api-quickstart");

	/** Global instance of the {@link FileDataStoreFactory}. */
	protected static FileDataStoreFactory DATA_STORE_FACTORY;

	/** Global instance of the JSON factory. */
	protected static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

	/** Global instance of the HTTP transport. */
	protected static HttpTransport HTTP_TRANSPORT;

	/** Global instance of the scopes required by this quickstart. */
	protected static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE);

	/** Global instance of the Special MemeTypes that dont have downloadURL property */ 
	protected static final List<String> MIMETYPES_SPECIAL = new ArrayList<String>();
	
	static {
		try {
			HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
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
		} catch (Throwable t) {
			t.printStackTrace();
			System.exit(1);
		}
	}

	
	
	/**
	 * Create GooDrive directory
	 * 
	 * @return true if the directory 'home/user/Desktop/GooDrive' is created
	 *         successfully
	 */

	public static String makeRootDir() {
		java.io.File file = new java.io.File(System.getProperty("user.home") + "/Desktop/GooDrive/");
		if (file.mkdirs()) {
			System.out.println("path created - " + file.getAbsolutePath());
		} else {
			System.out.println("path already exists");
		}
		return file.getAbsolutePath();
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

						String filePath = null;
						String fileName = null;
						// Check for filename longer than 255 characters -
						// Supported by Unix and DOS (255 chars)
						if (file.getTitle().length() > 255) {
							String extensionPart = file.getTitle().substring(file.getTitle().lastIndexOf("."));
							String namePart = file.getTitle().substring(0, 255 - extensionPart.length());
							fileName = namePart + extensionPart;
							filePath = curDir.getPath() + java.io.File.separator + fileName;
						} else {
							filePath = curDir.getPath() + java.io.File.separator + file.getTitle();
						}

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

						// Write MetaData of the file
						UserDefinedFileAttributeView view = Files.getFileAttributeView(path,
								UserDefinedFileAttributeView.class);
						view.write("id", ByteBuffer.wrap(file.getId().getBytes()));
						view.write("md5CheckSum", ByteBuffer.wrap(file.getMd5Checksum().getBytes()));
						view.write("mimeType", ByteBuffer.wrap(file.getMimeType().getBytes()));
						view.write("parents", ByteBuffer.wrap(file.getParents().toString().getBytes()));
						System.out.println("Remote : " + file.getModifiedDate().getValue());
						FileTime ft = FileTime.fromMillis(file.getModifiedDate().getValue());
						System.out.println("Local : " + ft.toMillis());
						Files.setLastModifiedTime(path, ft);
						System.out.println("Back from File : " + Files.getLastModifiedTime(path).toMillis());

						/*
						 * To Print BasicFileAttributes attr =
						 * Files.readAttributes(path,
						 * BasicFileAttributes.class);
						 * System.out.println("creationTime: " +
						 * attr.creationTime());
						 * System.out.println("lastModifiedTime: " +
						 * attr.lastModifiedTime());
						 * UserDefinedFileAttributeView view1 =
						 * Files.getFileAttributeView(path,
						 * UserDefinedFileAttributeView.class); String name =
						 * "md5CheckSum"; ByteBuffer buf =
						 * ByteBuffer.allocate(view1.size(name));
						 * view1.read(name, buf); buf.flip(); String value =
						 * Charset.defaultCharset().decode(buf).toString();
						 * System.out.println(value);
						 */
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

	/**
	 * Starting point of the program
	 * 
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		// Build a new authorized API client service.
		Drive service = Authentication.getDriveService();
		download(service);

	}

}