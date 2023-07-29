package main.java.myobjects;

import java.util.ArrayList;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.linearref.LengthIndexedLine;

import main.java.mysim.TakamatsuSim;
import sim.engine.SimState;
import sim.field.geo.GeomVectorField;
import sim.field.network.Edge;
import sim.util.Bag;
import sim.util.geo.MasonGeometry;
import swise.agents.TrafficAgent;
import swise.agents.communicator.Communicator;
import swise.agents.communicator.Information;
import swise.behaviours.BehaviourNode;
import swise.objects.RoadNetworkUtilities;
import swise.objects.network.GeoNode;
import swise.objects.network.ListEdge;

public class Person extends TrafficAgent {

//	TakamatsuSim world;
	String myID;
	
	Household myHousehold;
	Coordinate work;
	int age; // years
	int sex; // 0 for female, 1 for male, ++ for other as data becomes available
	int wayfindingMechanism = 0; // 0 for shortest path, 1 for most familiar path, 2 for hazard map paths, 3 for fuzzy wayfinding
	boolean promptNeighbourEvacuations = false;

	// movement utilities
	Vehicle myVehicle = null;
	Coordinate targetDestination = null;
	GeomVectorField space = null;
	double enteredRoadSegment = -1;
	double minSpeed = 7;
	
	ArrayList <MasonGeometry> fullShelters = new ArrayList <MasonGeometry> ();
	String myHistory = "";
	
	public Person dependent = null;
	public Person dependentOf = null;
	
	public boolean prepared = false;
	
	EvacuationPlan myPlan = null;
	//int evacuating = TakamatsuBehaviour.notEvacuating;
	boolean evacuatingCompleted = false;
	double evacuatingTime = -1;
	Shelter targetShelter = null;
	double distanceEstimationWeighting = 2;
	
	public int turnedAwayFromShelterCount = 0;
	
	BehaviourNode currentAction;

	
	public Person(String id, Coordinate position, Coordinate home, Coordinate work, Household household, int age, int sex, TakamatsuSim world){

		// add it to the space
		super((new GeometryFactory()).createPoint(position));
		this.space = world.agentsLayer;
		if(space != null)
			this.space.addGeometry(this);
		else 
			System.out.println("WARNING: Agent being added to NONEXISTENT SPACE");
		this.isMovable = true;

		
		this.promptNeighbourEvacuations = world.evacuationPolicy_neighbours;
		
		this.myID = id;		
		this.age = age;
		this.sex = sex;

		// other utilities
	//	this.world = world;
		
		// set the wayfinding mechanism based on probability
		if(world.random.nextDouble() < .8)
			this.wayfindingMechanism = 1;

		// establish if the person has a vehicle at their disposal
		if(world.random.nextDouble() < .1) {
			this.myVehicle = new Vehicle(id + "_vehicle", home, 4, world);
		}
		
		// set up the speed based on agent age
		if((age > 1 && age < 12 ) || !world.ageSpecificSpeeds) // if age is specific OR ages are turned off!
			this.speed = TakamatsuSim.speed_pedestrian;
		else
			this.speed = TakamatsuSim.speed_elderlyYoung;
		
		this.addIntegerAttribute("speed", (int)this.speed);
		
		// inject the agent into the Edge where it is starting
		if(position != null)
			placeOnEdge(world, position);

		// create a new Household for the person
		if(household == null)
			myHousehold = new Household(home);
		else
			myHousehold = household;

		// set up the workspace if needed
		if(work != null)
			this.work = (Coordinate)work.clone();
		
	}
	
	
	/**
	 * Assuming the Person is not interrupted by intervening events, they are activated
	 * roughly when it is time to begin moving toward the next Task
	 */
	@Override
	public void step(SimState state) {
		
		double time = state.schedule.getTime();		
		
		// check for own home
		if(!isEvacuating() && state.random.nextDouble() < assessRisk((TakamatsuSim) state, this.myHousehold, time)){
						
			beginEvacuating((TakamatsuSim) state);
			
			// if appropriate, encourage neighbours to evacuate
			if(promptNeighbourEvacuations)
				promptNeighbours((TakamatsuSim) state);
			
			return;
		}
		
		double delta = currentAction.next(this, time);
		state.schedule.scheduleOnce(time+delta, this);
	}

