package poke.server.storage.jdbc;

public class NameSpaceInfo {
	private String Name;
	private String Owner;
	private String Dest_Node;
	private String Port;
	private String Storage_Path;
	
	
	public String getOwner() {
		return Owner;
	}

	public void setOwner(String owner) {
		Owner = owner;
	}

	public String getDest_Node() {
		return Dest_Node;
	}

	public void setDest_Node(String dest_Node) {
		Dest_Node = dest_Node;
	}

	public String getPort() {
		return Port;
	}

	public void setPort(String port) {
		Port = port;
	}

	public String getStorage_Path() {
		return Storage_Path;
	}

	public void setStorage_Path(String storage_Path) {
		Storage_Path = storage_Path;
	}

	public String getName() {
		return Name;
	}

	public void setName(String name) {
		Name = name;
	}
	
	
}
