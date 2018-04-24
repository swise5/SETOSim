package objects;

import java.util.ArrayList;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;

import sim.engine.SimState;
import swise.agents.TrafficAgent;
import swise.agents.communicator.Communicator;
import swise.agents.communicator.Information;

public class Person extends TrafficAgent implements Communicator {
	
	public Person(String id, Coordinate position, Coordinate home, Coordinate work, SimState world){		
		this(id, position, home, work, world, .8, .5, .1, .1, 10000, 1000, .5, 2000);
	}
	
	public Person(String id, Coordinate position, Coordinate home, Coordinate work, SimState world, 
			double communication_success_prob, double contact_success_prob, double tweet_prob, 
			double retweet_prob, double comfortDistance, double observationDistance, double decayParam, double speed){

		super((new GeometryFactory()).createPoint(position));
	}
	
	public void addContact(Person contact, int weight){
	}
	
	public void addSocialMediaContact(Communicator contact){}

	@Override
	public ArrayList getInformationSince(double time) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void learnAbout(Object o, Information i) {
		// TODO Auto-generated method stub
		
	}
}