package uk.ac.ucl.SETOSim.myobjects;

import uk.ac.ucl.SETOSim.myobjects.Person.MovementOutcome;
import uk.ac.ucl.SETOSim.mysim.Params;
import uk.ac.ucl.SETOSim.mysim.TakamatsuSim;

import com.vividsolutions.jts.geom.Coordinate;

import sim.engine.Steppable;
import sim.util.geo.MasonGeometry;
import uk.ac.ucl.swise.behaviours.*;
import uk.ac.ucl.swise.objects.network.GeoNode;

public class TakamatsuBehaviour implements BehaviourFramework {
	
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
				double currentHour = (time % Params.ticks_per_day) / Params.ticks_per_hour;
				if(currentHour >= 8. && currentHour <= 16.){
					
					if(p.work != null){
						
						// try to go to work
						MovementOutcome outcome = p.headFor(p.work);
						if(outcome != MovementOutcome.successfulMovement) {
							p.setActivityNode(shelteringNode);
							p.evacuationRecord += ",SHELTER_AT_HOME:" + (int)time;
							return 1;							
						}

						p.setActivityNode(travelToWorkNode);
						if(p.myVehicle != null)
							p.myVehicle.setOperator(p);
						
						return 1;
					}
					else {
						p.removeFromEdge();
						return Params.ticks_per_day;
					}
				}
				
				// if it is not yet time for work, wait until it is to activate again!
				double delta = 8. - currentHour;
				if(delta < 0) 	// it might be the end of the day - so wait until tomorrow.
					delta += 24; 
				return delta * Params.ticks_per_hour; // don't active again until 8am!
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
				double currentHour = (time % Params.ticks_per_day) / Params.ticks_per_hour;
				if(currentHour >= 17.){
					Person p = (Person) s;
					p.setActivityNode(travelToHomeNode);
					
					MovementOutcome outcome = p.headFor(p.getHousehold().home);
					
					// if can't enter it, shelter in place here
					if(outcome != MovementOutcome.successfulMovement) {
						p.setActivityNode(shelteringNode);
						String report = "SHELTER_AT_WORK:" + time;
						if(p.evacuationRecord== null)
							p.evacuationRecord = report;
						else
							p.evacuationRecord += "," + report;
						//p.evacuatingTime = -2; // set distinctly to indicate that they were not able to evacuate at all
						return 1;
					}
					else if(p.myVehicle != null)
						p.myVehicle.setOperator(p);
					
					return 1;
				}
				
