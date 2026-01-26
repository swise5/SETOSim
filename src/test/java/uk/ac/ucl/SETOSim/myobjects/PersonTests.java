package uk.ac.ucl.SETOSim.myobjects;

import com.vividsolutions.jts.geom.Coordinate;

import sim.engine.Steppable;
import uk.ac.ucl.SETOSim.mysim.TakamatsuSim;
import uk.ac.ucl.swise.behaviours.BehaviourNode;

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
		return createDummyPerson(world, id, startLocation, homeLocation, workLocation, null);
	}
	
	
	public static Person createDummyPerson(TakamatsuSim world, int id, Coordinate startLocation, Coordinate homeLocation, Coordinate workLocation, Household h) {
		Person p = new Person("dummy_" + id, startLocation, homeLocation, workLocation, h, 0, 0, world);
		return p;
			
	}

	
	public static Person createDummyPerson(TakamatsuSim world, int id, Coordinate startLocation, int age) {
		Person p = new Person("dummy_" + id, startLocation, null, null, null, age, 0, world);
		return p;
	}
	
	
	
	public static void setupDummyPersonSchedule(TakamatsuSim world, Person p) {
	
		BehaviourNode dummyBehaviour = new BehaviourNode() {

			@Override
			public String getTitle() { return "dummy"; }

			@Override
			public double next(Steppable s, double time) {
				if(! p.finishedPath())
					p.navigate(world.params.resolution);
				return 1; }

			@Override
			public boolean isEndpoint() { return false;}
			
		};
		
		world.schedule.scheduleOnce(p);
		p.currentAction = dummyBehaviour;

	}
	/************************ END UTILITIES FOR TESTING *************************************/


}
