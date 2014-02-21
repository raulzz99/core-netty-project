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
package poke.client;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.nio.file.Path;





import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessage;

import eye.Comm.Document;
import eye.Comm.Finger;
import eye.Comm.Header;
import eye.Comm.NameSpace;
import eye.Comm.Payload;
import eye.Comm.Request;

/**
 * provides an abstraction of the communication to the remote server.
 * 
 * @author gash
 * 
 */
public class ClientConnection {
	protected static Logger logger = LoggerFactory.getLogger("client");

	private String host;
	private int port;
	private ChannelFuture channel; // do not use directly call connect()!
	private ClientBootstrap bootstrap;
	ClientDecoderPipeline clientPipeline;
	private LinkedBlockingDeque<com.google.protobuf.GeneratedMessage> outbound;
	private OutboundWorker worker;
	//private static final int CHUNK_SIZE = 1024; 
	private static final int CHUNK_SIZE = 100; 
	private long FILE_SIZE = 0; 
	private long TOTAL_CHUNK = 0; 
	
	protected ClientConnection(String host, int port) {
		this.host = host;
		this.port = port;

		init();
	}

	/**
	 * release all resources
	 */
	public void release() {
		bootstrap.releaseExternalResources();
	}

	public static ClientConnection initConnection(String host, int port) {

		ClientConnection rtn = new ClientConnection(host, port);
		return rtn;
	}

	/**
	 * add an application-level listener to receive messages from the server (as
	 * in replies to requests).
	 * 
	 * @param listener
	 */
	public void addListener(ClientListener listener) {
		try {
			if (clientPipeline != null)
				System.out.println("CLIENT CONNECTION class --- I am here");
				clientPipeline.addListener(listener);
		} catch (Exception e) {
			logger.error("failed to add listener", e);
		}
	}
	
	public byte[] read(InputStream ios) throws IOException{
		
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
	

	public void docAdd(String filename, String owner) throws IOException {
		
	//	File file = new File("/home/ankurthuse/Desktop/CMPE275/avengers-develop/temp.txt");
		//File file = new File("/Users/raul/Desktop/temp.txt");	
		File file = new File("/home/ramya/hi.test");
		InputStream ios = new FileInputStream(file);
		
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
			
			byte[] filedata = read(ios);
			if (filedata == null) {
				return;
			}
			
			if(FILE_SIZE < CHUNK_SIZE){
				System.out.println("File read less than chunk size");
				com.google.protobuf.ByteString fileinfo = ByteString.copyFrom(filedata);
				
				//Namespace builder
				ns = eye.Comm.NameSpace.newBuilder();
				ns.setName("temp");
				ns.setOwner(owner);
				// data to send
				f = eye.Comm.Document.newBuilder();
				f.setDocName("temp");
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
				h.setOriginator("client");
				h.setTime(System.currentTimeMillis());
				h.setRoutingId(eye.Comm.Header.Routing.DOCADD);
				h.setRemainingHopCount(3);
				
				r.setHeader(h.build());
		
				req = r.build();

			}else{
				com.google.protobuf.ByteString fileinfo = ByteString.copyFrom(filedata);
				
				//Namespace builder
				ns = eye.Comm.NameSpace.newBuilder();
				ns.setName("temp");
				ns.setOwner(owner);
				// data to send
				f = eye.Comm.Document.newBuilder();
				f.setDocName("temp");
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
				h.setOriginator("client");
				h.setTime(System.currentTimeMillis());
				h.setRoutingId(eye.Comm.Header.Routing.DOCADD);
				h.setRemainingHopCount(3);
				r.setHeader(h.build());
				req = r.build();
				FILE_SIZE = FILE_SIZE - CHUNK_SIZE;
			}
		
			try {
				// enqueue message
				outbound.put(req);
			} catch (InterruptedException e) {
				logger.warn("Unable to deliver message, queuing");
			}
		}
	}

