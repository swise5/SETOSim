package uk.ac.ucl.SETOSim.myobjects;

import org.junit.Test;


import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;

import uk.ac.ucl.SETOSim.mysim.TakamatsuSim;
import uk.ac.ucl.SETOSim.myobjects.Person;
import uk.ac.ucl.SETOSim.myobjects.Vehicle;
import sim.field.geo.GeomVectorField;

import static org.junit.Assert.*;

public class VehicleTests {

	public static String testingDirectory = "data/testing/";

	
	public static TakamatsuSim setupTestingWorld(String testingDirectory, long key) {
		TakamatsuSim ts = new TakamatsuSim(key);
		ts.dirName = testingDirectory;
		return ts;
	}
	
	static Person setupTestingPerson(TakamatsuSim world) {
		world.agentsLayer = new GeomVectorField(10, 10);
		//world.agentsLayer.setMBR(new Envelope(0, 1, 0, 1));
		Coordinate pLoc = new Coordinate(0, 0);
		Person p = new Person("testing1", pLoc, pLoc, null, null, 0, 0, world);
		return p;
	}
	
	@Test
	public void OperatorTakesTheWheel() {
		
		//this is too complicated, apparently - I need to start from an even simpler, less entangled point
		
		// assign
			TakamatsuSim world = setupTestingWorld(this.testingDirectory, 0);		
			Person p = setupTestingPerson(world);
			Vehicle sut = new Vehicle(null, null, 0, null);
			
			// act
			sut.setOperator(p);

			//assert
			assertEquals(p, sut.getOperator()); // checking that someone is in there
			assertEquals(1, sut.getPassengers().size()); // no passengers
	}
	
}
