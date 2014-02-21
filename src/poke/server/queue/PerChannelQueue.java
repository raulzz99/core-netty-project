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
package poke.server.queue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.lang.Thread.State;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingDeque;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.resources.DocumentResource;
import poke.server.Server;
import poke.server.conf.NodeDesc;
import poke.server.conf.ServerConf;
import poke.server.management.HeartbeatManager;
import poke.server.resources.Resource;
import poke.server.resources.ResourceFactory;
import poke.server.resources.ResourceUtil;
import poke.server.routing.ForwardResource;
import poke.server.routing.ServerHandler;
import poke.server.storage.InMemoryStorage;
import poke.server.storage.jdbc.DatabaseStorage;
import poke.server.storage.jdbc.NameSpaceInfo;
import poke.servertoserver.ServerConnector;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessage;

import eye.Comm.Header.ReplyStatus;
import eye.Comm.Document;
import eye.Comm.Header;
import eye.Comm.NameSpace;
import eye.Comm.Payload;
import eye.Comm.PayloadReply;
import eye.Comm.Request;
import eye.Comm.Response;
import eye.Comm.RoutingPath;

/**
 * A server queue exists for each connection (channel). A per-channel queue
 * isolates clients. However, with a per-client model. The server is required to
 * use a master scheduler/coordinator to span all queues to enact a QoS policy.
 * 
 * How well does the per-channel work when we think about a case where 1000+
 * connections?
 * 
 * @author gash
 * 
 */
public class PerChannelQueue implements ChannelQueue {
	protected static Logger logger = LoggerFactory.getLogger("server");
	private  InMemoryStorage docStorer = new InMemoryStorage();
	private Channel channel;
	private LinkedBlockingDeque<com.google.protobuf.GeneratedMessage> inbound;
	private LinkedBlockingDeque<com.google.protobuf.GeneratedMessage> outbound;
	private OutboundWorker oworker;
	private InboundWorker iworker;
	private ServerConf cfg = Server.conf;
	private static int replicaCount = 1;
	DatabaseStorage ds =null;
	private Properties prop=null;
	// not the best method to ensure uniqueness
	private ThreadGroup tgroup = new ThreadGroup("ServerQueue-" + System.nanoTime());
	
	private HashMap<String, ServerConnector> nearNeighborServers = new HashMap<String, ServerConnector>();
	private static final int CHUNK_SIZE = 100; 
	private long FILE_SIZE = 0; 
	private long TOTAL_CHUNK = 0; 

	protected PerChannelQueue(Channel channel) {
		this.channel = channel;
		init();
	}

