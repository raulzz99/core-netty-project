/*
 * copyright 2012, gash
 * 
 * Gash licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package poke.server.storage.jdbc;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.server.conf.NodeDesc;
import poke.server.storage.Storage;

import com.jolbox.bonecp.BoneCP;
import com.jolbox.bonecp.BoneCPConfig;

import eye.Comm.Document;
import eye.Comm.NameSpace;

public class DatabaseStorage implements Storage {
	protected static Logger logger = LoggerFactory.getLogger("database");

	public static final String sDriver = "jdbc.driver";
	public static final String sUrl = "jdbc.url";
	public static final String sUser = "jdbc.user";
	public static final String sPass = "jdbc.password";
	public static final String sTable_space = "namespace_info";
	public static final String sDB = "275_MetaData";
	protected Properties cfg;
	protected BoneCP cpool;

	protected DatabaseStorage() {

	}

	public DatabaseStorage(Properties cfg) {
		init(cfg);
	}

	public static void main(String[] args) {
		Properties prop = new Properties();
		try {
			prop.load(new FileInputStream(
					"/home/ankurthuse/Downloads/Server_DB_properties.properties"));
			DatabaseStorage db = new DatabaseStorage(prop);
			db.createTable();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	@Override
	public void init(Properties cfg) {
		if (cpool != null)
			return;

		this.cfg = cfg;

		try {
			Class.forName(cfg.getProperty(sDriver));
			BoneCPConfig config = new BoneCPConfig();
			config.setJdbcUrl(cfg.getProperty(sUrl));
			config.setUsername(cfg.getProperty(sUser));
			config.setPassword(cfg.getProperty(sPass));
			/*
			 * config.setMinConnectionsPerPartition(5);
			 * config.setMaxConnectionsPerPartition(10);
			 * config.setPartitionCount(1);
			 */

			cpool = new BoneCP(config);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see gash.jdbc.repo.Repository#release()
	 */
	@Override
	public void release() {
		if (cpool == null)
			return;

		cpool.shutdown();
		cpool = null;
	}

	//@Override
	public NameSpace getNameSpaceInfo(long spaceId) {
		NameSpace space = null;

		Connection conn = null;
		try {
			conn = cpool.getConnection();
			conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			// TODO complete code to retrieve through JDBC/SQL
			// select * from space where id = spaceId
		} catch (Exception ex) {
			ex.printStackTrace();
			logger.error("failed/exception on looking up space " + spaceId, ex);
			try {
				conn.rollback();
			} catch (SQLException e) {
			}
		} finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		return space;
	}

	@Override
	public List<NameSpace> findNameSpaces(NameSpace criteria) {
		List<NameSpace> list = null;

		Connection conn = null;
		try {
			conn = cpool.getConnection();
			conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			// TODO complete code to search through JDBC/SQL
		} catch (Exception ex) {
			ex.printStackTrace();
			logger.error("failed/exception on find", ex);
			try {
				conn.rollback();
			} catch (SQLException e) {
			}
		} finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		return list;
	}

	public void createTable() {
		Statement stmt=null;
		Connection conn = null;
		try {
			
			conn = cpool.getConnection();
			conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			stmt = conn.createStatement();
			if (conn != null) {
			String sql = "CREATE TABLE "+sTable_space+" (Name varchar(20), Owner varchar(20), Dest_Node varchar(20),Storage_Path varchar(20));";
				stmt.executeUpdate(sql);
			}
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			// finally block used to close resources
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException se2) {
			}// nothing we can do

		}// end try
		System.out.println("Goodbye!");

		
	}

	public boolean addNameSpace(String name, String owner,String ip,String path,String port) {
	Connection conn = null;
		try {
			conn = cpool.getConnection();
			conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			if (conn != null) {
				System.out.println("Connection successful!");
				Statement stmt = conn.createStatement();
				stmt.executeUpdate("insert into " + sTable_space
						+ " (Name,Owner,Dest_Node,Storage_Path,port) values('" + name + "','" + owner
						+ "','" + ip + "','" + path +"','" + port+ "')" ); // do something with the
												// connection.
			}

		} catch (Exception ex) {
			ex.printStackTrace();
			logger.error("failed/exception on creating space " + name, ex);
			try {
				conn.rollback();
			} catch (SQLException e) {
			}

			// indicate failure
			return false;
		} finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		return true;
	}
	public NameSpaceInfo checkNameSpace(String name, String owner) {
		Connection conn = null;
		NameSpaceInfo nsInfo = new NameSpaceInfo();
		try {
			conn = cpool.getConnection();
			conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			if (conn != null) {
				System.out.println("Connection successful!");
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("select Name, Owner, Dest_Node, Storage_Path, Port from " + sTable_space
						+ " where Name='" + name + "' and Owner= '"+owner+"';");
				
				if (rs.next()) {
					nsInfo.setName(rs.getString("Name"));
					nsInfo.setOwner(rs.getString("Owner"));
					nsInfo.setDest_Node(rs.getString("Dest_Node"));
					nsInfo.setPort(rs.getString("Port"));
					nsInfo.setStorage_Path(rs.getString("Storage_Path"));
					return nsInfo;
				}	
				
			}
			
		} catch (Exception ex) {
			ex.printStackTrace();
			try {
				conn.rollback();
			} 
			catch (SQLException e) 
			{
			}
		} finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return null;
		
	}
	
	public String getStoragePath(String name, String owner) {
		Connection conn = null;
		try {
			conn = cpool.getConnection();
			conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			if (conn != null) {
				System.out.println("Connection successful!");
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("select Storage_Path from " + sTable_space
						+ " where Name='" + name + "' and Owner= '"+owner+"';");
				
				if (rs.next()) {
					String path = rs.getString("Storage_Path");
					return path;
				}	
				
			}
			
		} catch (Exception ex) {
			ex.printStackTrace();
			try {
				conn.rollback();
			} 
			catch (SQLException e) 
			{
			}
		} finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return null;
		
	}
	
	public NameSpace getMetadata(String filename, String owner) {
		Connection conn = null;
		NodeDesc node = null;
		NameSpace.Builder ns = eye.Comm.NameSpace.newBuilder();
		try {
			conn = cpool.getConnection();
			conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
			if (conn != null) {
				System.out.println("Connection successful!");
				Statement stmt = conn.createStatement();
				ResultSet rs = stmt.executeQuery("select Name, Owner, Dest_Node, Storage_Path, port from " + sTable_space
						+ " where Name='" + filename + "' and Owner= '"+owner+"';");
				
				if (rs.next()) {
					String ip = rs.getString("Dest_Node"); //MetaData IP
					String path = rs.getString("Storage_Path"); // MetaData Storage_Path
					int port = rs.getInt("port"); //MetaData Port
					
					ns.setName(filename);
					ns.setIpAddress(ip);
					//ns.setPort(port);
					ns.setStoragePath(path);
				
				}	
				
			}
			
		} catch (Exception ex) {
			ex.printStackTrace();
			logger.error("failed/exception on creating space " + filename, ex);
			try {
				conn.rollback();
			} catch (SQLException e) {
			}

			// indicate failure
		//	return false;
		} finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return ns.build();
		//return true;
	}
	@Override
	public NameSpace createNameSpace(NameSpace space) {
		if (space == null)
			return space;

		Connection conn = null;
		try {
			conn = cpool.getConnection();
			conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);

		} catch (Exception ex) {
			ex.printStackTrace();
			logger.error("failed/exception on creating space " + space, ex);
			try {
				conn.rollback();
			} catch (SQLException e) {
			}

			// indicate failure
			return null;
		} finally {
			if (conn != null) {
				try {
					conn.close();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		return space;
	}

	@Override
	public boolean removeNameSpace(long spaceId) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean addDocument(String namespace, Document doc) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean removeDocument(String namespace, long docId) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean updateDocument(String namespace, Document doc) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public List<Document> findDocuments(String namespace, Document criteria) {
		// TODO Auto-generated method stub
		return null;
	}

}