	void beginEvacuatingDependent(TakamatsuSim world) {
		if(this.currentAction != world.behaviourFramework.travelToDependentNode) {
			setActivityNode(world.behaviourFramework.travelToDependentNode);
			if(this.evacuatingTime < 0)
				this.evacuatingTime = world.schedule.getTime();
			this.headFor(world, dependent.getHousehold().home);
		}
		else {
			System.out.println("already doing it!");
		}
	}
	
	// EVACUATION BEHAVIOURS
	
	Shelter selectTargetShelter(GeomVectorField shelterLayer) {

		double myDistance = Double.MAX_VALUE;
		MasonGeometry myShelter = null;
		for(Object o: shelterLayer.getGeometries()){
			Shelter s = (Shelter) o;
			if(fullShelters.contains(s)) continue;
			double possibleDist = geometry.getCoordinate().distance(s.getEntrance()) / (Math.log(s.getArea()) * Math.pow(speed, 2));
			if(possibleDist < myDistance){
				myDistance = possibleDist;
				myShelter = s;
			}
		}
		
		// make sure that such a place exists!
		if(myShelter == null){
			
			myHistory += "\tnoAvailableShelters";
			System.out.println("No shelters with available space!");

			return null;
		}

		return (Shelter) myShelter;
	}
	
	void headForShelter(TakamatsuSim world, Shelter myShelter) {
		
		targetDestination = ((Shelter)myShelter).getEntrance();
		targetShelter = (Shelter)myShelter;
		
		headFor(world, targetDestination);
		
		if(path == null){
			System.out.println("can't get there!!!");
		}

	}
	
	void beginEvacuating(TakamatsuSim world) {
		
		if(evacuatingTime < 0)
			evacuatingTime = world.schedule.getTime();

		if(world.tsunami) {
			setActivityNode(world.behaviourFramework.evacuatingNode);
		}
		else {
			setActivityNode(world.behaviourFramework.travelToHomeShelteringNode);
			headFor(world, myHousehold.home);
			
			if(dependentOf != null) // trigger them!
				dependentOf.beginEvacuatingDependent(world);
						
		}

		world.schedule.scheduleOnce(this);
	}
	
	void promptNeighbours(TakamatsuSim world) {
		Bag n = world.agentsLayer.getObjectsWithinDistance(this, world.neighbourDistance);
		int s = n.size();
		if(s <= 1) return; // no one nearby!
		Person p = (Person) n.get(world.random.nextInt(s));
		if(p != this)
			p.beginEvacuating(world);		
	}
	
	public static double rayleighDistrib(double unif){
		double x = TakamatsuSim.rayleigh_sigma * Math.sqrt(-2 * Math.log(unif));
		return x;
	}
	
	public double assessRisk(TakamatsuSim world, Household target, double time){
		
		if(world.tsunami && time > world.forecastArrivalTime)
			return 10; // definitely at risk, if the earthquake has already happened!
		
		// first we calculate whether the Person is exposed to the floodwater at all
		if(! target.inHazardZone) 
			return 0; // it will be times 0, so no reason to continue with expensive checks!
		
/*		// otherwise, find the nearest one
		double minDist = Double.MAX_VALUE;
		Bag nearbyObjects = world.waterLayer.getObjectsWithinDistance(myHousehold, world.hazardThresholdDistance);
		
		// if they are, we determine the nearest point of inundation
		for(Object o: nearbyObjects){
			MasonGeometry mg = (MasonGeometry) o;
			double d = mg.geometry.distance(target.geometry);
			
			if(d < minDist)
				minDist = d;
		}
		
		// we calculate the relative impact of the observed closeness of inundation
		double risk_observed = 1 - (minDist / world.hazardThresholdDistance); 
*/
		double risk_observed = 1;
		
		// next, we determine how much risk the Person perceives
		double journeyTimeEstimate = Double.MAX_VALUE; // we'll use this as a holder variable
		
		// find the nearest shelter to the target to be evacuated
		for(Object o: world.shelterLayer.getGeometries()) {
			MasonGeometry mg = (MasonGeometry) o;
			double d = mg.geometry.distance(target.geometry);
			if(journeyTimeEstimate > d)
				journeyTimeEstimate = d;
		}
		
		// also keep in mind that we need to travel TO the target location before we can leave from there
		journeyTimeEstimate += this.geometry.distance(target.geometry);
		
		// weight the journey time
		journeyTimeEstimate *= distanceEstimationWeighting;
		if(this.myVehicle != null)
			journeyTimeEstimate /= TakamatsuSim.speed_vehicle;
		else
			journeyTimeEstimate /= this.speed;
		
		// calculate the risk based on the forecast
		double risk_forecast = Math.pow(Math.E, -Math.pow(time - journeyTimeEstimate - world.forecastArrivalTime, 2)/
				(2 * world.forecastingWidthParam));
		
		return risk_observed * risk_forecast;
	}
	