				// if it is not yet time to go home, wait until it is to activate again!
				return (17. - currentHour) * Params.ticks_per_hour; // don't active again until 5pm!
			}
			
		};
		
		travelToWorkNode = new BehaviourNode(){

			@Override
			public String getTitle() { return "TravelToWork";}

			@Override
			public boolean isEndpoint() { return true; } // using this to indicate that they are NOT actively evacuating

			@Override
			public double next(Steppable s, double time) {
				
				// We're travelling! Attempt to travel toward the workplace
				Person p = (Person) s;
				
				try {
					int outcome = p.navigate(Params.resolution);
					
					// if the Person has successfully made it to work, begin working for 8 hours. 
					if(outcome > 0 && p.finishedPath()){
						p.setActivityNode(workNode);
						p.removeFromEdge();
						
						if(p.isOperatingVehicle())
							p.stopOperatingVehicle();
						
						return 8 * Params.ticks_per_hour; // work 8 hours
					}
					
					// if the Person has *not* been successful in going to work, try to find another path.
					if(outcome < 0) {
						
						Coordinate workLoc = p.work;
						if(workLoc == null) {
							GeoNode station = world.getNearestOpenStation(p.geometry);
							workLoc = station.geometry.getCoordinate();
						}
						MovementOutcome headToWork = p.headFor(workLoc);

						// if no path can be found and they have nothing further to explore, just go home
						if(headToWork == MovementOutcome.unrecoverableRoutingFailure) {
							p.setActivityNode(travelToHomeNode);
							p.headFor(p.getHousehold().home);
						}
					}
					
				} catch(Exception e) {
					p.setActivityNode(travelToHomeNode);
					p.headFor(p.getHousehold().home);					
				}
				
				
				// if we get here, the Person still has further to travel. Keep going!
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
				int outcome = p.navigate(Params.resolution);
				
				// if the Person has successfully made it home, stay there for a while. 
				if(outcome > 0 && p.finishedPath()){
					
					if(p.isOperatingVehicle())
						p.stopOperatingVehicle();

					p.setActivityNode(homeNode);
					p.removeFromEdge();

					return 10 * Params.ticks_per_hour; // stay at home for 10 hours
				}
				
				// if the Person has *not* been successful in travelling home, try to find another path.
				else if(outcome < 0){
					MovementOutcome headToHome = p.headFor(p.getHousehold().home);
					
					// if no path can be found and they have nothing further to explore, we can't get
					// home - and we need to start evacuating
					if(headToHome == MovementOutcome.unrecoverableRoutingFailure) { // we can't get there
						p.setActivityNode(evacuatingNode);
					}
				}
				
				// if we get here, the Person still has further to travel. Keep going!
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
						p.geometry.distance(p.dependentOf.geometry) <= Params.resolution
						) {
				
					// if so, begin evacuating and set the carer's status to be escorting
					p.dependentOf.setActivityNode(escortDependentNode);
					if(p.world.verbose)
						System.out.println(p.myID + "\tpickedUpBy\t" + p.dependentOf.getMyID());
					world.schedule.scheduleOnce(p.dependentOf);
					
					// if at least one of them has a vehicle, make sure both are travelling at that speed
					if(p.myVehicle != null || p.dependentOf.myVehicle != null) {
						p.setSpeed(Params.speed_vehicle);
						p.dependentOf.setSpeed(Params.speed_vehicle);
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
				
				// =======
				// if this is the first tick the Person is evacuating, set everything up!
				// =======
				
				if(p.evacuationRecord == null) {
					
					// store the current simulation time in the attribute
					//p.evacuatingTime = time;
					p.evacuationRecord = "START_EVACUATING:" + time;
					
					// pick a shelter and try to find a route there
					Shelter myShelter = p.selectTargetShelter(ts.shelterLayer);
					
					try {
						MovementOutcome headForShelter = p.headFor(myShelter.entrance);	
						// it may be that the Person couldn't identify a path to that particular
						// shelter - in which case they should check other shelters!
						if(headForShelter == MovementOutcome.unrecoverableRoutingFailure)
							p.fullShelters.add(myShelter);

						// if they *did* find an accessible Shelter, keep a copy of it as the goal
						else {
							p.targetShelter = myShelter;
							
							// if someone is responsible for me, they should also come to the shelter
							if(p.dependentOf != null) {
								p.dependentOf.headFor(myShelter.entrance);
								p.dependentOf.targetShelter = myShelter;
							}
							
						}
					} catch (Exception e) {
						// no shelters exist in the simulation - so try to shelter at home!
						p.setActivityNode(travelToHomeShelteringNode);
						return 1;
					}
					
					return 1; // check in again next step
				}
				
				// =======
				// otherwise, thing have already been set up; continue moving toward the evacuation point
				// =======

				int outcome = p.navigate(Params.resolution); // try to move along the path

				Shelter shelter = p.targetShelter;

				MovementOutcome headForNewTargetShelter = MovementOutcome.successfulMovement; // hopefully?
				
				// the Person might have reached the Shelter - if so, check if there is room! 
				if(shelter != null && outcome > 0 && p.finishedPath()){
					
					
					// attempt to enter! If successful, they'll stay here
					if(shelter.roomForN(1)){
						shelter.addNewPerson(p);
						p.setActivityNode(evacuatedNode);
						p.evacuationRecord += ",ENTER_SHELTER:" + (int)time;
						//p.evacuatingTime = time - p.evacuatingTime;

						// TODO if dependents are happening, update their possible passenger-ness here!!
						
						// special record-keeping if the Person is being escorted as a dependent
						if(p.dependentOf != null) {
							Person carer = p.dependentOf;
							carer.setActivityNode(travelToHomeNode);
							carer.headFor(carer.getHousehold().home);
							// TODO deal with this shit carer.evacuatingTime = time - carer.evacuatingTime;
							world.numAssisting--;
						}

						// this means we've successfully evacuated
						return 1;
					}
					
					// otherwise, it is full and we must replan
					else {
						p.turnedAwayFromShelterCount += 1;
						p.fullShelters.add(shelter);
						if(p.world.verbose)
							System.out.println(p.getMyID() + "\tshelterFull");
						p.targetShelter = null;
					}
					
				}
				
				else if(shelter != null && outcome < 0) { // we tried to move but couldn't

					// We can try to replan...
					headForNewTargetShelter = p.headFor(shelter.entrance);
					
					// ...but if that doesn't work either, 
					
				}
				
				// it may be the case that the shelter or the route are no longer workable! If so, replan!
				if(shelter == null || headForNewTargetShelter == MovementOutcome.unrecoverableRoutingFailure){
					
					// check for the next best shelter
					Shelter myShelter = p.selectTargetShelter(ts.shelterLayer);
					
					if(myShelter == shelter) { // found the same shelter, but it's inaccessible
						p.fullShelters.add(myShelter);
						return 1;
					} 
					
					// there may be no suitable shelter, or else no path to any shelter - can they go home??
					else if(myShelter == null || p.headFor(myShelter.entrance) == MovementOutcome.unrecoverableRoutingFailure){
						
						// reset the Person to try to go home
						p.targetShelter = null;
						
						// they might be stuck in their house, in which case they'll shelter in place
						if(p.geometry.getCoordinate().distance(p.getHousehold().home) < Params.resolution) {
							p.setActivityNode(shelteringNode);
							p.evacuationRecord += ",ABORT_EVAC_TO_SHELTER_AT_HOME:" + (int)time;
/*							if(p.evacuatingTime < 0)
								p.evacuatingTime = -2;
							else
								p.evacuatingTime = time - p.evacuatingTime;
*/							return 1;
						}
						
						// otherwise, the Person might be able to find a path home. Try it!
						else if(p.headFor(p.getHousehold().home) == MovementOutcome.unrecoverableRoutingFailure) 
							p.setActivityNode(trappedNode); // no path exists! They're trapped!
						
						// if the last test fails, then they've found a path! Take it!!!
						else // there may be a path! Try to go home!
							p.setActivityNode(travelToHomeShelteringNode);
					}
					else
						p.targetShelter = myShelter;
					
					// if the route was unworkable, any carers will need to reroute as well
					if(p.dependentOf != null) {
						p.dependentOf.headFor(p.targetDestination);
						p.dependentOf.targetShelter = p.targetShelter;
					}

					return 1;
				}
				
				// finally, it may be that the outcome was positive and there is still further to travel; keep going!
				
				// update again during the next tick
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
				p.removeFromEdge();
				p.evacuationRecord += ",FINISH_EVAC:" + (int)time;
				
//				if(p.evacuatingTime > 0)
//					p.evacuatingTime = time - p.evacuatingTime;

				
				if(p.world.verbose)
					System.out.println(p.getMyID() + "\tsuccessfulEvac");
				world.numAttemptingEvac--;

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
				
				// try to move along the path toward home
				Person p = (Person) s;
				int outcome = p.navigate(Params.resolution);
				
				// if the Person has successfully arrived at their home, they face a decision: 
				// shelter in place OR evacuate to somewhere safer 
				if(outcome > 0 && p.finishedPath()){
					
					if(p.isOperatingVehicle())
						p.stopOperatingVehicle();
					
					// check to see if there is an appropriate shelter still 
					Shelter shelter = p.selectTargetShelter(ts.shelterLayer);
					p.targetShelter = shelter;

					// TODO more nuance
					
					// it may be that the person should stay home and shelter there - if so, do so!
					if(p.targetShelter == null || p.headFor(p.targetShelter.entrance) == MovementOutcome.unrecoverableRoutingFailure) {
						p.setActivityNode(shelteringNode);
						p.evacuationRecord += ",ARRIVED_TO_SHELTER_AT_HOME:" + (int)time;
//						p.evacuatingTime = time - p.evacuatingTime; // finished the journey
						return 1;
					}
					
					// otherwise, they may now have a target Shelter
					else {// otherwise, start preparing to evacuate
						p.setActivityNode(preparingToEvacuateNode);
						p.evacuationRecord += ",ARRIVED_HOME_PREP_STARTED:" + (int)time;
					}
						
					
				}
				
				// on the other hand, they may not have been successful when they tried to move.
				// They might be trapped: let's check and try to fix it!
				else if(outcome < 0){
					
					// it might be the case that the Person can find another route home
					MovementOutcome headToHome = p.headFor(p.getHousehold().getHome());
					
					if(headToHome == MovementOutcome.unrecoverableRoutingFailure) { // ...or not...
						p.setActivityNode(trappedNode); // ...in which case they're trapped
						p.evacuationRecord += ",TRAPPED_TRYING_TO_REACH_HOME:" + (int)time;
					}
				}
				
				// otherwise, the Person may still be en route to their home - keep travelling!
				
				// regardless, they should check in again during the next step!				
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
				/*
				if(p.getHousehold().inHazardZone()) {
					p.evacuationRecord += ",HOME_UNSAFE_PROMPTED_EVACUATING:" + time;
					p.setActivityNode(evacuatingNode);
					return 1;
				}
				else { */
					p.removeFromEdge();
					p.evacuationRecord += ",SHELTERING:" + time + ",DONE";
//					if(p.evacuatingTime > 0)
//						p.evacuatingTime = time - p.evacuatingTime;
					
					if(p.dependentOf != null)
						System.out.println("nooo don't come visit");
					return Double.MAX_VALUE;
			//	}


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
				p.evacuationRecord += ",TRAPPED:" + time + ",DONE";
				if(p.world.verbose)
					System.out.println("TRAPPED :(");
				// TODO REMOVE
				//p.tempUpdateLoc(world.notInSimulation);
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
				int outcome = p.navigate(Params.resolution);
				
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
						p.dependent.headFor(p.myHousehold.home);
						world.schedule.scheduleOnce(p.dependent);
						
						// travel home with them to shelter there
						p.setActivityNode(travelToHomeShelteringNode);
						p.headFor(p.myHousehold.home);
						return 1;
					}

					// or the dependent may be trapped! Depending on where they are,
					// either move to them or else help them escape!

					double distanceToDependent = p.geometry.getCoordinate().distance(
							p.dependent.getGeometry().getCoordinate());
					
					// if they're far away, go to them!
					if(p.dependent.currentAction == trappedNode && distanceToDependent > Params.resolution) {
						
						// if they are trapped, travel to them (if possible!)
						p.headFor(p.dependent.geometry.getCoordinate());
						return 1;
					}
					
					// if they are trapped and you have reached them, evacuate them!
					else if(p.dependent.currentAction == trappedNode) {
						
						// if they were sheltering, take them home to their carer's home
						p.dependent.setActivityNode(evacuatingNode);
						p.dependent.headFor(p.myHousehold.home);
						world.schedule.scheduleOnce(p.dependent);
						
						// travel home with them to shelter there
						p.setActivityNode(travelToHomeShelteringNode);
						return 1;
					}

					if(p.world.verbose)
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
					
				int outcome = p.navigate(Params.resolution);
				
				// if the Person has successfully reached their dependent, wait for that person to be ready! 
				if(outcome > 0 && p.finishedPath()){
					
					// successfully delivered to the shelter! Now go home!
					if(p.dependent.currentAction == evacuatedNode) {
				/*		if(p.evacuatingTime <= time) TODO fix this nonsense
							System.out.println("WHAT HOW");
						p.evacuatingTime = time - p.evacuatingTime; // record the end of the escorting! (TODO still need to go home though...)
				*/		p.setActivityNode(travelToHomeNode);
						p.headFor(p.getHousehold().home);
						if(p.myVehicle == null)
							p.setSpeed(Params.speed_pedestrian); // bump them back up in speed once separated
					}
					else if(p.dependent.targetDestination != null){ // oops! No room! Keep going :(
						p.headFor(p.dependent.targetDestination);
					}

					return 1; // depart immediately
				}
				
				else if(p.targetShelter == null || outcome < 0){ // try to replan
					
					if(p.dependent.targetDestination == null) {
						if(p.world.verbose)
							System.out.println(p.myID + "'s dependent has no goal: replanning!");
						return 1;
					}
					p.headFor(p.dependent.targetDestination);
					
					if(p.getPath() == null){ // there may be no path forward - in which case the person is trapped
						p.setActivityNode(trappedNode);
						if(p.world.verbose)
							System.out.println(p.getMyID() + "\ttrapped");
						return 1;//Double.MAX_VALUE;
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
