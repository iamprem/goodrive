/**
 * Created by Prem on 8/8/15.
 */
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.*;
import com.google.api.services.drive.Drive;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

public class GooDrive {
	/** Application name. */
	private static final String APPLICATION_NAME = "GooDrive";

	/** GooDrive home folder */
	private static final String HOME_DIR = makeRootDir();

	/** Directory to store user credentials for this application. */
	private static final java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"),
			".credentials/drive-api-quickstart");

	/** Global instance of the {@link FileDataStoreFactory}. */
	private static FileDataStoreFactory DATA_STORE_FACTORY;

	/** Global instance of the JSON factory. */
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

	/** Global instance of the HTTP transport. */
	private static HttpTransport HTTP_TRANSPORT;

	/** Global instance of the scopes required by this quickstart. */
	private static final List<String> SCOPES = Arrays.asList(DriveScopes.DRIVE);

	static {
		try {
			HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
		} catch (Throwable t) {
			t.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Creates an authorized Credential object.
	 * 
	 * @return an authorized Credential object.
	 * @throws IOException
	 */
	public static Credential authorize() throws IOException {
		// Load client secrets.
		InputStream in = GooDrive.class.getResourceAsStream("/client_secret.json");
		GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

		// Build flow and trigger user authorization request.
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY,
				clientSecrets, SCOPES).setDataStoreFactory(DATA_STORE_FACTORY).setAccessType("offline").build();
		Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
		System.out.println("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
		return credential;
	}

	/**
	 * Build and return an authorized Drive client service.
	 * 
	 * @return an authorized Drive client service
	 * @throws IOException
	 */
	public static Drive getDriveService() throws IOException {
		Credential credential = authorize();
		return new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
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

	public static void main(String[] args) throws IOException {
		// Build a new authorized API client service.
		Drive service = getDriveService();

		FileList result = service.files().list().setMaxResults(1000).execute();
		List<File> files = result.getItems();
		if (files == null || files.size() == 0) {
			System.out.println("No files found.");
		} else {

			System.out.println("Files:");
			OutputStream os = null;
			for (File file : files) {
				
				System.out.printf("%s (%s) - %s\n", file.getTitle(), file.getMimeType(), file.getDownloadUrl());
				if (file.getDownloadUrl() != null) {
					os = new FileOutputStream(new java.io.File(HOME_DIR +"/"+ file.getTitle()));
					InputStream is = service.files().get(file.getId()).executeMediaAsInputStream();
					int read = 0;
					byte[] bytes = new byte[1024];
					while ((read = is.read(bytes)) != -1) {
						 os.write(bytes, 0, read);
					}
					System.out.println(file.getTitle() + " - Done!");
				} else{
					java.io.File dir = new java.io.File(HOME_DIR+"/"+file.getTitle());
					dir.mkdirs();
				}

			}
		}
	}

}