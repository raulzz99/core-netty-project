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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.server.resources.Resource;
import poke.server.resources.ResourceUtil;
import eye.Comm.PayloadReply;
import eye.Comm.Request;
import eye.Comm.Response;
import eye.Comm.Header.ReplyStatus;

public class NameSpaceResource implements Resource {
	protected static Logger logger = LoggerFactory.getLogger("server");

	@Override
	public Response process(Request request) {
		// TODO Auto-generated method stub
		Response.Builder rb = Response.newBuilder();
		// metadata
		rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(), ReplyStatus.SUCCESS, null));
		// payload
		PayloadReply.Builder pb = PayloadReply.newBuilder();
		pb.addSpaces(request.getBody().getSpace());
		rb.setBody(pb.build());
		Response reply = rb.build();
		return reply;
	}

}
