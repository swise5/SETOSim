package uk.ac.ucl.SETOSim.myobjects;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.linearref.LengthIndexedLine;

import uk.ac.ucl.SETOSim.mysim.Params;
import uk.ac.ucl.SETOSim.mysim.TakamatsuSim;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.field.geo.GeomVectorField;
import sim.field.network.Edge;
import sim.util.Bag;
import sim.util.geo.MasonGeometry;
import uk.ac.ucl.swise.agents.TrafficAgent;
import uk.ac.ucl.swise.behaviours.BehaviourNode;
import uk.ac.ucl.swise.objects.RoadNetworkUtilities;
import uk.ac.ucl.swise.objects.network.GeoNode;
import uk.ac.ucl.swise.objects.network.ListEdge;

public class Person extends TrafficAgent {

	TakamatsuSim world;
	String myID;
	int hash;
	
	Household myHousehold;
	Coordinate work;
	int age; // years
	int sex; // 0 for female, 1 for male, ++ for other as data becomes available
	WayfindingType wayfindingMechanism = WayfindingType.perfectKnowledge; // 0 for shortest path, 1 for most familiar path, 2 for hazard map paths, 3 for fuzzy wayfinding
	boolean promptNeighbourEvacuations = false;
	
	enum WayfindingType{ perfectKnowledge, wandering, searching};
	enum MovementOutcome { successfulMovement, blockedMovement, routingFailure, unrecoverableRoutingFailure};

	// movement utilities
	Vehicle myVehicle = null; // vehicle to which the Person, theoretically, has access
	Vehicle operatorOf = null; // vehicle the Person is operating
	Vehicle passengerOf = null;// vehicle in which the Person is currently travelling
	
	Coordinate targetDestination = null;
	GeomVectorField space = null;
	double enteredRoadSegment = -1;
	double minSpeed = 7;
	
	HashSet <MasonGeometry> fullShelters = new HashSet <MasonGeometry> ();
	String myHistory = "";
	
	public Person dependent = null;
	public Person dependentOf = null;
	
	public boolean prepared = false;
	public boolean inundated = false;
	
	EvacuationPlan myPlan = null;
	String evacuationRecord = null;
	//int evacuating = TakamatsuBehaviour.notEvacuating;
//	boolean evacuatingCompleted = false;
//	double evacuatingTime = -1;
	Shelter targetShelter = null;
	double distanceEstimationWeighting = 2;
	
	public int turnedAwayFromShelterCount = 0;
	
	BehaviourNode currentAction;

	SpatialMentalModel mySpatialMentalModel;
	
	boolean isLeader = false;
	Person myLeader = null;
	
