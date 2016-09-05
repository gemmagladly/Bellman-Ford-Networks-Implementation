import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Timer;
import java.util.TimerTask;
import java.net.*;

import javax.imageio.ImageIO;

public class bfClient {
	private int localPort;
	private int timeout;
	private HashMap<Host, Link> distanceVector;
	private HashMap<Host, Float> immediateNeighbors;
	private HashMap<Host, Float> finalImmediateNeighbors;
	private Host localHost;
	private InetAddress localIP;
	private DatagramSocket serverSocket = null;
	private float infinity = 2147483647; //the maximum value of an integer, therefore a representation of infinity 
	private HashMap<Host, SavedDV> savedNeighborDVs;
	private HashMap<Host, Host> heartBeatMonitorHash;
	private byte[] image = new byte[200000];

	public bfClient(File configuration){
		try {
			localIP = InetAddress.getLocalHost();
		}  catch (UnknownHostException e) {
			System.out.println("Cannot get local host IP.");
			e.printStackTrace();
		}

		distanceVector = new HashMap<Host, Link>();
		immediateNeighbors = new HashMap<Host, Float>();
		finalImmediateNeighbors = new HashMap<Host, Float>();
		savedNeighborDVs = new HashMap<Host, SavedDV>();
		heartBeatMonitorHash = new HashMap<Host, Host>();
		configure(configuration);
		beginRouteUpdates();
		setUpServer();
	}

	private void configure(File configuration){
		try {
			BufferedReader br = new BufferedReader(new FileReader(configuration));

			String firstLine = br.readLine();
			String[] firstLineSplit = firstLine.split(" ");

			String localPortString = firstLineSplit[0];
			try{
				localPort = Integer.parseInt(localPortString);
				localHost = new Host(localIP.getHostAddress(), localPort);
			}catch (NumberFormatException e){
				System.out.println("Local port must be an integer.");
			}
			String timeoutString = firstLineSplit[1];
			setTimeout(timeoutString);

			String neighborConfigs;
			while((neighborConfigs = br.readLine()) != null){
				configureNeighbor(neighborConfigs);
			}
		}catch (FileNotFoundException e) {
			System.out.println("Cannot open configuration file.");
			e.printStackTrace();
			System.exit(0);
		} catch (IOException e) {
			System.out.println("Cannot read file");
			e.printStackTrace();
			System.exit(0);
		}

		//create server
		try {
			serverSocket = new DatagramSocket(localPort);
			serverSocket.setReuseAddress(true);
		} catch (SocketException e) {
			e.printStackTrace();
		}


	}

	private void setUpInputListener(Host localhost){
		new Thread(new UserInputListener(localhost)).start();
	}

	//check timeout validity 
	private void setTimeout(String timeoutString){
		try{
			timeout = Integer.parseInt(timeoutString);
		}catch (NumberFormatException e){
			System.out.println("Timeout must be an integer.");
			e.printStackTrace();
		}
	}

	//put neighbors from config file into distance vector
	private synchronized void configureNeighbor(String neighborConfig){
		String[] neighborArr = neighborConfig.split(" ");
		String weightString = neighborArr[1];
		float weight = -1;
		try{
			weight = Float.parseFloat(weightString);
		}catch (NumberFormatException e){
			System.out.println("Weight is not a number.");
			e.printStackTrace();
		}

		String[] neighborhost = neighborArr[0].split(":");
		String neighborIP = neighborhost[0];
		String neighborPort = neighborhost[1];
		Host neighbor = new Host(neighborIP, neighborPort);

		//add neighbor Host to distance vector and immediateNeighbors hash
		Link neighborLink = new Link(weight, localHost);
		distanceVector.put(neighbor, neighborLink);
		immediateNeighbors.put(neighbor, weight);
		finalImmediateNeighbors.put(neighbor, weight);
		establishHeartBeatListening(neighbor);
	}

	private synchronized void establishHeartBeatListening(Host neighbor){
		try {
			DatagramSocket timerSocket = new DatagramSocket();
			Thread heartbeatThread = new Thread(new bfClientServer(localHost, neighbor, timerSocket, timeout));
			heartbeatThread.start();
			//put in heartbeatMonitorHash
			String ip = localHost.getIPAddress().getHostAddress();
			int port = timerSocket.getLocalPort();
			Host socketHost = new Host(ip, port);
			heartBeatMonitorHash.put(neighbor, socketHost);
		} catch (SocketException e) {
			System.out.println("Cannot create heart beat monitor socket for " + neighbor.toString());	
			e.printStackTrace();
		}
	}