	//
	// UTILITIES
	//
	
	// NAVIGATION
	
	/**
	 * Set up a course to take the Agent to the given coordinates
	 * 
	 * @param place - the target destination
	 * @return 1 for success, -1 for a failure to find a path, -2 for failure based on the provided destination or current position
	 */
	public int headFor(TakamatsuSim world, Coordinate place) {

		//TODO: MUST INCORPORATE ROAD NETWORK STUFF
		if(place == null){
			System.out.println("ERROR: can't move toward nonexistant location");
			return -1;
		}
		
		// first, record from where the agent is starting
		startPoint = this.geometry.getCoordinate();
		goalPoint = null;

		if(!(edge.getTo().equals(node) || edge.getFrom().equals(node))){
			System.out.println( (int)world.schedule.getTime() + "\tMOVE_ERROR_mismatch_between_current_edge_and_node");
			return -2;
		}

		// FINDING THE GOAL //////////////////

		// set up goal information
		targetDestination = world.snapPointToRoadNetwork(place);
		
		GeoNode destinationNode = RoadNetworkUtilities.getClosestGeoNode(targetDestination, world.resolution, world.networkLayer, 
				world.networkEdgeLayer, world.fa);//place);
		if(destinationNode == null){
			System.out.println((int)world.schedule.getTime() + "\tMOVE_ERROR_invalid_destination_node");
			return -2;
		}

		// be sure that if the target location is not a node but rather a point along an edge, that
		// point is recorded
		if(destinationNode.geometry.getCoordinate().distance(targetDestination) > world.resolution)
			goalPoint = targetDestination;
		else
			goalPoint = null;


		// FINDING A PATH /////////////////////

		if(this.wayfindingMechanism == 0)
			path = world.pathfinder.astarPath(node, destinationNode, world.roads);
		else if(this.myVehicle == null)
			path = world.pathfinder.astarWeightedPath(node, destinationNode, world.roads, "highway", world.typeWeighting_pedestrian);
		else
			path = world.pathfinder.astarWeightedPath(node, destinationNode, world.roads, "highway", world.typeWeighting_vehicle);


		// if it fails, give up
		if (path == null){
			return -1;
		}

		// CHECK FOR BEGINNING OF PATH ////////

		// we want to be sure that we're situated on the path *right now*, and that if the path
		// doesn't include the link we're on at this moment that we're both
		// 		a) on a link that connects to the startNode
		// 		b) pointed toward that startNode
		// Then, we want to clean up by getting rid of the edge on which we're already located

		// Make sure we're in the right place, and face the right direction
		if (edge.getTo().equals(node))
			direction = 1;
		else if (edge.getFrom().equals(node))
			direction = -1;
		else {
			System.out.println((int)world.schedule.getTime() + "MOVE_ERROR_mismatch_between_current_edge_and_node_2");
			return -2;
		}

		// reset stuff
		if(path.size() == 0 && targetDestination.distance(geometry.getCoordinate()) > world.resolution){
			path.add(edge);
			node = (GeoNode) edge.getOtherNode(node); // because it will look for the other side in the navigation!!! Tricky!!
		}

		// CHECK FOR END OF PATH //////////////

		// we want to be sure that if the goal point exists and the Agent isn't already on the edge 
		// that contains it, the edge that it's on is included in the path
		if (goalPoint != null) {

			ListEdge myLastEdge = RoadNetworkUtilities.getClosestEdge(goalPoint, world.resolution, 
					world.networkEdgeLayer, world.fa);
			
			if(myLastEdge == null){
				System.out.println((int)world.schedule.getTime() + "\tMOVE_ERROR_goal_point_is_too_far_from_any_edge");
				return -2;
			}
			
			// make sure the point is on the last edge
			Edge lastEdge;
			if (path.size() > 0)
				lastEdge = path.get(0);
			else
				lastEdge = edge;

			Point goalPointGeometry = world.fa.createPoint(goalPoint);
			if(!lastEdge.equals(myLastEdge) && ((MasonGeometry)lastEdge.info).geometry.distance(goalPointGeometry) > world.resolution){
				if(lastEdge.getFrom().equals(myLastEdge.getFrom()) || lastEdge.getFrom().equals(myLastEdge.getTo()) 
						|| lastEdge.getTo().equals(myLastEdge.getFrom()) || lastEdge.getTo().equals(myLastEdge.getTo()))
					path.add(0, myLastEdge);
				else{
					System.out.println((int)world.schedule.getTime() + "\tMOVE_ERROR_goal_point_edge_is_not_included_in_the_path");
					return -2;
				}
			}
			
		}

		// set up the coordinates
		this.startIndex = segment.getStartIndex();
		this.endIndex = segment.getEndIndex();

		return 1;
	}