	public Person(String id, Coordinate position, Coordinate home, Coordinate work, Household household, int age, int sex, TakamatsuSim world){

		// add it to the space
		super((new GeometryFactory()).createPoint(new Coordinate(position.x, position.y)));
		this.space = world.agentsLayer;
		
		// make sure the space exists
		if(this.space == null)
			System.out.println("WARNING: trying to add Person to NONEXISTENT SPACE");
		else if(!position.equals(world.notInSimulation))
			this.space.addGeometry(this);
		// otherwise, we simply don't add it yet			

		this.isMovable = true;

		
		this.promptNeighbourEvacuations = world.params.evacuationPolicy_neighbours;
		
		this.myID = id;	
		this.hash = id.hashCode();
		this.age = age;
		this.sex = sex;

		// other utilities
		this.world = world;
		
		if(!world.params.leaderSet) {
			world.params.setLeader();
			isLeader = true;
		}
		
		if(this.wayfindingMechanism != WayfindingType.perfectKnowledge)
			mySpatialMentalModel = new SpatialMentalModel(); // otherwise, leave it as null!
	

		// set the wayfinding mechanism based on probability
		//if(world.random.nextDouble() < .8)
		//	this.wayfindingMechanism = 1;

		
		// set up the speed based on agent age
		if(!world.params.ageSpecificSpeeds || (age > 1 && age < 12 )) // if age is specific OR ages are turned off!
			this.speed = Params.speed_pedestrian;
		else
			this.speed = Params.speed_elderlyYoung;
		
		this.addIntegerAttribute("speed", (int)this.speed);
		
		// inject the agent into the Edge where it is starting
//		if(position != null)
//			placeOnEdge(position);
//		mySpatialMentalModel.addEdge(edge); // should add both?
		
		// establish if the person has a vehicle at their disposal
//		if(world.random.nextDouble() < world.params.likelihoodOfOwningVehicle) {
//			this.myVehicle = new Vehicle(id + "_vehicle", home, 4, world);
//		}

		// create a new Household for the person
		if(home == null && household == null) { // if they don't have a home location, assume initiated at home
			myHousehold = new Household("HH_" + world.pullNextHouseholdID(), new Coordinate(position.x, position.y), world.householdsLayer);
			System.out.println("commuter?");
		}
		else if(household == null) {
			myHousehold = new Household("HH_" + world.pullNextHouseholdID(), new Coordinate(home.x, home.y), world.householdsLayer);
		}
		else
			myHousehold = household;
		
		myHousehold.addMember(this);

		if(world.params.universalVehicles && myHousehold.members.size() == 0)
			this.myVehicle = new Vehicle(id + "_vehicle", home, 4, world); // each household has a car

		
		// set up the workspace if needed
		if(work != null)
			this.work = new Coordinate(work.x, work.y);//(Coordinate)work.clone();
		
	}
	
	
	/**
	 * Assuming the Person is not interrupted by intervening events, they are activated
	 * roughly when it is time to begin moving toward the next Task
	 */
	@Override
	public void step(SimState state) {
		
		double time = state.schedule.getTime();		
		double delta = Double.MAX_VALUE;
		
		if(this.isPassengerOfVehicle())
			return; // don't do anything while a passenger
		else if(this.evacuatingCompleted())
			return;
		
		
		// check for own home
		if(this.evacuationRecord == null && shouldIEvacuate()) {//state.random.nextDouble() < assessRisk(this.myHousehold, time)){			
			beginEvacuating(time);
			return;
		}
	//	else if(isEvacuating()) {
			delta = currentAction.next(this, time);
	//	}
		
//
/*		System.out.println(time + "\t" + this.myID + ":\t" + this.geometry.getCoordinate() + "\t" + this.node.toString() + this.edge.toString());
		if(this.path != null) {
			String output = "\t";
			for(Edge e: path)
				output += e.toString() + " - ";
			System.out.println(output);
		}
*/
//		TakamatsuSim world = (TakamatsuSim)state;
//		double delta = 1;
		//if(isLeader) {
//			wander(world);
/*			if(targetShelter != null)
				delta = search(world, targetShelter.entrance);
			else
				targetShelter = selectTargetShelter(world.shelterLayer);
			
/*			Bag neighbours = world.agentsLayer.getObjectsWithinDistance(this, 30);
			for(Object o: neighbours) {
				if(o == this) continue; // don't follow ourselves
				Person p = (Person) o;
				if(p.myLeader != null) continue; // if they're already following someone, ignore them
				p.myLeader = this;
			}
			*/
	//	}
	/*	else if (myLeader != null){
			follow(world, myLeader);
		}
		
	*/	
		state.schedule.scheduleOnce((int)(time+delta), this);
	}
	
	boolean shouldIEvacuate() {
		if(world.params.tsunami) // tsunami is the big boss
			return true;
		if(myHousehold != null && myHousehold.inHazardZone) // my house is flooded
			return true;
		else if(this.inundated) // I'm in the middle of a flood
			return true;
		return false;
	}

	void wander() {
		int outcome = navigate(world.params.resolution);
		
		// if they don't have an activity or destination, just wander around 
		if((outcome > 0 && finishedPath()) || outcome == -1){
			
			// pick a random edge leading from our current node
			Bag edgesOut = world.roads.getEdges(node, null);
			int index = world.random.nextInt(edgesOut.numObjs);
			Edge myNewEdge = (Edge) edgesOut.get(index);
			
			// pick the node at the other end of the edge, whichever that is
			GeoNode nextNode = (GeoNode)myNewEdge.getTo();
			if(nextNode == node) nextNode = (GeoNode)myNewEdge.getFrom();
			
			// head for that node
			headFor(nextNode.geometry.getCoordinate());
		}
	}
	
