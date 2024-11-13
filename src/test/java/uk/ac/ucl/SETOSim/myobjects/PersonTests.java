package uk.ac.ucl.SETOSim.myobjects;

import org.junit.Test;

import com.vividsolutions.jts.geom.Coordinate;

import uk.ac.ucl.SETOSim.mysim.TakamatsuSim;

public class PersonTests {

	public static Person createDummyPerson(TakamatsuSim world, int id, Coordinate location) {
		Person p = new Person("dummy_" + id, location, null, null, null, 0, 0, world);
		return p;
	}
	
	@Test
	public void PersonFindsShortestRoute() {

	}
}
	