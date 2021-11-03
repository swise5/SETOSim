package myobjects;

import java.util.ArrayList;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;

import sim.util.geo.MasonGeometry;

public class Household extends MasonGeometry {
	ArrayList <Person> members;
	Coordinate home;
	boolean inHazardZone = false;
	
	public Household(Coordinate homeLocation){
		super((new GeometryFactory()).createPoint(homeLocation));
		home = (Coordinate)homeLocation.clone();
		members = new ArrayList <Person> ();
	}
	
	public Coordinate getHome(){ return home;}
	public void addMember(Person p){ members.add(p);}
	public ArrayList <Person> getMembers(){ return members;}
	public boolean inHazardZone() { return inHazardZone; }
	public void setInHazardZone(boolean b) { inHazardZone = b; }
}