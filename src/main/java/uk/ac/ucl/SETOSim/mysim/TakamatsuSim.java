package uk.ac.ucl.SETOSim.mysim;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.FileLock;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;

import sim.engine.SimState;
import sim.engine.Steppable;
import sim.field.geo.GeomGridField;
import sim.field.geo.GeomGridField.GridDataType;
import sim.field.geo.GeomVectorField;
import sim.field.grid.Grid2D;
import sim.field.grid.IntGrid2D;
import sim.field.network.Edge;
import sim.field.network.Network;
import sim.util.Bag;
import sim.util.geo.MasonGeometry;
import uk.ac.ucl.SETOSim.myobjects.*;
import uk.ac.ucl.SETOSim.utilities.*;
import uk.ac.ucl.swise.objects.NetworkUtilities;
import uk.ac.ucl.swise.objects.RoadNetworkUtilities;
import uk.ac.ucl.swise.objects.network.GeoNode;
import uk.ac.ucl.swise.objects.network.ListEdge;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateSequenceFilter;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.linearref.LengthIndexedLine;

import ec.util.MersenneTwisterFast;

/**
 * TakamatsuSim is the core of a simulation which projects the behavior of agents in the aftermath
 * of an incident.
 * 
 * @author swise and Hitomi Nakanishi
 *
 */
public class TakamatsuSim extends SimState {

	/////////////// Model Setup ///////////////////////////////////
	
	private static final long serialVersionUID = 1L;

	// settings
	public Params params;
	public static String paramsFilename = "src/main/resources/params_default.txt";//hicss2025.txt";//"src/main/resources/myriad.txt";
	long mySeed = 0;
	
	public static boolean verbose = false;
	
	//// END Model Setup ////////////////////////
	
	/////////////// Containers ///////////////////////////////////////

	// basic
	public GeomVectorField waterLayer;
	public GeomVectorField roadLayer;
	public GeomVectorField buildingLayer;
	public GeomVectorField agentsLayer;
	public GeomVectorField householdsLayer;
	public GeomVectorField namesLayer;

	public ArrayList <Person> agents = new ArrayList <Person> ();
	public int household_index = 0;

	// transport
	public GeomVectorField networkLayer;
	public GeomVectorField networkEdgeLayer;	
	public GeomVectorField majorRoadNodesLayer;
	public GeomVectorField hi_roadLayer;

	public Bag roadNodes = new Bag();
	public Network roads = new Network(false);
	HashMap <MasonGeometry, ArrayList <GeoNode>> localNodes;
	public Bag terminus_points = new Bag();
	public Edge [][] roadAdjacencyMatrix = null;
//	public HashMap <Integer, HashSet> roadClosures;
	public HashSet roadClosures;

	public GeomVectorField stationLayer;
	public ArrayList <GeoNode> stations = new ArrayList <GeoNode> ();

	// hazard
	public GeomVectorField shelterLayer;
	public GeomVectorField floodedLayer;
	public GeomVectorField evacuationAreas;
	//public GeomVectorField fireLayer = new GeomVectorField(grid_width, grid_height);
	//public ArrayList <GeomVectorField> firePoints = new ArrayList <GeomVectorField>();
	

	// reporters
	public GeomGridField heatmap = new GeomGridField();
	public HashMap <String, Integer> roadUsageRecord = new HashMap <String, Integer> ();
	public HashMap <String, ArrayList<Integer>> roadUsageByTimestep = new HashMap <String, ArrayList<Integer>> ();

	public int numEvacuated = 0;
	public ArrayList <Integer> numEvacuatedOverTime = new ArrayList <Integer> ();
	
	public int numAttemptingEvac = 0;
	public ArrayList <Integer> numAttemptedEvacsOverTime = new ArrayList <Integer> ();
	
	public int numAssisting = 0;
	public ArrayList <Integer> numAssistingOverTime = new ArrayList <Integer> ();
	
	public int shelterReportCounter = -1;
	public HashMap <Shelter, ArrayList <Integer>> shelterReport = new HashMap <Shelter, ArrayList <Integer>> ();

	ArrayList <Person> tracked = new ArrayList <Person> ();
	public HashSet <String> possibleStatuses;
	
	/////////////// END Containers ///////////////////////////////////////

	/////////////// Objects //////////////////////////////////////////////

	// geometry
	public GeometryFactory fa = new GeometryFactory();
	Envelope MBR = null;

	// movement
	public AStar pathfinder;
	public TakamatsuBehaviour behaviourFramework;
	public Coordinate notInSimulation = new Coordinate(-10000, -10000);
	
	// output
	BufferedWriter record_shelters, record_heatmap;
	public BufferedWriter record_info;

	/////////////// END Objects //////////////////////////////////////////
	
	///////////////////////////////////////////////////////////////////////////
	/////////////////////////// BEGIN functions ///////////////////////////////
	///////////////////////////////////////////////////////////////////////////	
	
	/**
	 * Default constructor function
	 * @param seed
	 */
	public TakamatsuSim(long seed) {
		this(seed, paramsFilename);
	}
	
	public TakamatsuSim(long seed, String paramsFilename) {
		this(seed, paramsFilename, verbose);
	}
	
	public TakamatsuSim(long seed, String paramsFilename, boolean verbose) {
		super(seed);
		params = new Params(paramsFilename, verbose);
	}

	public void startStubForTesting() {
		super.start(); // only used for testing to allow schedule etc to be initialised correctly
	}

