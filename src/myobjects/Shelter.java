package myobjects;

import java.util.ArrayList;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import mysim.TakamatsuSim;
import sim.util.geo.MasonGeometry;
import swise.agents.SpatialAgent;
import swise.objects.network.GeoNode;
import utilities.RoadNetworkUtilities;

public class Shelter extends SpatialAgent {
	
	int capacity;
	int vehicleCapacity;
	Coordinate entrance;
	
	static double shelterDensityPerPerson = 40.; 
	
	ArrayList <Person> currentlyPresent = new ArrayList <Person> ();
	
	public Shelter(MasonGeometry g, TakamatsuSim world){ this(g, 0, world); }
	public Shelter(MasonGeometry g, int numVehicles, TakamatsuSim world) {
		super(g.geometry.getCoordinate());
		geometry = g.geometry;
		capacity = (int) (g.geometry.getArea() / shelterDensityPerPerson);
		vehicleCapacity = numVehicles;
		
		Coordinate c = new Coordinate(g.getDoubleAttribute("entranceX"), g.getDoubleAttribute("entranceY"));
		
		// test to make sure it's close enough to the road network
		double rez = world.resolution;
		GeoNode gn = RoadNetworkUtilities.getClosestGeoNode(c, rez, world.networkLayer,world.networkEdgeLayer, world.fa);
		while(gn == null && rez < world.grid_width){
			rez *= 2;
			gn = RoadNetworkUtilities.getClosestGeoNode(c, rez, world.networkLayer,world.networkEdgeLayer, world.fa);
		}
		
		// store it with the new, updated entrance!
		entrance = (Coordinate)gn.geometry.getCoordinate().clone();
		
	}
	
	boolean roomForN(int n){
		return capacity - n >= currentlyPresent.size();
	}
	
	void addNewPerson(Person p){
		currentlyPresent.add(p);
	}
	
	boolean removePerson(Person p){
		return currentlyPresent.remove(p);
	}
	
	public Coordinate getEntrance(){
		return entrance;
	}
	
	double getArea(){
		return geometry.getArea();
	}
}