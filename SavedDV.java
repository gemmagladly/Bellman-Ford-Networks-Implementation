import java.util.HashMap;


public class SavedDV {
	
	private Host DVowner;
	private HashMap<Host, Link> dv;
	
	public SavedDV(Host owner, HashMap<Host,Link> distVec){
		this.DVowner = owner;
		this.dv = distVec;
	}
	
	@Override
	public boolean equals(Object obj) {
		boolean equal = false;
		SavedDV other = (SavedDV) obj;
		if(other.getDVowner().equals(DVowner)){
			equal = true;
		}
		return equal;
	}
	
	public HashMap<Host, Link> getDv() {
		return dv;
	} 
	
	public Host getDVowner() {
		return DVowner;
	}
	

}
