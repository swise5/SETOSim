package uk.ac.ucl.SETOSim.myobjects;


import static org.junit.Assert.*;

import java.util.ArrayList;

import org.junit.Test;

import com.vividsolutions.jts.geom.Coordinate;

import sim.engine.SimState;
import sim.engine.Steppable;
import sim.field.network.Edge;
import sim.util.geo.MasonGeometry;
import uk.ac.ucl.swise.objects.network.GeoNode;
import uk.ac.ucl.SETOSim.mysim.TakamatsuSim;
import uk.ac.ucl.SETOSim.utilities.AStar;
import uk.ac.ucl.SETOSim.utilities.SpatialTests;

public class TrainStationTests {

	public static String testingDirectory = "data/testing/";
	
	@Test
	/**
	 * Ensure that People who attempt to take trains from train stations are able to leave the simulation through
	 * the station location. After they leave, they should not be physically present within the bounds of the simulation;
	 * they should be in a "working" status; and their point of entry/egress to the road network should be the station's
	 * location.
	 */
	public void PeopleExitTheMapThroughStations() {
		// SETUP //////////////////////////////////////////
		
		// create world and set up the pathfinder
		TakamatsuSim world = SpatialTests.setupTestingWorldWithRoads(testingDirectory, "simplisticRoads.shp", 1);
		world.behaviourFramework = new TakamatsuBehaviour(world);
		world.pathfinder = new AStar();
		
		// read in the stations file
		world.params.stationFilename = "simplisticTrainStations.shp";
		world.setupStations();

		// set up the locations involved
		Coordinate startPoint = new Coordinate(0, 0);
		GeoNode station = world.stations.get(0);

		// set up the Person and set them on course to go to work outside the simulation,
		// so that they will exit the map
		Person p = PersonTests.createDummyPerson(world, 0, startPoint, null, world.notInSimulation);
		p.setActivityNode(world.behaviourFramework.travelToWorkNode);
		world.schedule.scheduleOnce(p);
		
		// advance the timer to the morning peak
		while(world.schedule.getTime() < 8 * world.params.ticks_per_hour) {
			world.schedule.step(world);
		}
		
		// SUT ///////////////////////////////////////////
		int stillMoving = 1;
		while(stillMoving > 0)
			stillMoving = p.navigate(world.params.resolution);

		// TESTING ///////////////////////////////////////////
		
		assertEquals(p.getActivityNode(), world.behaviourFramework.workNode); // they are now at work
		assertEquals(p.geometry.getCoordinate(), world.notInSimulation); // they have left the simulation area
		assertEquals(p.getNode(), station); // their exit point was the station
	}
	
	@Test
	/**
	 * Ensure that during the course of the simulation, new Persons can arrive in the simulation space through
	 * the stations.
	 */
	public void PeopleEnterTheMapThroughStations() {
		// SETUP //////////////////////////////////////////
		
		// create world and set up the pathfinder
		TakamatsuSim world = SpatialTests.setupTestingWorldWithRoads(testingDirectory, "simplisticRoads.shp", 1);
		world.behaviourFramework = new TakamatsuBehaviour(world);
		world.pathfinder = new AStar();
		
		// read in the stations file
		world.params.stationFilename = "simplisticTrainStations.shp";
		world.setupStations();

		// set up the Person and set them on course to go home from the station
		Coordinate homePoint = new Coordinate(10, 0);
		Person p = PersonTests.createDummyPerson(world, 0, world.notInSimulation, homePoint);
		p.setActivityNode(world.behaviourFramework.travelToHomeNode);
		GeoNode station = world.stations.get(0);
		p.scheduleArrival(station, 5); // scheduled to arrive at the 5th tick
		
		// SUT ///////////////////////////////////////////
		
		// make sure they don't arrive too soon
		boolean notInSimYet = true;
		while(world.schedule.getTime() < 5) {
			notInSimYet = notInSimYet && p.geometry.getCoordinate().equals(world.notInSimulation);
			world.schedule.step(world);
		}
		
		// make sure they then do actually arrive!
		world.schedule.step(world);
		boolean arrivedAtStation = p.geometry.getCoordinate().equals(station.getGeometry().getCoordinate());
		
		// go home
		while(world.schedule.getTime() < 10)
			world.schedule.step(world);

		// TESTING ///////////////////////////////////////////
		
		assertTrue(notInSimYet); // doesn't enter until the scheduled time
		assertTrue(arrivedAtStation); // arrives at the station, at the right time
		assertEquals(p.geometry.getCoordinate(), homePoint); // arrives at home at the end
	}
	

