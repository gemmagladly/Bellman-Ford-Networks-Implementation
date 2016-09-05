import java.io.File;


public class bfClientTester {
	
	public static void main(String[] args){
		String fileName = args[0];
		File configFile = new File(fileName);
		bfClient myBfClient = new bfClient(configFile);
	}
	
}
