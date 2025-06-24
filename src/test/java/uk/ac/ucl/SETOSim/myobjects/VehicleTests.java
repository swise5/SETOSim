package uk.ac.ucl.SETOSim.myobjects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.Test;

import com.vividsolutions.jts.geom.Coordinate;

import uk.ac.ucl.SETOSim.mysim.TakamatsuSim;

public class VehicleTests {

	public static String testingDirectory = "data/testing/";

	@Test
	public void OperatorsAndOccupantsCanEnterAndLeaveTheVehicle() {

		// SETUP

		TakamatsuSim world = SpatialTests.setupTestingWorld(VehicleTests.testingDirectory, 0);
		Person p = PersonTests.createDummyPerson(world, 0, 0, 0);
		Person passenger = PersonTests.createDummyPerson(world, 1, 0, 0);//setupTestingPerson(world);
		Vehicle sut = new Vehicle("v1", new Coordinate(0, 0), 1, world);

		// SUT

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


		// TESTING

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
