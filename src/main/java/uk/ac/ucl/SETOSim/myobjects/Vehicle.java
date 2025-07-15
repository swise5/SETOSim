package uk.ac.ucl.SETOSim.myobjects;

import java.util.ArrayList;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.linearref.LengthIndexedLine;

import uk.ac.ucl.SETOSim.mysim.Params;
import uk.ac.ucl.SETOSim.mysim.TakamatsuSim;
import sim.util.geo.MasonGeometry;
import uk.ac.ucl.swise.agents.TrafficAgent;
import uk.ac.ucl.swise.objects.RoadNetworkUtilities;
import uk.ac.ucl.swise.objects.network.GeoNode;

public class Vehicle extends TrafficAgent {
	
	String id;
	Person operator;
	ArrayList <Person> passengers;
	int passengerCapacity = -1;
	boolean inMotion = false;
	
	public Vehicle(String id, Coordinate position, int capacity, TakamatsuSim world) {
		super((new GeometryFactory()).createPoint(position));
		this.id = id;
		this.passengerCapacity = capacity;
		this.speed = Params.speed_vehicle;
		this.passengers = new ArrayList <Person> ();
	}
	
	public void setOperator(Person p) {
		
		// don't kick out the current operator!
		if(operator != null) {
			System.out.println("ERROR: Vehicle " + id + " cannot be reset while " + operator.getMyID() + " is operating it!");
			return;
		}
	
		// operator can't start operating one vehicle if they're still a passenger in another one - so check
		else if(p.isPassengerOfVehicle()) {
			System.out.println("ERROR: cannot set Passenger " + p.getMyID() + " to be operator of Vehicle " + id + " while already a passenger of Vehicle " + p.passengerOf.id);
			return;			
		}
		
		// otherwise, update everyone
		operator = p;
		p.operatorOf = this;
	}
	
	public Person removeOperator() {
		
		// make sure the operator exists!!
		Person result = operator;
		if(result == null) {
			System.out.println("ERROR: Vehicle " + id + " does not have an operator to remove!");
			return null;
		}

		// otherwise, proceed
		
		// there is no longer an operator
		operator = null;
		
		// reset the Person's status
		result.operatorOf = null; 
		
		return result;
	}
	
	public boolean addPassenger(Person p) {
		if(passengers.size() < passengerCapacity && ! p.isPassengerOfVehicle()) {
			passengers.add(p);
			p.passengerOf = this;
			return true;
		}
		else if(p.isPassengerOfVehicle()) {
			System.out.println("ERROR: cannot add Passenger" + p.getMyID() + ", who is already a passenger in Vehicle " + p.passengerOf.id);
			return false;
		}
		else {
			System.out.println("ERROR: Vehicle " + id + " does not have space for potential passenger " + p.getMyID());
			return false;
		}
	}
	
	public ArrayList <Person> removeAllPassengers(){
		ArrayList <Person> result = passengers;
		passengers = new ArrayList <Person> ();
		
		// update everyone regarding their location
		for(Person p: result) {
			if(p.passengerOf != this)
				System.out.println("ERROR: cannot remove Passenger" + p.getMyID() + " from Vehicle " + id + " as they are not a passenger!");
			p.passengerOf = null;
		}
		return result;
	}

	
	public Person getOperator() { return operator; }
	public ArrayList <Person> getPassengers() { return passengers; }
	public boolean hasOccupants() { return passengers.size() > 0 || operator != null; }
	public boolean inMotion() { return inMotion; }
	public boolean startMovement() {
		
		// was it already in motion? Can't turn it on!
		if(inMotion)
			return false;
		inMotion = true;
		return true;
	}
	
	public boolean stopMovement() {
		
		// was it already stopped? Can't turn it off!
		if(! inMotion)
			return false;
		inMotion = false;
		return true;
	}
	
	public void moveTo(Coordinate c) {
		this.updateLoc(c);
		for(Person p: passengers)
			p.forceUpdateLoc();
	}
}
