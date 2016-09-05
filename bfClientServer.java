import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Timer;
import java.util.TimerTask;

public class bfClientServer implements Runnable{
	private DatagramSocket serverListeningSocket = null;
	private Host bfClient;
	private Host neighborToMonitor;
	private int timeout;
	private int timeoutInMilliseconds;

	public bfClientServer(Host bfClientHost, Host neighborToMonitor, DatagramSocket listener, int timeout){
		bfClient = bfClientHost;
		this.neighborToMonitor = neighborToMonitor;
		serverListeningSocket = listener;
		this.timeout = timeout; // in seconds
		timeoutInMilliseconds = this.timeout * 1000;;
	}

	public void run(){
		Timer timer = new Timer();
		timer.scheduleAtFixedRate(new heartbeatTimer(), 0, 3*timeoutInMilliseconds);
	}

	private class heartbeatTimer extends TimerTask{
		public void run(){
			Host receivedFrom = null;
			try{
				serverListeningSocket.setSoTimeout(3*timeoutInMilliseconds);
				byte[] receiveData = new byte[1024];

				DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
				serverListeningSocket.receive(receivePacket);

				byte[] neighborDVBytes = receivePacket.getData();
				InetAddress neighborIPInet = receivePacket.getAddress();
				String neighborIP = neighborIPInet.getHostAddress();
				int neighborPort = receivePacket.getPort();
				receivedFrom = new Host(neighborIP, neighborPort);

			}catch (SocketTimeoutException s){
				sendMessageTobfClient("logout " + neighborToMonitor.toString());
			}catch (IOException e) {
				System.out.println("ServerSocket cannot receive packet ");
				e.printStackTrace();
			}
		}
	}

	private synchronized void sendMessageTobfClient(String message){
		byte[] sendData = new byte[1024];
		sendData = message.getBytes();
		DatagramPacket sendPacket = new DatagramPacket(sendData,
				sendData.length, bfClient.getIPAddress(), bfClient.getPort());
		try {
			serverListeningSocket.send(sendPacket);
		} catch (IOException e) {
			System.out.println("cannot send logout packet.");
			e.printStackTrace();
		}
	}

}