	protected void init() {
		prop = new Properties();
		try {
			String path = System.getProperty("user.dir")+"/../../Server_DB_properties.properties";
			prop.load(new FileInputStream(path));
			ds= new DatabaseStorage(prop);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		inbound = new LinkedBlockingDeque<com.google.protobuf.GeneratedMessage>();
		outbound = new LinkedBlockingDeque<com.google.protobuf.GeneratedMessage>();

		iworker = new InboundWorker(tgroup, 1, this);
		iworker.start();

		oworker = new OutboundWorker(tgroup, 1, this);
		oworker.start();

		// let the handler manage the queue's shutdown
		// register listener to receive closing of channel
		// channel.getCloseFuture().addListener(new CloseListener(this));
		Iterator confList = cfg.getNearest().getNearestNodes().values().iterator();
		
		while (confList.hasNext()) {
			NodeDesc node = (NodeDesc)confList.next();
//			logger.info("INIT NODE PORT: "+node.getPort());
//			logger.info("INIT NODE HOST: "+node.getHost());
			ServerConnector serve = new ServerConnector(node.getHost(),node.getPort());
			//serve = ServerConnector.getInstance(node.getHost(), node.getPort());
//			logger.info("INIT SERVER NODE PORT: "+serve.getPort());
//			logger.info("INIT SERVER NODE HOST: "+serve.getHost());
			nearNeighborServers.put(node.getHost(), serve);
		}
	}

	protected Channel getChannel() {
		return channel;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see poke.server.ChannelQueue#shutdown(boolean)
	 */
	@Override
	public void shutdown(boolean hard) {
		logger.info("server is shutting down");

		channel = null;

		if (hard) {
			// drain queues, don't allow graceful completion
			inbound.clear();
			outbound.clear();
		}

		if (iworker != null) {
			iworker.forever = false;
			if (iworker.getState() == State.BLOCKED || iworker.getState() == State.WAITING)
				iworker.interrupt();
			iworker = null;
		}

		if (oworker != null) {
			oworker.forever = false;
			if (oworker.getState() == State.BLOCKED || oworker.getState() == State.WAITING)
				oworker.interrupt();
			oworker = null;
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see poke.server.ChannelQueue#enqueueRequest(eye.Comm.Finger)
	 */
	@Override
	public void enqueueRequest(Request req) {
		try {
			inbound.put(req);
		} catch (InterruptedException e) {
			logger.error("message not enqueued for processing", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see poke.server.ChannelQueue#enqueueResponse(eye.Comm.Response)
	 */
	@Override
	public void enqueueResponse(Response reply) {
		if (reply == null){
			//logger.info("reply is null");
			return;
		}
		try {
			outbound.put(reply);
		} catch (InterruptedException e) {
			logger.error("message not enqueued for reply", e);
		}
	}

	protected class OutboundWorker extends Thread {
		int workerId;
		PerChannelQueue sq;
		boolean forever = true;

		public OutboundWorker(ThreadGroup tgrp, int workerId, PerChannelQueue sq) {
			super(tgrp, "outbound-" + workerId);
			this.workerId = workerId;
			this.sq = sq;

			if (outbound == null)
				throw new RuntimeException("connection worker detected null queue");
		}

		@Override
		public void run() {
			
			Channel conn = sq.channel;
			if (conn == null || !conn.isOpen()) {
				PerChannelQueue.logger.error("connection missing, no outbound communication");
				return;
			}

			while (true) {
				if (!forever && sq.outbound.size() == 0)
					break;

				try {
					// block until a message is enqueued
					GeneratedMessage msg = sq.outbound.take();
					if (conn.isWritable()) {
						boolean rtn = false;
						if (channel != null && channel.isOpen() && channel.isWritable()) {
							ChannelFuture cf = channel.write(msg);

							// blocks on write - use listener to be async
							cf.awaitUninterruptibly();
							rtn = cf.isSuccess();
							if (!rtn)
								sq.outbound.putFirst(msg);
						}

					} else
						sq.outbound.putFirst(msg);
				} catch (InterruptedException ie) {
					break;
				} catch (Exception e) {
					PerChannelQueue.logger.error("Unexpected communcation failure", e);
					break;
				}
			}

			if (!forever) {
				PerChannelQueue.logger.info("connection queue closing");
			}
		}
	}

	protected class InboundWorker extends Thread {
		int workerId;
		PerChannelQueue sq;
		boolean forever = true;

		public InboundWorker(ThreadGroup tgrp, int workerId, PerChannelQueue sq) {
			super(tgrp, "inbound-" + workerId);
			this.workerId = workerId;
			this.sq = sq;

			if (outbound == null)
				throw new RuntimeException("connection worker detected null queue");
		}

		@Override
		public void run() {
			Channel conn = sq.channel;
			logger.info("Server side---- Inbound worker run method " );
			if (conn == null || !conn.isOpen()) {
				PerChannelQueue.logger.error("connection missing, no inbound communication");
				return;
			}

			while (true) {
				if (!forever && sq.inbound.size() == 0)
					break;

				try {
					// block until a message is enqueued
					GeneratedMessage msg = sq.inbound.take();
					// process request and enqueue response
					if (msg instanceof Request) 
					{
						Request req = ((Request) msg);
						Resource rsc = ResourceFactory.getInstance().resourceInstance(req.getHeader());
						// do we need to route the request?
						Response reply = null;
						Request.Builder reqBuilder = req.toBuilder();
						
						if (rsc == null) {
							logger.error("failed to obtain resource for " + req);
							reply = ResourceUtil.buildError(req.getHeader(), ReplyStatus.FAILURE,
									"Request not processed");
						}
						if (! req.getHeader().getTag().equals("response") ) 
						{
							String path = null;
							if (req.getHeader().getRoutingId() == Header.Routing.DOCADD) 
							{
								logger.info("******INSIDE DOC ADD*******lEADER STATUS IS " +HeartbeatManager.getInstance().isLeader());
							//	if(!(cfg.getServer().getGeneral().get("nodeType").equals("leader")))
								if(HeartbeatManager.getInstance().isLeader()==false)
								{
									logger.info(" Inside DOCADD and I am NOT the leader ");
									docStorer.addDocument(req.getBody().getSpace().getName(),req.getBody().getDoc());
									
									if((req.getBody().getDoc().getChunkId()) == (req.getBody().getDoc().getTotalChunk()))
									{
										logger.info("CBefore Save file is called " +req.getBody().getDoc().getDocName());
										logger.info("CHUNK ID IS " + req.getBody().getDoc().getChunkId() );
										path = docStorer.saveFile(req.getBody().getDoc(), req.getBody().getSpace(), req.getBody().getDoc().getDocName());
										NameSpace.Builder ns = req.getBody().getSpace().toBuilder();
										//Request.Builder r = req.toBuilder();
										eye.Comm.Payload.Builder p = eye.Comm.Payload.newBuilder();
										
										ns.setStoragePath(path);
										
										p.setSpace(ns.build());
										
										reqBuilder.setBody(p.build());
										reqBuilder.setHeader(req.getHeader());
										
										reply = rsc.process(reqBuilder.build());
										sq.enqueueResponse(reply);
									}
								}
							}
							boolean addresult = false;
							NameSpaceInfo nsResult = null;
							Request.Builder r = null;
	                        if (req.getHeader().getRoutingId() == Header.Routing.METADD) 
	                        {
	                                NameSpace tempns = req.getBody().getSpace();  
	                                addresult = ds.addNameSpace(tempns.getName(),tempns.getOwner(),req.getHeader().getToNode(),tempns.getStoragePath(),req.getHeader().getToPort()); // STORE TO DB
	                                if(addresult){				
										reply = rsc.process(req);
										sq.enqueueResponse(reply);
	                                }
	                        }
	                        else if(req.getHeader().getRoutingId() == Header.Routing.DOCQUERY){
	                        	logger.info("Check IF  I am the leader " +HeartbeatManager.getInstance().isLeader());
	                        	if(HeartbeatManager.getInstance().isLeader())
	                        //	if((cfg.getServer().getGeneral().get("nodeType").equals("leader")))
								{
	                        		logger.info("Inside  DOCQUERY and I am the leader ");
	                        		nsResult = ds.checkNameSpace(req.getBody().getSpace().getName(), req.getBody().getSpace().getOwner());
		                        	eye.Comm.Header.Builder h = req.getHeader().toBuilder();
	                     			eye.Comm.RoutingPath.Builder rp = eye.Comm.RoutingPath.newBuilder();
	                     			//r = req.toBuilder();
	                     			logger.info("ADD RESULT IS: " + nsResult);
	                        		if(nsResult != null)
	                        		{
	                        			logger.info("FILE WAS FOUND");
	                        			
	                        			ChannelQueue queue = ServerHandler.mapCorrelation.get(req.getHeader().getCoorelationId());
                        				h.setReplyMsg("SUCCESS");
                        				int rpCount = req.getHeader().getPathCount();
                        				RoutingPath.Builder rpbuilder  = null;
                        				if(rpCount==0){
                        					rpCount = 1;
                        					rpbuilder  =  eye.Comm.RoutingPath.newBuilder();
                        				}
                        				else{
                        					rpbuilder = req.getHeader().getPath(rpCount-1).toBuilder();
                        				}
                        				rpbuilder.setNode(nsResult.getDest_Node());
                        				rpbuilder.setPort(nsResult.getPort());
                        				rpbuilder.setTime(System.currentTimeMillis());		
    	                        		//r.getHeader().getPathList().add(rpbuild.build());
    	                        		h.addPath(rpbuilder.build());
                        				reqBuilder.setHeader(h.build());
                        				reply = rsc.process(reqBuilder.build());
                        				queue.enqueueResponse(reply);		
                        				logger.info("Sending response in doc query " + reply.toString());
	                        		}
	                        		else{
	                        			long hopcount = req.getHeader().getRemainingHopCount();
	                        			
	                        			if(hopcount != 0){
	                        			
	                        			rp.setNode(cfg.getServer().getGeneral().get("host"));
	                        			rp.setTime(System.currentTimeMillis());
	                        			h.addPath(rp.build());
										h.setRemainingHopCount(hopcount--);
										
										reqBuilder.setHeader(h.build());
	                        			}else{
	                        				ChannelQueue queue = ServerHandler.mapCorrelation.get(req.getHeader().getCoorelationId());
	                        				h.setReplyMsg("failure");
	                        			
	                        				RoutingPath.Builder rpbuild = RoutingPath.newBuilder();
	                        				rpbuild.setNode(cfg.getServer().getGeneral().get("host"));
	                        				rpbuild.setTime(System.currentTimeMillis());
	                        				
	                        				//r.getHeader().getPathList().add(rpbuild.build());
	                        				h.addPath(rpbuild.build());
	                        				reqBuilder.setHeader(h.build());
	                        				reply = rsc.process(reqBuilder.build());
	                        				queue.enqueueResponse(reply);		
	                        			}
	                        		}
								}
	                        } else if (req.getHeader().getRoutingId() == Header.Routing.DOCFIND){
	                        	logger.info("INSIDE DOC FIND ON MY SERVER");
	                        	String pathOnServer = ds.getStoragePath(req.getBody().getSpace().getName(), req.getBody().getSpace().getOwner());
//	                        	File file = new File(pathOnServer);
	                        	ChannelQueue queue = ServerHandler.mapCorrelation.get(req.getHeader().getCoorelationId());
	                        	chunkFile(pathOnServer,req.getBody().getSpace().getName(),req.getBody().getSpace().getOwner(),queue,req.getHeader().getCoorelationId());
	                        }
							String nodeid = cfg.getServer().getGeneral().get("node.id");
							Iterator confList = cfg.getNearest().getNearestNodes().values().iterator();
							int count = 0;
							while(confList.hasNext())
							{
								count++;
									logger.info("loop count: "+count);
									NodeDesc node = (NodeDesc)confList.next();
									
									if (req.getHeader().getRoutingId() == Header.Routing.DOCADD)
									{
										 
									//	if (cfg.getServer().getGeneral().get("nodeType").equals("leader") && node.getNodeType().equals("internal")) 
										if (HeartbeatManager.getInstance().isLeader() && node.getNodeType().equals("internal")) 
										{
											ServerConnector server = nearNeighborServers.get(node.getHost());
											ForwardResource fr = new ForwardResource(req.getHeader().getRoutingId(), node, server);
											fr.process(req);
										}
									}
									if(req.getHeader().getRoutingId() == Header.Routing.DOCQUERY && node.getNodeType().equals("external"))
									{
											int pathCount = req.getHeader().getPathCount();
											RoutingPath rpath = req.getHeader().getPath(pathCount-1);
											
											if(node.getNodeId().equals(rpath.getNode()))
											{	
												ServerConnector server = nearNeighborServers.get(node.getHost());
												ForwardResource fr = new ForwardResource(req.getHeader().getRoutingId(), node, server);
												fr.process(r.build());
											}
									}
							}
					}
					else{//ELSE REQ IS RESPONSE
						if (req.getHeader().getRoutingId() == Header.Routing.DOCADD && req.getHeader().getReplyMsg().equals("SUCCESS")){
							logger.info("Server: "+req.getHeader().getToNode()+"DOC ADD STATUS: "+req.getHeader().getReplyMsg());
							NameSpace tempns = req.getBody().getSpace();
							boolean addresult = ds.addNameSpace(tempns.getName(),tempns.getOwner(),req.getHeader().getToNode(),tempns.getStoragePath(),req.getHeader().getToPort()); // STORE TO DB
							logger.info("ADD RESULT: "+addresult);
							if(addresult){ // SUCCESSFULL ADD TO DB
								NodeDesc node = new NodeDesc();
								
								eye.Comm.Header.Builder h = req.getHeader().toBuilder();
								//Request.Builder r = req.toBuilder();
								h.setTag("request");
								reqBuilder.setHeader(h.build());
								
								ServerConnector server = nearNeighborServers.get(req.getHeader().getToNode());
								logger.info("META ADD TO FORWARD TO: "+req.getHeader().getToNode());
								logger.info("SERVER HOST: "+server.getHost());
								logger.info("SERVER POST: "+server.getPort());
								node.setHost(server.getHost());
								node.setPort(server.getPort());
								
								ForwardResource fr = new ForwardResource(Header.Routing.METADD, node, server);
								fr.process(reqBuilder.build());
							}
						}
						else if (req.getHeader().getRoutingId() == Header.Routing.METADD && req.getHeader().getReplyMsg().equals("SUCCESS")){
							logger.info("Server: "+req.getHeader().getToNode()+" META ADD STATUS: "+req.getHeader().getReplyMsg());
						}
						else if (req.getHeader().getRoutingId() == Header.Routing.DOCQUERY){
							// enqueue response
								
							ChannelQueue queue = ServerHandler.mapCorrelation.get(req.getHeader().getCoorelationId());
                				
                			reply = rsc.process(reqBuilder.build());
                			queue.enqueueResponse(reply);	

						}
					}
				}
				} catch (InterruptedException ie) {
				break;
				} catch (Exception e) {
					PerChannelQueue.logger.error("Unexpected processing failure", e);
					break;
				}
				
			}

			if (!forever) {
				PerChannelQueue.logger.info("connection queue closing");
			}
		}
	}
	
	protected Channel createConnection(){
		
		return null;
	}

	public class CloseListener implements ChannelFutureListener {
		private ChannelQueue sq;

		public CloseListener(ChannelQueue sq) {
			this.sq = sq;
		}

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			sq.shutdown(true);
		}
	}
	
	private void chunkFile(String storagePath,String fileName,String owner,ChannelQueue queue,String id ){
		File file = new File(storagePath);
		InputStream ios=null;
		try {
			ios = new FileInputStream(file);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
		}
		
		//File size in bytes
		FILE_SIZE = file.length();
		//Set totalchuck by dividing FILE_SIZE/65536 (divide by 12 for now)
		TOTAL_CHUNK = FILE_SIZE/CHUNK_SIZE;
		long fileRead = FILE_SIZE;
		
		for(long i=0; i<=TOTAL_CHUNK; i++)
		{
			
			NameSpace.Builder ns = null;
			Document.Builder f = null;
			eye.Comm.Payload.Builder p = null;
			Request.Builder r = null;
			eye.Comm.Header.Builder h = null;
			eye.Comm.Request req = null;
			
			byte[] filedata=null;
			try {
				filedata = read(ios);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (filedata == null) {
				return;
			}
			
			if(FILE_SIZE < CHUNK_SIZE){
				System.out.println("File read less than chunk size");
				com.google.protobuf.ByteString fileinfo = ByteString.copyFrom(filedata);
				
				//Namespace builder
				ns = eye.Comm.NameSpace.newBuilder();
				ns.setName(fileName);
				ns.setOwner(owner);
				// data to send
				f = eye.Comm.Document.newBuilder();
				f.setDocName(fileName);
				f.setChunkContent(fileinfo);
				f.setChunkId(i);
				f.setTotalChunk(TOTAL_CHUNK);
				// payload containing data
				p = eye.Comm.Payload.newBuilder();
				r = eye.Comm.Request.newBuilder();
				p.setDoc(f.build());
				p.setSpace(ns.build());
				r.setBody(p.build());
				
				// header with routing info
				h = Header.newBuilder();
				
				h.setTime(System.currentTimeMillis());
				h.setRoutingId(eye.Comm.Header.Routing.DOCFIND);
				h.setCoorelationId(id);
				
				r.setHeader(h.build());
		
				req = r.build();
				

			}else{
				com.google.protobuf.ByteString fileinfo = ByteString.copyFrom(filedata);
				
				//Namespace builder
				ns = eye.Comm.NameSpace.newBuilder();
				ns.setName(fileName);
				ns.setOwner(owner);
				// data to send
				f = eye.Comm.Document.newBuilder();
				f.setDocName(fileName);
				f.setChunkContent(fileinfo);
				f.setChunkId(i);
				f.setTotalChunk(TOTAL_CHUNK);
				// payload containing data
				p = eye.Comm.Payload.newBuilder();
				r = eye.Comm.Request.newBuilder();
				p.setDoc(f.build());
				p.setSpace(ns.build());
				r.setBody(p.build());
				// header with routing info
				h = Header.newBuilder();
				h.setTime(System.currentTimeMillis());
				h.setRoutingId(eye.Comm.Header.Routing.DOCFIND);
				h.setCoorelationId(id);
				r.setHeader(h.build());
				req = r.build();
				FILE_SIZE = FILE_SIZE - CHUNK_SIZE;
			}
			//queue.enqueueRequest(req);
			Resource rsc = ResourceFactory.getInstance().resourceInstance(req.getHeader());
			Response  reply = rsc.process(req);
			queue.enqueueResponse(reply);
		}
		
	}
	
	private byte[] read(InputStream ios) throws IOException{
		
	    ByteArrayOutputStream ous = null;
	    try {
	        byte[] buffer = new byte[CHUNK_SIZE];
	        ous = new ByteArrayOutputStream();
	        
	        int read = CHUNK_SIZE;
	        if ( (read = ios.read(buffer)) != -1 ) {
	        	ous.write(buffer, 0, read);
	        }
	        else{
	        	return null;
	        }
	    } 
	    catch(Exception e){
	    	System.out.print(e);
	    }
	    return ous.toByteArray();
	}
}