	@Test
	public void PeopleChooseTheClosestStation() {
		// SETUP //////////////////////////////////////////
		
		// create world
		TakamatsuSim world = SpatialTests.setupTestingWorldWithRoads(testingDirectory, "simplisticRoads.shp", 1);
		world.behaviourFramework = new TakamatsuBehaviour(world); // set up the behavioural framework
		world.pathfinder = new AStar(); // set up movement
		
		// read in the stations file
		world.params.stationFilename = "simplisticTrainStations.shp";
		world.setupStations();
	
		// create four Persons to test
		// TODO in an ideal world, I'd also have them test the path distance - that is, someone at (0, 10) would actually 
		// be closer to station 2 than 1, but for now, I'm omitting this.
		
		// Two are arriving...
		Person arriver_station1 = PersonTests.createDummyPerson(world, 0, world.notInSimulation, new Coordinate(0, 0)),
			   arriver_station2 = PersonTests.createDummyPerson(world, 1, world.notInSimulation, new Coordinate(10, 0));

		// ...and two leaving.
		Person leaver_station1 = PersonTests.createDummyPerson(world, 2, new Coordinate(0, 0), null, world.notInSimulation),
			   leaver_station2 = PersonTests.createDummyPerson(world, 3, new Coordinate(10, 0), null, world.notInSimulation);
		
		// set them to be travelling either home or to work, as appropriate 
		arriver_station1.currentAction = world.behaviourFramework.travelToHomeNode; // those arriving are going home
		arriver_station2.currentAction = world.behaviourFramework.travelToHomeNode;
		leaver_station1.currentAction = world.behaviourFramework.homeNode;  // those leaving are going to work, starting from home
		leaver_station2.currentAction = world.behaviourFramework.homeNode;

		// Schedule them all to start doing things
		arriver_station1.scheduleArrival(world.stations.get(0), 8 * world.params.ticks_per_hour);
		arriver_station2.scheduleArrival(world.stations.get(1), 8 * world.params.ticks_per_hour);
		world.schedule.scheduleOnce(8 * world.params.ticks_per_hour + 1, leaver_station1);
		world.schedule.scheduleOnce(8 * world.params.ticks_per_hour + 1, leaver_station2);
		
		// holders
		boolean a_s1_arrived = false, a_s2_arrived = false;
		boolean l_s1_travelling = false, l_s2_travelling = false;

		// force stopper to slow things down
		Steppable forceStopper = new Steppable() {
			@Override
			public void step(SimState arg0) {
				// literally just to have something in the schedule
			}
		};
		world.schedule.scheduleRepeating(forceStopper, world.params.ticks_per_hour); // once an hour
		
		// advance the timer to the morning peak
		while(world.schedule.getTime() < 8 * world.params.ticks_per_hour - 1) {
			world.schedule.step(world);
		}	
		
		// SUT ////////////////////////////////////////////
		
		double maxTime = 8 * world.params.ticks_per_hour + 10;
		while(world.schedule.getTime() < maxTime) {	
			// arrivers at their stations?
			if(arriver_station1.geometry.getCoordinate().equals(new Coordinate(-5, 10)))
				a_s1_arrived = true;
			if(arriver_station2.geometry.getCoordinate().equals(new Coordinate(10, 10)))
				a_s2_arrived = true;
			
			if(leaver_station1.getActivityNode() == world.behaviourFramework.travelToWorkNode)
				l_s1_travelling = true;
			if(leaver_station2.getActivityNode() == world.behaviourFramework.travelToWorkNode)
				l_s2_travelling = true;
			
			world.schedule.step(world);
		}
		
		// TESTING ////////////////////////////////////////
		
		// Check that the arrivers have gotten home safely
		assertEquals(arriver_station1.geometry.getCoordinate(), new Coordinate(0, 0));
		assertEquals(arriver_station2.geometry.getCoordinate(), new Coordinate(10, 0));

		// ...and the leavers have left
		assertEquals(leaver_station1.geometry.getCoordinate(), world.notInSimulation);
		assertEquals(leaver_station2.geometry.getCoordinate(), world.notInSimulation);
		
		// Did the arrivers pass through the correct stations?
		assertTrue(a_s1_arrived && a_s2_arrived); // arrivers worked right
		
		// Did the leavers leave through the correct nodes?
		assertEquals(leaver_station1.getNode(), world.stations.get(0));
		assertEquals(leaver_station2.getNode(), world.stations.get(1));
		
		// Did the leavers end up travelling?
		assertTrue(l_s1_travelling && l_s2_travelling);
		
		// Check that they've all changed statuses as appropriate
		assertEquals(arriver_station1.currentAction, world.behaviourFramework.homeNode);
		assertEquals(arriver_station2.currentAction, world.behaviourFramework.homeNode);
		assertEquals(leaver_station1.currentAction, world.behaviourFramework.workNode);
		assertEquals(leaver_station2.currentAction, world.behaviourFramework.workNode);
	}
	
