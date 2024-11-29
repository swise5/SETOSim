package uk.ac.ucl.SETOSim.myobjects;

import org.junit.Test;


import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;

import sim.field.geo.GeomVectorField;
import sim.util.Bag;
import sim.util.geo.MasonGeometry;
import uk.ac.ucl.SETOSim.mysim.TakamatsuSim;
import uk.ac.ucl.SETOSim.utilities.InputCleaning;

import static org.junit.Assert.*;

import java.util.ArrayList;

public class SpatialTests {

	public static String testingDirectory = "data/testing/";

	public static TakamatsuSim setupTestingWorld(String testingDirectory, long key) {
		TakamatsuSim ts = new TakamatsuSim(key);
		ts.startStubForTesting();
		ts.dirName = testingDirectory;
		return ts;
	}
	
	public static TakamatsuSim setupTestingWorldWithRoads(String testingDirectory, String roadFilename, long key) {

		// initialise holders for the dummy road network
		TakamatsuSim sut = setupTestingWorld(testingDirectory, key);
		sut.roadLayer = new GeomVectorField();
		sut.networkLayer = new GeomVectorField();
		sut.networkEdgeLayer = new GeomVectorField();
		sut.majorRoadNodesLayer = new GeomVectorField();
		InputCleaning.readInVectorLayer(sut.roadLayer, testingDirectory + roadFilename, "road network", new Bag());
		
		// set up the network
		sut.setupRoadNetwork();

		return sut;
	}

	@Test
	public void RoadNetworkReadInCorrectly() {
		
		TakamatsuSim sut = setupTestingWorldWithRoads(testingDirectory, "roads.shp", 1);
		
		System.out.println(sut.roadAdjacencyMatrix);
		
		// should read in 18 bidirectional roads - so there should be 36 geometries
		assert(sut.roadLayer.getGeometries().size() == 36);
		
		// roads default to being open
		for(Object o: sut.roadLayer.getGeometries()) {
			MasonGeometry mg = (MasonGeometry) o;
			assert(mg.getStringAttribute("open").equals("OPEN"));
		}
		
	}
	
	@Test
	public void DummyHelpful() {
		TakamatsuSim sut = setupTestingWorld(testingDirectory, 1);
		ArrayList <Double> vals = new ArrayList <Double> ();
		
		double min = Double.MAX_VALUE, max = Double.MIN_VALUE;
		for(int i = 0; i < 1000; i++) {
			double d = sut.random.nextGaussian();
			vals.add(d);
			if(d < min) min = d;
			if(d > max) max = d;
		}
		
		System.out.println(vals.size());
	}

}