	/**
	 * Read in data and set up the simulation
	 */
	public void start()
    {
		super.start();
		try {
			
			
			//////////////////////////////////////////////
			///////////// READING IN DATA ////////////////
			//////////////////////////////////////////////
		
			waterLayer = InputCleaning.readInVectorLayer(params.formatInputFilename(params.waterFilename), //params.dirName + params.waterFilename, 
					params.grid_width, params.grid_height, "water", new Bag());
			if(params.floodedFilename.length() > 0)
				floodedLayer = InputCleaning.readInVectorLayer(params.formatInputFilename(params.floodedFilename), //params.dirName + params.floodedFilename, 
					params.grid_width, params.grid_height, "flooding", new Bag());
			else
				floodedLayer = new GeomVectorField(params.grid_width, params.grid_height);
			
			buildingLayer = InputCleaning.readInVectorLayer(params.formatInputFilename(params.buildingsFilename), //params.dirName + params.buildingsFilename, 
					params.grid_width, params.grid_height, "buildings", new Bag());
			Bag roadAttsToRead = new Bag();
			roadAttsToRead.add("full_id"); roadAttsToRead.add("highway");
			roadLayer = InputCleaning.readInVectorLayer(params.formatInputFilename(params.roadsFilename), //params.dirName + params.roadsFilename, 
					params.grid_width, params.grid_height, "road network", new Bag());
			
			if( params.evacuationAreasFilename != null )
				evacuationAreas = InputCleaning.readInVectorLayer(params.formatInputFilename(params.evacuationAreasFilename), //params.dirName + params.evacuationAreasFilename, 
						params.grid_width, params.grid_height, "evacuationAreas", new Bag());
			
			// if this hasn't been set, set it!
			if(params.outputPrefix == null)
				params.outputPrefix = params.dirName;
			
			//////////////////////////////////////////////
			////////////////// CLEANUP ///////////////////
			//////////////////////////////////////////////

			// standardize the MBRs so that the visualization lines up
			
			MBR = buildingLayer.getMBR();
			//MBR.init(501370, 521370, 4292000, 4312000);

			params.grid_width = buildingLayer.fieldWidth;
			params.grid_height = buildingLayer.fieldHeight;

			evacuationAreas.setMBR(MBR);
			
			heatmap = new GeomGridField();
			heatmap.setMBR(MBR);
			heatmap.setGrid(new IntGrid2D((int)(MBR.getWidth() / 10), (int)(MBR.getHeight() / 10), 0));

			
			// set up the road network
			setupRoadNetwork();
			weightRoads();
			
			// set up train stations
			if(params.stationFilename != null && params.stationFilename.length() > 0)
				setupStations();
			
			// add shelter entrance info
			setupShelters();
			
			/////////////////////
			///////// Clean up roads for Persons to use ///////////
			/////////////////////
			// classifyRoadsByType();

			System.gc(); // force garbage collection for memory management purposes
			
			pathfinder = new AStar();
						
			/////////////////////
			///////// Set up Persons ///////////
			/////////////////////
		
			// first set up BehaviourFramework
			behaviourFramework = new TakamatsuBehaviour(this);
			
			// collect all of the possible statuses
/*			possibleStatuses = new HashSet <String> ();
			behaviourFramework.getAllNodes().forEach(
					(e) -> {possibleStatuses.add(e.getTitle());});
*/			
			setupPersons();

			//InputCleaning.readInVectorLayer(namesLayer, params.dirName + regionalNamesFilename, "name", new Bag());


			// reset MBRS in case it got messed up during all the manipulation
			resetAllMBRs();
			
			//setupAgentRoadKnowledge();
		
			// set up the evacuation orders to be inserted into the social media environment
//			setupCommunicators(params.dirName + communicatorFilename);

			// make sure all Households in the immediate area know that they are in the area
			//alertHouseholdsOfHazard();
			
			// SCHEDULE FLOOD
			scheduleFlood();
			scheduleEvacuationOrders();


			// SCHEDULE SHELTERS
			setupShelterReporting();
			
			// SCHEDULE ROAD REPORTING
			if(params.exportRoadUsage) 
				setupRoadReporting();

			System.out.println("done");			
			
		} catch (Exception e) { e.printStackTrace();}
    }
	

	public void alertHouseholdsOfHazard() {
		HashSet <Household> householdsImpacted = new HashSet <Household> ();
		for(Object o: waterLayer.getGeometries()){
			MasonGeometry mg = (MasonGeometry) o;
			Bag b = householdsLayer.getObjectsWithinDistance(mg, params.hazardThresholdDistance);
			householdsImpacted.addAll(b);
		}

		for(Household h: householdsImpacted)
			h.setInHazardZone(true);
	}
	
	public void classifyRoadsByType() {
		Network majorRoads = RoadNetworkUtilities.extractMajorRoads(roads);
		RoadNetworkUtilities.testNetworkForIssues(majorRoads);

		
		// assemble list of secondary versus local roads
		ArrayList <Edge> myEdges = new ArrayList <Edge> ();
		majorRoadNodesLayer = new GeomVectorField(params.grid_width, params.grid_height);
		GeomVectorField secondaryRoadsLayer = new GeomVectorField(params.grid_width, params.grid_height);
		GeomVectorField localRoadsLayer = new GeomVectorField(params.grid_width, params.grid_height);
		for(Object o: majorRoads.allNodes){
			
			majorRoadNodesLayer.addGeometry((GeoNode)o);
			
			for(Object e: roads.getEdges(o, null)){
				Edge ed = (Edge) e;
				
				myEdges.add(ed);
									
				String type = ((MasonGeometry)ed.getInfo()).getStringAttribute("class");
				if(type.equals("secondary"))
						secondaryRoadsLayer.addGeometry((MasonGeometry) ed.getInfo());
				else if(type.equals("local"))
						localRoadsLayer.addGeometry((MasonGeometry) ed.getInfo());					
			}
		}

	}
	
	public void resetAllMBRs() {
		Envelope mbrCopy = new Envelope(MBR);
		waterLayer.setMBR(mbrCopy);
		
		buildingLayer.setMBR(MBR);
		roadLayer.setMBR(MBR);			
		networkLayer.setMBR(MBR);
		networkEdgeLayer.setMBR(MBR);
		if(majorRoadNodesLayer != null)
			majorRoadNodesLayer.setMBR(MBR);
		agentsLayer.setMBR(MBR);
		shelterLayer.setMBR(MBR);
		heatmap.setMBR(MBR);
		//namesLayer.setMBR(MBR);

	}
	