	private synchronized void beginRouteUpdates(){
		TimerTask routeUpdate = new RouteUpdater(distanceVector, serverSocket, immediateNeighbors, localHost);
		Timer timer = new Timer();
		timer.scheduleAtFixedRate(routeUpdate, 0, 30000);
	}

	@SuppressWarnings("unchecked")
	private void setUpServer(){
		setUpInputListener(localHost);
		System.out.println("Listening on port: " + serverSocket.getLocalPort());
		byte[] receiveData = new byte[1024];
		Host receivedFrom = null;

		while(true){
			DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
			try {
				serverSocket.receive(receivePacket);
			} catch (IOException e) {
				System.out.println("ServerSocket cannot receive packet ");
				e.printStackTrace();
			}

			boolean isCommand = interpretMessage(receivePacket);

			if(isCommand == false){//it is a DV from a neighbor then

				HashMap<Host, Link> neighborDV = null;
				byte[] neighborDVBytes = receivePacket.getData();
				InetAddress neighborIPInet = receivePacket.getAddress();
				String neighborIP = neighborIPInet.getHostAddress();
				int neighborPort = receivePacket.getPort();
				receivedFrom = new Host(neighborIP, neighborPort);
				//				System.out.println("****New message from " + receivedFrom.toString());

				//send to heart beat monitor
				updateHeartBeatMonitor(receivedFrom);

				ByteArrayInputStream inBytes = new ByteArrayInputStream(neighborDVBytes);
				ObjectInput in = null;

				try{
					//extract received neighbor DV
					in = new ObjectInputStream(inBytes);
					neighborDV = (HashMap<Host, Link>) in.readObject();	
					//					showDV(neighborDV, receivedFrom);

					if(!localHost.equals(receivedFrom)){
						if (savedNeighborDVs.size() != 0){
							boolean contains = false;
							for( Host hostOfSavedDV : savedNeighborDVs.keySet()){
								if(hostOfSavedDV.equals(receivedFrom)){
									contains = true;
									savedNeighborDVs.put(hostOfSavedDV, new SavedDV(receivedFrom, neighborDV));
								}
							}
							if(!contains){
								savedNeighborDVs.put(receivedFrom, new SavedDV(receivedFrom, neighborDV));
							}
						}else{
							savedNeighborDVs.put(receivedFrom, new SavedDV(receivedFrom, neighborDV));
						}

						//if immidiateNeighbor hash value = infinity, restore original weight (in the case of returning after being closeD)
						Host receivedFromNeighbor = retrieveHostImmediateNeighbors(receivedFrom);
						if(receivedFromNeighbor != null && immediateNeighbors.containsKey(receivedFromNeighbor)){
							if(immediateNeighbors.get(receivedFromNeighbor) == infinity){
								System.out.println(receivedFrom.toString() + " has just been reopened");
								String linkUpMessage = "LINKUP " + receivedFrom.getIPAddress().getHostAddress() +
										" " + receivedFrom.getPort();
								linkUp(linkUpMessage, 1);
							}
						}
					}
				}catch (IOException ex) {
					System.out.println("IOException when processing incoming byte array");
					ex.printStackTrace();
				} catch (ClassNotFoundException e) {
					System.out.println("ClassNotFoundException when processing incoming byte array");
					e.printStackTrace();
				}
				finally {
					try {
						if (in != null) {
							in.close();
						}
						inBytes.close();
					}catch (IOException ex) {
						System.out.println("IOException when trying to close input object "
								+ "and/or byteArrayInputStream");
					}
				}

				//bellman ford update
				if(neighborDV != null){
					bellmanFordUpdate(neighborDV, neighborIP, neighborPort);
				}
				else{
					System.out.println("NeighborDV received from" + neighborIP +"is null.");
				}
			}
		}
	}

