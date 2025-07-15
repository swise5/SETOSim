package uk.ac.ucl.SETOSim.myobjects;

import com.vividsolutions.jts.geom.Coordinate;

import uk.ac.ucl.SETOSim.mysim.TakamatsuSim;

public class PersonTests {

	/************************ UTILITIES FOR TESTING ****************************************/

	public static Person createDummyPerson(TakamatsuSim world, int id, double x, double y) {
		return createDummyPerson(world, id, new Coordinate(x, y), null, null);
	}

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
	
	
	public static Person createDummyPerson(TakamatsuSim world, int id, Coordinate startLocation, Coordinate homeLocation, Coordinate workLocation, Household h) {
		Person p = new Person("dummy_" + id, startLocation, homeLocation, workLocation, h, 0, 0, world);
		return p;
			
	}

	/************************ END UTILITIES FOR TESTING *************************************/


}
