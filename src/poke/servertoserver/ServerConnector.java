package poke.servertoserver;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicReference;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.GeneratedMessage;

import poke.server.management.HeartbeatConnector;
import poke.server.routing.ServerDecoderPipeline;
import poke.server.routing.ServerHandler;

public class ServerConnector {

	protected static Logger logger = LoggerFactory.getLogger("server");

	private String host;
	private int port;
	private ChannelFuture channel; // do not use directly call connect()!
	private ClientBootstrap bootstrap;
	private LinkedBlockingDeque<com.google.protobuf.GeneratedMessage> outboundServer;
	private ServerDecoderPipeline serverPipeline;
	private OutboundWorker worker;
	protected static AtomicReference<ServerConnector> instance = new AtomicReference<ServerConnector>();
	private static ServerConnector server;
	
//		public static ServerConnector getInstance(String host , int port){
//			return new ServerConnector(host, port);
//		}
	public String getHost(){
		return this.host;
	}
	public int getPort(){
		return this.port;
	}
	public LinkedBlockingDeque<com.google.protobuf.GeneratedMessage> getOutboundServer() {
		return outboundServer;
	}

	public void setOutboundServer(
			LinkedBlockingDeque<com.google.protobuf.GeneratedMessage> outboundServer) {
		this.outboundServer = outboundServer;
	}

	
	
	public static ServerConnector getInstance(String host , int port){
		instance.compareAndSet(null, new ServerConnector(host,port));
		return instance.get();
	}
	
	public ServerConnector(String host , int port){
		this.host = host;
		this.port = port;
		init();
	}
	
	private void init() {
//		logger.info("Inside init");
		outboundServer = new LinkedBlockingDeque<com.google.protobuf.GeneratedMessage>();
		serverPipeline = new ServerDecoderPipeline();
		//Configure the server connection 
		//bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory());
		bootstrap = new ClientBootstrap(new NioClientSocketChannelFactory(Executors.newCachedThreadPool(),
				Executors.newFixedThreadPool(2)));
		bootstrap.setOption("connectTimeoutMillis", 10000);
		bootstrap.setOption("tcpNoDelay", true);
		bootstrap.setOption("keepAlive", true);
		bootstrap.setPipelineFactory(serverPipeline);
		worker = new OutboundWorker(this);
		worker.start();
		
	}
	
	protected Channel connect(){
		if(channel==null){
			channel = bootstrap.connect(new InetSocketAddress(host, port));
		}
		channel.awaitUninterruptibly();
		if (channel.isDone() && channel.isSuccess())
			return channel.getChannel();
		
		else
			throw new RuntimeException("Not able to establish connection to server");
		
	}
	
	protected class OutboundWorker extends Thread{
		ServerConnector conn;
		boolean forever = true;
		
		public OutboundWorker(ServerConnector conn) {
			this.conn = conn;

			if (conn.outboundServer == null)
				throw new RuntimeException("connection worker detected null queue");
		}
		
		@Override
		public void run(){
			Channel ch = conn.connect();
			if (ch == null || !ch.isOpen()) {
				ServerConnector.logger.error("connection missing, no outbound communication");
				return;
			}

			while (true) {
				
				if (!forever && conn.outboundServer.size() == 0)
				{ 
					logger.info("Outound server size is zero");
				}

				try {
					// block until a message is enqueued
					GeneratedMessage msg = conn.outboundServer.take();
					if (ch.isWritable()) {
						ServerHandler handler = conn.connect().getPipeline().get(ServerHandler.class);
						logger.info("INSIDE " + msg.toString());
						logger.info("Handler Value is " + handler);
						if (!handler.send(msg))
							conn.outboundServer.putFirst(msg);

					} 
//					else
//						conn.outboundServer.putFirst(msg);
				} catch (InterruptedException ie) {
					break;
				} catch (Exception e) {
					ServerConnector.logger.error("Unexpected communcation failure", e);
					break;
				}
			}

			if (!forever) {
				ServerConnector.logger.info("connection queue closing");
			}

			
		}

} 
	
	
	

}