	private synchronized void updateHeartBeatMonitor(Host neighborToUpdate){
		String message = "heartbeat";
		byte[] sendData = message.getBytes();
		for(Host neighbor : heartBeatMonitorHash.keySet()){
			if (neighbor.equals(neighborToUpdate)){
				Host heartBeatListener = heartBeatMonitorHash.get(neighbor);
				DatagramPacket sendPacket = new DatagramPacket(sendData,
						sendData.length, heartBeatListener.getIPAddress(), heartBeatListener.getPort());
				try {
					serverSocket.send(sendPacket);
				} catch (IOException e) {
					System.out.println("Cannot send heart beat of " + neighborToUpdate);
					e.printStackTrace();
				}
			}
		}
	}

	private boolean interpretMessage(DatagramPacket incomingPacket){
		boolean command = false;
		byte[] data = incomingPacket.getData();

		String stringData = new String(data);
		//User entered an invalid command
		if (stringData.startsWith("-1")){
			command = true;
			System.out.println("Please enter a valid command.");
		}
		//User entered SHOWRT
		else if(stringData.startsWith("0")){ // show routing table
			System.out.println("SHOWRT entered");
			command = true;
			showrt();
		}
		else if(stringData.startsWith("1")){ //link down
			System.out.println("LINKDOWN entered");
			command = true;
			String cleanedData = new String(data, 0, incomingPacket.getLength());
			linkDown(cleanedData, 0);
		}
		else if (stringData.startsWith("8")){//received command from other host
			command = true;
			String cleanedData = new String(data, 0, incomingPacket.getLength());
			InetAddress neighborIPInet = incomingPacket.getAddress();
			String neighborIP = neighborIPInet.getHostAddress();
			int neighborPort = incomingPacket.getPort();
			//			System.out.println("Received command " + cleanedData + " from " + neighborIP + ":" + neighborPort);
			if(cleanedData.equals("8LINKDOWN")){
				linkDown("8LINKDOWN " + neighborIP + " " + neighborPort, 1);
			}
			else if(cleanedData.equals("8LINKUP")){
				linkUp("8LINKUP " + neighborIP + " " + neighborPort, 1);
			}
			else if(cleanedData.startsWith("8CHANGECOST")){
				changeCost(cleanedData + neighborIP + " " +neighborPort, 1);
			}
			else if (cleanedData.startsWith("8TRANSFER ")){
				handleImage(cleanedData, neighborIP, neighborPort, data);
			}
		}
		else if (stringData.startsWith("2")){//link up
			command = true;
			String cleanedData = new String(data, 0, incomingPacket.getLength());
			linkUp(cleanedData, 0);

		}
		else if (stringData.startsWith("4")){//changecost 
			command = true;
			String cleanData = new String(data, 0, incomingPacket.getLength());
			changeCost(cleanData, 0);
		}
		else if (stringData.startsWith("logout")){
			command = true;
			String cleanData = new String(data, 0, incomingPacket.getLength());
			String[] dataArr = cleanData.split(" ");
			String hostToLogoutString = dataArr[1];
			System.out.println("LOGOUT " + hostToLogoutString);
			String[] logoutArr = hostToLogoutString.split(":");
			String logoutIP = logoutArr[0];
			String logoutPort = logoutArr[1];
			Host toLogout = new Host(logoutIP, logoutPort);
			logout(toLogout);

		}
		else if (stringData.startsWith("5")){ //transfer
			command = true;
			String cleanData = new String(data, 0, incomingPacket.getLength());
			String[] dataArr = cleanData.split(" ");
			String fileName = dataArr[1];
			String destinationIP = dataArr[2];
			String destinationPort = dataArr[3];
			Host destHost = new Host(destinationIP, destinationPort);
			beginTransfer(fileName, destHost);
		}
		return command;
	}

