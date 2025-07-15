package uk.ac.ucl.SETOSim.utilities;

import org.junit.Test;

import sim.field.geo.GeomVectorField;
import sim.util.Bag;
import sim.util.geo.MasonGeometry;
import uk.ac.ucl.SETOSim.myobjects.Person;
import uk.ac.ucl.SETOSim.mysim.TakamatsuSim;
import uk.ac.ucl.swise.agents.TrafficAgent;

public class SchedulingTests {

	public static String testingDirectory = "data/testing/";

	/************************ UTILITIES FOR TESTING ****************************************/

	public static void runScheduleUntilTime(TakamatsuSim world, double time) {
		while(world.schedule.getTime() < time) {	
		
			world.schedule.step(world);
		}

	}
	
	public static void runScheduleUntilAgentFinishes(TakamatsuSim world, Person agent, double time) {
		while(world.schedule.getTime() < time && ! agent.finishedPath()) {	
		
			world.schedule.step(world);
		}
	}
	

}