import java.io.Serializable;

public class Link implements Serializable {
	float cost;
	Host origin;

	public Link(float linkCost, Host originalNode){
		cost = linkCost;
		origin = originalNode;
	}

	public float getCost() {
		return cost;
	}


	public Host getOrigin() {
		return origin;
	}
	
	public void setCost(float cost) {
		this.cost = cost;
	}
	
	public void setOrigin(Host origin) {
		this.origin = origin;
	}
	
	public String toString(){
		String toReturn = "Link from node: " + origin.toString() + " with weight: " + cost;
		return toReturn;
	}
}