	private synchronized void beginTransfer(String fileName, Host destination){
		try {
			//find next hop
			Host nextHop = findNextHop(destination);

			BufferedImage image = ImageIO.read(new File(fileName));
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(image, "jpg", out);
			out.flush();
			byte[] imageBuffer = out.toByteArray();

			out.close();
			int finalpacket = 0;
			int j=0;

			byte[] imagePacketBuff = new byte[1024];
			for(int i = 0; i<imageBuffer.length; i++){

				imagePacketBuff[j]=imageBuffer[i];
				if(i == imageBuffer.length-1){
					finalpacket = 1;
					String header = "8TRANSFER "+ destination.getIPAddress().getHostAddress() +
							" " + destination.getPort() + " " + fileName + " " + finalpacket + " "+
									i + " :";
					sendImage(nextHop, header, imagePacketBuff);
				}else{
					j++;
					if(i%1023==0 && i!=0){
						String header = "8TRANSFER "+ destination.getIPAddress().getHostAddress() +
								" " + destination.getPort() + " " + fileName + " " + finalpacket + " "+
								i + " :";
						sendImage(nextHop, header, imagePacketBuff);
						j = 0;
					}
				}
			}


		} catch (IOException e) {
			System.out.println("Please enter a valid image file.");
			return;
		}	
	}

	private synchronized Host findNextHop(Host destination){
		boolean neighborFound = false;
		Host destinationInDV = retrieveHostFromDV(destination);
		Host nextHop = destinationInDV;
		while(!neighborFound){
			nextHop = retrieveHostFromDV(nextHop);
			Host origin = distanceVector.get(nextHop).getOrigin();
			if (localHost.equals(origin)){
				neighborFound = true;
			}else{
				nextHop = origin;
			}
		}
		return nextHop;
	}

	private synchronized void handleImage(String data, String receivedFromIP, int receivedFromPort, byte[] dataBytes){
		String[] dataArr = data.split(" ");
		String destinationIP = dataArr[1];
		String destinationPort = dataArr[2];
		String fileName = dataArr[3];
		String finalPacket = dataArr[4];
		String offset = dataArr[5];
		int finalPacketInt = Integer.parseInt(finalPacket);
		int offsetInt = Integer.parseInt(offset);
		
		String[] headerArr = data.split(":");
		String header = headerArr[0] + ":";
		byte[] headerBytes = header.getBytes();
		int headerLength = headerBytes.length;
		
		Host destination = new Host(destinationIP, destinationPort);

		if(destination.equals(localHost)){
			if(offsetInt == 1023){
				image = new byte[200000];
				int k = headerLength;
				for(int i = 0; i<1023-headerLength; i++){
					image[i] = dataBytes[k];
					k++;
				}
			}else{
				int k = headerLength;
				for(int i = offsetInt-1022; i<=offsetInt-headerLength; i++){
					image[i] = dataBytes[k];
					k++;
				}
			}
			if(finalPacketInt == 1){
				System.out.println("File \""+ fileName + "\" received successfully");
				convertImage(image, fileName);
			}
		}else{
			Host nextHop = findNextHop(destination);
			if(finalPacketInt == 1){
				System.out.println("File received");
				System.out.println("Source = " + receivedFromIP + ":" + receivedFromPort);
				System.out.println("Destination = " + destination.toString());
				System.out.println("Next hop = " + nextHop);
			}
			sendMessage(nextHop, dataBytes);
		}
	}
	private void convertImage(byte[] image, String ImageName){

		try {
			InputStream in = new ByteArrayInputStream(image);
			BufferedImage bufferedImg = ImageIO.read(in);
			ImageIO.write(bufferedImg, "jpg", new File("new"+ImageName));
		} catch (IOException e) {
			System.out.println("Error while converting bytes to image file.");
			e.printStackTrace();
		}

	}


	private synchronized void logout(Host toLogout){
		//change cost to infinity, thus stop sending route updates --> linkdown
		String logoutIP = toLogout.getIPAddress().getHostAddress();
		int logoutPort = toLogout.getPort();
		linkDown("8LINKDOWN " + logoutIP + " " + logoutPort, 1);

	}

