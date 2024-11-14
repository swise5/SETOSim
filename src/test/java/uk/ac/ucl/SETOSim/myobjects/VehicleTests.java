package uk.ac.ucl.SETOSim.myobjects;

import org.junit.Test;


import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;

import uk.ac.ucl.SETOSim.mysim.TakamatsuSim;
import uk.ac.ucl.SETOSim.myobjects.Person;
import uk.ac.ucl.SETOSim.myobjects.Vehicle;
import sim.field.geo.GeomVectorField;

import static org.junit.Assert.*;

import java.util.ArrayList;

public class VehicleTests {

	public static String testingDirectory = "data/testing/";

	
	public static TakamatsuSim setupTestingWorld(String testingDirectory, long key) {
		TakamatsuSim ts = new TakamatsuSim(key);
		ts.dirName = testingDirectory;
		return ts;
	}
	
	static Person setupTestingPerson(TakamatsuSim world) {
		world.agentsLayer = new GeomVectorField(10, 10);
		//world.agentsLayer.setMBR(new Envelope(0, 1, 0, 1));
		Coordinate pLoc = new Coordinate(0, 0);
		Person p = new Person("testing1", pLoc, pLoc, null, null, 0, 0, world);
		return p;
	}
	
	@Test
	public void OperatorsAndOccupantsCanEnterAndLeaveTheVehicle() {
		
		// ASSIGN -
		TakamatsuSim world = setupTestingWorld(this.testingDirectory, 0);		
		Person p = setupTestingPerson(world);
		Person passenger = setupTestingPerson(world);
		Vehicle sut = new Vehicle(null, null, 1, null);
			
		// ACT ---
		// add the operator
		sut.setOperator(p);
		Person operator = sut.getOperator();
		int numInitialPassengers = sut.getPassengers().size();
		boolean initialOccupied = sut.hasOccupants();
		
		// add a passenger
		sut.addPassenger(passenger);
		int numPassengersWhenOneAdded = sut.getPassengers().size();
		boolean occupiedByPassenger = sut.hasOccupants();
		
		// take the operator out and confirm
		sut.removeOperator();
		boolean occupiedOnlyByPassenger = sut.hasOccupants();
		
		// now remove the passenger
		ArrayList <Person> formerOccupants = sut.removeAllPassengers();
		int numFinalPassengers = sut.getPassengers().size();
		boolean occupiedAfterAllLeft = sut.hasOccupants();

		
		// ASSERT - 
		
		// operator tests
		assertEquals(p, operator); // checking that someone is in there
		assertEquals(0, numInitialPassengers); // no passengers added yet
		assertTrue(initialOccupied); // there are no passengers, but there's someone in the vehicle
		
		// passenger tests
		assertEquals(1, numPassengersWhenOneAdded); // when passenger added, should be 1 person - operator is separate!
		assertTrue(occupiedByPassenger); // they're both in there
		
		// operator removed, passenger still there
		assertTrue(occupiedOnlyByPassenger); // even though the operator has left, the vehicle is still occupied!
		
		// passenger removed
		assertTrue(formerOccupants.contains(passenger)); // passenger is in the group removed from the vehicle
		assertEquals(0, numFinalPassengers); // passenger list is now reset to be empty
		assertFalse(occupiedAfterAllLeft); // there shouldn't be anyone left there
	}
	
	
}