	public void scheduleFlood() {
		
	//	ArrayList <Integer> roadClosureTimes = new ArrayList <Integer> (roadClosures.keySet());

		// if there are no road closures and no flood3d file, it must be some other kind of simulation
		if((roadClosures == null || roadClosures.size() == 0) && this.floodedLayer == null) {
			System.out.println("WARNING: simulation has no road flooding information included");
			return;
		}
		
//		Collections.sort(roadClosureTimes);
//		int maxTime = roadClosureTimes.get(roadClosureTimes.size() - 1);
		
		Steppable floodScheduler = new Steppable(){

//			int floodIndex = maxTime;
			
			@Override
			public void step(SimState arg0) {
				
//				if(floodIndex < 0) return;
				double time = arg0.schedule.getTime();
				
				HashSet <Household> householdsImpacted = new HashSet <Household> ();
				HashSet <Person> peopleImpacted = new HashSet <Person> ();
				
				GeomVectorField fieldWithFloodingInfo = floodedLayer;
//				if(params.floodedFilename.length() == 0)
//					fieldWithFloodingInfo = roadLayer;
				
				System.out.println("FLOOD BEGINNING");
				
				for(Object o: floodedLayer.getGeometries()){
					MasonGeometry mg = (MasonGeometry) o;

					// the hazard may be read in as a polygon...
//					if(mg.hasAttribute("depth") && mg.getIntegerAttribute("depth").intValue() != floodIndex) // only add the latest set!
//						continue;
					
					// ...or else as road segments
					//else 
						if(params.roadInundationColumnName != null && mg.hasAttribute(params.roadInundationColumnName) && 
							mg.getDoubleAttribute(params.roadInundationColumnName) < params.roadInundationImpassableDepth)
						continue; // if it's not inundated, ignore it
					
					else if(mg.hasAttribute(params.roadInundationColumnName)) {
						Geometry g = mg.geometry.buffer(params.resolution);
						mg.geometry = g;
					}
					
					waterLayer.addGeometry(mg);
					Bag b = householdsLayer.getObjectsWithinDistance(mg, params.hazardThresholdDistance);
					householdsImpacted.addAll(b);
					
					Bag p = agentsLayer.getObjectsWithinDistance(mg, params.hazardThresholdDistance);
					peopleImpacted.addAll(p);
				}
				
				for(Household h: householdsImpacted)
					h.setInHazardZone(true);
				
				//HashSet roadsTakenOut = roadClosures;//.get(floodIndex); 
				for(Object o: roadClosures) {// roadsTakenOut) {
					MasonGeometry mg = (MasonGeometry) o;
					mg.addAttribute("open", "CLOSED");
				}
				
				// make sure everyone currenly inundated checks in
				for(Person p: peopleImpacted) {
					p.setInundated(true, time);
					p.updateEvacRecord("FLOOD_PROMPTED_AT:" + (int) time);
					//p.beginEvacuating((TakamatsuSim) arg0); 
					if(! p.evacuatingCompleted())
						p.setActivityNode(behaviourFramework.getTrapped());
						p.updateMyStatus(4);
				}
				
				Envelope mbrCopy = new Envelope(MBR);
				waterLayer.setMBR(mbrCopy);
//				waterLayer.updateSpatialIndex();
				
//				floodIndex--;
//				if(floodIndex > 0)
//					arg0.schedule.scheduleOnce(arg0.schedule.getTime() + params.ticks_per_hour, this);
					
			}
			
		};
		
		schedule.scheduleOnce(params.forecastArrivalTime, floodScheduler);
		
	}
	
	public double convertTimeToTicks (String date, DateTimeFormatter formatter) {
		try {
			// get time of the event
			LocalDateTime evacDate = LocalDateTime.parse(date, formatter);
			
			// compare the two times, converting to hours and then ticks per hour
			double diff = Duration.between(params.simulationStart, evacDate).toHoursPart() * params.ticks_per_hour;
			
			// return this difference
			return diff;
			
		} catch (Exception e) {
			
			// otherwise, just say it's right now
			return schedule.getTime();
		}
	}
	
	public void scheduleEvacuationOrders() {
		
		// multiple phases - either 
		//    1. denoted by time (if the geometries have "time" parameters
		//    2. denoted by area, with an "optional" evac order 

		if( params.evacuationScenarioFilename != null && params.evacuationScenarioFilename.length() > 0) {
			
			// first, pull out all of the areas by name so they can be accessed easily
			HashMap <String, ArrayList <MasonGeometry>> evacAreaNameMapping = new HashMap <String, ArrayList <MasonGeometry>> ();
			for(Object o: this.evacuationAreas.getGeometries()) {
				MasonGeometry mg = (MasonGeometry) o;
				String myID = mg.getStringAttribute(params.evacuationAreaIDColumnName);
				if(!evacAreaNameMapping.containsKey(myID))
					evacAreaNameMapping.put(myID, new ArrayList <MasonGeometry> ());
				evacAreaNameMapping.get(myID).add(mg);
			}
			
			// next, iterate through the evacuation events and schedule them
			ArrayList <String> events = InputCleaning.readInTextDataAsLines(params.formatInputFilename(params.evacuationScenarioFilename), 
					"defined evacuation scenario");
			
			// data parser
			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
			
			// headers
			HashMap <String, Integer> columnsToIndex = new HashMap <String, Integer> ();
			Integer index = 0;
			String headerLine = events.get(0).strip();
			for(String s: headerLine.split(","))
				columnsToIndex.put(s.trim(), index++);
			int areaNameIndex = columnsToIndex.get("name");
			int	rawTimeIndex = columnsToIndex.get("time");
					int		elderlyIndex = columnsToIndex.get("elderly");
							int	allPeopleIndex = columnsToIndex.get("all");
									int floodTimeIndex = columnsToIndex.get("flooding");
			
			for(int i = 1; i < events.size(); i++) { // ignore the header
				String [] bits = events.get(i).split(",");
				
				String areaName = bits[areaNameIndex], 
						rawTime = bits[rawTimeIndex], 
						elderly = bits[elderlyIndex], 
						allPeople = bits[allPeopleIndex], 
						flooding = bits[floodTimeIndex];
				
				double parsedTime = convertTimeToTicks(rawTime, formatter),
						elderlyPercent = Double.parseDouble(elderly),
						allPercent = Double.parseDouble(allPeople);

				// set up with the correct parameters - for each of the geometries which make up this space
				for(MasonGeometry myGeom: evacAreaNameMapping.get(areaName)) {
					AreaEvacuater scheduledEvac = new AreaEvacuater(myGeom, elderlyPercent, allPercent);				
					schedule.scheduleOnce(parsedTime, scheduledEvac);
				}
			}
			
			return;
		}
		
		// otherwise, do it by the areas; the geometries should have a column with the TIME of the evacuation
		for(Object o: this.evacuationAreas.getGeometries()) {
			MasonGeometry mg = (MasonGeometry) o;
			
			// extract the evacuation time (formated as 00:00) and turn it into simulation time!!
			int timeAsInt;
			if(mg.hasAttribute("Time")) {
				String [] evacTimeStr = mg.getStringAttribute("Time").split(":");
				timeAsInt = (int)(Integer.parseInt(evacTimeStr[0]) * params.ticks_per_hour + Integer.parseInt(evacTimeStr[1]));
			}
			else {
				//double depthOfInundation = mg.getDoubleAttribute(params.roadInundationColumnName);
				//if(depthOfInundation <= .1)
				//	continue; // this road is not a threat
				timeAsInt = params.forecastArrivalTime - params.forecastNoticePeriod; // otherwise, it will inundate at the time of the forecast!
			}
			
			
			AreaEvacuater scheduledEvac = new AreaEvacuater(mg, 0, params.compliance);
			schedule.scheduleOnce(timeAsInt, scheduledEvac);
			
			// sometimes there will be a voluntary evacuation before the mandatory one - in that case, schedule
			// a mandatory evacuation to begin an hour after the guidance!
		/*	if(params.compliance < 1) {
				AreaEvacuater mandatoryEvac = new AreaEvacuater(1, mg.geometry);
				schedule.scheduleOnce(timeAsInt + params.ticks_per_hour, mandatoryEvac);
				
			}*/
		}

	}
	