	private synchronized void changeCost(String message, int type){
		//type 0 indicates message came from user, type 1 indicates message came from other node
		//messaae = CHANGECOST [IP] [Port] [new weight]
		System.out.println(message);
		String nodePortString;
		String nodeIP;
		String newCostString;

		if(type ==0){
			String[] messageArr = message.split(" ");
			nodeIP = messageArr[1];
			nodePortString = messageArr[2];
			newCostString = messageArr[3];
		}else{
			String[] messageArr = message.split(" ");
			newCostString = messageArr[1];
			nodeIP = messageArr[2];
			nodePortString = messageArr[3];
		}

		int nodePortInt = -1;
		float newCostInt = -1;
		try{
			nodePortInt = Integer.parseInt(nodePortString);
		}catch (NumberFormatException e){
			System.out.println("Cannot turn port " + nodePortString + " into an integer.");
			return;
		}

		try{
			newCostInt = Float.parseFloat(newCostString);

		}catch (NumberFormatException e){
			System.out.println("Cannot turn cost " + nodePortString + " into an integer.");
			return;
		}
		Host nodeToChangeCost = new Host(nodeIP, nodePortInt);

		if(confirmHostDV(nodeToChangeCost)){
			//reset original cost
			Host confirmedHost = retrieveHostFromDV(nodeToChangeCost.getIPAddress().getHostAddress(), nodeToChangeCost.getPort());
			Link newCostLink = new Link(newCostInt, localHost);
			distanceVector.put(confirmedHost, newCostLink);
			immediateNeighbors.put(retrieveHostImmediateNeighbors(confirmedHost), newCostInt);
			if(type == 0){
				String messageToSend = "CHANGECOST " + newCostInt + " ";
				sendMessage(confirmedHost,messageToSend);
			}
			changeCostUpdateDV(nodeToChangeCost, newCostInt);
			bellmanFordUpdate(distanceVector, localHost.getIPAddress().getHostAddress(), localHost.getPort());
		}else{
			System.out.println("Invalid node to change cost.");
		}
	}

	private synchronized void linkUp(String message, int type){
		//type 0 indicates message came from user, type 1 indicates message came from other node
		System.out.println(message);
		String nodePortString;
		String nodeIP;


		String[] messageArr = message.split(" ");
		nodeIP = messageArr[1];
		nodePortString = messageArr[2];


		int nodePortInt = -1;
		try{
			nodePortInt = Integer.parseInt(nodePortString);
		}catch (NumberFormatException e){
			System.out.println("Cannot turn port " + nodePortString + " into an integer.");
			return;
		}
		Host nodeToLinkUp = new Host(nodeIP, nodePortInt);
		System.out.println("Link up " +nodeToLinkUp.toString());

		if(confirmHostFinalImmediateNeighbors(nodeToLinkUp)){
			//reset original cost
			Host confirmedHost = retrieveHostFromFIN(nodeToLinkUp.getIPAddress().getHostAddress(), nodeToLinkUp.getPort());
			float originalCost = finalImmediateNeighbors.get(confirmedHost);
			Link originalCostLink = new Link(originalCost, localHost);
			distanceVector.put(confirmedHost, originalCostLink);
			//re-begin sending ROUTE UPDATE messages
			immediateNeighbors.put(confirmedHost, originalCost);

			if(type == 0){
				sendMessage(nodeToLinkUp,"LINKUP");
			}
			System.out.println("Linkup complete.");
		}else{
			System.out.println("Invalid node to link up.");
		}
	}

	private synchronized void linkDown(String message, int type){
		//type 0 indicates message came from user, type 1 indicates message came from other node
		System.out.println(message);
		String nodePortString;
		String nodeIP;

		String[] messageArr = message.split(" ");
		nodeIP = messageArr[1];
		nodePortString = messageArr[2];

		int nodePortInt = -1;
		try{
			nodePortInt = Integer.parseInt(nodePortString);
		}catch (NumberFormatException e){
			System.out.println("Cannot turn port " + nodePortString + " into an integer.");
			return;
		}
		Host nodeToLinkDown = new Host(nodeIP, nodePortInt);

		//confirm entered host is an immediate neighbor
		if(confirmHostIM(nodeToLinkDown)){
			if(confirmHostDV(nodeToLinkDown)){
				//set cost to infinity
				Host confirmedHost = retrieveHostFromDV(nodeToLinkDown.getIPAddress().getHostAddress(), nodeToLinkDown.getPort());
				Link infinityLink = new Link(infinity, localHost);
				distanceVector.put(confirmedHost, infinityLink);
				String linkDownMessage  = "CHANGECOST " + nodeToLinkDown.getIPAddress().getHostAddress() + " " 
						+ nodeToLinkDown.getPort() + " " +infinity;
				changeCost(linkDownMessage, 0);

				if(type == 0){
					sendMessage(nodeToLinkDown,"LINKDOWN");
				}
				System.out.println("Linkdown complete.");
			}else{
				System.out.println("Invalid node to link down.");
			}
		}else{
			System.out.println("Invalid node to link down. Node must be an immediate neighbor.");
		}
	}


