package uk.ac.ucl.SETOSim.movement;


import static org.junit.Assert.*;

import java.util.ArrayList;

import org.junit.Test;

import com.vividsolutions.jts.geom.Coordinate;

import sim.engine.SimState;
import sim.engine.Steppable;
import sim.field.network.Edge;
import sim.util.geo.MasonGeometry;
import uk.ac.ucl.swise.objects.network.GeoNode;
import uk.ac.ucl.SETOSim.myobjects.Person;
import uk.ac.ucl.SETOSim.myobjects.PersonTests;
import uk.ac.ucl.SETOSim.mysim.TakamatsuSim;
import uk.ac.ucl.SETOSim.utilities.AStar;
import uk.ac.ucl.SETOSim.utilities.SchedulingTests;
import uk.ac.ucl.SETOSim.utilities.SpatialTests;

public class WayfindingTests {

	public static String testingDirectory = "data/testing/";
	
	@Test
	/**
	 * Confirm that the Person object finds the shortest route between two locations.
	 */
	public void PersonFindsShortestRoute() {
		// SETUP //////////////////////////////////////////
		
		// create world, set up the pathfinder, and pull out two nodes
		TakamatsuSim world = SpatialTests.setupTestingWorldWithRoads(testingDirectory, "simplisticRoads.shp", 1);
		world.pathfinder = new AStar();
		
		// generate the person at one of the nodes and have them create a reasonable path
		Coordinate startPoint = new Coordinate(0, 0); // southmost point
		Coordinate endPoint = new Coordinate(10, 0);  // northmost point

		Person p = PersonTests.createDummyPerson(world, 0, startPoint);
		
		// SUT //////////////////////////////////////////
		p.headFor(endPoint);
		
		// TESTING //////////////////////////////////////////
		
		// did it actually find a path?
		assertTrue(p.hasPath());
		
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
	}
	
	@Test
	/**
	 * Confirm that a Person who tries to use an inaccessible road segment fails in the attempt.
	 */
	public void BlockedRouteIsInaccessible() {
		// SETUP //////////////////////////////////////////
		
		// create world, set up the pathfinder, and pull out two nodes
		TakamatsuSim world = SpatialTests.setupTestingWorldWithRoads(testingDirectory, "simplisticRoads.shp", 1);
		world.pathfinder = new AStar();
		double testing_roadInundationImpassableDepth = .01; // force road3 to be impassable
		
		// generate the person at one of the nodes and have them create a reasonable path
		Coordinate startPoint = new Coordinate(10, 0);
		Coordinate endPoint_closed = new Coordinate(-5, 10);
		Coordinate endPoint_dynamicallyClosed = new Coordinate(0,0);
		Coordinate lastPoint_accessible = new Coordinate(0, 10);

		// create the person
		Person p = PersonTests.createDummyPerson(world, 0, startPoint);
		PersonTests.setupDummyPersonSchedule(world, p);
		
		// close the roads
		for(Object o: world.roadLayer.getGeometries()) // check all roads for inundation; here, there should only be road 3, 'r3'
			if( ((MasonGeometry) o).getIntegerAttribute("depth") >= testing_roadInundationImpassableDepth)
				((MasonGeometry) o).addAttribute("open", "CLOSED"); // close any relevant roads
				
		// SUT //////////////////////////////////////////
		p.headFor(endPoint_closed);
		boolean hasPathToInaccessibleEndpoint = p.hasPath();
		
		p.headFor(endPoint_dynamicallyClosed);
		boolean hasPathToDynamicallyInaccessibleEndpoint = p.hasPath();
		
		// DYNAMICALLY close Road 0, which runs between 0, 10 and 0, 0
		for(Object o: world.roadLayer.getGeometries())
			if( ((MasonGeometry) o).getStringAttribute("full_id").equals("r0"))
				((MasonGeometry) o).addAttribute("open", "CLOSED"); // close any relevant roads
		
		SchedulingTests.runScheduleUntilAgentFinishes(world, p, 100);
		
		// TESTING //////////////////////////////////////////
		
		// it should fail to find a path to point blocked by road closures
		assertFalse(hasPathToInaccessibleEndpoint);
		
		// it should find a path to the location that was accessible at the time planning happened...
		assertTrue(hasPathToDynamicallyInaccessibleEndpoint);
		
		// ...but then not be able to reach it
		assertFalse(p.geometry.getCoordinate().distance(endPoint_dynamicallyClosed) <= world.params.resolution);
		
		// instead, it should end its journey at the final accessible point - 0, 10
		assertTrue(p.geometry.getCoordinate().distance(lastPoint_accessible) <= world.params.resolution);
		
	}
	

}