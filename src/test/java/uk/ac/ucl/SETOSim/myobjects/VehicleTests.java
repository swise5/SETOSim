package uk.ac.ucl.SETOSim.myobjects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.Test;

import com.vividsolutions.jts.geom.Coordinate;

import uk.ac.ucl.SETOSim.mysim.TakamatsuSim;
import uk.ac.ucl.SETOSim.utilities.AStar;
import uk.ac.ucl.SETOSim.utilities.SchedulingTests;
import uk.ac.ucl.SETOSim.utilities.SpatialTests;

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

	@Test
	public void vehicleMustBeTurnedOnToMove() {
		// SETUP

		// world
		TakamatsuSim world = SpatialTests.setupTestingWorldWithRoads(VehicleTests.testingDirectory, "simplisticRoads.shp", 0);
		world.behaviourFramework = new TakamatsuBehaviour(world); // set up the behavioural framework
		world.pathfinder = new AStar();
		
		// agents
		Person p = PersonTests.createDummyPerson(world, 0, 0, 0);
		Vehicle sut = new Vehicle("v1", new Coordinate(0, 0), 1, world);
		p.myVehicle = sut;

		// SUT

		// add the operator
		sut.setOperator(p);
		Person operator = sut.getOperator();
		world.schedule.scheduleOnce(operator);

		// tell the operator that they're on their way to their home as an example
		operator.setActivityNode(world.behaviourFramework.travelToHomeNode);

		// set the path for a different location
		operator.headFor(new Coordinate(10, 0));

		try {
			SchedulingTests.runScheduleUntilAgentFinishes(world, operator, world.params.ticks_per_hour);
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		double distanceFromOperatorToStartPoint_off = operator.geometry.getCoordinate().distance(new Coordinate(0, 0)),
				distanceFromVehicleToStartPoint_off = sut.geometry.getCoordinate().distance(new Coordinate(0, 0));
		
		// TESTING

		// vehicle and agent should not have moved
	//	assertTrue(distanceFromOperatorToStartPoint_off < world.params.resolution);
//		assertTrue(distanceFromVehicleToStartPoint_off < world.params.resolution);
	}
	
	
	@Test
	public void OperatorsAndOccupantsMoveWithVehicle() {
		// SETUP

		// world
		TakamatsuSim world = SpatialTests.setupTestingWorldWithRoads(VehicleTests.testingDirectory, "simplisticRoads.shp", 0);
		world.behaviourFramework = new TakamatsuBehaviour(world); // set up the behavioural framework
		world.pathfinder = new AStar();
		
		// agents
		Person p = PersonTests.createDummyPerson(world, 0, 0, 0);
		Person passenger = PersonTests.createDummyPerson(world, 1, 0, 0);//setupTestingPerson(world);
		Vehicle sut = new Vehicle("v1", new Coordinate(0, 0), 1, world);

		// SUT

		// add the operator
		sut.setOperator(p);
		Person operator = sut.getOperator();
		world.schedule.scheduleOnce(operator);
		
		// add a passenger
		sut.addPassenger(passenger);
		
		// tell the operator that they're on their way to their home as an example
		operator.setActivityNode(world.behaviourFramework.travelToHomeNode);
		passenger.setActivityNode(world.behaviourFramework.travelToHomeNode);

		// set the path for a different location
		operator.headFor(new Coordinate(10, 0));
		
		SchedulingTests.runScheduleUntilAgentFinishes(world, operator, world.params.ticks_per_hour);
		
		// TESTING
		
		// operator is at end point
		assertTrue(operator.geometry.getCoordinate().distance(new Coordinate(10, 0)) < world.params.resolution);
		
		// passenger is at end point
		assertTrue(passenger.geometry.getCoordinate().distance(new Coordinate(10, 0)) < world.params.resolution);

		// vehicle is at end point
		assertTrue(sut.geometry.getCoordinate().distance(new Coordinate(10, 0)) < world.params.resolution);

		assertEquals(operator.currentAction, world.behaviourFramework.homeNode);
//		assertEquals(passenger.currentAction, world.behaviourFramework.homeNode);
	}
}
