package uk.ac.ucl.SETOSim.myobjects;

import static org.junit.Assert.*;
import org.junit.Test;

import com.vividsolutions.jts.geom.Coordinate;

import sim.engine.SimState;
import sim.engine.Steppable;
import sim.field.geo.GeomVectorField;
import sim.util.Bag;
import sim.util.geo.MasonGeometry;
import uk.ac.ucl.SETOSim.mysim.TakamatsuSim;
import uk.ac.ucl.SETOSim.utilities.AStar;
import uk.ac.ucl.SETOSim.utilities.SchedulingTests;
import uk.ac.ucl.SETOSim.utilities.SpatialTests;

public class ShelterTests {

	public static String testingDirectory = "data/testing/";
	
	
	@Test
	public void sheltersAcceptArrivalsIfCapacityExists() {
		// SETUP //////////////////////////////////////////
		
		// create world
		TakamatsuSim world = SpatialTests.setupTestingWorldWithRoads(testingDirectory, "simplisticRoads.shp", 1);
		world.behaviourFramework = new TakamatsuBehaviour(world); // set up the behavioural framework
		world.pathfinder = new AStar();

		// set up the shelters
		world.params.sheltersFilename = "simplisticShelters.shp";
		world.setupShelters();
		
		Person evacuee = PersonTests.createDummyPerson(world, 0, new Coordinate(0, 0), new Coordinate(0, 0));
		evacuee.setActivityNode(world.behaviourFramework.evacuatingNode);
		world.schedule.scheduleOnce(evacuee);
				
		// force stopper to slow things down
		Steppable forceStopper = new Steppable() {
			@Override
			public void step(SimState arg0) {
				// literally just to have something in the schedule
			}
		};
		world.schedule.scheduleRepeating(forceStopper, world.params.ticks_per_hour); // once an hour
		
		// morning peak isn't relevant when evacuating, so just get right into it!

		// SUT ////////////////////////////////////////////
		
		double maxTime = world.params.ticks_per_hour;
		SchedulingTests.runScheduleUntilTime(world, maxTime);
		
		// TESTING ////////////////////////////////////////
		// check that evacuee has gotten safely into the shelter
		assertEquals(evacuee.currentAction, world.behaviourFramework.evacuatedNode);
		assertEquals(evacuee.geometry.getCoordinate(), new Coordinate(0, 0));
	}

	
	
	@Test
	public void sheltersRejectArrivalsIfNoCapacity() {
		// SETUP //////////////////////////////////////////
		
		// create world
		TakamatsuSim world = SpatialTests.setupTestingWorldWithRoads(testingDirectory, "simplisticRoads.shp", 1);
		world.behaviourFramework = new TakamatsuBehaviour(world); // set up the behavioural framework
		world.pathfinder = new AStar();

		// set up the shelters
		world.params.sheltersFilename = "simplisticShelters.shp";
		world.setupShelters();
		
		// set up 5 evacuees
		for(int i = 0; i < 5; i++) {
			Person evacuee = PersonTests.createDummyPerson(world, 0, new Coordinate(0, 0), new Coordinate(0, 0));
			evacuee.setActivityNode(world.behaviourFramework.evacuatingNode);
			world.schedule.scheduleOnce(evacuee);
			world.agents.add(evacuee);
		}
				
		// morning peak isn't relevant when evacuating, so just get right into it!

		// SUT ////////////////////////////////////////////
		
		double maxTime = world.params.ticks_per_hour;
		SchedulingTests.runScheduleUntilTime(world, maxTime);
		
		// TESTING ////////////////////////////////////////
		
		// check that 2 evacuees are NOT in shelters, while 3 ARE in shelters
		int numInShelters = 0, numNotInShelters = 0;
		for(Person p: world.agents) {
			if(p.getActivityNode() == world.behaviourFramework.evacuatedNode)
				numInShelters++;
			else
				numNotInShelters++;
		}
		assertEquals(numInShelters, 3);
		assertEquals(numNotInShelters, 2);

	}
}