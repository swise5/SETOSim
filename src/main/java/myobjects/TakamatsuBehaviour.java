package main.java.myobjects;

import main.java.mysim.TakamatsuSim;
import sim.engine.Steppable;
import swise.behaviours.*;

public class TakamatsuBehaviour extends BehaviourFramework {
	
	TakamatsuSim world;
		
	// traditional nodes
	BehaviourNode workNode = null, homeNode = null, travelToWorkNode = null, travelToHomeNode = null;

	// evacuating nodes
	BehaviourNode evacuatingNode = null, preparingToEvacuateNode = null, trappedNode = null, shelteringNode = null, 
			travelToHomeShelteringNode = null, evacuatedNode = null, travelToDependentNode = null, escortDependentNode = null;
	
	public TakamatsuBehaviour(TakamatsuSim ts){
		world = ts;
		
		homeNode = new BehaviourNode(){

			@Override
			public String getTitle() {return "Home";}

			@Override
			public boolean isEndpoint() { return true; } // using this to indicate that they are NOT actively evacuating

			@Override
			public double next(Steppable s, double time) {
				
				Person p = (Person) s;
				if(p.dependentOf != null)
					return Double.MAX_VALUE; // not going anywhere if dependent on others to leave house!
				
				// based on the time, go out to work or to stores
				double currentHour = (time % world.ticks_per_day) / world.ticks_per_hour;
				if(currentHour >= 8. && currentHour <= 16.){
					
					if(p.work != null){
						p.setActivityNode(travelToWorkNode);
						p.headFor(ts, p.work);
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
			public boolean isEndpoint() { return true; }  // using this to indicate that they are NOT actively evacuating

			@Override
			public double next(Steppable s, double time) {
				
				// based on the time, go home
				double currentHour = (time % world.ticks_per_day) / world.ticks_per_hour;
				if(currentHour >= 17.){
					Person p = (Person) s;
					p.setActivityNode(travelToHomeNode);
					p.headFor(ts, p.getHousehold().home);
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
			public boolean isEndpoint() { return true; } // using this to indicate that they are NOT actively evacuating

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
					p.headFor(ts, p.getHousehold().home);
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
			public boolean isEndpoint() { return true; } // using this to indicate that they are NOT actively evacuating

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
					p.headFor(ts, p.getHousehold().home); // try another path
				}
				
				// finally, if the outcome was positive and there is still further to travel, keep going.				
				return 1;
			}
			
		};
		
		preparingToEvacuateNode = new BehaviourNode(){

			@Override
			public String getTitle() { return "Preparing"; }

			@Override
			public boolean isEndpoint() { return false; }

			@Override
			public double next(Steppable s, double time) {

				Person p = (Person) s;

				// if the person has just begun preparing, wait to activate them again!
				if(!p.prepared) {
					p.prepared = true; // now they have taken this step!
					
					// prepare for some amount of time before checking in again
					return p.rayleighDistrib(world.random.nextDouble());
				}

				// otherwise, they are ready to take the next step, which is either
				// leaving or coordinating with their carer.
				
				// If they are independent, they will evacuate by themselves
				if(p.dependentOf == null) {
					p.setActivityNode(evacuatingNode);
					return 1;
				}
				
				// If they rely on a carer, they must wait until that person is nearby and
				// in the TravelToDependent mode.
				
				// first, check whether the carer is already here
				if(p.dependentOf.getActivityNode() == travelToDependentNode &&
						p.geometry.distance(p.dependentOf.geometry) <= world.resolution
						) {
				
					// if so, begin evacuating and set the carer's status to be escorting
					p.dependentOf.setActivityNode(escortDependentNode);
					System.out.println(p.myID + "\tpickedUpBy\t" + p.dependentOf.getMyID());
					world.schedule.scheduleOnce(p.dependentOf);
					
					// if at least one of them has a vehicle, make sure both are travelling at that speed
					if(p.myVehicle != null || p.dependentOf.myVehicle != null) {
						p.setSpeed(TakamatsuSim.speed_vehicle);
						p.dependentOf.setSpeed(TakamatsuSim.speed_vehicle);
					}
					else { // otherwise, make the dependent walk at the same speed as the person they're helping
						p.dependentOf.setSpeed(p.getSpeed());
					}

					// activate on the next turn!
					return 1;
				}
				

				// otherwise, we're just waiting for the carer to arrive. Check back in next step!
				return 1;

			}
			
		};
		
		evacuatingNode = new BehaviourNode(){

			@Override
			public String getTitle() { return "Evacuating"; }

			@Override
			public boolean isEndpoint() { return false; }

			@Override
			public double next(Steppable s, double time) {

				Person p = (Person) s;
				
				// if this is the first tick the person has spend evacuating, set everything up!
				if(p.evacuatingTime < 0) {
					p.evacuatingTime = time;
					Shelter myShelter = p.selectTargetShelter(ts.shelterLayer);
					p.headForShelter(ts, myShelter);
					
					if(p.dependentOf != null)
						p.dependentOf.headForShelter(ts, myShelter);
					return 1;
				}
				
				// otherwise, continue moving toward the evacuation point
				int outcome = p.navigate(world.resolution);

				Shelter shelter = p.targetShelter;

				// if they have reached the Shelter, determine if there is room 
				if(shelter!= null && outcome > 0 && p.finishedPath()){
					
					
					// attempt to enter! If successful, they will stay here
					if(shelter.roomForN(1)){
						shelter.addNewPerson(p);
						p.setActivityNode(evacuatedNode);
						p.evacuatingTime = time - p.evacuatingTime;
						System.out.println(p.getMyID() + "\tsuccessfulEvac");
						
						if(p.dependentOf != null) {
							Person carer = p.dependentOf;
							carer.setActivityNode(travelToHomeNode);
							carer.headFor(ts, carer.getHousehold().home);
							carer.evacuatingTime = time - carer.evacuatingTime;
						}
						
						return Double.MAX_VALUE;
					}
					
					// otherwise, it is full and they must replan
					else {
						p.turnedAwayFromShelterCount += 1;
						p.fullShelters.add(shelter);
						System.out.println(p.getMyID() + "\tshelterFull");						
					}
					
				}
				
				// it may be the case that the shelter or the route are no longer workable! If so, replan!
				if(shelter == null || outcome < 0){
					
					// check for the next best shelter
					Shelter myShelter = p.selectTargetShelter(ts.shelterLayer);
					
					// there may be no path forward - in which case the person is trapped
					if(myShelter == null){ 
						p.setActivityNode(trappedNode);
						p.evacuatingTime = time - p.evacuatingTime;
						System.out.println(p.getMyID() + "\ttrapped");
						return Double.MAX_VALUE;
					}
					
					// if there is another viable shelter, however, move toward that!
					p.headForShelter(ts, myShelter);
					
					// carers will need to reroute as well, in this case
					if(p.dependentOf != null) {
						p.dependentOf.headFor(ts, p.targetDestination);
						p.dependentOf.targetShelter = p.targetShelter;
					}

					return 1;
				}
				
				// finally, if the outcome was positive and there is still further to travel, keep going.				
				return 1;
			}
			
		};
		
		evacuatedNode = new BehaviourNode(){

			@Override
			public String getTitle() { return "Evacuated"; }

			@Override
			public boolean isEndpoint() { return true; }

			@Override
			public double next(Steppable s, double time) {

				Person p = (Person) s;				
				return Double.MAX_VALUE;
			}
			
		};

		travelToHomeShelteringNode = new BehaviourNode() {
			@Override
			public String getTitle() { return "TravelToHomeSheltering"; }

			@Override
			public boolean isEndpoint() { return false; }

			@Override
			public double next(Steppable s, double time) {
				
				// travel toward home
				Person p = (Person) s;
								
				int outcome = p.navigate(world.resolution);
				
				// if the Person has arrived at home, determine if they will need to evacuate the home itself 
				if(outcome > 0 && p.finishedPath()){
					
					// check to see if there is an appropriate shelter still 
					Shelter shelter = p.selectTargetShelter(ts.shelterLayer);

					// it may be that the person should stay home and shelter there - if so, do so!
					if(shelter == null) {
						p.setActivityNode(shelteringNode);
						p.evacuatingTime = time - p.evacuatingTime;
						if(p.dependentOf != null)
							System.out.println("nooo don't come visit");
						return Double.MAX_VALUE; // stay at home indefinitely						
					}
					
					// otherwise, they may now have a target Shelter
					else {
						p.headForShelter(ts, shelter);
						p.setActivityNode(preparingToEvacuateNode);
						return 1; // begin preparing to evacuate
					}
					
				}
				else if(outcome < 0){ // TODO such replanning wow
					p.setActivityNode(trappedNode);
					p.evacuatingTime = time - p.evacuatingTime;
					return Double.MAX_VALUE;
				}
				
				// finally, if the outcome was positive and there is still further to travel, keep going.				
				return 1;
			}
		};
		
		shelteringNode = new BehaviourNode(){

			@Override
			public String getTitle() { return "Sheltering"; }

			@Override
			public boolean isEndpoint() { return true; }

			@Override
			public double next(Steppable s, double time) {

				Person p = (Person) s;				
				return Double.MAX_VALUE;
			}
			
		};
		
		trappedNode = new BehaviourNode(){

			@Override
			public String getTitle() { return "Trapped"; }

			@Override
			public boolean isEndpoint() { return true; }

			@Override
			public double next(Steppable s, double time) {

				Person p = (Person) s;				
				return Double.MAX_VALUE;
			}
			
		};
		
		travelToDependentNode = new BehaviourNode(){

			@Override
			public String getTitle() { return "TravelToDependent";}

			@Override
			public boolean isEndpoint() { return false; }

			@Override
			public double next(Steppable s, double time) {
				
				// travel toward the workplace
				Person p = (Person) s;
				int outcome = p.navigate(world.resolution);
				
				// if the Person has successfully reached their dependent, wait for that person to be ready! 
				if(outcome > 0 && p.finishedPath()){
					
					// if they're ready to go, escort them!
					if(p.dependent.currentAction == preparingToEvacuateNode) {
						return Double.MAX_VALUE; // will be activated later!
					}
					
					// if they're not ready, the dependent may be sheltering....
					if(p.dependent.currentAction == shelteringNode) {
						
						// if they were sheltering, take them home to their carer's home
						p.dependent.setActivityNode(evacuatingNode);
						p.dependent.headFor(ts, p.myHousehold.home);
						world.schedule.scheduleOnce(p.dependent);
						
						// travel home with them to shelter there
						p.setActivityNode(travelToHomeShelteringNode);
						p.headFor(ts, p.myHousehold.home);
						return 1;
					}

					// or the dependent may be trapped! Depending on where they are,
					// either move to them or else help them escape!

					double distanceToDependent = p.geometry.getCoordinate().distance(
							p.dependent.getGeometry().getCoordinate());
					
					// if they're far away, go to them!
					if(p.dependent.currentAction == trappedNode && distanceToDependent > world.resolution) {
						
						// if they are trapped, travel to them (if possible!)
						p.headFor(ts, p.dependent.geometry.getCoordinate());
						return 1;
					}
					
					// if they are trapped and you have reached them, evacuate them!
					else if(p.dependent.currentAction == trappedNode) {
						
						// if they were sheltering, take them home to their carer's home
						p.dependent.setActivityNode(evacuatingNode);
						p.dependent.headFor(ts, p.myHousehold.home);
						world.schedule.scheduleOnce(p.dependent);
						
						// travel home with them to shelter there
						p.setActivityNode(travelToHomeShelteringNode);
						return 1;
					}

					System.out.println(p.myID + "\twaitingFor\t" + p.dependent.getMyID());
					return Double.MAX_VALUE; // otherwise, wait for them to be ready!
				}
				
				// otherwise, the outcome was positive and there is still further to travel. Keep going!
				return 1;
			}
			
		};
		
		escortDependentNode = new BehaviourNode(){

			@Override
			public String getTitle() { return "EscortDependent";}

			@Override
			public boolean isEndpoint() { return false; }

			@Override
			public double next(Steppable s, double time) {
				
				// travel toward the dependent's target
				Person p = (Person) s;
				
				// first, make sure that the dependent is evacuating!
				if(p.dependent.currentAction == preparingToEvacuateNode) {
					p.dependent.setActivityNode(evacuatingNode); // handshake successful!
					return 1;
				}
					
				int outcome = p.navigate(world.resolution);
				
				// if the Person has successfully reached their dependent, wait for that person to be ready! 
				if(outcome > 0 && p.finishedPath()){
					
					// successfully delivered to the shelter! Now go home!
					if(p.dependent.currentAction == evacuatedNode) {
						if(p.evacuatingTime <= time)
							System.out.println("WHAT HOW");
						p.evacuatingTime = time - p.evacuatingTime; // record the end of the escorting! (TODO still need to go home though...)
						p.setActivityNode(travelToHomeNode);
						p.headFor(ts, p.getHousehold().home);
						if(p.myVehicle == null)
							p.setSpeed(TakamatsuSim.speed_pedestrian); // bump them back up in speed once separated
					}
					else if(p.dependent.targetDestination != null){ // oops! No room! Keep going :(
						p.headFor(ts, p.dependent.targetDestination);
					}

					return 1; // depart immediately
				}
				
				else if(p.targetShelter == null || outcome < 0){ // try to replan
					
					if(p.dependent.targetDestination == null) {
						System.out.println(p.myID + "'s dependent has no goal: replanning!");
						return 1;
					}
					p.headFor(ts, p.dependent.targetDestination);
					
					if(p.getPath() == null){ // there may be no path forward - in which case the person is trapped
						p.setActivityNode(trappedNode);
						System.out.println(p.getMyID() + "\ttrapped");
						return Double.MAX_VALUE;
					}
					return 1;
					//p.headFor(p.getHome()); // try another path
				}
				
				// otherwise, the outcome was positive and there is still further to travel. Keep going!
				return 1;
			}
			
		};
	}
	
	
	public BehaviourNode getEntryPoint(){
		return homeNode;
		
	}
}
