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
	 * Confirm that a Person who tries to use an inaccessible road segment fails in the attempt. Likewise,
	 * ensure that the mere existence of a blocked path doesn't keep the Person from reaching a different,
	 * accessible location.
	 */
	public void BlockedRouteIsInaccessible() {
		// SETUP //////////////////////////////////////////
		
		// create world, set up the pathfinder, and pull out two nodes
		TakamatsuSim world = SpatialTests.setupTestingWorldWithRoads(testingDirectory, "simplisticRoads.shp", 1);
		world.pathfinder = new AStar();
		
		// generate the person at one of the nodes and have them create a reasonable path
		Coordinate startPoint = new Coordinate(0, 0);
		Coordinate endPoint_inaccessible = new Coordinate(-5, 10);
		Coordinate endPoint_accessible = new Coordinate(10, 0);

		// create the person
		Person p = PersonTests.createDummyPerson(world, 0, startPoint);
		
		// close the roads
		for(Object o: world.roadClosures.get(1)) // take all roads where the depth is set to 1 (only road 3, 'r3')
			((MasonGeometry) o).addAttribute("open", "CLOSED"); // close any relevant roads
		
		// SUT //////////////////////////////////////////
		p.headFor(endPoint_inaccessible);
		boolean hasPathToInaccessibleEndpoint = p.hasPath();
		
		p.headFor(endPoint_accessible);
		
		// TESTING //////////////////////////////////////////
		
		// did it find a path to the inaccessible point?
		assertFalse(hasPathToInaccessibleEndpoint);
		
		// did it find a path to the accessible point?
		assertTrue(p.hasPath());
	}
	

}