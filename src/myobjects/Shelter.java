package myobjects;

import java.util.ArrayList;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import sim.util.geo.MasonGeometry;
import swise.agents.SpatialAgent;

public class Shelter extends SpatialAgent {
	
	int capacity;
	int vehicleCapacity;
	Point entrance;
	
	static double shelterDensityPerPerson = 40.; 
	
	ArrayList <Person> currentlyPresent = new ArrayList <Person> ();
	
	public Shelter(MasonGeometry g){ this(g, 0); }
	public Shelter(MasonGeometry g, int numVehicles) {
		super(g.geometry.getCoordinate());
		geometry = g.geometry;
		capacity = (int) (g.geometry.getArea() / shelterDensityPerPerson);
		vehicleCapacity = numVehicles;
		GeometryFactory fa = new GeometryFactory();
		entrance = fa.createPoint(new Coordinate(g.getDoubleAttribute("entranceX"), g.getDoubleAttribute("entranceY")));
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
	
	Point getEntrance(){
		return entrance;
	}
	
	double getArea(){
		return geometry.getArea();
	}
}