	public class AreaEvacuater implements Steppable {

		MasonGeometry g;
		double elderly = 0.;
		double minors = 0.;
		double all = 0.;
		
		public AreaEvacuater(MasonGeometry geom) {
			this.g = geom;
		}
		
		public AreaEvacuater(MasonGeometry geom, double elderly, double all) {
			this(geom);
			this.elderly = elderly;
			this.all = all;
		}
		
		@Override
		public void step(SimState arg0) {
			
			// pull out which households and persons are impacted
			HashSet <Household> householdsImpacted = new HashSet <Household> ();
			HashSet <Person> peopleImpacted = new HashSet <Person> ();
			
			double time = arg0.schedule.getTime();

			// update all households
			Bag b = householdsLayer.getObjectsWithinDistance(g, params.hazardThresholdDistance);
			householdsImpacted.addAll(b);
			
			boolean evacAll = all > 0, evacElderly = elderly > 0, evacMinors = minors > 0;
			
			for(Household h: householdsImpacted) {
				double val = arg0.random.nextDouble();
				if( (evacAll && val < all) || 
					(evacElderly && h.hasElderly() && val < elderly)) {

					h.setInHazardZone(true);
					for(Person m: h.getMembers()) {
						boolean prompted = m.beginEvacuating(time);
						if(prompted) m.updateEvacRecord("EVAC_ORDER_PROMPTED_AT", time);
					}
				}
			}

			
			Bag p = agentsLayer.getObjectsWithinDistance(g, params.hazardThresholdDistance);
			peopleImpacted.addAll(p);
			
			// make sure everyone in the inundated area knows about it!
			for(Person a: peopleImpacted) {
				
				if(a.evacuatingCompleted()) // don't update people who are already in shelters
					continue;
				
				double val = arg0.random.nextDouble();
				if( (evacAll && val < all) || 
					(evacElderly && a.getAge() > 12 && val < elderly)) {
					boolean prompted = a.beginEvacuating(time);//.setInundated(true, time); // they're all inundated
					if(prompted) 
						a.updateEvacRecord("EVAC_ORDER_PROMPTED_AT:" + (int) time);
				}
				
			}
		}
		
		
	}
	
	
	public void setupPersons() {
		agentsLayer = new GeomVectorField(params.grid_width, params.grid_height);
		householdsLayer = new GeomVectorField(params.grid_width, params.grid_height);

		ArrayList<Person> myAgents = PersonUtilities.setupHouseholdsFromFile(params.dirName + params.agentFilename,
				agentsLayer, householdsLayer, this, params.percIgnored);
		// TODO establish meaningful workplaces!!!
		agents.addAll(myAgents);
		
		//agents.addAll(PersonUtilities.setupHouseholdsAtRandom(networkLayer, schedule, this, fa));
		int numRoadNodes = roadNodes.size();
		int numPeople = agents.size();
		
		// num people is trueNum * (1 - sampleSize), right? so we should adjust similarly
		// perc commuters is thus equal to trueNumCommuters * (1 - sampleSize) / numPeople
		double proportionOfCommuters = params.numCommutersOutbound * (1 - params.percIgnored)/ numPeople;
		
		for(Person p: agents){
			
			// workplaces
			Coordinate workC;
			if(random.nextDouble() < proportionOfCommuters)// TODO add ages back in && p.getAge() > 3)
				workC = this.notInSimulation; 
			else
				workC = ((GeoNode)roadNodes.get(random.nextInt(numRoadNodes))).geometry.getCoordinate();
			p.setWorkLocation(workC);
			
			// if designating dependents for evacuation, do so!
			if(params.evacuationPolicy_designatedPerson && p.dependent == null) {
				
				// some likelihood that people at any age will need assistance, but let's say it scales
				// with age!
				double myProb = p.getAge() * .05; // ages are in units of 5 year blocks, so normalise in reverse!
				
				if(random.nextDouble() > myProb) { // designate who my person is!
					
					int randomIndex = random.nextInt(numPeople);
					Person otherPerson = agents.get(randomIndex);
					
					// only one dependent per person, and you can't depend on someone this way. Also, no children as helpers!
					
					int breaker = 20;
					while(breaker > 0 && (otherPerson.dependent != null || otherPerson.dependentOf != null || otherPerson.getAge() < 2)) {
						randomIndex = random.nextInt(numPeople);
						otherPerson = agents.get(randomIndex);
						breaker--;
					}
					if(breaker <= 0)
						continue;
					
					if(otherPerson.dependentOf != null)
						System.out.println("seriousy wtf");
					// otherwise, you found someone!
					otherPerson.dependent = p;
					p.dependentOf = otherPerson;
				}
			}
			
			if(random.nextDouble() < .01 && tracked.size() < 30) {
				p.tracked = true;
				tracked.add(p);
			}
				
			
		}
		

		double numberOfInboundCommuters = params.percIgnored * params.numCommutersInbound;
		for(int i = 0; i < numberOfInboundCommuters; i++) {
		
			// where do they work and how will they get there?
			Geometry workGeom = ((GeoNode)roadNodes.get(random.nextInt(numRoadNodes))).geometry;
			Coordinate workC = workGeom.getCoordinate();
			GeoNode arrivalStation = getNearestOpenStation(workGeom);
			
			// create the commuter TODO less dummy values???
			Person p = new Person("commuter_" + i, notInSimulation, notInSimulation, workC, null, 0, 0, this);
			p.setActivityNode(behaviourFramework.getEntryPoint());
			agents.add(p);
			
			// schedule arrival during morning rush hour
			double arrivalTime = Math.ceil(8.5 * params.ticks_per_hour + random.nextGaussian() * params.ticks_per_hour); 
			p.scheduleArrival(arrivalStation, arrivalTime);
		}
		
	}
	
