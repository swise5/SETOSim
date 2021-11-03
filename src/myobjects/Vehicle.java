package myobjects;

import java.util.ArrayList;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.linearref.LengthIndexedLine;

import mysim.TakamatsuSim;
import sim.util.geo.MasonGeometry;
import swise.agents.TrafficAgent;
import swise.objects.network.GeoNode;
import swise.objects.RoadNetworkUtilities;

public class Vehicle extends TrafficAgent {
	
	TakamatsuSim world;
	String id;
	Person operator;
	ArrayList <Person> passengers;
	int capacity = -1;
	
	public Vehicle(String id, Coordinate position, int capacity, TakamatsuSim world) {
		super((new GeometryFactory()).createPoint(position));
		this.id = id;
		this.world = world;
		this.capacity = capacity;
		this.speed = TakamatsuSim.speed_vehicle;
	}
	
	public void setOperator(Person p) {
		
		// don't kick out the current operator!
		if(operator != null) {
			System.out.println("ERROR: Vehicle " + id + " cannot be reset while " + operator.getMyID() + " is operating it!");
			return;
		}
		
		operator = p;
	}
	
	public Person removeOperator() {
		Person result = operator;
		operator = null;
		return result;
	}
	
	public boolean addPassenger(Person p) {
		if(passengers.size() < capacity) {
			passengers.add(p);
			return true;
		}
		else {
			System.out.println("ERROR: Vehicle " + id + " does not have space for potential passenger " + p.getMyID());
			return false;
		}
	}
	
	void placeOnEdge(Coordinate c){
		edge = RoadNetworkUtilities.getClosestEdge(c, world.resolution, world.networkEdgeLayer, world.fa);
		
		if(edge == null){
			System.out.println("\tINIT_ERROR: no nearby edge");
			return;
		}
			
		GeoNode n1 = (GeoNode) edge.getFrom();
		GeoNode n2 = (GeoNode) edge.getTo();
		
		if(n1.geometry.getCoordinate().distance(c) <= n2.geometry.getCoordinate().distance(c))
			node = n1;
		else 
			node = n2;

		segment = new LengthIndexedLine((LineString)((MasonGeometry)edge.info).geometry);
		startIndex = segment.getStartIndex();
		endIndex = segment.getEndIndex();
		currentIndex = segment.indexOf(c);
	}
	
	public int navigate(double resolution){
		if(path != null){
			double time = 1;
			while(path != null && time > 0){
				time = move(time, speed, resolution);
				if(segment != null)
					world.updateRoadUseage(((MasonGeometry)edge.info).getStringAttribute("myid"));
			}
			
			if(segment != null)
				updateLoc(segment.extractPoint(currentIndex));				

			if(time < 0){
				return -1;
			}
			else
				return 1;
		}
		return -1;
	}
}