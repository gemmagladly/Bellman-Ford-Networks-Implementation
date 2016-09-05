import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class Host implements Serializable {
	private String ipAddress; 
	private int port; 
	private String toString;

	public Host(String x, String y) { 
		this.ipAddress = x; 
		try{
			this.port = Integer.parseInt(y);
		}catch (NumberFormatException e){
			System.out.println("Cannot turn port " + y + " into an integer.");
		}
		formatToString();
	}

	public Host(String x, int y){
		this.ipAddress = x; 
		this.port = y;
	}

	private void formatToString(){
		toString = ipAddress+":"+port; 
	}

	public InetAddress getIPAddress(){
		InetAddress address = null;
		try {
			if(ipAddress.startsWith("/")){
				address = InetAddress.getByName(ipAddress.substring(1));//remove / in front of IP-address
			}
			else{
				address = InetAddress.getByName(ipAddress);
			} 
		}catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		return address;
	}

	public int getPort(){
		return port;
	}

	public String toString(){
		toString = ipAddress+":"+port; 
		return toString;
	}

	public void setPort(int portToSet){
		this.port = portToSet;
	}

	public void setIP(String ip){
		this.ipAddress = ip;
	}

	@Override
	public boolean equals(Object other) {
		boolean equals = false;
		Host otherHost = null;
		try{
		otherHost = (Host) other;
		}catch (ClassCastException e){
			System.out.println("Cannot cast " + other.toString() + " to a Host.");
			e.printStackTrace();
		}
		if (toString.equals(otherHost.toString())){
			equals = true;
		}
		return equals;
	}
}

