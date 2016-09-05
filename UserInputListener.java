import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Scanner;


public class UserInputListener implements Runnable{
	private byte[] sendData;
	private DatagramSocket serverSocket;
	private boolean run;
	private Scanner userScanner = null;
	private Host bfClient;
	
	public  UserInputListener(Host bfclient){
		try {
			serverSocket = new DatagramSocket();
		} catch (SocketException e) {
			System.out.println("Cannot create socket in userInputListener class");
			e.printStackTrace();
		}
		userScanner = new Scanner(System.in);
		bfClient = bfclient;
	}
	
	public void run(){
		run = true;
		while (run){
			if (userScanner.hasNextLine()){
				String command = userScanner.nextLine();
				String commandValue = determineCommand(command);
				sendMessageToBfClient(command, commandValue);
			}
		}
	}
	
	private String determineCommand(String command){
		String commandNum = "-1";
		if(command.equals("SHOWRT")){
			commandNum = "0";
		}
		else if(command.matches("\\bLINKDOWN \\d\\d?\\d?.\\d\\d?\\d?.\\d\\d?\\d?.\\d\\d?\\d? \\d+\\b")){
			commandNum = "1";
		}
		else if(command.matches("\\bLINKUP \\d\\d?\\d?.\\d\\d?\\d?.\\d\\d?\\d?.\\d\\d?\\d? \\d+\\b")){
			commandNum = "2";
		}
		else if(command.equals("CLOSE")){
//			commandNum = "3";
			System.exit(0);
		}
		else if(command.matches("\\bCHANGECOST \\d\\d?\\d?.\\d\\d?\\d?.\\d\\d?\\d?.\\d\\d?\\d? \\d+ \\d+\\b")){
			commandNum = "4";
		}
		else if (command.matches("\\bTRANSFER \\w+.\\w+ \\d\\d?\\d?.\\d\\d?\\d?.\\d\\d?\\d?.\\d\\d?\\d? \\d+\\b")){
			commandNum = "5";
		}
		return commandNum;
	}
	
	private void sendMessageToBfClient(String message, String value){
		sendData = new byte[1024];
		String toSend = value + message;
		sendData = toSend.getBytes();
		DatagramPacket sendPacket = new DatagramPacket(sendData,
				sendData.length, bfClient.getIPAddress(), bfClient.getPort());
		try {
			serverSocket.send(sendPacket);
		} catch (IOException e) {
			System.out.println("cannot send hashmap packet.");
			e.printStackTrace();
		}
	}
}