	public void setupPersonRoadKnowledge() {
		/*			
		// for each of the Persons, set up relevant, environment-specific information
		int aindex = 0;
		for(Person a: agents){
			
			if(a.familiarRoadNetwork == null){
				
				// the Person knows about major roads
				Network familiar = majorRoads.cloneGraph();

				// connect the major network to the Person's location
				connectToMajorNetwork(a.getNode(), familiar);
				
				a.familiarRoadNetwork = familiar;

				// add local roads into the network
				for(Object o: agentsLayer.getObjectsWithinDistance(a, 50)){
					Person b = (Person) o;
					if(b == a || b.familiarRoadNetwork != null || b.getNode() != a.getNode()) continue;
					b.familiarRoadNetwork = familiar.cloneGraph();
				}

			}
			
			// connect the Person's work into its personal network
			if(a.getWork() != null)
				connectToMajorNetwork(getClosestGeoNode(a.getWork()), a.familiarRoadNetwork);
			
			// set up its basic paths (fast and quicker and recomputing each time)
			a.setupPaths();

			if(aindex % 100 == 0){ // print report of progress
				System.out.println("..." + aindex + " of " + agents.size());
			}
			aindex++;
		}
*/
	}

	public void setupStations() {
		System.out.print("Setting up train stations...");
		
		stationLayer = InputCleaning.readInVectorLayer(params.dirName + params.stationFilename, params.grid_width, params.grid_height, "train stations", new Bag());
		
		// iterate over the stations and connect them to the road network as GeoNodes 
		for(Object o: stationLayer.getGeometries()) {
			MasonGeometry mg = (MasonGeometry) o;
			GeoNode station = attachStation(mg, params.resolution);
			
		}
	}
	
	public GeoNode attachStation(MasonGeometry stationLocation, double bestRes) {
		Bag nearby = networkLayer.getObjectsWithinDistance(stationLocation, bestRes);
		for(Object n: nearby) {
			if(n instanceof GeoNode) {
				GeoNode stationNode = (GeoNode) n;
				stationNode.addAttribute("station", "train");
				stations.add(stationNode);
				return stationNode;
			}
		}
		if(bestRes < networkLayer.fieldHeight)
			return attachStation(stationLocation, bestRes * 2);
		else
			return null;

	}
	
	public GeoNode getNearestOpenStation(Geometry g) {
		GeoNode bestSoFar = null;
		double bestDist = Double.MAX_VALUE;
		for(GeoNode s: stations) {
			if(s.hasAttribute("CLOSED"))
				continue;
			double dist = g.distance(s.geometry); 
			if(dist < bestDist) {
				bestDist = dist;
				bestSoFar = s;
			}
		}
		
		return bestSoFar;
	}
	
	public void setupRoadNetwork() {
		System.out.print("Cleaning the road network...");

		//Object myDummyObj = NetworkUtilities.class.getResource("NetworkUtilities.class");
		roads = NetworkUtilities.multipartNetworkCleanup(roadLayer, roadNodes, params.resolution, fa, random, 0);
		roadNodes = roads.getAllNodes();
		NetworkUtilities.testNetworkForIssues(roads);

		// set up the adjacency matrix for easier access
		roadAdjacencyMatrix = roads.getAdjacencyList(true); // outgoing from this node

		roadClosures = new HashSet (); //HashMap <Integer, HashSet> ();
		networkLayer = new GeomVectorField(params.grid_width, params.grid_height);
		networkEdgeLayer = new GeomVectorField(params.grid_width, params.grid_height);
		
		// set up roads as being "open" and assemble the list of potential terminii
		roadLayer = new GeomVectorField(params.grid_width, params.grid_height);
		int roadNodeIndex = 0;
		for(Object o: roadNodes){
			GeoNode n = (GeoNode) o;
			n.addIntegerAttribute("indexInNetwork", roadNodeIndex);
			roadNodeIndex++;
			
			networkLayer.addGeometry(n);
			
			
			boolean potential_terminus = false;
			
			// check all roads out of the nodes
			for(Object ed: roads.getEdgesOut(n)){
				
				// set it as being (initially, at least) "open"
				ListEdge edge = (ListEdge) ed;
				MasonGeometry edgeInfo = (MasonGeometry)edge.info;
				
				String roadName = edgeInfo.getStringAttribute("full_id");
				if(!roadUsageByTimestep.containsKey(roadName))
					roadUsageByTimestep.put(roadName, new ArrayList <Integer>());
				
				edgeInfo.addStringAttribute("open", "OPEN");
				double myLength = edgeInfo.geometry.getLength();
				edgeInfo.addDoubleAttribute("length", myLength);
				
				networkEdgeLayer.addGeometry( edgeInfo );
				roadLayer.addGeometry(edgeInfo);
				edgeInfo.addAttribute("ListEdge", edge);
				
				String type = ((MasonGeometry)edge.info).getStringAttribute(params.weightedRoadAttribute);
				if(type.equals("motorway") || type.equals("primary") || type.equals("trunk"))
					potential_terminus = true;
				
				
				if(edgeInfo.hasAttribute("depth"))
					try {
						int ultimateDepth = edgeInfo.getIntegerAttribute("depth").intValue();
						if(ultimateDepth > params.roadInundationImpassableDepth)
							roadClosures.add(edgeInfo);
/*						if(ultimateDepth > 0 && roadClosures.containsKey(ultimateDepth))
							roadClosures.get(ultimateDepth).add(edgeInfo);
						else {
							HashSet newTiming = new HashSet();
							newTiming.add(edgeInfo);
							roadClosures.put(ultimateDepth, newTiming);
						}
*/					} catch (Exception e) {
						System.out.println("WARNING: roads do not have attribute 'depth', used for updating closures");
						//int bluh = 0;
						//e.printStackTrace();
					}
				else if(edgeInfo.hasAttribute(params.roadInundationColumnName)) {
					try {
						double depth = edgeInfo.getDoubleAttribute(params.roadInundationColumnName);
						if(depth > params.roadInundationImpassableDepth) {
//							if(! roadClosures.containsKey(1))
//								roadClosures.put(1,  new HashSet());
//							roadClosures.get(1).add(edgeInfo);
							roadClosures.add(edgeInfo);
						}
;
					} catch (Exception e) {
						System.out.println("WARNING: problem formatting roads for closure with column name " + params.roadInundationColumnName);
					}
				}
				
			}
			
			// check to see if it's a terminus
			if(potential_terminus && !MBR.contains(n.geometry.getCoordinate()) && roads.getEdges(n, null).size() == 1){
				terminus_points.add(n);
			}

		}
	}
	
