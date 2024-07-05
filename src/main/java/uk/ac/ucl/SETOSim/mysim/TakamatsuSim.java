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
import java.util.ArrayList;
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
//import sim.field.network.Network;
import sim.io.geo.ShapeFileImporter;
import sim.io.geo.ArcInfoASCGridImporter;
import sim.util.Bag;
import sim.util.geo.AttributeValue;
import sim.util.geo.GeomPlanarGraph;
import sim.util.geo.MasonGeometry;
import sim.util.geo.PointMoveTo;
import uk.ac.ucl.SETOSim.myobjects.*;
import uk.ac.ucl.SETOSim.utilities.*;
import swise.agents.communicator.Communicator;
import swise.agents.communicator.Information;
import swise.disasters.Wildfire;
import swise.objects.NetworkUtilities;
import swise.objects.PopSynth;
import swise.objects.RoadNetworkUtilities;
//import swise.objects.network.GeoNetwork;
import swise.objects.network.GeoNode;
import swise.objects.network.ListEdge;

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

	/////////////// Model Parameters ///////////////////////////////////
	
	// SIZE
	
	private static final long serialVersionUID = 1L;
	public int grid_width = 500;
	public int grid_height = 1000;
	public static double resolution = 1;// the granularity of the simulation (fiddle to merge nodes into one another)

	// TIME
	
	public static double ticks_per_hour = 60; // each tick is 1 minute
	public static double ticks_per_day = ticks_per_hour * 24; // to save time later
	
	// SPEED
	
	public static double speed_pedestrian = 1.5 * 60;
	public static double speed_elderlyYoung = 1 * 60; 
	public static double speed_vehicle = 5.5 * 60; // m per s, ~20kph
	public static double rayleigh_sigma = 2;//8; // from Wang et al, http://dx.doi.org/10.1016/j.trc.2015.11.010

	public HashMap <String, Double> typeWeighting_vehicle;
	public HashMap <String, Double> typeWeighting_pedestrian;


	// POLICIES
	
	public boolean tsunami = false;
	public boolean evacuationPolicy_neighbours = true;
	public boolean evacuationPolicy_designatedPerson = true;
	public static double neighbourDistance = 100; // meters
	public static double hazardThresholdDistance = 100; // meters
	public static int forecastArrivalTime = 720;//1440; // in ticks
	public static int forecastingWidthParam = 720;//1440; // in ticks
	
	
	public double percSample = .9;

	/////////////// Data Sources ///////////////////////////////////////
	
	String dirName = "/Users/swise/Projects/hitomi/data/CanberraDemoData/";//RitsurinDemo/";//
	
	
	
	public static String communicatorFilename = "empty.txt";
	public static String agentFilename = "synthPop_hh_1720106623551.txt";//"defaultRitsurinFiles/synthPop_Ritsurin.txt";//
	//public static String regionalNamesFilename = "defaultRitsurinFiles/regionalNames.shp";
	public String floodedFilename = "selectedWater.shp";//"ACTGOV_WATER_BODY_POLY_893807661266283771/ACTGOV_WATER_BODY_POLY.shp";//"TakamatsuTyphoon16.shp";
	public String waterFilename = "selectedWater.shp";//"ACTGOV_WATER_BODY_POLY_893807661266283771/ACTGOV_WATER_BODY_POLY.shp";//"defaultRitsurinFiles/TakamatsuWaterAll.shp";
	public String sheltersFilename = "sheltersByHandWithEntrances.shp";//"ACTGOV_Shelter_Assets_4461799249747129901/ACTGOV_Shelter_Assets.shp";//"defaultRitsurinFiles/sheltersUnion.shp";
	public String buildingsFilename = "uglyHouses.shp";//"defaultRitsurinFiles/Ritsurin.shp";
	public String roadsFilename = "ACTGOV_ROAD_CENTRELINES_-8699904174011627171/ACTGOV_ROAD_CENTRELINES.shp";//"defaultRitsurinFiles/RitsurinRoads.shp";
	
	public String weightedRoadAttribute = "HIERARCHY";//"highway";
	
