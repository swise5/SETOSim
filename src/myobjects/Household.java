package myobjects;

import java.util.ArrayList;

import com.vividsolutions.jts.geom.Coordinate;

public class Household {
	ArrayList <Person> members;
	Coordinate home;
	
	public Household(Coordinate homeLocation){
		home = (Coordinate)homeLocation.clone();
	}
	
	public Coordinate getHome(){ return home;}
	public void addMember(Person p){ members.add(p);}
}