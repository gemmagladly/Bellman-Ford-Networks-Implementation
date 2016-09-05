import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.*;
import java.util.HashMap;
import java.util.TimerTask;

public class RouteUpdater extends TimerTask{
	private HashMap<Host, Link> distanceVector;
	//	private byte[] sendData;
	private DatagramSocket serverSocket;
	private HashMap<Host, Float> immediateNeighbors;
	private float infinity = 2147483647; //the maximum value of an integer, therefore a representation of infinity
	private Host localHost;

	public RouteUpdater(HashMap<Host, Link> dv, DatagramSocket socket, HashMap<Host, Float> neighborsToSendUpdates, Host localHost){
		distanceVector = dv;
		serverSocket = socket;
		immediateNeighbors = neighborsToSendUpdates;
		this.localHost = localHost;
	}

	public void run() {
		//		prepareHashMap();
		sendUpdate();
	}

	private synchronized void sendUpdate(){
		byte [] sendData = null;
		for(Host neighbor : immediateNeighbors.keySet()){
			if(immediateNeighbors.get(neighbor) != infinity){ //don't send route updates to links that are down
				//make a copy of DV
				HashMap<Host, Link> dvToSend = new HashMap<Host, Link>();
				dvToSend.putAll(distanceVector);

				//poison-reverse
				for (Host destination : distanceVector.keySet()){
					Host pathToDestination = distanceVector.get(destination).getOrigin();//node that leads to destination
					//				System.out.println("The path to " + destination.getPort() + " is through " + pathToDestination.getPort());
					if (neighbor.equals(pathToDestination)){ //if we go thru this neighbor to get to destination
//						System.out.println("Sending infinity link to " + neighbor.getPort() +" to get to "+ destination);
						Link infinityLink = new Link(infinity, localHost);
						dvToSend.put(destination, infinityLink);
					}
				}

				sendData = prepareHashMap(dvToSend);
				if (sendData != null){
					DatagramPacket sendPacket = new DatagramPacket(sendData,
							sendData.length, neighbor.getIPAddress(), neighbor.getPort());
					try {
						serverSocket.send(sendPacket);
					} catch (IOException e) {
						System.out.println("cannot send hashmap packet.");
						e.printStackTrace();
					}
				}else{
					System.out.println("Error formatting hashmap to send to neighbor " + neighbor.getPort());
				}
			}
		}
	}

	//turn hashmap into array of bytes to be sent to neighbor hosts
	private byte[] prepareHashMap(HashMap<Host, Link> mapToSend){
		ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
		byte [] sendData = null;
		ObjectOutput out = null;
		try {
			out = new ObjectOutputStream(outBytes);   
			out.writeObject(mapToSend);
			sendData = outBytes.toByteArray();

		}catch (IOException ex) {
			System.out.println("IOException when creating hashMap to byte array");
			ex.printStackTrace();
		}
		finally {
			try {
				if (out != null) {
					out.close();
				}
				outBytes.close();
			}catch (IOException ex) {
				System.out.println("IOException when trying to close ouput object "
						+ "and/or byteArrayOutputStream");
			}
		}
		return sendData;
	}

}