	private void sendImage(Host destination, String header, byte[] image){
		byte[] sendData = new byte[3072];
		byte[] headerBytes = header.getBytes();
		//copy header into sendData

		for(int g = 0; g<header.getBytes().length; g++){
			sendData[g] = headerBytes[g];
		}

		//copy image into sendData
		int j = 0;
		for(int i = header.getBytes().length; i < header.getBytes().length + image.length; i++){
			sendData[i] = image[j];
			j++;
		}
		DatagramPacket sendPacket = new DatagramPacket(sendData,
				sendData.length, destination.getIPAddress(), destination.getPort());
		try {
			serverSocket.send(sendPacket);
			//			System.out.println("File packet sent successfully to " + destination.toString());
		} catch (IOException e) {
			System.out.println("cannot send file to " + destination.toString());
			e.printStackTrace();
		}
	}

	private void sendMessage(Host destinationHost, String message){
		byte[] sendData = new byte[1024];
		String toSend = "";
		if(message.startsWith("8")){
			toSend = message;
		}else{
			toSend = 8+message;
		}
		sendData = toSend.getBytes();
		DatagramPacket sendPacket = new DatagramPacket(sendData,
				sendData.length, destinationHost.getIPAddress(), destinationHost.getPort());
		try {
			serverSocket.send(sendPacket);
			//			System.out.println("Successfully send packet to " + destinationHost.toString());
		} catch (IOException e) {
			System.out.println("cannot send "+ message +" packet to " + destinationHost.toString());
			e.printStackTrace();
		}
	}

	private void sendMessage(Host destinationHost, byte[] message){
		byte[] sendData = new byte[1024];
		sendData = message;
		DatagramPacket sendPacket = new DatagramPacket(sendData,
				sendData.length, destinationHost.getIPAddress(), destinationHost.getPort());
		try {
			serverSocket.send(sendPacket);
			//			System.out.println("Successfully send packet to " + destinationHost.toString());
		} catch (IOException e) {
			System.out.println("cannot send "+ message +" packet to " + destinationHost.toString());
			e.printStackTrace();
		}
	}


	private synchronized void showrt(){
		DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		Date date = new Date();
		String toPrint = dateFormat.format(date) + " Distance vector for "+ localHost.toString() +" is:\n";

		for(Host h : distanceVector.keySet()){
			String destination = h.toString();
			String link = distanceVector.get(h).getOrigin().toString();
			float cost = distanceVector.get(h).getCost();

			String listElement = "Destination = " + destination + ", Cost = " 
					+ cost + ", Link = (" + link + ")\n";

			toPrint += listElement;
		}
		System.out.println(toPrint);
	}

	private synchronized void showDV(HashMap<Host, Link> mapToShow, Host owner){
		DateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
		Date date = new Date();
		String toPrint = dateFormat.format(date) + " Distance vector for "+ owner.toString() +" is:\n";

		for(Host h : mapToShow.keySet()){
			String destination = h.toString();
			String link = mapToShow.get(h).getOrigin().toString();
			float cost = mapToShow.get(h).getCost();

			String listElement = "Destination = " + destination + ", Cost = " 
					+ cost + ", Link = (" + link + ")\n";

			toPrint += listElement;
		}
		System.out.println(toPrint);
	}

