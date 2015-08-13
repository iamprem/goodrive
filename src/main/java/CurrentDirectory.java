/**
 * Data Structure for Preserving the folder structure
 * 
 * @author prem
 *
 */
public class CurrentDirectory {

	
	private String id;
	private String path;
	private String title;
	
	public CurrentDirectory(String id, String path, String title) {
		super();
		this.id = id;
		this.path = path;
		this.title = title;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}
	
}