/*	String record_speeds_filename = "output/speeds", 
			record_sentiment_filename = "output/sentiment",
			record_heatmap_filename = "output/heatmap",
			record_info_filename = "output/bifurc_info";
*/
	// EXPORTS
	
	BufferedWriter record_speeds, record_sentiment, record_heatmap;
	public BufferedWriter record_info;
	
	public String outputPrefix = null;
	
	//// END Data Sources ////////////////////////
	
	/////////////// Containers ///////////////////////////////////////

	public GeomVectorField waterLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField floodedLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField roadLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField buildingLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField agentsLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField householdsLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField shelterLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField namesLayer = new GeomVectorField(grid_width, grid_height);
	
	public GeomVectorField networkLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField networkEdgeLayer = new GeomVectorField(grid_width, grid_height);	
	public GeomVectorField majorRoadNodesLayer = new GeomVectorField(grid_width, grid_height);
/*	public GeomVectorField evacuationAreas = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField fireLayer = new GeomVectorField(grid_width, grid_height);
	public ArrayList <GeomVectorField> firePoints = new ArrayList <GeomVectorField>();
	*/

	public GeomGridField heatmap = new GeomGridField();
	public HashMap <String, Integer> roadUsageRecord = new HashMap <String, Integer> ();

	public GeomVectorField hi_roadLayer = new GeomVectorField(grid_width, grid_height);


	/////////////// End Containers ///////////////////////////////////////

	/////////////// Objects //////////////////////////////////////////////

	
	public AStar pathfinder;
	
	public Bag roadNodes = new Bag();
	public Network roads = new Network(false);
	HashMap <MasonGeometry, ArrayList <GeoNode>> localNodes;
	public Bag terminus_points = new Bag();

	public ArrayList <Person> agents = new ArrayList <Person> ();
	public Network agentSocialNetwork = new Network();
	
	public GeometryFactory fa = new GeometryFactory();
	Envelope MBR = null;
	
	public int numEvacuated = 0;
	public ArrayList <Integer> numEvacuatedOverTime = new ArrayList <Integer> ();
	
	public int numAttemptingEvac = 0;
	public ArrayList <Integer> numAttemptedEvacsOverTime = new ArrayList <Integer> ();
	
	public int numAssisting = 0;
	public ArrayList <Integer> numAssistingOverTime = new ArrayList <Integer> ();
	
	
	public int shelterReportCounter = -1;
	public HashMap <Shelter, ArrayList <Integer>> shelterReport = new HashMap <Shelter, ArrayList <Integer>> ();
	
	public TakamatsuBehaviour behaviourFramework;
	
	/////////////// END Objects //////////////////////////////////////////

	/////////////// Parameters ///////////////////////////////////////////
	
	long mySeed = 0;
	boolean exportHeatmap = false; // export the heatmap or no?
	boolean verbose = false;
	public boolean ageSpecificSpeeds = true;
	public double likelihoodOfOwningVehicle = .6;
	
	/////////////// END Parameters ///////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////
	/////////////////////////// BEGIN functions ///////////////////////////////
	///////////////////////////////////////////////////////////////////////////	
	
	/**
	 * Default constructor function
	 * @param seed
	 */
	public TakamatsuSim(long seed) {
		super(seed);
//		random = new MersenneTwisterFast(12345);
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
		
			InputCleaning.readInVectorLayer(waterLayer, dirName + waterFilename, "water", new Bag());
			InputCleaning.readInVectorLayer(floodedLayer, dirName + floodedFilename, "flooding", new Bag());
			
			InputCleaning.readInVectorLayer(buildingLayer, dirName + buildingsFilename, "buildings", new Bag());
			InputCleaning.readInVectorLayer(roadLayer, dirName + roadsFilename, "road network", new Bag());
			
			
			GeomVectorField shelterRaw = new GeomVectorField(grid_width, grid_height);
			Bag shelterAtts = new Bag();
			shelterAtts.add("parkingNum"); shelterAtts.add("entranceX"); shelterAtts.add("entranceY");
			InputCleaning.readInVectorLayer(shelterRaw, dirName + sheltersFilename, "shelters", shelterAtts);
			
			// if this hasn't been set, set it!
			if(this.outputPrefix == null)
				this.outputPrefix = dirName;
			
			//////////////////////////////////////////////
			////////////////// CLEANUP ///////////////////
			//////////////////////////////////////////////

			// standardize the MBRs so that the visualization lines up
			
			MBR = buildingLayer.getMBR();
			//MBR.init(501370, 521370, 4292000, 4312000);

			this.grid_width = buildingLayer.fieldWidth;
			this.grid_height = buildingLayer.fieldHeight;

			//evacuationAreas.setMBR(MBR);
			
			heatmap = new GeomGridField();
			heatmap.setMBR(MBR);
			heatmap.setGrid(new IntGrid2D((int)(MBR.getWidth() / 10), (int)(MBR.getHeight() / 10), 0));

			
			// clean up the road network
			
			System.out.print("Cleaning the road network...");

			//Object myDummyObj = NetworkUtilities.class.getResource("NetworkUtilities.class");
			roads = NetworkUtilities.multipartNetworkCleanup(roadLayer, roadNodes, resolution, fa, random, 0);
			roadNodes = roads.getAllNodes();
			NetworkUtilities.testNetworkForIssues(roads);
			
			// set up roads as being "open" and assemble the list of potential terminii
			roadLayer = new GeomVectorField(grid_width, grid_height);
			for(Object o: roadNodes){
				GeoNode n = (GeoNode) o;
				networkLayer.addGeometry(n);
				
				boolean potential_terminus = false;
				
				// check all roads out of the nodes
				for(Object ed: roads.getEdgesOut(n)){
					
					// set it as being (initially, at least) "open"
					ListEdge edge = (ListEdge) ed;
					((MasonGeometry)edge.info).addStringAttribute("open", "OPEN");
					double myLength = ((MasonGeometry)edge.info).geometry.getLength();
					((MasonGeometry)edge.info).addDoubleAttribute("length", myLength);
					
					networkEdgeLayer.addGeometry( (MasonGeometry) edge.info);
					roadLayer.addGeometry((MasonGeometry) edge.info);
					((MasonGeometry)edge.info).addAttribute("ListEdge", edge);
					
					String type = ((MasonGeometry)edge.info).getStringAttribute("HIERARCHY");//"highway");
					if(type.equals("motorway") || type.equals("primary") || type.equals("trunk"))
						potential_terminus = true;
				}
				
				// check to see if it's a terminus
				if(potential_terminus && !MBR.contains(n.geometry.getCoordinate()) && roads.getEdges(n, null).size() == 1){
					terminus_points.add(n);
				}

			}

			
			typeWeighting_vehicle = new HashMap <String, Double> ();
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
				typeWeighting_vehicle.put(myType, .5);
			
			
			typeWeighting_pedestrian = new HashMap <String, Double> ();
			typeWeighting_pedestrian.put("cycleway", 10000.);
			typeWeighting_pedestrian.put("HIGHWAYS", 10000.);
			
			// add shelter entrance info
			
			for(Object o: shelterRaw.getGeometries()){
				MasonGeometry shelter = (MasonGeometry)o;
				int numParkingSpaces = Integer.MAX_VALUE;
				if(shelter.hasAttribute("parkingNum")) numParkingSpaces = (int) shelter.getIntegerAttribute("parkingNum");
				Shelter myShelter = new Shelter(shelter, numParkingSpaces, this);
				shelterLayer.addGeometry(myShelter);
			}
			
			System.out.print(buildingLayer.MBR.toString());
			/////////////////////
			///////// Clean up roads for Persons to use ///////////
			/////////////////////
			
			/*
			Network majorRoads = RoadNetworkUtilities.extractMajorRoads(roads);
			RoadNetworkUtilities.testNetworkForIssues(majorRoads);

			
			// assemble list of secondary versus local roads
			ArrayList <Edge> myEdges = new ArrayList <Edge> ();
			GeomVectorField secondaryRoadsLayer = new GeomVectorField(grid_width, grid_height);
			GeomVectorField localRoadsLayer = new GeomVectorField(grid_width, grid_height);
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
			*/

			System.gc();
			
			pathfinder = new AStar();
						
			/////////////////////
			///////// Set up Persons ///////////
			/////////////////////
		
			// first set up BehaviourFramework
			behaviourFramework = new TakamatsuBehaviour(this);
			
			agents.addAll(PersonUtilities.setupHouseholdsFromFile(dirName + agentFilename,
					agentsLayer, householdsLayer, this));
			// TODO establish meaningful workplaces!!!
			
			//agents.addAll(PersonUtilities.setupHouseholdsAtRandom(networkLayer, schedule, this, fa));
			int numRoadNodes = roadNodes.size();
			int numPeople = agents.size();
			for(Person p: agents){
				
				// throwing this in here to make sure they are moving correctly
				Coordinate workC = 
						((GeoNode)roadNodes.get(random.nextInt(numRoadNodes))).geometry.getCoordinate();
				p.setWorkLocation(workC);
				
				// if designating dependents for evacuation, do so!
				if(evacuationPolicy_designatedPerson && p.dependent == null) {
					
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
			}

			//InputCleaning.readInVectorLayer(namesLayer, dirName + regionalNamesFilename, "name", new Bag());


			// reset MBRS in case it got messed up during all the manipulation
			waterLayer.setMBR(MBR);
			buildingLayer.setMBR(MBR);
			roadLayer.setMBR(MBR);			
			networkLayer.setMBR(MBR);
			networkEdgeLayer.setMBR(MBR);
			majorRoadNodesLayer.setMBR(MBR);
			agentsLayer.setMBR(MBR);
			shelterLayer.setMBR(MBR);
			heatmap.setMBR(MBR);
			//namesLayer.setMBR(MBR);
			
			System.out.println("done");

			
			//////////////////////////////////////////////
			////////////////// AGENTS ///////////////////
			//////////////////////////////////////////////

/*			// for each of the Persons, set up relevant, environment-specific information
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
/*			
			// schedule the road network to update as the wildfire moves
			this.schedule.scheduleRepeating(new Steppable(){
				private static final long serialVersionUID = 1L;

				@Override
				public void step(SimState state) {

					// check to see if any roads have been overtaken by the wildfire: if so, remove them from the network
					badRoads = new ArrayList <ListEdge> ();
					Bag overlappers = networkEdgeLayer.getObjectsWithinDistance(wildfire.extent, resolution);
					for(Object o: overlappers){
						ListEdge aBadRoad = (ListEdge) ((AttributeValue) ((MasonGeometry) o).getAttribute("ListEdge")).getValue();
						badRoads.add( aBadRoad);
					}

					// close the closed roads
					for(ListEdge r: badRoads){
						((MasonGeometry)r.info).addStringAttribute("open", "CLOSED");
					}
				}
				
			}, 10, 12);

*/			
			// set up the evacuation orders to be inserted into the social media environment
//			setupCommunicators(dirName + communicatorFilename);

			// first, make sure all Households in the immediate area know that they are in the area
			HashSet <Household> householdsImpacted = new HashSet <Household> ();
			for(Object o: waterLayer.getGeometries()){
				MasonGeometry mg = (MasonGeometry) o;
				Bag b = householdsLayer.getObjectsWithinDistance(mg, hazardThresholdDistance);
				householdsImpacted.addAll(b);
			}

			for(Household h: householdsImpacted)
				h.setInHazardZone(true);

			
			// SCHEDULE FLOOD
			
			Steppable floodScheduler = new Steppable(){

				@Override
				public void step(SimState arg0) {
					//waterLayer.clear();// = new GeomVectorField(grid_width, grid_height);
					HashSet <Household> householdsImpacted = new HashSet <Household> ();
					for(Object o: floodedLayer.getGeometries()){
						MasonGeometry mg = (MasonGeometry) o;
						waterLayer.addGeometry(mg);
						Bag b = householdsLayer.getObjectsWithinDistance(mg, hazardThresholdDistance);
						householdsImpacted.addAll(b);
					}
					
					for(Household h: householdsImpacted)
						h.setInHazardZone(true);
					
					waterLayer.setMBR(MBR);
					waterLayer.updateSpatialIndex();
				}
				
			};
			schedule.scheduleOnce(forecastArrivalTime, floodScheduler);

			// SCHEDULE SHELTERS
			
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
			this.schedule.scheduleRepeating(forecastArrivalTime, 1, shelterReporter, 10);
			
			
		} catch (Exception e) { e.printStackTrace();}
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
			if(this.exportHeatmap) {
				
				String heatmapFilename = outputPrefix + this.seed() + "_heatmap.txt";
				record_heatmap = new BufferedWriter(new FileWriter(heatmapFilename));
				System.out.println(heatmapFilename);
				IntGrid2D myHeatmap = ((IntGrid2D) this.heatmap.getGrid());

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
				for(String s: roadUsageRecord.keySet()){
					record_heatmap.write(s + "\t" + roadUsageRecord.get(s) + "\n");
				}
				record_heatmap.close();
			}


			// print a record out
			System.out.println(this.mySeed + "\t" + this.numEvacuated);
			
			// SAVE ALL AGENT INFO
			String myOutFile = outputPrefix + this.seed() + ".txt";
			System.out.println("writing out to " + myOutFile);
			record_info = new BufferedWriter(new FileWriter(myOutFile));

			double worldTime = schedule.getTime();
			
			record_info.write(numEvacuatedOverTime.toString() + "\n");
			record_info.write(this.numAttemptedEvacsOverTime.toString() + "\n");
			record_info.write(this.numAssistingOverTime.toString());

/*			record_info.write("ID\tage\tstatus\tevacuatingTime\tflooded\tx_home\ty_home\tx_loc\ty_loc\tdependent\tdependentOf\tturnedAway\n");
			for(Person a: agents){

				if(a.getEvacuatingTime() < 0) // don't export info about those who don't evacuate!
				{
					if(! a.getHousehold().inHazardZone()) // if they're safe, don't export them; otherwise DO include them!
						continue;					
				}
				
				String myID = a.getMyID();

				String status = a.getActivityNode().getTitle();
				
//				Bag bagOWater= this.waterLayer.getObjectsWithinDistance(a.getHousehold(), this.resolution);
//				String inWater = "notSubmerged";
//				if(bagOWater.size() > 0)
//					inWater = "inWater";
//
				String inWater = "notSubmerged";
				if(a.getHousehold().inHazardZone())
					inWater = "inZone";

				Coordinate homeCoord = a.getHousehold().getHome();
				Coordinate locCoord = a.geometry.getCoordinate();
				String dependent = "<none>";
				if(a.dependent != null)
					dependent = a.dependent.getMyID();
				String dependentOf = "<none>";
				if(a.dependentOf != null)
					dependentOf = a.dependentOf.getMyID();
				
				double myTime = a.getEvacuatingTime();
				if(!a.getActivityNode().isEndpoint()) {
					myTime = worldTime - a.getEvacuatingTime();
				}
				System.out.print(myTime + "\t");
				
				record_info.write(myID + "\t" +  a.getAge() + "\t" + status + "\t" + a.getEvacuatingTime() + "\t"
						+ inWater + "\t" + homeCoord.x + "\t" + homeCoord.y + 
						"\t" + locCoord.x + "\t" + locCoord.y + "\t" + dependent + "\t" + dependentOf + "\t" + a.turnedAwayFromShelterCount
						+ "\n");//a.getHistory() + "\n");
				
				
			}
*/
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
		double resolution = this.resolution;

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

	/**
	 * To run the model without visualization
	 */
	public static void main(String[] args)
    {
		
		if(args.length < 0){
			System.out.println("usage error");
			System.exit(0);
		}

		long seed = System.currentTimeMillis();
		
		// set up the seed
		if(args.length > 0)
			seed = Long.parseLong(args[0]);

		TakamatsuSim takamatsuModel = new TakamatsuSim(seed);

		// set up any other specifics accordingly
		boolean tsunamiScenario = false;
		Integer timeToRun = 60 * 24;
		try {
			
			boolean ageEnabled = Boolean.parseBoolean(args[1]);
			takamatsuModel.ageSpecificSpeeds = ageEnabled;

			String outputPrefix = args[2];
			takamatsuModel.outputPrefix = outputPrefix;
			
			String floodData = args[3];
			takamatsuModel.floodedFilename = floodData;
			
			String popData = args[4];
			takamatsuModel.agentFilename = popData;

			boolean neighbourPolicy = Boolean.parseBoolean(args[5]);
			boolean designatedPersonPolicy = Boolean.parseBoolean(args[6]);
			takamatsuModel.evacuationPolicy_neighbours = neighbourPolicy;
			takamatsuModel.evacuationPolicy_designatedPerson = designatedPersonPolicy;
			
			tsunamiScenario = Boolean.parseBoolean(args[7]);
			timeToRun = Integer.parseInt(args[8]);
			
			String dirName = args[9];
			takamatsuModel.dirName = dirName;
			
			Double sample = Double.parseDouble(args[10]);
			takamatsuModel.percSample  = sample;
		} catch (Exception e) {
			System.out.println("WARNING: not all parameters specified. Continuing with run!");
		}
		System.out.println("Loading...");

		takamatsuModel.start();
		if(tsunamiScenario)
				takamatsuModel.resetForTsunamiScenario();

		System.out.println("Running...");

		while(takamatsuModel.schedule.getTime() < timeToRun) {// * 3){ // ONLY 3 DAYS
			takamatsuModel.schedule.step(takamatsuModel);
			//System.out.println(takamatsuModel.schedule.getTime());
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
	
	public void resetForTsunamiScenario() {
		this.tsunami = true;
		
		// the "shelters" are actually tall buildings
		for(Object o: shelterLayer.getGeometries()) {
			Shelter s = (Shelter) o;
			s.setCapacity(Integer.MAX_VALUE);
			s.setVehicleCapacity(Integer.MAX_VALUE);
		}
		
		this.sheltersFilename = "tsunami/bigBuildings.shp";
		this.floodedFilename = "tsunami/emptyFloodingFile.shp";
	}
}