	public void setupRoadReporting() {
		
		Steppable roadReporter = new Steppable() {

			@Override
			public void step(SimState arg0) {
				resetRoadUsage();
			}
			
		};
		
		this.schedule.scheduleRepeating(roadReporter, params.road_reporting_step_interval);
	}
		
	public void setupShelters() {
		
		Bag shelterAtts = new Bag();
		String [] attsToAdd = {"capacity", "parkingcap", "entranceX", "entranceY", "name"};
		shelterAtts.addAll(attsToAdd);
		GeomVectorField shelterRaw = InputCleaning.readInVectorLayer(//dirName + 
				params.formatInputFilename(params.sheltersFilename), params.grid_width, params.grid_height, "shelters", shelterAtts);
		shelterLayer = new GeomVectorField(params.grid_width, params.grid_height);
		
		for(Object o: shelterRaw.getGeometries()){
			MasonGeometry shelter = (MasonGeometry)o;
			int numPeople = 300, numParkingSpaces = 50;
			if(shelter.hasAttribute("capacity")) numPeople = (int) shelter.getIntegerAttribute("capacity");
			if(shelter.hasAttribute("parkingcap")) numParkingSpaces = (int) shelter.getIntegerAttribute("parkingcap");
			Shelter myShelter = new Shelter(shelter, numPeople, numParkingSpaces, this);
			myShelter.addStringAttribute("name", shelter.getStringAttribute("name"));
			shelterLayer.addGeometry(myShelter);
		}

	}
	
	public void setupShelterReporting() {
		for(Object o: shelterLayer.getGeometries()){
			Shelter s = (Shelter) o;
			shelterReport.put(s, new ArrayList <Integer> ());
		}
		
		Steppable shelterReporter = new Steppable(){

			@Override
			public void step(SimState arg0) {
				shelterReportCounter++;
				for(Object o: shelterLayer.getGeometries()){
					Shelter s = (Shelter) o;
					int currentSize = s.currentPopulation();
					shelterReport.get(s).add(currentSize);
/*						ArrayList <Integer> count = shelterReport.get(s);
					if(shelterReportCounter == 0)
						count.add(0);
					else
						count.add(currentSize - count.get(shelterReportCounter - 1));
						*/
				}
				
				numEvacuatedOverTime.add(numEvacuated);
				numAttemptedEvacsOverTime.add(numAttemptingEvac);
				numAssistingOverTime.add(numAssisting);
				
			}
			
		};
		this.schedule.scheduleRepeating(params.forecastArrivalTime - params.forecastNoticePeriod, 1, shelterReporter, 10);
	}
	
	public void weightRoads() {
		params.typeWeighting_vehicle = new HashMap <String, Double> ();
		/*
		typeWeighting_vehicle.put("motorway", .5);
		typeWeighting_vehicle.put("primary", .5);
		typeWeighting_vehicle.put("trunk", .5);
		typeWeighting_vehicle.put("footway", 10000.);
		typeWeighting_vehicle.put("path", 10000.);
		typeWeighting_vehicle.put("pedestrian", 10000.);
		typeWeighting_vehicle.put("cycleway", 10000.);
		*/
		String [] preferredForVehicles = new String [] {"HIGHWAYS", "RURAL ARTERIAL", "URBAN ARTERIAL"};
		for(String myType: preferredForVehicles)
			params.typeWeighting_vehicle.put(myType, .5);
		
		
		params.typeWeighting_pedestrian = new HashMap <String, Double> ();
		params.typeWeighting_pedestrian.put("cycleway", 10000.);
		params.typeWeighting_pedestrian.put("HIGHWAYS", 10000.);
	}


