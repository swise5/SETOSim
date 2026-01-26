package uk.ac.ucl.SETOSim.utilities;

import org.junit.Test;

import sim.field.geo.GeomVectorField;
import sim.util.Bag;
import sim.util.geo.MasonGeometry;
import uk.ac.ucl.SETOSim.mysim.TakamatsuSim;

public class SpatialTests {

	public static String testingDirectory = "data/testing/";

	/************************ UTILITIES FOR TESTING ****************************************/

	public static TakamatsuSim setupTestingWorld(String testingDirectory, long key) {
		TakamatsuSim simStub = new TakamatsuSim(key);
		simStub.startStubForTesting();
		simStub.params.dirName = testingDirectory;
		simStub.agentsLayer = new GeomVectorField(10, 10);
		return simStub;
	}

	public static TakamatsuSim setupTestingWorldWithRoads(String testingDirectory, String roadFilename, long key) {

		// initialise holders for the dummy road network
		TakamatsuSim sut = setupTestingWorld(testingDirectory, key);
		sut.roadLayer = new GeomVectorField();
		sut.networkLayer = new GeomVectorField();
		sut.networkEdgeLayer = new GeomVectorField();
		sut.majorRoadNodesLayer = new GeomVectorField();
		sut.roadLayer = InputCleaning.readInVectorLayer(testingDirectory + roadFilename, sut.params.grid_width, sut.params.grid_height, "road network", new Bag());

		// set up the network
		sut.setupRoadNetwork();

		return sut;
	}

	/************************ END UTILITIES FOR TESTING *************************************/

	/****************************************************************************************/
	/********************************* TESTING **********************************************/
	/****************************************************************************************/

	@Test
	/**
	 * Read in an extract of a real example of road network spatial data
	 */
	public void RoadNetworkReadInCorrectly() {

		TakamatsuSim sut = setupTestingWorldWithRoads(testingDirectory, "roads.shp", 1);
		TakamatsuSim sut2 = setupTestingWorldWithRoads(testingDirectory, "simplisticRoads.shp", 1);

		//System.out.println(sut.roadAdjacencyMatrix);
		

		// should read in 18 bidirectional roads - so there should be 36 geometries
		assert(sut.roadLayer.getGeometries().size() == 36);
		
		// in the dummy version there are 4 roads connecting 5 nodes - so there should be 8 geometries
		assert(sut2.roadLayer.getGeometries().size() == 8);
		assert(sut2.roadNodes.size() == 5);

		// roads default to being open
		for(Object o: sut.roadLayer.getGeometries()) {
			MasonGeometry mg = (MasonGeometry) o;
			assert(mg.getStringAttribute("open").equals("OPEN"));
		}
		
		for(Object o: sut2.roadLayer.getGeometries()) {
			MasonGeometry mg = (MasonGeometry) o;
			assert(mg.getStringAttribute("open").equals("OPEN"));
		}

	}
	
}