	void follow(Person myLeader) {
		if(myLeader.edge == this.edge) { // we're on the same road - just follow them
			this.direction = myLeader.direction; // (do make sure we're heading in the same direction, though)
			this.speed = myLeader.speed; // don't want to overtake them, as following them
		}
		else if(myLeader.node == this.node) {
			headFor(this.node.geometry.getCoordinate());
		}
		else {
			headFor(myLeader.geometry.getCoordinate());
		}
		
		navigate(world.params.resolution);
	}
	
	SortedMap <Double, GeoNode> myUnexploredNodesByProx = null;
	
	int search(Coordinate target) {
		
		// if we're searching and we have a path already and we can NAVIGATE it, we can stop processing
		// and save ourselves some time
		double outcome = navigate(world.params.resolution);
		if(outcome > -1)
			return 1;
		
		// we may have arrived - in which case, hooray! We can stop searching!
		else if(target.distance(this.geometry.getCoordinate()) <= world.params.resolution) {
			System.out.println("We've arrived!!!");
			((ListEdge)edge).removeElement(this); // take ourselves off the road
			return Integer.MAX_VALUE;
		}
		
		// otherwise, we need to either EXPLORE or PLAN!

		// PLANNING setup:
		// we may need to set up the distance measure for searching; store this to
		// prevent having to recalculate every time
		if(myUnexploredNodesByProx == null) {
			myUnexploredNodesByProx  = new TreeMap <Double, GeoNode> ();
			
			// add all known nodes by distance
			for(Object gn_obj: this.mySpatialMentalModel.getAllNodes()) {
				GeoNode gn = (GeoNode) gn_obj;
				myUnexploredNodesByProx.put(target.distance(gn.geometry.getCoordinate()), gn);
			}
			
		}

		// we must have arrived at a new, unexplored node: let's EXPLORE it!

		mySpatialMentalModel.exploreNode(node); // record that we've done this

		// add all the edges here
		Object [] edgesHere = world.roadAdjacencyMatrix[node.getIntegerAttribute("indexInNetwork")];
		HashSet foundNodes = new HashSet();
		
		// check for any unexplored nodes
		for(Object o: edgesHere) {
			
			// learn about this new edge
			Edge e = (Edge) o;
			mySpatialMentalModel.addEdge(e);

			// figure out the node at the other end of this connection
			Object otherNode = e.getFrom();
			if(otherNode == node) otherNode = e.getTo();
			foundNodes.add(otherNode);			
		}

		// if we've found any new nodes to explore, make sure to add them to our weighting
		for(Object o: foundNodes) {
			
			// if we've already explored this, make sure not to add it back in
			if(this.mySpatialMentalModel.hasExploredNode((GeoNode) o)) 
				continue;
			
			GeoNode gn = (GeoNode) o;
			myUnexploredNodesByProx.put(gn.geometry.getCoordinate().distance(target), gn);
		}
		
		// now, find the next nearest node and travel there
		Entry<Double, GeoNode> closestNodeEntry = myUnexploredNodesByProx.pollFirstEntry();
		GeoNode closestKnownNode = closestNodeEntry.getValue();
		headFor(closestKnownNode.geometry.getCoordinate());
		return 1;
	}

