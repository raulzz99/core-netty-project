package poke.client;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import org.apache.commons.collections.map.HashedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.client.util.ClientUtil;
import eye.Comm.Header;
import eye.Comm.RoutingPath;
import eye.Comm.Document;

/**
 * example listener that an application would use to receive events.
 * 
 * @author gash
 * 
 */
public class ClientPrintListener implements ClientListener {
	protected static Logger logger = LoggerFactory.getLogger("client");

	private String id;
	private HashMap<String,HashMap<Long, Document>> docMap = new HashMap<String,HashMap<Long,Document>>();
	
	//Hardcoded port number
	private static String PORT = "5570";

	public ClientPrintListener(String id) {
		this.id = id;
	}

	@Override
	public String getListenerID() {
		return id;
	}

	@Override
	public void onMessage(eye.Comm.Response msg) {
		System.out.println("*************on message client side id  "+msg.getHeader().getRoutingId());
		if (logger.isDebugEnabled())
		ClientUtil.printHeader(msg.getHeader());

		if (msg.getHeader().getRoutingId() == Header.Routing.FINGER)
			ClientUtil.printFinger(msg.getBody().getFinger());
		if (msg.getHeader().getRoutingId() == Header.Routing.DOCADD){
			//for (int i = 0, I = msg.getBody().getDocsCount(); i < I; i++)
				ClientUtil.printNameSpace(msg.getBody().getSpaces(0));
		}
		
		if (msg.getHeader().getRoutingId() == Header.Routing.DOCQUERY) {
			if (msg.getHeader().getReplyMsg().equals("SUCCESS")) {
				int pathCount = msg.getHeader().getPathCount();
				RoutingPath path = msg.getHeader().getPath(pathCount-1);
				String node = path.getNode();
				String port = path.getPort();
				if (port == null || port.equals("")) {
					port = PORT;
				}
				ClientConnection connection = ClientConnection.initConnection(node, Integer.parseInt(port));
				ClientPrintListener listener = new ClientPrintListener("Querying");
				connection.addListener(listener);
				String queryName = msg.getBody().getSpaces(0).getName();
				String queryOwner = msg.getBody().getSpaces(0).getOwner();
				connection.docFind(queryName,queryOwner);
			}
		}
		
		
		if (msg.getHeader().getRoutingId() == Header.Routing.DOCFIND){
			HashMap <Long, Document> map;
			
			if(docMap.get(msg.getHeader().getCoorelationId())!= null){
			    logger.info("DOC MAP IS NOT NULL");
				map = docMap.get(msg.getHeader().getCoorelationId());
				Long chunkId = msg.getBody().getDocs(0).getChunkId();
				Document doc = msg.getBody().getDocs(0);
				map.put(chunkId, doc);
				docMap.put(msg.getHeader().getCoorelationId(), map);
				}else {
				 logger.info("DOC MAP IS NULL");
				 map = new HashMap<Long, Document>();
				 map.put(msg.getBody().getDocs(0).getChunkId(), msg.getBody().getDocs(0));
				 docMap.put(msg.getHeader().getCoorelationId(), map);
				}
				
				if(msg.getBody().getDocs(0).getChunkId() == msg.getBody().getDocs(0).getTotalChunk()){
					File file = null;
			        try {
			        	
			               file = new File("/tmp/DocFind/"+msg.getBody().getSpaces(0).getName());
			               logger.info("/tmp/DocFind/"+msg.getBody().getSpaces(0).getName());
			               //logger.info("File Name is " + fileName); 
			               if (!file.exists()) {
			                	
			            	   System.out.println("Creating file  "+msg.getBody().getSpaces(0).getName());
			                         file.createNewFile();
			               }
			                FileWriter fw = new FileWriter(file);		             
			                for (Document doc1 : map.values()){
			                        com.google.protobuf.ByteString fileinfo = doc1.getChunkContent();
			                        String s = new String(fileinfo.toByteArray());
			                        logger.info(s);
			                        fw.write(s);
			                }
			            	
			                fw.close();
			                
			        } catch (IOException e) {
			                // TODO Auto-generated catch block
			                e.printStackTrace();
			        }
			        docMap.remove(msg.getHeader().getCoorelationId());
				}
//			        else {
//					docMap.put(msg.getHeader().getCoorelationId(), map);
//				}
			
				
		}
	}
}
