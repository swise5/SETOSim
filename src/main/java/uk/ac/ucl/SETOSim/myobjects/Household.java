package uk.ac.ucl.SETOSim.myobjects;

import java.util.ArrayList;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;

import sim.field.geo.GeomVectorField;
import sim.util.geo.MasonGeometry;
import uk.ac.ucl.SETOSim.utilities.PopulationSynthesis;

public class Household extends MasonGeometry {
	String id;
	ArrayList <Person> members;
	Coordinate home;
	boolean inHazardZone = false;
	boolean hasElderly = false;
	boolean hasMinors = false;
	int hash;
	
	public Household(String myId, Coordinate homeLocation, GeomVectorField householdLayer){
		super((new GeometryFactory()).createPoint(homeLocation));
		id = myId;
		home = (Coordinate)homeLocation.clone();
		members = new ArrayList <Person> ();

		if(householdLayer != null)
			householdLayer.addGeometry(this);
		
		this.hash = id.hashCode();
	}
	
	public Coordinate getHome(){ return home;}
	public void addMember(Person p){ 
		members.add(p);
		if(p.age >= 65. / PopulationSynthesis.numYearsPerBin)
			hasElderly = true;
		else if(p.age <= 18. / PopulationSynthesis.numYearsPerBin)
			hasMinors = true;
	}
	public ArrayList <Person> getMembers(){ return members;}
	public boolean inHazardZone() { return inHazardZone; }
	public void setInHazardZone(boolean b) { inHazardZone = b; }
	
	public boolean hasElderly() { return hasElderly; }
	public boolean hasMinors() { return hasMinors; }
	
	public int hashCode(){ return hash; }

}
