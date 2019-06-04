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
import sim.util.geo.AttributeValue;
import sim.util.geo.MasonGeometry;
import swise.agents.MobileAgent;
import swise.agents.TrafficAgent;
import swise.agents.communicator.Communicator;
import swise.agents.communicator.Information;
import swise.objects.network.GeoNode;
import swise.objects.network.ListEdge;
import utilities.RoadNetworkUtilities;

public class Person extends TrafficAgent implements Communicator {
	
	Coordinate home, work;
	TakamatsuSim world;

	// movement utilities
	Coordinate targetDestination = null;
	GeomVectorField space = null;
	double enteredRoadSegment = -1;
	double minSpeed = 7;
	
	ArrayList <MasonGeometry> fullShelters = new ArrayList <MasonGeometry> ();
	
	boolean evacuating = false;
	double evacuatingTime = 0;
	Shelter targetShelter = null;

	
	public Person(String id, Coordinate position, Coordinate home, Coordinate work, TakamatsuSim world){		
		this(id, position, home, work, world, .8, .5, .1, .1, 10000, 1000, .5, 2000);
	}
	
	public Person(String id, Coordinate position, Coordinate home, Coordinate work, TakamatsuSim world, 
			double communication_success_prob, double contact_success_prob, double tweet_prob, 
			double retweet_prob, double comfortDistance, double observationDistance, double decayParam, double speed){

		super((new GeometryFactory()).createPoint(position));
		
		
		if(position != null) {
			this.home = (Coordinate)position.clone();
			edge = RoadNetworkUtilities.getClosestEdge(position, world.resolution, world.networkEdgeLayer, world.fa);
			
			if(edge == null){
				System.out.println("\tINIT_ERROR: no nearby edge");
				return;
			}
				
			GeoNode n1 = (GeoNode) edge.getFrom();
			GeoNode n2 = (GeoNode) edge.getTo();
			
			if(n1.geometry.getCoordinate().distance(position) <= n2.geometry.getCoordinate().distance(position))
				node = n1;
			else 
				node = n2;

			segment = new LengthIndexedLine((LineString)((MasonGeometry)edge.info).geometry);
			startIndex = segment.getStartIndex();
			endIndex = segment.getEndIndex();
			currentIndex = segment.indexOf(position);
		}
		if(work != null)
			this.work = (Coordinate)work.clone();
		this.world = world;
		if(world.random.nextDouble() < .1) 
			this.speed = TakamatsuSim.speed_vehicle;
		else
			this.speed = TakamatsuSim.speed_pedestrian;
		this.addIntegerAttribute("speed", (int)this.speed);

		// add it to the space
		this.space = world.agentsLayer;
		this.space.addGeometry(this);
		this.isMovable = true;
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
	
	
	/**
	 * 
	 * @param resolution
	 * @return 1 for success, -1 for failure
	 */	
	public int navigate(double resolution){
		myLastSpeed = -1;
		
		if(path != null){
			double time = 1;//speed;
			while(path != null && time > 0){
				time = move(time, speed, resolution);
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
	 * @param time - a positive amount of time, representing the period of time agents 
	 * 				are allocated for movement
	 * @param obstacles - set of spaces which are obstacles to the agent
	 * @return the amount of time left after moving, negated if the movement failed
	 */
	protected double move(double time, double mySpeed, double resolution){
		
		// if we're at the end of the edge and we have more edges, move onto the next edge
		if(arrived() ){
			
			// clean up any edge we leave
			if(edge != null && edge.getClass().equals(ListEdge.class)){
				((ListEdge)edge).removeElement(this);
				
				// update the edge with how long you've spent on it
			//	double durationOnSegment = ((MasonGeometry)edge.info).getDoubleAttribute("MikeSim_timeOnRoad");
				
			//	if(enteredRoadSegment > 0) // if you began on the edge and never really entered it, don't consider this
			//		((MasonGeometry)edge.info).addDoubleAttribute("MikeSim_timeOnRoad", 
			//			durationOnSegment + world.schedule.getTime() - enteredRoadSegment);
			}

			// if we have arrived and there is no other edge in the path, we have finished our journey: 
			// reset the path and return the remaining time
			if(goalPoint == null && path.size() == 0 && (currentIndex <= startIndex || currentIndex >= endIndex )){
				path = null;
				return time;
			}
			
			// make sure that there is another edge in the path
			if(path.size() > 0) { 

				// take the next edge
				Edge newEdge = path.remove(path.size() - 1);				
				edge = newEdge;

				// make sure it's open
				// if it's not, return an error!
		/*		if(((MasonGeometry)newEdge.info).getStringAttribute("open").equals("CLOSED")){
					updateLoc(node.geometry.getCoordinate());
					edge = newEdge;
					path = null;
					return -1;
				}				
*/
				// change our positional node to be the Node toward which we're moving
				node = (GeoNode) edge.getOtherNode(node);
				
				// format the edge's geometry so that we can move along it conveniently
				LineString ls = (LineString)((MasonGeometry)edge.info).geometry;

				// set up the segment and coordinates
				segment = new LengthIndexedLine(ls);
				startIndex = segment.getStartIndex();
				endIndex = segment.getEndIndex();
				currentIndex = segment.project(this.geometry.getCoordinate());
				
				
				// if that was the last edge and we have a goal point, resize the expanse
				if(path.size() == 0 && goalPoint != null){ 
					double goalIndex = segment.project(goalPoint);
					if(currentIndex < goalIndex)
						endIndex = goalIndex;
					else
						startIndex = goalIndex;
				}
				
				// make sure we're moving in the correct direction along the Edge
				if(node.equals(edge.to())){
					direction = 1;
					currentIndex = Math.max(currentIndex, startIndex);
				} else {
					direction = -1;
					currentIndex = Math.min(currentIndex, endIndex);
				}

				if(edge.getClass().equals(ListEdge.class)){
					((ListEdge)edge).addElement(this);
				//	int numUsages = ((MasonGeometry)edge.info).getIntegerAttribute("MikeSim_useages");
				//	((MasonGeometry)edge.info).addIntegerAttribute("MikeSim_useages", numUsages + 1);

					enteredRoadSegment = world.schedule.getTime();
				}

			}
						

		}
		
		// otherwise, we're on an Edge and moving forward!

		// set our speed
		double speed;
		if(edge != null && edge.getClass().equals(ListEdge.class)){
			
			// Each car has a certain amount of space: wants to preserve a following distance. 
			// If the amount of following distance is less than 20 meters (~ 6 car lengths) it'll slow
			// proportionately
			double val = ((ListEdge)edge).lengthPerElement() / 5;
			if(val < 10 && super.speed == TakamatsuSim.speed_vehicle) {
				speed = mySpeed / val;//minSpeed);
				if(speed < 1){ // if my speed is super low, set it to some baseline to keep traffic moving at all
					int myIndexInEdge =((ListEdge)edge).returnMyIndex(this);
					if(myIndexInEdge == 0 || myIndexInEdge == ((ListEdge)edge).numElementsOnListEdge() - 1)
						speed = super.speed; // if I'm at the head or end of the line, move ahead at a fairly normal speed
				}
			}
			else
				speed = super.speed;
			
		}
		else
			speed = mySpeed;

		myLastSpeed = speed;
		
		// construct a new current index which reflects the speed and direction of travel
		double proposedCurrentIndex = currentIndex + time * speed * direction;
		
		// great! It works! Move along!
		currentIndex = proposedCurrentIndex;
				
		if( direction < 0 ){
			if(currentIndex < startIndex){
				time = (startIndex - currentIndex) / speed; // convert back to time
				currentIndex = startIndex;
			}
			else
				time = 0;
		}
		else if(currentIndex > endIndex){
			time = (currentIndex - endIndex) / speed; // convert back to time
			currentIndex = endIndex;
		}
		else
			time = 0;

		// don't overshoot if we're on the last bit!
		if(goalPoint != null && path.size() == 0){
			double idealIndex = segment.indexOf(goalPoint);
			if((direction == 1 && idealIndex <= currentIndex) || (direction == -1 && idealIndex >= currentIndex)){
				currentIndex = idealIndex;
				time = 0;
				startIndex = endIndex = currentIndex;
			}
		}

		updateLoc(segment.extractPoint(currentIndex));
		
		if(path.size() == 0 && arrived()){
			path = null;
			if(edge != null)
				((ListEdge)edge).removeElement(this);
		}
		return time;
	}

	public double getEvacuatingTime(){ return evacuatingTime; }
	
	void testEvacuating(double myRand){
		if(evacuating) {
			evacuatingTime += 1;
			return;
		}
		
		else if(myRand < .05){
			evacuating = true;
			double myDistance = Double.MAX_VALUE;
			MasonGeometry myShelter = null;
			for(Object o: world.shelterLayer.getGeometries()){
				Shelter s = (Shelter) o;
				if(fullShelters.contains(s)) continue;
				double possibleDist = geometry.distance(s.getEntrance()) / (Math.log(s.getArea()) * Math.pow(speed, 2));
				if(possibleDist < myDistance){
					myDistance = possibleDist;
					myShelter = s;
				}
			}
			if(myShelter == null){
				System.out.println("what!? No shelters!!!");
			}
			targetDestination = RoadNetworkUtilities.getClosestGeoNode(((Shelter)myShelter).getEntrance().getCoordinate(), 
					world.resolution, world.networkLayer, 
					world.networkEdgeLayer, world.fa).geometry.getCoordinate();
			path = null;
			targetShelter = (Shelter)myShelter;
		}
	}
	
	/**
	 * Assuming the Person is not interrupted by intervening events, they are activated
	 * roughly when it is time to begin moving toward the next Task
	 */
	@Override
	public void step(SimState state) {
		
		double time = state.schedule.getTime(); // find the current time

		// random chance of beginning to evacuate
		testEvacuating(state.random.nextDouble());
		
		// if the agent is already in transit, continue moving
		if(targetDestination != null){
			if(path == null){
				headFor(targetDestination);
			}
			this.navigate(world.resolution);
		}
		else {
			Bag ns = world.roadNodes;
			targetDestination = ((GeoNode)ns.get(world.random.nextInt(ns.size()))).geometry.getCoordinate();
		}

		if(!evacuating || path != null)
			world.schedule.scheduleOnce(time+1, this);
		
		if(evacuating && path == null){
			if(targetShelter.roomForN(1)){
				targetShelter.addNewPerson(this);
				return;
			}
			else{
				fullShelters.add(targetShelter);
				System.out.println("Shelter is full! " + targetShelter.toString());
				testEvacuating(.001); // force a reassessment
			}
		}
	}
	
	public double estimateTravelTimeTo(Geometry g){
		return(g.distance(this.geometry) / speed);
	}
	
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

}