	private synchronized void changeCostUpdateDV(Host destinationOfChangedLink, float changedCost){
		Host destInDV = retrieveHostFromDV(destinationOfChangedLink);
		for(Host neighbor : savedNeighborDVs.keySet()){

			SavedDV current = savedNeighborDVs.get(neighbor);
			Host neighborInDV = retrieveHostFromDV(neighbor);
			HashMap<Host, Link> neighborDV = current.getDv();


			//Check to see if destinationOfChangedLink is there
			if(!neighbor.equals(localHost)){
				for (Host hostInNeighborDV : neighborDV.keySet()){
					if(hostInNeighborDV.equals(destinationOfChangedLink)){
						//if it is, see if cost through this node is shorter
						float costFromNeighborToDest = neighborDV.get(hostInNeighborDV).getCost();
						Host neighborInDv = retrieveHostFromDV(neighbor);
						Host neighborInNeighborHash = retrieveHostImmediateNeighbors(neighbor);
						float costToNeighbor = immediateNeighbors.get(neighborInNeighborHash);
						float costThruNeighbor = costToNeighbor + costFromNeighborToDest;
						if (costThruNeighbor < changedCost && costThruNeighbor > 0){
							//							System.out.println("Cost through " + neighbor.getPort() + " is shorter at weight " + costThruNeighbor +
							//									" than previous weight at " + changedCost);
							//if it is shorter thru new node, change link in local DV to go through new node
							Link thruNewNode = new Link(costThruNeighbor, neighborDV.get(hostInNeighborDV).getOrigin());
							distanceVector.put(destInDV, thruNewNode);
							//							showrt();

							//change link to neighbor
							Link toNeighbor = new Link(costToNeighbor, localHost);
							distanceVector.put(neighborInDV, toNeighbor);
							//							showrt();
							broadcastChange();
						}
					}
				}
			}
		}
	}

	private synchronized void broadcastChange(){
		(new Thread(new RouteUpdater(distanceVector, serverSocket, immediateNeighbors, localHost))).start();
	}

	private synchronized void bellmanFordUpdate(HashMap<Host, Link> distanceVec, String neighborIP, int neighborPort){
		Host receivedFrom = retrieveHostFromDV(neighborIP, neighborPort);
		float costToReceivedFrom = -1;
		if(receivedFrom.equals(localHost)){
			costToReceivedFrom = 0;
		}else{
			costToReceivedFrom = distanceVector.get(receivedFrom).getCost();
		}


		HashMap<Host, Link> temp = new HashMap<Host, Link>();
		for (Host h : distanceVec.keySet()){ //for each host in other node's DV that was just received
			boolean found = false;
			for (Host neighbor : distanceVector.keySet()){ //for each host in local DV
				if (h.equals(localHost)){ //signifies distance between the local node and the node from which DV was received
					found = true;
				}
				else if(h.equals(neighbor)){//h is already in local host's distance vector
					found = true;
					float originalCost = distanceVector.get(neighbor).getCost();
					float costThruNewNode = distanceVec.get(h).getCost() + costToReceivedFrom;
					if(costThruNewNode < 0){
						costThruNewNode = infinity;
					}



					if(receivedFrom.equals(localHost) && distanceVec.get(h).getOrigin().equals(localHost)){//we know its from a change cost
						Link newRoute = new Link (costThruNewNode, receivedFrom);
						temp.put(neighbor, newRoute); 
					}
					else if(costThruNewNode < originalCost&& costThruNewNode > 0){
						//						System.out.println("Updating distance vector.");
						Link newRoute = new Link (costThruNewNode, distanceVec.get(h).getOrigin());
						temp.put(neighbor, newRoute); 
					} else{// else costThruNewNode >= original cost

						//find the origin to destination in savedDV of neighbor
						Host thruNode = distanceVec.get(h).getOrigin(); 
						Host thruNodeInDV = retrieveHostFromDV(thruNode); 

						float lowest = distanceVector.get(neighbor).getCost();

						//find the origin to destination in local DV
						Host origin = distanceVector.get(neighbor).getOrigin(); 
						if(origin.equals(receivedFrom)){
							lowest = costThruNewNode;
						}

						boolean connectionToOrigin = false;
						for(Host hostOfSavedDV : savedNeighborDVs.keySet()){
							SavedDV current = savedNeighborDVs.get(hostOfSavedDV);

							float costToDesth = -1; 

							if(hostOfSavedDV.equals(thruNodeInDV)){ 
								connectionToOrigin = true;
								float costToThruNode = distanceVector.get(thruNodeInDV).getCost(); 
								HashMap<Host, Link> currentHostsHash = current.getDv();
								//								System.out.println("Current Host Hash: " + currentHostsHash.toString());

								//if current DV owner can get to h
								Host hInDV = retrieveHostFromDV(h, currentHostsHash);
								if(currentHostsHash.get(hInDV) != null){
									costToDesth = costToThruNode + currentHostsHash.get(hInDV).getCost(); 
									//									System.out.println("costToDesth (" +costToDesth +") = " + costToThruNode + " + " + currentHostsHash.get(hInDV).getCost());
									if(costToDesth < 0){
										costToDesth = infinity;
									}
									if(costToDesth < lowest){
										lowest = costToDesth;
										origin = thruNode;
										//										System.out.println("Cost of " + h.getPort() + " updated to " + costToDesth);
									}else if (thruNode.equals(origin)){
										// we know that link is un-updated bc it is telling us diff weights for same path
										lowest = costToDesth;
										//check immediateNeighbors of local node
										if(confirmHostIM(h)){
											Host immediateNeighbor = retrieveHostImmediateNeighbors(h);
											if(immediateNeighbors.get(immediateNeighbor) < lowest){
												lowest = immediateNeighbors.get(immediateNeighbor);
												origin = localHost;
											}
										}
									}
								}
							}
						}
						if (!connectionToOrigin){
							//if receivedFrom == origin to Dest in DV
							if(receivedFrom.equals(origin)){
								lowest = costThruNewNode;
							}
						}
						Link newRoute = new Link (lowest, origin);
						temp.put(neighbor, newRoute);
					}

				}
			}

			if(!found){//node is not in local DV, up until now was unreachable to local host
				temp = addNewNode(h, receivedFrom, distanceVec, temp);
			}
		}
		distanceVector.putAll(temp);
	}