	/**
	 * Finish the simulation and clean up
	 */
	public void finish(){
		super.finish();
		try{
			
			// create part of the title to record all the paramters used in this simulation
		//	String mySettings = communication_success_prob + "_" + contact_success_prob + "_" + tweet_prob + "_" + 
		//			retweet_prob + "_" + comfortDistance + "_" + observationDistance + "_" + decayParam + "_" + speed + "_";

			// SAVE THE HEATMAP
			if(params.exportHeatmap) {
				
				String heatmapFilename = params.outputPrefix + this.seed() + "_heatmap.txt";
				record_heatmap = new BufferedWriter(new FileWriter(heatmapFilename));
				System.out.println(heatmapFilename);
				IntGrid2D myHeatmap = ((IntGrid2D) this.heatmap.getGrid());
				
				/*
				// write a header
				record_heatmap.write(myHeatmap.getWidth() + "\t" + myHeatmap.getHeight() + "\t" + (int)schedule.getTime() + "\n");
				for(Shelter s: shelterReport.keySet()){
					ArrayList <Integer> sigh = shelterReport.get(s);
					String blah = "";
					for(Integer ugh: sigh){
						blah += ugh + "\t";
					}
					blah += s.getCapacity();
					record_heatmap.write(blah + "\n");
				}
				for(int i = 0; i < myHeatmap.getWidth(); i++){
					String output = "";
					for(int j = 0; j < myHeatmap.getHeight(); j++){
						output += myHeatmap.field[i][j] + "\t";
					}
					record_heatmap.write(output + "\n");
				}
				
				record_heatmap.write("\n\n\n");
				*/
				for(String s: roadUsageRecord.keySet()){
					record_heatmap.write(s + "\t" + roadUsageRecord.get(s) + "\n");
				}
				record_heatmap.close();
			}
			
			if(params.exportRoadUsage) {
				
				String roadUsageFilename = params.outputPrefix + this.seed() + "_roaduse_" + params.road_reporting_step_interval + ".txt";
				record_heatmap = new BufferedWriter(new FileWriter(roadUsageFilename));
				System.out.println(roadUsageFilename);

				for(String s: roadUsageByTimestep.keySet()){
					record_heatmap.write(s + "\t" + roadUsageByTimestep.get(s).toString() + "\n");
				}
				record_heatmap.close();
			}
			
			
			if(tracked.size() > 0) {
				
				String trackedAgentFilename = params.outputPrefix + this.seed() + "_trackedAgents_" + ".txt";
				record_heatmap = new BufferedWriter(new FileWriter(trackedAgentFilename));
				System.out.println(trackedAgentFilename);

				for(Person p: tracked){
					record_heatmap.write(p.tracks + "\n");
				}
				record_heatmap.close();
			}
			
			// print a record out
			System.out.println(this.mySeed + "\t" + this.numEvacuated);
			
			// SAVE ALL AGENT INFO
			String myOutFile = params.outputPrefix + this.seed() + ".txt";
			System.out.println("writing out to " + myOutFile);
			record_info = new BufferedWriter(new FileWriter(myOutFile));

			double worldTime = schedule.getTime();
			
/*			record_info.write(numEvacuatedOverTime.toString() + "\n");
			record_info.write(this.numAttemptedEvacsOverTime.toString() + "\n");
			record_info.write(this.numAssistingOverTime.toString());
*/
			record_info.write("ID\tage\tstatus\tevacuatingRecord\tflooded\tx_home\ty_home\tx_loc\ty_loc\thas_vehicle\n");//\tdependent\tdependentOf\tturnedAway\n");
			for(Person a: agents){

/*				if(a.getEvacuationRecord() == null)//.getEvacuatingTime() < 0) // don't export info about those who don't evacuate!
				{
					//if(! a.getHousehold().inHazardZone()) // if they're safe, don't export them; otherwise DO include them!
						continue;					
				}
*/				

				String myID = a.getMyID();

				String status = a.getActivityNode().getTitle();
				
//				Bag bagOWater= this.waterLayer.getObjectsWithinDistance(a.getHousehold(), this.resolution);
//				String inWater = "notSubmerged";
//				if(bagOWater.size() > 0)
//					inWater = "inWater";
//
				String inWater = "notSubmerged";
				if(a.getHousehold() != null && a.getHousehold().inHazardZone()) {
					inWater = "inZone";
					if(a.getEvacuationRecord().contains("VERTICAL_EVACUATION"))
						inWater += "_verticalEvac";
				}
				

				Coordinate homeCoord = a.getHousehold().getHome();
				Coordinate locCoord = a.geometry.getCoordinate();
			/*	String dependent = "<none>";
				if(a.dependent != null)
					dependent = a.dependent.getMyID();
				String dependentOf = "<none>";
				if(a.dependentOf != null)
					dependentOf = a.dependentOf.getMyID();
			*/	
			/*	double myTime = a.getEvacuatingTime();
				if(!a.getActivityNode().isEndpoint()) {
					myTime = worldTime - a.getEvacuatingTime();
				}
				System.out.print(myTime + "\t");
			*/	
				int car = 0;
				if(a.hasVehicle()) car = 1;
				record_info.write(myID + "\t" +  a.getAge() + "\t" + status + "\t" + a.getEvacuationRecord() + "\t"
						+ inWater + "\t" + (int) homeCoord.x + "\t" + (int)  homeCoord.y + 
						"\t" + (int) locCoord.x + "\t" + (int) locCoord.y + "\t" + car//+ "\t" + dependent + "\t" + dependentOf + "\t" + a.turnedAwayFromShelterCount
						+ "\n");//a.getHistory() + "\n");
				
				
			}

			this.record_info.close();
			
			// SAVE ALL AGENT INFO
/*
			String myShelterOutFile = params.outputPrefix + this.seed() + "_SHELTERS.txt";
			System.out.println("writing out to " + myShelterOutFile);
			record_info = new BufferedWriter(new FileWriter(myShelterOutFile));
			for(Person p: tracked) {
				record_info.write(p.getMyID() + "\t" + p.tracks);
			}
*/
// OVERWRITING FOR CRIMES			
//			for(Entry<Shelter, ArrayList<Integer>> s: shelterReport.entrySet()) {
//				record_info.write(s.getKey().getStringAttribute("name") + "\t" + s.getValue().toString() + "\n");
//			}


			this.record_info.close();
		} catch (IOException e){
			e.printStackTrace();
		}
	}

	/** set the seed of the random number generator */
	void seedRandom(long number){
		random = new MersenneTwisterFast(number);
		mySeed = number;
	}
	
