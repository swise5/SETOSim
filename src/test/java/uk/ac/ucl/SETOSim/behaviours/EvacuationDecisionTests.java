package uk.ac.ucl.SETOSim.behaviours;


import static org.junit.Assert.*;

import java.util.ArrayList;

import org.junit.Test;

import com.vividsolutions.jts.geom.Coordinate;

import sim.engine.SimState;
import sim.engine.Steppable;
import sim.field.network.Edge;
import sim.util.geo.MasonGeometry;
import uk.ac.ucl.swise.objects.network.GeoNode;
import uk.ac.ucl.SETOSim.myobjects.Household;
import uk.ac.ucl.SETOSim.myobjects.Person;
import uk.ac.ucl.SETOSim.myobjects.PersonTests;
import uk.ac.ucl.SETOSim.myobjects.TakamatsuBehaviour;
import uk.ac.ucl.SETOSim.mysim.TakamatsuSim;
import uk.ac.ucl.SETOSim.utilities.AStar;
import uk.ac.ucl.SETOSim.utilities.SchedulingTests;
import uk.ac.ucl.SETOSim.utilities.SpatialTests;

public class EvacuationDecisionTests {

	public static String testingDirectory = "data/testing/";
	
	public void everyoneGetsInVehicleToEvacuate() {
		
		// create world, set up the pathfinder, and pull out two nodes
		TakamatsuSim world = SpatialTests.setupTestingWorldWithRoads(testingDirectory, "simplisticRoads.shp", 1);
		world.pathfinder = new AStar();
		world.behaviourFramework = new TakamatsuBehaviour(world);

		// locations of home, shelter
		Coordinate homePoint = new Coordinate(0, 0);
		Coordinate shelterPoint = new Coordinate(10, 0);

		// generate a Household and populate it with evacuees
		Household h = new Household("hh_" + world.pullNextHouseholdID(), homePoint, world.householdsLayer);
		
		Person driver = PersonTests.createDummyPerson(world, 0, homePoint, homePoint, null , h), 
				p1 = PersonTests.createDummyPerson(world, 1, homePoint, homePoint, null , h),
				p2 = PersonTests.createDummyPerson(world, 2, homePoint, homePoint, null , h);
		
		
		// SUT //////////////////////////////////////////
		
		h.setInHazardZone(true);
		for(Person p: h.getMembers())
			p.beginEvacuating(0);
		
		SchedulingTests.runScheduleUntilAgentFinishes(world, driver, 100);
		
		// TESTING //////////////////////////////////////////

		// are all of the household members, and also the driver's vehicle, present at the shelter location?
		
		
		// was the last known speed of the driver equivalent to the speed of the vehicle?
		/*
		// is it the *correct* path?
		ArrayList <Edge> myPath = p.getPath();
		String [] roadIds = new String [] {"2", "1", "0"}; // road segment IDs for dummy data
		assertEquals(p.getPath().size(), roadIds.length);  // this must match if they match
		
		// confirm segment by segment
		for(int i = 0; i < p.getPath().size(); i++) {
			// pull out the ID of the next segment in the path
			String roadName = ((MasonGeometry)myPath.get(i).info).getStringAttribute("osm_id"); 
			assertEquals(roadName, roadIds[i]); // check that it's correct
		}
	*/	
	}
	
}