	private synchronized boolean confirmHostDV(Host toConfirm){
		boolean confirmed = false;
		for(Host inDV : distanceVector.keySet()){
			if(inDV.equals(toConfirm)){
				toConfirm = inDV;
				confirmed = true;
			}
		}
		return confirmed;
	}

	private synchronized boolean confirmHostIM(Host toConfirm){
		boolean confirmed = false;
		for(Host inDV : immediateNeighbors.keySet()){
			if(inDV.equals(toConfirm)){
				toConfirm = inDV;
				confirmed = true;
			}
		}
		return confirmed;
	}

	private synchronized boolean confirmHostFinalImmediateNeighbors(Host toConfirm){
		boolean confirmed = false;
		for(Host inHash : finalImmediateNeighbors.keySet()){
			if(inHash.equals(toConfirm)){
				toConfirm = inHash;
				confirmed = true;
			}
		}
		return confirmed;
	}

	private synchronized Host retrieveHostImmediateNeighbors(Host toConfirm){
		Host host = null;
		for(Host inHash : immediateNeighbors.keySet()){
			if(inHash.equals(toConfirm)){
				host = inHash;
			}
		}
		return host;
	}

	private synchronized Host retrieveHostFromDV(Host hostToFind, HashMap<Host, Link> dv){
		Host host = null;
		for(Host inDV : dv.keySet()){
			if(inDV.equals(hostToFind)){
				host = inDV;
			}
		}
		return host;
	}

	private synchronized Host retrieveHostFromFIN(String neighborIP, int neighborPort){
		Host host = new Host(neighborIP, neighborPort);
		for(Host inHash : finalImmediateNeighbors.keySet()){
			if(inHash.equals(host)){
				host = inHash;
			}
		}
		return host;
	}

	private synchronized Host retrieveHostFromDV(String neighborIP, int neighborPort){
		Host host = new Host(neighborIP, neighborPort);
		for(Host inDV : distanceVector.keySet()){
			if(inDV.equals(host)){
				host = inDV;
			}
		}
		return host;
	}

	private synchronized Host retrieveHostFromDV(Host hostToFind){
		Host host = null;
		for(Host inDV : distanceVector.keySet()){
			if(inDV.equals(hostToFind)){
				host = inDV;
			}
		}
		return host;
	}

	private synchronized HashMap<Host, Link> addNewNode(Host nodeToAdd, Host neighbor, HashMap<Host, Link> neighborDistanceVec, HashMap<Host, Link> temp){
		float costThruNode = distanceVector.get(neighbor).getCost() + neighborDistanceVec.get(nodeToAdd).getCost();
		Link newRoute = new Link (costThruNode, neighbor);
		temp.put(nodeToAdd, newRoute);
		return temp;
	}

}
