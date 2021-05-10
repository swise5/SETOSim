package myobjects;

import mysim.TakamatsuSim;
import sim.engine.Steppable;
import swise.behaviour.*;

public class TakamatsuBehaviour extends BehaviourFramework {
	
	TakamatsuSim world;
	
	// traditional nodes
	BehaviourNode workNode = null, homeNode = null, travelToWorkNode = null, travelToHomeNode = null;

	// evacuating nodes
	BehaviourNode evacuatingNode = null, preparingToEvacuateNode = null, trappedNode = null;
	
	public TakamatsuBehaviour(TakamatsuSim ts){
		world = ts;
		
		homeNode = new BehaviourNode(){

			@Override
			public String getTitle() {return "Home";}

			@Override
			public double next(Steppable s, double time) {
				
				// based on the time, go out to work or to stores
				double currentHour = (time % world.ticks_per_day) / world.ticks_per_hour;
				if(currentHour >= 8. && currentHour <= 16.){
					Person p = (Person) s;
					
					if(p.work != null){
						p.setActivityNode(travelToWorkNode);
						p.headFor(p.work);
						return 1;
					}
					else {
						return world.ticks_per_day;
					}
				}
				
				// if it is not yet time for work, wait until it is to activate again!
				double delta = 8. - currentHour;
				if(delta < 0) 	// it might be the end of the day - so wait until tomorrow.
					delta += 24; 
				return delta * world.ticks_per_hour; // don't active again until 8am!
			}
		};
		
		workNode = new BehaviourNode(){

			@Override
			public String getTitle() { return "Work";}

			@Override
			public double next(Steppable s, double time) {
				
				// based on the time, go home
				double currentHour = (time % world.ticks_per_day) / world.ticks_per_hour;
				if(currentHour >= 17.){
					Person p = (Person) s;
					p.setActivityNode(travelToHomeNode);
					p.headFor(p.getHome());
					return 1;
				}
				
				// if it is not yet time to go home, wait until it is to activate again!
				return (17. - currentHour) * world.ticks_per_hour; // don't active again until 5pm!
			}
			
		};
		
		travelToWorkNode = new BehaviourNode(){

			@Override
			public String getTitle() { return "TravelToWork";}

			@Override
			public double next(Steppable s, double time) {
				
				// travel toward the workplace
				Person p = (Person) s;
				int outcome = p.navigate(world.resolution);
				
				// if the Person has successfully made it to work, begin working for 8 hours. 
				if(outcome > 0 && p.finishedPath()){
					p.setActivityNode(workNode);
					return 8 * world.ticks_per_hour; // work 8 hours
				}
				// if the Person has not been successful in going to work, go home instead.
				else if(outcome < 0){
					p.setActivityNode(travelToHomeNode);
					p.headFor(p.getHome());
					return 1;
				}
				// finally, if the outcome was positive and there is still further to travel, keep going.
				return 1;
			}
			
		};
		
		travelToHomeNode = new BehaviourNode(){

			@Override
			public String getTitle() { return "TravelToHome"; }

			@Override
			public double next(Steppable s, double time) {
				
				// travel toward home
				Person p = (Person) s;
				int outcome = p.navigate(world.resolution);
				
				// if the Person has successfully made it to work, begin working for 8 hours. 
				if(outcome > 0 && p.finishedPath()){
					p.setActivityNode(homeNode);
					return 10 * world.ticks_per_hour; // stay at home for 10 hours
				}
				else if(outcome < 0){ // TODO such evacuation wow
					p.headFor(p.getHome()); // try another path
				}
				
				// finally, if the outcome was positive and there is still further to travel, keep going.				
				return 1;
			}
			
		};
	}
	
	@Override
	public BehaviourNode getEntryPoint(){
		return homeNode;
		
	}
}