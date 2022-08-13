package swise;

import org.junit.Test;


import com.vividsolutions.jts.geom.Coordinate;

import takamatsu.myobjects.Person;
import takamatsu.myobjects.Vehicle;
import takamatsu.mysim.TakamatsuSim;
import sim.field.geo.GeomVectorField;

import static org.junit.Assert.*;

public class VehicleTests {

	TakamatsuSim setupTestingWorld() {
		TakamatsuSim ts = new TakamatsuSim(0);
		ts.agentsLayer = new GeomVectorField(10, 10);
		return ts;
	}
	
	@Test
	public void OperatorTakesTheWheel() {
		// assign
		TakamatsuSim world = setupTestingWorld();		
		Person p = new Person("testing1", new Coordinate(1,1), new Coordinate(1,1), null, null, 0, 0, world);
		Vehicle sut = new Vehicle(null, null, 0, null);
		
		// act
		sut.setOperator(p);
		
		//assert
		assertEquals(p, sut.getOperator()); // checking that someone is in there
		assertEquals(0, sut.getPassengers().size()); // no passengers
	}
	
}