	public double estimateTravelTimeTo(Geometry g){ return(g.distance(this.geometry) / speed); }

	void placeOnEdge(TakamatsuSim world, Coordinate c){
		edge = RoadNetworkUtilities.getClosestEdge(c, world.resolution, world.networkEdgeLayer, world.fa);
		
		if(edge == null){
			System.out.println("\tINIT_ERROR: no nearby edge");
			return;
		}
			
		GeoNode n1 = (GeoNode) edge.getFrom();
		GeoNode n2 = (GeoNode) edge.getTo();
		
		if(n1.geometry.getCoordinate().distance(c) <= n2.geometry.getCoordinate().distance(c))
			node = n1;
		else 
			node = n2;

		segment = new LengthIndexedLine((LineString)((MasonGeometry)edge.info).geometry);
		startIndex = segment.getStartIndex();
		endIndex = segment.getEndIndex();
		currentIndex = segment.indexOf(c);
	}

	public int navigate(TakamatsuSim world, double resolution){
		if(path != null){
			double time = 1;//speed;
			while(path != null && time > 0){
				time = move(time, speed, resolution);
				if(segment != null)
					world.updateRoadUseage(((MasonGeometry)edge.info).getStringAttribute("myid"));
			}
			
			if(segment != null)
				updateLoc(segment.extractPoint(currentIndex));				

			if(time < 0){
				return -1;
			}
			else
				return 1;
		}
		return -1;
	}
	
	//
	// GETTERS AND SETTERS
	//
	
	public boolean evacuatingCompleted(){ return evacuatingCompleted; }
	public String getMyID(){ return this.myID; }	
	public int getAge(){ return this.age; }
	public String getHistory(){ return this.myHistory; }
//	public int getEvacuating(){ return this.evacuating; }
	
	public boolean isEvacuating() { return !this.evacuatingCompleted && this.evacuatingTime > 0;
			/*!
			(this.currentAction == world.behaviourFramework.homeNode || 
			this.currentAction == world.behaviourFramework.travelToHomeNode ||
			this.currentAction == world.behaviourFramework.workNode ||
			this.currentAction == world.behaviourFramework.travelToWorkNode);*/}
	
	public double getEvacuatingTime(){ return evacuatingTime; }	
	public Household getHousehold() { return this.myHousehold;}
	
	public void setActivityNode(BehaviourNode bn){ this.currentAction = bn;}
	public BehaviourNode getActivityNode(){ return this.currentAction;}
	public boolean finishedPath(){ return path == null; }
	public ArrayList <Edge> getPath(){ return path;}
	
	public void setSpeed(double d) { this.speed = d; }
	public double getSpeed() { return this.speed; }
	
	public void setWorkLocation(Coordinate c){ this.work = (Coordinate) c.clone(); }
	
//	public EvacuationPlan getEvacuationPlan(){ return myPlan; }
}
