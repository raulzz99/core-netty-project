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
package poke.resources;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.server.Server;
import poke.server.conf.NodeDesc;
import poke.server.conf.ServerConf;
import poke.server.resources.Resource;
import poke.server.resources.ResourceFactory;
import poke.server.resources.ResourceUtil;
import eye.Comm.Finger;
import eye.Comm.Header;
import eye.Comm.NameSpace;
import eye.Comm.Payload;
import eye.Comm.PayloadReply;
import eye.Comm.Request;
import eye.Comm.Response;
import eye.Comm.RoutingPath;
import eye.Comm.Header.ReplyStatus;

public class DocumentResource implements Resource {
	protected static Logger logger = LoggerFactory.getLogger("server");
	private ServerConf cfg = Server.conf;

	@Override
	public Response process(Request request) {
		// TODO Auto-generated method stub
		Response.Builder rb = Response.newBuilder();
		eye.Comm.Header.Builder h = request.getHeader().toBuilder();
		// metadata
		//rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(), ReplyStatus.SUCCESS, null));
		// payload
		PayloadReply.Builder pb = PayloadReply.newBuilder();
		
		if(request.getHeader().getRoutingId() == Header.Routing.DOCADD){
			h.setToNode(cfg.getServer().getGeneral().get("host")); //Respond with IP
			h.setToPort(cfg.getServer().getGeneral().get("port")); //To Port
			h.setRoutingId(request.getHeader().getRoutingId()); //Save Orignial Request Header Routing ID
			h.setTag("response");
			h.setReplyMsg("SUCCESS");
			
			pb.addSpaces(request.getBody().getSpace()); //Save Namespace
		}
		
		if(request.getHeader().getRoutingId() == Header.Routing.DOCQUERY){
			if(request.getHeader().getReplyMsg().equals("failure")){
				rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(), ReplyStatus.FAILURE, null));
			}
			else{
				rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(), ReplyStatus.SUCCESS, null));
			}
			pb.addSpaces(request.getBody().getSpace()); //Save Namespace
		}
		
		if(request.getHeader().getRoutingId() == Header.Routing.DOCFIND){
			
//			eye.Comm.Document.Builder f = eye.Comm.Document.newBuilder();
//			eye.Comm.NameSpace.Builder ns = eye.Comm.NameSpace.newBuilder();
//			ns.setName(request.getBody().getSpace().getName());
//			ns.setOwner(request.getBody().getSpace().getOwner());
//			f.setDocName(request.getBody().getSpace().getName());
//			f.setChunkContent(request.getBody().getDoc().getChunkContent());
//			f.setChunkId(request.getBody().getDoc().getChunkId());
//			f.setTotalChunk(request.getBody().getDoc().getTotalChunk());
//			Payload.Builder payBuilder = Payload.newBuilder();
//			payBuilder.setDoc(request.getBody().getDoc().toBuilder());
//			payBuilder.setSpace(request.getBody().getSpace().toBuilder());
			
			pb.addDocs(request.getBody().getDoc());
			pb.addSpaces(request.getBody().getSpace());
		}
		rb.setBody(pb.build());
		rb.setHeader(h.build());
		Response reply = rb.build();
		return reply;
	}

}
