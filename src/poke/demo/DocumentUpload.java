package poke.demo;

import java.io.IOException;

import poke.client.ClientConnection;
import poke.client.ClientPrintListener;

public class DocumentUpload {

public DocumentUpload(){
		
} 
	
private void run() {
	ClientConnection connection = ClientConnection.initConnection("192.168.0.130", 5571);
	ClientPrintListener listener = new ClientPrintListener("Querying");
	connection.addListener(listener);

		//connection.poke("temp.txt","ankurthuse");
	//	connection.docQuery("hi","ramya");
		try {
			connection.docAdd("hi", "ramya");
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
}
	
	public static void main(String[] args) {
		DocumentUpload upload = new DocumentUpload();
		upload.run();
	}

	

}