	// reset the agent layer's MBR
	public void resetLayers(){
		MBR = waterLayer.getMBR();
		//MBR.init(501370, 521370, 4292000, 4312000);
		this.agentsLayer.setMBR(MBR);
		this.roadLayer.setMBR(MBR);
	}

	
	/**
	 * Convenient method for incrementing the heatmap
	 * @param geom - the geometry of the object that is impacting the heatmap
	 */
	public void incrementHeatmap(Geometry geom){
		Point p = geom.getCentroid();
		
		int x = (int)(heatmap.getGrid().getWidth() * (1 - (MBR.getMaxX() - p.getX())/(MBR.getMaxX() - MBR.getMinX()))), 
				y = (int)(heatmap.getGrid().getHeight()*(MBR.getMaxY() - p.getY())/(MBR.getMaxY() - MBR.getMinY()));
		if(x >= 0 && y >= 0 && x < heatmap.getGrid().getWidth() && y < heatmap.getGrid().getHeight())
			((IntGrid2D) this.heatmap.getGrid()).field[x][y]++;
	}
	
	public Coordinate snapPointToRoadNetwork(Coordinate c) {
		ListEdge myEdge = null;
		double resolution = params.resolution;

		if (networkEdgeLayer.getGeometries().size() == 0)
			return null;

		while (myEdge == null && resolution < Double.MAX_VALUE) {
			myEdge = RoadNetworkUtilities.getClosestEdge(c, resolution, networkEdgeLayer, fa);
			resolution *= 10;
		}
		if (resolution == Double.MAX_VALUE)
			return null;

		LengthIndexedLine closestLine = new LengthIndexedLine(
				(LineString) (((MasonGeometry) myEdge.info).getGeometry()));
		double myIndex = closestLine.indexOf(c);
		return closestLine.extractPoint(myIndex);
	}

	public ArrayList <MasonGeometry> getBuildingsAt(Geometry g){
		Bag result = buildingLayer.getObjectsWithinDistance(g, params.resolution);
		return new ArrayList <MasonGeometry> (result);
	}

	public int getMaxBuildingHeightHere(Geometry g){
		Bag result = buildingLayer.getObjectsWithinDistance(g, params.resolution);
		int maxHeight = -1;
		for(Object o: result) {
			Integer i = ((MasonGeometry) o).getIntegerAttribute("levels");
			if(i != null && i > maxHeight)
				maxHeight = i;
		}
		return maxHeight;
	}

	/**
	 * To run the model without visualization
	 */
	public static void main(String[] args)
    {
		// 29	false	false	RitsurinDemo/TakamatsuTyphoon16.shp	RitsurinDemo/synthPop_Ritsurin.txt	false	false	false	780
		// -Xms6G
		if(args.length < 0){
			System.out.println("usage error");
			System.exit(0);
		}

		long seed = 59;//System.currentTimeMillis();
		
		// set up the seed
		if(args.length > 0)
			seed = Long.parseLong(args[0]);
		
		TakamatsuSim takamatsuModel;
		if(args.length > 1)
			takamatsuModel = new TakamatsuSim(seed, args[1]);
		else
			takamatsuModel = new TakamatsuSim(seed);

		// set up any other specifics accordingly
		Integer timeToRun = 60 * 24;
		boolean tsunamiScenario = false;
		try {
			
/*			boolean ageEnabled = Boolean.parseBoolean(args[1]);
			takamatsuModel.params.ageSpecificSpeeds = ageEnabled;

			String outputPrefix = args[2];
			takamatsuModel.params.outputPrefix = outputPrefix;
			
			String floodData = args[3];
			takamatsuModel.params.floodedFilename = floodData;
			
			String popData = args[4];
			takamatsuModel.params.agentFilename = popData;

			boolean neighbourPolicy = Boolean.parseBoolean(args[5]);
			boolean designatedPersonPolicy = Boolean.parseBoolean(args[6]);
			takamatsuModel.params.evacuationPolicy_neighbours = neighbourPolicy;
			takamatsuModel.params.evacuationPolicy_designatedPerson = designatedPersonPolicy;
			
			tsunamiScenario = Boolean.parseBoolean(args[7]);
			timeToRun = Integer.parseInt(args[8]);
			
			String dirName = args[9];
			takamatsuModel.params.dirName = dirName;
			
			Double sample = Double.parseDouble(args[10]);
			takamatsuModel.params.percIgnored  = sample;
			*/
		} catch (Exception e) {
			System.out.println("WARNING: not all parameters specified. Continuing with run!");
		}
		System.out.println("Loading...");

		takamatsuModel.start();
		if(tsunamiScenario)
				takamatsuModel.resetForTsunamiScenario();

		System.out.println("Running...");

		while(takamatsuModel.schedule.getTime() < timeToRun) {
			takamatsuModel.schedule.step(takamatsuModel);
			if(takamatsuModel.schedule.getTime() % 100 == 0)
				System.out.println("TIME: " + takamatsuModel.schedule.getTime());
		}
		
		takamatsuModel.finish();
		
		System.out.println("...run finished");

		//System.exit(0);
    }


	public void updateRoadUseage(String usedRoad) {
		Integer i = roadUsageRecord.get(usedRoad);
		if(i == null)
			roadUsageRecord.put(usedRoad, 1);
		else
			roadUsageRecord.put(usedRoad, i + 1);
	}
	
	public void resetRoadUsage() {

		for(String road: roadUsageByTimestep.keySet()) {
			
			int numVehiclesPassed = 0;

			// if the road experienced flow this step, record volume
			if(roadUsageRecord.containsKey(road)) {
				numVehiclesPassed = roadUsageRecord.get(road);
				roadUsageRecord.put(road, 0);
			}

			roadUsageByTimestep.get(road).add(numVehiclesPassed);			
		}
	}
	
	public void resetForTsunamiScenario() {
		params.tsunami = true;
		
		// the "shelters" are actually tall buildings
		for(Object o: shelterLayer.getGeometries()) {
			Shelter s = (Shelter) o;
			s.setCapacity(Integer.MAX_VALUE);
			s.setVehicleCapacity(Integer.MAX_VALUE);
		}
		
		params.sheltersFilename = "tsunami/bigBuildings.shp";
		params.floodedFilename = "tsunami/emptyFloodingFile.shp";
	}
	
	public int pullNextHouseholdID() { return this.household_index++; }
}