package uk.ac.ucl.SETOSim.utilities;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import com.vividsolutions.jts.geom.Coordinate;

import sim.field.geo.GeomVectorField;
import sim.util.Bag;
import uk.ac.ucl.SETOSim.myobjects.Person;
import uk.ac.ucl.SETOSim.myobjects.PersonTests;
import uk.ac.ucl.SETOSim.myobjects.TakamatsuBehaviour;
import uk.ac.ucl.SETOSim.mysim.TakamatsuSim;

public class EvacuationOrderTests {

	public static String testingDirectory = "data/testing/";
	public static String roadNames = "simplisticRoads.shp";
	public static String evacuationAreasUntimed = "simplisticEvacuationAreasUntimed.shp";
	public static String evacuationAreasTimed = "simplisticEvacuationAreasTimed.shp";
	public static String evacuationScenarioName = "simplisticEvacuationScenario.csv";
	public static String evacuationScenarioColname = "name";

	public TakamatsuSim setupEvacuationOrderWorld(String myEvacAreas) {
		TakamatsuSim world = SpatialTests.setupTestingWorldWithRoads(testingDirectory, roadNames, 1);
		world.behaviourFramework = new TakamatsuBehaviour(world);

		// load the evacuation areas
		world.evacuationAreas = InputCleaning.readInVectorLayer(world.params.formatInputFilename(myEvacAreas), 
				world.params.grid_width, world.params.grid_height, "evacuationAreas", new Bag());
		world.params.forecastArrivalTime = 3;
		world.params.forecastNoticePeriod = 1;
		
		// load other necessary bits so the agent can move around
		world.householdsLayer = new GeomVectorField(world.params.grid_width, world.params.grid_height);
		world.pathfinder = new AStar();
		world.params.sheltersFilename = "simplisticShelters.shp";
		world.setupShelters();

		return world;
	}
	
	@Test
	/**
	 * Basic case: no time info
	 */
	public void AllEvacuateForMandatoryUntimedOrder() {

		// SETUP 
		// load the world
		TakamatsuSim world = setupEvacuationOrderWorld(evacuationAreasUntimed);
		
		// create agents
		Person p1 = PersonTests.createDummyPerson(world, 1, new Coordinate(0, 0));
		p1.setActivityNode(world.behaviourFramework.getEntryPoint());
		Person p2 = PersonTests.createDummyPerson(world, 2, new Coordinate(10, 0));
		p2.setActivityNode(world.behaviourFramework.getEntryPoint());
		
		// SUT
		world.scheduleEvacuationOrders();
		SchedulingTests.runScheduleUntilTime(world, 10);
		
		// TESTING
		
		assertEquals(p1.getActivityNode().getTitle(), "Evacuated");
		assertEquals(p2.getActivityNode().getTitle(), "Home");
		assert(p1.getEvacuationRecord().contains("START:"));
		assert(p1.getEvacuationRecord().contains("ENTER_SHELTER"));
		assert(p1.getEvacuationRecord().contains("FINISH_EVAC"));
	}
	
	@Test
	/**
	 * Basic case: time info
	 */
	public void AllEvacuateForMandatoryTimedOrder() {

		// SETUP 
		// load the world
		TakamatsuSim world = setupEvacuationOrderWorld(evacuationAreasTimed);
		
		// create agents
		Person p1 = PersonTests.createDummyPerson(world, 1, new Coordinate(0, 0));
		p1.setActivityNode(world.behaviourFramework.getEntryPoint());
		Person p2 = PersonTests.createDummyPerson(world, 2, new Coordinate(10, 0));
		p2.setActivityNode(world.behaviourFramework.getEntryPoint());
		
		// SUT
		world.scheduleEvacuationOrders();
		SchedulingTests.runScheduleUntilTime(world, 10);
		
		// TESTING
		
		assertEquals(p1.getActivityNode().getTitle(), "Evacuated");
		assertEquals(p2.getActivityNode().getTitle(), "Home");
		assert(p1.getEvacuationRecord().contains("START:"));
		assert(p1.getEvacuationRecord().contains("ENTER_SHELTER"));
		assert(p1.getEvacuationRecord().contains("FINISH_EVAC"));
	}
	
	@Test
	/**
	 * Basic case: time info
	 */
	public void AllEvacuateForScenario() {

		// SETUP 
		// load the world
		TakamatsuSim world = setupEvacuationOrderWorld(evacuationAreasUntimed);
		world.params.evacuationScenarioFilename = evacuationScenarioName;
		world.params.evacuationAreaIDColumnName = evacuationScenarioColname;
		
		// create agents
		Person p1 = PersonTests.createDummyPerson(world, 1, new Coordinate(0, 0), 15); // 15 * 5 = 75 yo - senior/dependent
		p1.setActivityNode(world.behaviourFramework.getEntryPoint());
		
		Person p2 = PersonTests.createDummyPerson(world, 2, new Coordinate(0, 0), 1); // 1 * 5 = 5 yo - minor
		p2.setActivityNode(world.behaviourFramework.getEntryPoint());
		
		ArrayList <Person> people = new ArrayList <Person> ();
		people.add(p1); people.add(p2);
		
		// SUT
		world.scheduleEvacuationOrders();
		SchedulingTests.runScheduleUntilTime(world, 500);
		
		// TESTING		
		assertEquals(p1.getActivityNode().getTitle(), "Evacuated");
		assertEquals(p2.getActivityNode().getTitle(), "Evacuated");
		assert(p1.getEvacuationRecord().contains("START:"));
		assert(p1.getEvacuationRecord().contains("ENTER_SHELTER"));
		assert(p1.getEvacuationRecord().contains("FINISH_EVAC"));
	}
	
}