	public void docQuery(String filename, String owner){
		System.out.println("INSDIDE CLIENT DOC QUERY");
		NameSpace.Builder ns = null;
		eye.Comm.Payload.Builder p = null;
		Request.Builder r = null;
		eye.Comm.Header.Builder h = null;
		eye.Comm.Request req = null;
		
		ns = eye.Comm.NameSpace.newBuilder();
		ns.setName(filename);
		ns.setOwner(owner);
		ns.setIpAddress(this.host);
		//ns.setPort(this.port);
		
		// payload containing data
		p = eye.Comm.Payload.newBuilder();
		r = eye.Comm.Request.newBuilder();
		p.setSpace(ns.build());
		r.setBody(p.build());
		
		// header with routing info
		h = Header.newBuilder();
		h.setOriginator("client");
		h.setTime(System.currentTimeMillis());
		h.setRoutingId(eye.Comm.Header.Routing.DOCQUERY);
		h.setCoorelationId("123");
		
		r.setHeader(h.build());
		req = r.build();
	
		try {
			// enqueue message
			outbound.put(req);
		} catch (InterruptedException e) {
			logger.warn("Unable to deliver message, queuing");
		}
		
	}
	
	public void docFind(String filename, String owner){
		NameSpace.Builder ns = null;
		eye.Comm.Payload.Builder p = null;
		Request.Builder r = null;
		eye.Comm.Header.Builder h = null;
		eye.Comm.Request req = null;
		
		ns = eye.Comm.NameSpace.newBuilder();
		ns.setName(filename);
		ns.setOwner(owner);
		//ns.setIpAddress(this.host);
		//ns.setPort(this.port);
		
		// payload containing data
		p = eye.Comm.Payload.newBuilder();
		r = eye.Comm.Request.newBuilder();
		p.setSpace(ns.build());
		r.setBody(p.build());
		
		// header with routing info
		h = Header.newBuilder();
		//h.setOriginator("client");
		//h.setTime(System.currentTimeMillis());
		h.setRoutingId(eye.Comm.Header.Routing.DOCFIND);
		h.setCoorelationId("123");
		
		r.setHeader(h.build());
		req = r.build();
	
		try {
			// enqueue message
			outbound.put(req);
		} catch (InterruptedException e) {
			logger.warn("Unable to deliver message, queuing");
		}
		
	}
	
	private void init() {
		// the queue to support client-side surging
		outbound = new LinkedBlockingDeque<com.google.protobuf.GeneratedMessage>();

		// Configure the client.
		bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(Executors.newCachedThreadPool(),
				Executors.newCachedThreadPool()));

		bootstrap.setOption("connectTimeoutMillis", 10000);
		bootstrap.setOption("tcpNoDelay", true);
		bootstrap.setOption("keepAlive", true);

		// Set up the pipeline factory.
		clientPipeline = new ClientDecoderPipeline();
		System.out.println("Testing");
		bootstrap.setPipelineFactory(clientPipeline);

		// start outbound message processor
		worker = new OutboundWorker(this);
		worker.start();
	}

	/**
	 * create connection to remote server
	 * 
	 * @return
	 */
	protected Channel connect() {
		// Start the connection attempt.
		if (channel == null) {
			
			channel = bootstrap.connect(new InetSocketAddress(host, port));

			// cleanup on lost connection

		}

		// wait for the connection to establish
		channel.awaitUninterruptibly();

		if (channel.isDone() && channel.isSuccess())
			return channel.getChannel();
		else
			throw new RuntimeException("Not able to establish connection to server");
	}

	/**
	 * queues outgoing messages - this provides surge protection if the client
	 * creates large numbers of messages.
	 * 
	 * @author gash
	 * 
	 */
	protected class OutboundWorker extends Thread {
		ClientConnection conn;
		boolean forever = true;

		public OutboundWorker(ClientConnection conn) {
			this.conn = conn;

			if (conn.outbound == null)
				throw new RuntimeException("connection worker detected null queue");
		}

		@Override
		public void run() {
			Channel ch = conn.connect();
			logger.info("Inside run");
			if (ch == null || !ch.isOpen()) {
				ClientConnection.logger.error("connection missing, no outbound communication");
				return;
			}

			while (true) {
				
				if (!forever && conn.outbound.size() == 0)
					break;

				try {
					// block until a message is enqueued
					GeneratedMessage msg = conn.outbound.take();
					if (ch.isWritable()) {
						ClientHandler handler = conn.connect().getPipeline().get(ClientHandler.class);

						if (!handler.send(msg))
							conn.outbound.putFirst(msg);

					} else
						conn.outbound.putFirst(msg);
				} catch (InterruptedException ie) {
					break;
				} catch (Exception e) {
					ClientConnection.logger.error("Unexpected communcation failure", e);
					break;
				}
			}

			if (!forever) {
				ClientConnection.logger.info("connection queue closing");
			}
		}
	}
}
