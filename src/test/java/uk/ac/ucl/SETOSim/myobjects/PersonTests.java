package uk.ac.ucl.SETOSim.myobjects;

import org.junit.Test;

import com.vividsolutions.jts.geom.Coordinate;

import uk.ac.ucl.SETOSim.mysim.TakamatsuSim;

public class PersonTests {

	public static Person createDummyPerson(TakamatsuSim world, int id, Coordinate startLocation) {
		return createDummyPerson(world, id, startLocation, null, null);
	}
	
	public static Person createDummyPerson(TakamatsuSim world, int id, Coordinate startLocation, Coordinate homeLocation) {
		return createDummyPerson(world, id, startLocation, homeLocation, null);
	}

	public static Person createDummyPerson(TakamatsuSim world, int id, Coordinate startLocation, Coordinate homeLocation, Coordinate workLocation) {
		Person p = new Person("dummy_" + id, startLocation, homeLocation, workLocation, null, 0, 0, world);
		return p;
	}
	
	@Test
	public void PersonFindsShortestRoute() {

	}
}
	