	@Test
	public void NonresidentCommutersFromCancelledTrainGoToShelter() {
		// SETUP //////////////////////////////////////////
		
		// create world
		TakamatsuSim world = SpatialTests.setupTestingWorldWithRoads(testingDirectory, "simplisticRoads.shp", 1);
		world.behaviourFramework = new TakamatsuBehaviour(world); // set up the behavioural framework
		world.pathfinder = new AStar(); // set up movement
		
		// read in the stations file
		world.params.stationFilename = "simplisticTrainStations.shp";
		world.setupStations();
		
		// set up the shelters
		world.params.sheltersFilename = "simplisticShelters.shp";
		world.setupShelters();
	
		// shut down the stations - people are arriving, but cannot leave through them
		for(GeoNode station: world.stations) {
			station.addAttribute("CLOSED", true);
		}
		
		// create commuters who arrive in the area unintentionally. They do not have a home location in the
		// region, and must go to shelters. They can begin by either being:
		// - travelling on the way to work
		// - travelling on the way home
		Person commuter_travellingHomeFromOutsideSimArea = PersonTests.createDummyPerson(world, 0, world.notInSimulation, null),
			   commuter_travellingToWorkThroughSimArea = PersonTests.createDummyPerson(world, 1, world.notInSimulation, world.notInSimulation);

		// set them to be travelling either home or to work, as appropriate 
		commuter_travellingHomeFromOutsideSimArea.currentAction = world.behaviourFramework.travelToHomeNode;
		commuter_travellingToWorkThroughSimArea.currentAction = world.behaviourFramework.travelToWorkNode;

		// Schedule them all to start doing things
		commuter_travellingHomeFromOutsideSimArea.scheduleArrival(world.stations.get(0), 8 * world.params.ticks_per_hour);
		commuter_travellingToWorkThroughSimArea.scheduleArrival(world.stations.get(1), 8 * world.params.ticks_per_hour);
		
		// force stopper to slow things down
		Steppable forceStopper = new Steppable() {
			@Override
			public void step(SimState arg0) {
				// literally just to have something in the schedule
			}
		};
		world.schedule.scheduleRepeating(forceStopper, world.params.ticks_per_hour); // once an hour
		
		// advance the timer to the morning peak
		while(world.schedule.getTime() < 8 * world.params.ticks_per_hour - 1) {
			world.schedule.step(world);
		}	
		
		// SUT ////////////////////////////////////////////
		
		double maxTime = 8 * world.params.ticks_per_hour + 10;
		while(world.schedule.getTime() < maxTime) {	
		
			world.schedule.step(world);
		}
		
		// TESTING ////////////////////////////////////////
		
		// Check that the arrivers have gotten home safely
		assertEquals(commuter_travellingHomeFromOutsideSimArea.geometry.getCoordinate(), new Coordinate(0, 0)); // Station 1 -> Shelter 1
		assertEquals(commuter_travellingToWorkThroughSimArea.geometry.getCoordinate(), new Coordinate(10, 0));

		// Check that they've all changed statuses as appropriate
		assertEquals(commuter_travellingHomeFromOutsideSimArea.currentAction, world.behaviourFramework.evacuatedNode);
		assertEquals(commuter_travellingToWorkThroughSimArea.currentAction, world.behaviourFramework.evacuatedNode);
		
	}
}