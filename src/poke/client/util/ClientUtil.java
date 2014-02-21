package poke.client.util;

import com.google.protobuf.ByteString;

import poke.util.PrintNode;
import eye.Comm.Document;
import eye.Comm.Finger;
import eye.Comm.Header;
import eye.Comm.NameSpace;
import eye.Comm.NameValueSet;

public class ClientUtil {

	public static void printDocument(Document doc) {
		if (doc == null) {
			System.out.println("document is null");
			return;
		}
		
			
			com.google.protobuf.ByteString fileinfo = doc.getChunkContent();
			String s = new String(fileinfo.toByteArray());
			System.out.println(s);
		
		
	}
	public static void printNameSpace(NameSpace space) {
		if (space == null) {
			System.out.println("namespace is null");
			return;
		}
		
		//get namespace 
		System.out.println("-------------------------------------------------------");
		System.out.println("NameSpace");
		System.out.println(" - ID   : " + space.getId());
		System.out.println(" - Name   : " + space.getName());
		System.out.println(" - Created : " + space.getCreated());
		
	}
	
	public static void printFinger(Finger f) {
		if (f == null) {
			System.out.println("finger is null");
			return;
		}

		System.out.println("Poke: " + f.getTag() + " - " + f.getNumber());
	}

	public static void printHeader(Header h) {
		System.out.println("-------------------------------------------------------");
		System.out.println("Header");
		System.out.println(" - Orig   : " + h.getOriginator());
		System.out.println(" - Req ID : " + h.getRoutingId());
		System.out.println(" - Tag    : " + h.getTag());
		System.out.println(" - Time   : " + h.getTime());
		System.out.println(" - Status : " + h.getReplyCode());
		if (h.getReplyCode().getNumber() != eye.Comm.Header.ReplyStatus.SUCCESS_VALUE)
			System.out.println(" - Re Msg : " + h.getReplyMsg());

		System.out.println("");
	}

}
