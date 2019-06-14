package myobjects;

import java.util.ArrayList;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.linearref.LengthIndexedLine;

import mysim.TakamatsuSim;
import sim.engine.SimState;
import sim.field.geo.GeomVectorField;
import sim.field.network.Edge;
import sim.util.Bag;
import sim.util.geo.MasonGeometry;
import swise.agents.TrafficAgent;
import swise.agents.communicator.Communicator;
import swise.agents.communicator.Information;
import swise.objects.network.GeoNode;
import swise.objects.network.ListEdge;
import utilities.RoadNetworkUtilities;

public class Person extends TrafficAgent implements Communicator {
	
	Household myHousehold;
	Coordinate work;
	int age; // years
	int sex; // 0 for female, 1 for male, ++ for other
	TakamatsuSim world;
	String myID;

	// movement utilities
	Coordinate targetDestination = null;
	GeomVectorField space = null;
	double enteredRoadSegment = -1;
	double minSpeed = 7;
	double riskToleranceParam = 100; // meters
	
	ArrayList <MasonGeometry> fullShelters = new ArrayList <MasonGeometry> ();
	
	boolean evacuating = false;
	boolean evacuatingCompleted = false;
	double evacuatingTime = -1;
	Shelter targetShelter = null;

	
	public Person(String id, Coordinate position, Coordinate home, Coordinate work, Household household, TakamatsuSim world){

		// add it to the space
		super((new GeometryFactory()).createPoint(position));
		this.space = world.agentsLayer;
		this.space.addGeometry(this);
		this.isMovable = true;

		this.myID = id;
		

		// other utilities
		this.world = world;
		
		// set up the speed based on whether the individual has a vehicle or is walking
		if(world.random.nextDouble() < .1) 
			this.speed = TakamatsuSim.speed_vehicle;
		else if(age > 1 && age < 12)
			this.speed = TakamatsuSim.speed_pedestrian;
		else
			this.speed = TakamatsuSim.speed_elderlyYoung;
		
		this.addIntegerAttribute("speed", (int)this.speed);
		
		// inject the agent into the Edge where it is starting
		if(position != null)
			placeOnEdge(position);

		// create a new Household for the person
		if(household == null)
			myHousehold = new Household(home);
		else
			myHousehold = household;

		// set up the workspace if needed
		if(work != null)
			this.work = (Coordinate)work.clone();
		
	}
	
	void placeOnEdge(Coordinate c){
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

	public static double rayleighDistrib(double unif){
		double x = TakamatsuSim.rayleigh_sigma * Math.sqrt(-2 * Math.log(unif));
		return x;
	}
	
	public double assessRisk(){
		
		double minDist = Double.MAX_VALUE;
		for(Object o: world.waterLayer.getGeometries()){
			MasonGeometry mg = (MasonGeometry) o;
			double d = mg.geometry.getCoordinate().distance(this.myHousehold.home);
			if(d < minDist)
				minDist = d;
		}
		
		return minDist / riskToleranceParam;
	}
	
	/**
	 * Assuming the Person is not interrupted by intervening events, they are activated
	 * roughly when it is time to begin moving toward the next Task
	 */
	@Override
	public void step(SimState state) {
		
		// find the current time
		double time = state.schedule.getTime();
		world.incrementHeatmap(this.geometry);

		// either update the evacuation time or possibly begin evacuating
		if(!evacuating && state.random.nextDouble() > assessRisk()){
			beginEvacuating(time);
			state.schedule.scheduleOnce(time + (int)rayleighDistrib(world.random.nextDouble()), this);
			return;
		}
		
		// if the agent is already in transit, continue moving
		if(targetDestination != null){
			if(path == null){
				headFor(targetDestination);
			}
			this.navigate(world.resolution);
		}
		else {
			
			if((int)time % 1440 >= 900)
				targetDestination = myHousehold.home;
			else{				
				Bag ns = world.roadNodes;
				targetDestination = ((GeoNode)ns.get(world.random.nextInt(ns.size()))).geometry.getCoordinate();
			}
		}

		// the Person has arrived at a Shelter - try to enter it!
		if(evacuating && path == null){
			
			// if there is room, successfully enter the Shelter
			if(targetShelter.roomForN(1)){
				targetShelter.addNewPerson(this);
				
				// remove them from the road network they're on
				if(edge.getClass().equals(ListEdge.class))
					((ListEdge)edge).removeElement(this);
				
				if(time - evacuatingTime <= 10)
					System.out.println("wut");
				// set the final evacuating time!
				evacuatingTime = time - evacuatingTime;
				evacuatingCompleted = true;
				return;
			}
			
			// otherwise, learn that the Shelter is full and consider going to another!
			else{
				fullShelters.add(targetShelter);
				System.out.println("Shelter is full! " + myID);
				beginEvacuating(evacuatingTime); // force a reassessment
				if(targetShelter == null){ // just stay on the road and cry pitiably, I guess??
					//evacuatingTime = time - evacuatingTime;
					evacuatingCompleted = true;
					return;
				}
				// if that shelter is also full, wait a bit to think it over, then try again
				else if(!targetShelter.roomForN(1)){
					fullShelters.add(targetShelter);
					path = null;
					world.schedule.scheduleOnce(time + (int) rayleighDistrib(world.random.nextDouble()), this);
					return;
				}
			}
		}
		
		// check if the person has arrived at their destination
		else if(path == null && time % 1440 <  960){ // only set a new target if it's during the day - if it's at night, wait
			if(targetDestination != null && targetDestination.distance(this.geometry.getCoordinate()) < world.resolution){
				Bag ns = world.roadNodes;
				targetDestination = ((GeoNode)ns.get(world.random.nextInt(ns.size()))).geometry.getCoordinate();
			}
		}
		
		world.schedule.scheduleOnce(time+1, this);
	}

	public int navigate(double resolution){
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
	
	/**
	 * 
	 * @param myTime - the time at which the evacuation effort started
	 */
	void beginEvacuating(double myTime){
		evacuatingTime = myTime; // save it here as a record
		evacuating = true;
		double myDistance = Double.MAX_VALUE;
		MasonGeometry myShelter = null;
		for(Object o: world.shelterLayer.getGeometries()){
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
			System.out.println("No shelters with available space!");
			targetShelter = null;
			return;
		}
		/*GeoNode gn = RoadNetworkUtilities.getClosestGeoNode(((Shelter)myShelter).getEntrance().getCoordinate(), 
				world.resolution, world.networkLayer, 
				world.networkEdgeLayer, world.fa);
		targetDestination = gn.geometry.getCoordinate();
		*/
		targetDestination = ((Shelter)myShelter).getEntrance();
		path = null;
		targetShelter = (Shelter)myShelter;
	}
	
	
	public double estimateTravelTimeTo(Geometry g){
		return(g.distance(this.geometry) / speed);
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

	public double getEvacuatingTime(){ return evacuatingTime; }
	
	/**
	 * Set up a course to take the Agent to the given coordinates
	 * 
	 * @param place - the target destination
	 * @return 1 for success, -1 for a failure to find a path, -2 for failure based on the provided destination or current position
	 */
	public int headFor(Coordinate place) {

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

		path = pathfinder.astarPath(node, destinationNode, world.roads);

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

	public boolean evacuatingCompleted(){ return evacuatingCompleted; }
	public String getMyID(){ return this.myID; }	
	public int getAge(){ return this.age; }
}