	void beginEvacuatingDependent() {
		if(this.currentAction != world.behaviourFramework.travelToDependentNode) {
			setActivityNode(world.behaviourFramework.travelToDependentNode);
	// TODO fix		if(this.evacuatingTime < 0)
	//			this.evacuatingTime = world.schedule.getTime();
			this.headFor(dependent.getHousehold().home);
			world.numAssisting++;
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
			double possibleDist = geometry.getCoordinate().distance(s.getEntrance()) / (Math.pow(speed, 2)); // Math.log(s.getArea()) * 
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
	
/*	int headForShelter(Shelter myShelter) {
		
		targetDestination = ((Shelter)myShelter).getEntrance();
		targetShelter = (Shelter)myShelter;
		
		path = null;
		
		headFor(targetDestination);
		
		if(path == null){
			System.out.println("can't get there!!!");
			fullShelters.add(myShelter);
			return -1;
		}
		return 1;
	}*/
	
	public boolean beginEvacuating(double time) {
		
/*		if(evacuatingTime < 0) {
			evacuatingTime = world.schedule.getTime();
			world.numAttemptingEvac++;
		}
*/
		if( evacuationRecord != null && evacuationRecord.contains("BEGIN_EVACUATING"))
			return false;
		
		updateEvacRecord("BEGIN_EVACUATING:"+time);

		if(world.params.tsunami) {
			setActivityNode(world.behaviourFramework.evacuatingNode);
		}
		
//		else if(!this.myHousehold.inHazardZone){

//			if(evacuatingTime < 0)
//				evacuatingTime = world.schedule.getTime(); // record when we started, at least
			
			setActivityNode(world.behaviourFramework.travelToHomeShelteringNode);
			headFor(myHousehold.home);
			
			if(dependentOf != null) // trigger them!
				dependentOf.beginEvacuatingDependent();
						
//		}
//		else {
//			setActivityNode(world.behaviourFramework.evacuatingNode);
//		}

		// if appropriate, encourage neighbours to evacuate
		if(promptNeighbourEvacuations) {
			promptNeighbours();
			world.numAssisting++;
		}
		
		// reset the path
		path = null;
		
		world.schedule.scheduleOnce(this);
		
		return true;
	}
	
	void promptNeighbours() {
		Bag n = world.agentsLayer.getObjectsWithinDistance(this, world.params.neighbourDistance);
		int s = n.size();
		if(s <= 1) return; // no one nearby!
		Person p = (Person) n.get(world.random.nextInt(s));
		if(p != this)
			p.beginEvacuating(world.schedule.getTime());
	}
	
	public static double rayleighDistrib(double unif){
		double x = Params.rayleigh_sigma * Math.sqrt(-2 * Math.log(unif));
		return x;
	}
	
	public double assessRisk(Household target, double time){
		
		String recordStub = "ASSESS_RISK:" + time;
		if(this.evacuationRecord == null)
			evacuationRecord = recordStub;
		else
			evacuationRecord += "," + recordStub;
		
		
		if(world.params.tsunami && time > world.params.forecastArrivalTime)
			return 10; // definitely at risk, if the earthquake has already happened!
		else if(this.inundated)
			return 10;
		
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
		for(Object o: world.shelterLayer.getGeometries()) { // TODO make this a voronoi partition look-up
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
			journeyTimeEstimate /= Params.speed_vehicle;
		else
			journeyTimeEstimate /= this.speed;
		
		// calculate the risk based on the forecast
		double risk_forecast = Math.pow(Math.E, -Math.pow(time - journeyTimeEstimate - world.params.forecastArrivalTime, 2)/
				(2 * world.params.forecastingWidthParam));
		
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
	public MovementOutcome headFor(Coordinate place) {

		// some basic error checking
		
		//
		// can't go somewhere undefined
		//
		if(place == null){
			System.out.println("ERROR: can't move toward nonexistant location");
			return MovementOutcome.unrecoverableRoutingFailure;
		}

		//
		// if we're trying to go somewhere outside the station, reroute to the station and go through it
		//
		else if(place.equals(world.notInSimulation)) {
			
			// find the nearest point of exit
			GeoNode targetStation = world.getNearestOpenStation(geometry);
			
			// there might not be any available stations - in that case, stop trying for this destination
			if(targetStation == null)
				return MovementOutcome.unrecoverableRoutingFailure;

			if(targetStation.geometry.getCoordinate().distance(world.notInSimulation) < world.params.resolution)
				System.out.println("what");
			
			// otherwise, try to route through the target station!
			MovementOutcome routableThroughStation = headFor(targetStation.geometry.getCoordinate());
			
			// clean up: the true target is outside the simulated map, and it *might* be routable!
			targetDestination = place;
			return routableThroughStation;
			
		}
		
		// a few more checks to make life easier
		double distanceFromCurrentPosition = place.distance(this.geometry.getCoordinate());

		//
		// if we're already in the place...great! No need to route our way there!
		//
		if(distanceFromCurrentPosition < world.params.resolution) {
			path = new ArrayList <Edge> ();
			return MovementOutcome.successfulMovement;
		}
		
		// first, record from where the agent is starting
		startPoint = this.geometry.getCoordinate();
		goalPoint = null;

		if(edge == null) {
			int placed = placeOnEdge(startPoint);
			if(edge == null || placed < 0) {
				if(world.verbose)
					System.out.println( (int)world.schedule.getTime() + "\tMOVE_ERROR_can't_place_on_an_edge");				
				return MovementOutcome.unrecoverableRoutingFailure; 
			}
		}
		
		if(!(edge.getTo().equals(node) || edge.getFrom().equals(node))){
			if(world.verbose)
				System.out.println( (int)world.schedule.getTime() + "\tMOVE_ERROR_mismatch_between_current_edge_and_node");
			return MovementOutcome.unrecoverableRoutingFailure;
		}

		// FINDING THE GOAL //////////////////

		// set up goal information
		targetDestination = world.snapPointToRoadNetwork(place);
		
		GeoNode destinationNode = RoadNetworkUtilities.getClosestGeoNode(targetDestination, world.params.resolution, world.networkLayer, 
				world.networkEdgeLayer, world.fa);//place);
		if(destinationNode == null){
			if(world.verbose)
				System.out.println((int)world.schedule.getTime() + "\tMOVE_ERROR_invalid_destination_node");
			return MovementOutcome.blockedMovement;
		}

		// be sure that if the target location is not a node but rather a point along an edge, that
		// point is recorded
		if(destinationNode.geometry.getCoordinate().distance(targetDestination) > world.params.resolution)
			goalPoint = targetDestination;
		else
			goalPoint = null;


		// FINDING A PATH /////////////////////

		if(this.wayfindingMechanism == WayfindingType.perfectKnowledge)
			path = world.pathfinder.astarPath(node, destinationNode, world.roadAdjacencyMatrix, null); //transportNetwork);
		else if(this.wayfindingMechanism == WayfindingType.searching)
			path = world.pathfinder.astarPath(node, destinationNode, world.roadAdjacencyMatrix, this.mySpatialMentalModel.getAllEdges()); //transportNetwork);
		else if(this.myVehicle == null)
			path = world.pathfinder.astarWeightedPath(node, destinationNode, world.roadAdjacencyMatrix, world.params.weightedRoadAttribute, world.params.typeWeighting_pedestrian);
		else
			path = world.pathfinder.astarWeightedPath(node, destinationNode, world.roadAdjacencyMatrix, world.params.weightedRoadAttribute, world.params.typeWeighting_vehicle);


		// if it fails, give up
		if (path == null){
			if(this.wayfindingMechanism == WayfindingType.perfectKnowledge)
				return MovementOutcome.unrecoverableRoutingFailure; 
			else
				return MovementOutcome.routingFailure;
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
			if(world.verbose)
				System.out.println((int)world.schedule.getTime() + "MOVE_ERROR_mismatch_between_current_edge_and_node_2");
			return MovementOutcome.routingFailure;
		}

		// reset stuff
		if(path.size() == 0 && targetDestination.distance(geometry.getCoordinate()) > world.params.resolution){
			path.add(edge);
			node = (GeoNode) edge.getOtherNode(node); // because it will look for the other side in the navigation!!! Tricky!!
		}

		// CHECK FOR END OF PATH //////////////

		// we want to be sure that if the goal point exists and the Agent isn't already on the edge 
		// that contains it, the edge that it's on is included in the path
		if (goalPoint != null) {

			ListEdge myLastEdge = RoadNetworkUtilities.getClosestEdge(goalPoint, world.params.resolution, 
					world.networkEdgeLayer, world.fa);
			
			if(myLastEdge == null){
				if(world.verbose)
					System.out.println((int)world.schedule.getTime() + "\tMOVE_ERROR_goal_point_is_too_far_from_any_edge");
				return MovementOutcome.routingFailure;
			}
			
			// make sure the point is on the last edge
			Edge lastEdge;
			if (path.size() > 0)
				lastEdge = path.get(0);
			else
				lastEdge = edge;

			Point goalPointGeometry = world.fa.createPoint(goalPoint);
			if(!lastEdge.equals(myLastEdge) && ((MasonGeometry)lastEdge.info).geometry.distance(goalPointGeometry) > world.params.resolution){
				if(lastEdge.getFrom().equals(myLastEdge.getFrom()) || lastEdge.getFrom().equals(myLastEdge.getTo()) 
						|| lastEdge.getTo().equals(myLastEdge.getFrom()) || lastEdge.getTo().equals(myLastEdge.getTo()))
					path.add(0, myLastEdge);
				else{
					if(world.verbose)
						System.out.println((int)world.schedule.getTime() + "\tMOVE_ERROR_goal_point_edge_is_not_included_in_the_path");
					return MovementOutcome.routingFailure;
				}
			}
			
		}
		
		// if the very first edge is closed, we absolutely cannot proceed
		Edge myFinalEdge = path.get(path.size() - 1);
		if( ((MasonGeometry)myFinalEdge.info).getStringAttribute("open").equals("CLOSED"))
			return MovementOutcome.unrecoverableRoutingFailure;


		// set up the coordinates
		this.startIndex = segment.getStartIndex();
		this.endIndex = segment.getEndIndex();

		return MovementOutcome.successfulMovement;
	}

	public double estimateTravelTimeTo(Geometry g){ return(g.distance(this.geometry) / speed); }

	int placeOnEdge(Coordinate c){
		edge = RoadNetworkUtilities.getClosestEdge(c, world.params.resolution, world.networkEdgeLayer, world.fa);
		
		if(edge == null){
			if(world.verbose)
				System.out.println("\tINIT_ERROR: no nearby edge");
			return -1;
		}
		else if(((MasonGeometry)edge.info).getStringAttribute("open").equals("CLOSED")){
			if(world.verbose)
				System.out.println("\tINIT_ERROR: edge is closed");
			updateEvacRecord("ROAD_CLOSURE_AT_TIME" + (int) world.schedule.getTime());
			edge = null; // reset
			return -2;
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
		
		if(mySpatialMentalModel != null)
			mySpatialMentalModel.addEdge(edge);
		return 1;
	}

	public void removeFromEdge() {
		if(edge == null) return;
		
		if(edge != null && edge.getClass().equals(ListEdge.class))
			((ListEdge)edge).removeElement(this);

	}
	
	public int navigate(double resolution){
		if(path != null){
			
			if(!this.isOperatingVehicle() && this.myVehicle != null && this.myVehicle.geometry.distance(this.geometry) < world.params.resolution)
				myVehicle.setOperator(this);
			
			double time = 1;
			while(path != null && time > 0){
				
/*				// if the Person is operating a vehicle, it must be turned on for them to move it
				if(operatorOf != null && !operatorOf.inMotion())
					operatorOf.startMovement();
*/				
				time = move(time, speed, resolution);
				if(segment != null)
					world.updateRoadUseage(((MasonGeometry)edge.info).getStringAttribute("full_id"));
				//mySpatialMentalModel.addEdge(edge);
				
				// they've arrived at a train station, which is their destination
				if(time >= 0 && finishedPath() && node != null && node.hasAttribute("station") && targetDestination.distance(world.notInSimulation) < resolution) {
					
					// if we're in a vehicle, stop it!!
					//if(operatorOf != null && operatorOf.inMotion()) stopVehicle();
						
					updateLoc(world.notInSimulation);
					return 2; // they've successfully left the area of the simulation
				}
			}
			
			if(segment != null) {
				Coordinate newLoc = segment.extractPoint(currentIndex); 
				updateLoc(newLoc);
				if(operatorOf != null)
					operatorOf.moveTo(newLoc);
			}

			if(time < 0){
				return -1;
			}
			else
				return 1;
		}
		return -1;
	}
	
	public void stopVehicle() {
		
			operatorOf.stopMovement();
			operatorOf.removeAllPassengers();
			operatorOf.removeOperator();
			operatorOf = null;
		

	}
	
	public void scheduleArrival(GeoNode entryPoint, double time) {
		
		Person holder = this; // hideous hack
		world.schedule.scheduleOnce(time, new Steppable() {

			@Override
			public void step(SimState arg0) {
				Coordinate entryCoordinate = entryPoint.geometry.getCoordinate();
				holder.geometry = (new GeometryFactory()).createPoint(new Coordinate(entryCoordinate.x, entryCoordinate.y));
				space.addGeometry(holder);
				world.schedule.scheduleOnce(time + 1, holder);
			}
			
		});
	}
	
	//
	// GETTERS AND SETTERS
	//
	
	public boolean evacuatingCompleted() {return this.evacuationRecord != null && evacuationRecord.endsWith("DONE");}
	public String getMyID(){ return this.myID; }	
	public int getAge(){ return this.age; }
	public String getHistory(){ return this.myHistory; }
	
	//public boolean isEvacuating() { return !this.evacuatingCompleted && this.evacuatingTime > 0;
			/*!
			(this.currentAction == world.behaviourFramework.homeNode || 
			this.currentAction == world.behaviourFramework.travelToHomeNode ||
			this.currentAction == world.behaviourFramework.workNode ||
			this.currentAction == world.behaviourFramework.travelToWorkNode);*/
	
//	public double getEvacuatingTime(){ return evacuatingTime; }	
	public Household getHousehold() { return this.myHousehold;}
	
	public void setActivityNode(BehaviourNode bn){ this.currentAction = bn;}
	public BehaviourNode getActivityNode(){ return this.currentAction;}
	public boolean finishedPath(){
		if(path == null) return true;
		else if(path.size() == 0 && this.geometry.getCoordinate().distance(this.targetDestination) < world.params.resolution)
			return true;
		return false; 
	}
	public ArrayList <Edge> getPath(){ return path;}
	
	public void setSpeed(double d) { this.speed = d; }
	public double getSpeed() { return this.speed; }
	
	public void setWorkLocation(Coordinate c){ this.work = (Coordinate) c.clone(); }
	public void setInundated(boolean isInundated, double time) { 
		this.inundated = isInundated;
		
		if(this.evacuationRecord == null)
			this.evacuationRecord = "";
		else
			this.evacuationRecord += ",";
		this.evacuationRecord += "SET_TO_INUNDATED:" + (int) time;
		
	
	}
//	public EvacuationPlan getEvacuationPlan(){ return myPlan; }
	
	public boolean hasPath() { return path != null; }
	public GeoNode getNode() { return node; }
	public String getEvacuationRecord() { return this.evacuationRecord; }

	public boolean isOperatingVehicle() { return this.operatorOf != null; }
	public boolean isPassengerOfVehicle() { return this.passengerOf != null; }
	public boolean isInVehicle() { return isOperatingVehicle() || isPassengerOfVehicle(); }
	
	public void stopOperatingVehicle() {
		
		// make sure the Person is actually operating a vehicle
		if(this.operatorOf == null) {
			System.out.println("ERROR: Person " + this.myID + " is not operating a vehicle, and so cannot stop operating a vehicle");
			return;
		}
		
		// set all of the passengers to reset themselves!
		for(Person p: this.operatorOf.getPassengers())
			world.schedule.scheduleOnce(p);
			
		// throw everyone out!
		this.operatorOf.removeAllPassengers();

		// stop operating it!
		this.operatorOf.removeOperator();
		
	}
	
	public void forceUpdateLoc() {
		if(operatorOf != null)
			updateLoc(operatorOf.geometry.getCoordinate());
		else if(passengerOf != null)
			updateLoc(passengerOf.geometry.getCoordinate());
	}

	public void updateEvacRecord(String s) {
		if(this.evacuationRecord == null)
			this.evacuationRecord = "";
		else
			this.evacuationRecord += ",";
		this.evacuationRecord += s;
	}
	
	public void updateEvacRecord(String flag, double time) {
		updateEvacRecord(flag + ":" + (int) time);
	}
	
	public int hashCode(){ return hash; }
}
