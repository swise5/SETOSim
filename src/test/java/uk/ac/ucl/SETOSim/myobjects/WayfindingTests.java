package uk.ac.ucl.SETOSim.myobjects;


import static org.junit.Assert.*;

import java.util.ArrayList;

import org.junit.Test;

import com.vividsolutions.jts.geom.Coordinate;

import sim.field.network.Edge;
import sim.util.geo.MasonGeometry;
import swise.objects.network.GeoNode;
import uk.ac.ucl.SETOSim.mysim.TakamatsuSim;
import uk.ac.ucl.SETOSim.utilities.AStar;

public class WayfindingTests {

	public static String testingDirectory = "data/testing/";
	
	@Test
	public void PersonFindsShortestRoute() {
		// SETUP //////////////////////////////////////////
		
		// create world, set up the pathfinder, and pull out two nodes
		TakamatsuSim world = SpatialTests.setupTestingWorldWithRoads(testingDirectory, "simplisticRoads.shp", 1);
		world.pathfinder = new AStar();
		
		// generate the person at one of the nodes and have them create a reasonable path
		Coordinate startPoint = new Coordinate(0, 0);//693959.0,3873915.0); // southmost point
		Coordinate endPoint = new Coordinate(10, 0);//694332.0,3874762.0); // northmost point

		Person p = PersonTests.createDummyPerson(world, 0, startPoint);
		
		// SUT //////////////////////////////////////////
		p.headFor(endPoint);
		
		// TESTING //////////////////////////////////////////
		
		// did it actually find a path?
		assertTrue(p.hasPath());
		
		// is it the *correct* path?
		ArrayList <Edge> myPath = p.getPath();
		String [] roadIds = new String [] {"2", "1", "0"};//"128714635", "461450875", "128710327", "128712625", "128709855", "128712803", "128720064"};
		assertEquals(p.getPath().size(), roadIds.length);
		
		for(int i = 0; i < p.getPath().size(); i++) { // confirm segment by segment
			String roadName = ((MasonGeometry)myPath.get(i).info).getStringAttribute("osm_id"); 
			assertEquals(roadName, roadIds[i]);
		}
	}
	
	@Test
	public void BlockedRouteIsInaccessible() {
		// SETUP //////////////////////////////////////////
		
		// create world, set up the pathfinder, and pull out two nodes
		TakamatsuSim world = SpatialTests.setupTestingWorldWithRoads(testingDirectory, "simplisticRoads.shp", 1);
		world.pathfinder = new AStar();
		
		// generate the person at one of the nodes and have them create a reasonable path
		Coordinate startPoint = new Coordinate(0, 0);
		Coordinate endPoint = new Coordinate(-5, 10);

		// create the person
		Person p = PersonTests.createDummyPerson(world, 0, startPoint);
		
		// close the roads
		for(Object o: world.roadClosures.get(1))
			((MasonGeometry) o).addAttribute("open", "CLOSED"); // close any relevant roads
		
		// SUT //////////////////////////////////////////
		p.headFor(endPoint);
		
		// TESTING //////////////////////////////////////////
		
		// did it find a path?
		assertTrue(p.hasPath());
		
		// is it the *correct* path?
		ArrayList <Edge> myPath = p.getPath();
		String [] roadIds = new String [] {"3"};
		assertEquals(p.getPath().size(), roadIds.length);
		for(int i = 0; i < p.getPath().size(); i++) { // confirm segment by segment
			String roadName = ((MasonGeometry)myPath.get(i).info).getStringAttribute("osm_id"); 
			assertEquals(roadName, roadIds[i]);
		}
		
		// but it breaks when we try to move, right?
		assertEquals(p.navigate(world.resolution), -1);
	}
	
	@Test
	public void PeopleEnterAndExitTheMapAtStations() {
		// SETUP //////////////////////////////////////////
		
		// create world, set up the pathfinder, and pull out two nodes
		TakamatsuSim world = SpatialTests.setupTestingWorldWithRoads(testingDirectory, "simplisticRoads.shp", 1);
		world.pathfinder = new AStar();
		
		// read in the stations file
		world.stationFilename = "simplisticTrainStations.shp";
		world.setupStations();

		// set up the Person and set them on course to go to the station
		Coordinate startPoint = new Coordinate(0, 0);
		Person p = PersonTests.createDummyPerson(world, 0, startPoint);
		GeoNode station = world.stations.get(0);
		p.headFor(station.geometry.getCoordinate());
		
		// SUT ///////////////////////////////////////////
		int stillMoving = 1;
		while(stillMoving > 0)
			stillMoving = p.navigate(world.resolution);

		// TESTING ///////////////////////////////////////////
		assertEquals(p.geometry.getCoordinate(), world.notInSimulation); // they have left the simulation area
		assertEquals(p.getNode(), station); // their exit point was the station
	}
	
}