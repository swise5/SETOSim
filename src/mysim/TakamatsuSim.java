package mysim;

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
import sim.io.geo.ShapeFileImporter;
import sim.io.geo.ArcInfoASCGridImporter;
import sim.util.Bag;
import sim.util.geo.AttributeValue;
import sim.util.geo.GeomPlanarGraph;
import sim.util.geo.MasonGeometry;
import sim.util.geo.PointMoveTo;
import swise.agents.communicator.Communicator;
import swise.agents.communicator.Information;
import swise.disasters.Wildfire;
import swise.objects.AStar;
import swise.objects.NetworkUtilities;
import swise.objects.PopSynth;
import swise.objects.network.GeoNode;
import swise.objects.network.ListEdge;
import utilities.InputCleaning;
import utilities.PersonUtilities;
import utilities.RoadNetworkUtilities;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateSequenceFilter;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.linearref.LengthIndexedLine;

import ec.util.MersenneTwisterFast;
import myobjects.Person;
import myobjects.Shelter;

/**
 * TakamatsuSim is the core of a simulation which projects the behavior of agents in the aftermath
 * of an incident.
 * 
 * @author swise and Hitomi Nakanishi
 *
 */
public class TakamatsuSim extends SimState {

	/////////////// Model Parameters ///////////////////////////////////
	
	private static final long serialVersionUID = 1L;
	public int grid_width = 800;
	public int grid_height = 600;
	public static double resolution = 1;// the granularity of the simulation 
				// (fiddle around with this to merge nodes into one another)

/*	double communication_success_prob = -1;
	double contact_success_prob = -1;
	double tweet_prob = -1;
	double retweet_prob = -1;
	double comfortDistance = -1;
	double observationDistance = -1;
	double decayParam = -1;
*/	public static double speed_pedestrian = 1.5 * 60;
	public static double speed_vehicle = 4 * 60;

	
	/////////////// Data Sources ///////////////////////////////////////
	
	String dirName = "/Users/swise/Projects/hitomi/data/";//"/Users/swise/Google Drive/GTD-projects/ROR/Hitomi_Nakanishi/data/";
	
	public static String communicatorFilename = "communicatorEvents.txt";
	public static String agentFilename = "synthPopulationHOUSEHOLD.txt";

	String record_speeds_filename = "speeds/speeds", 
			record_sentiment_filename = "sentiments/sentiment",
			record_heatmap_filename = "heatmaps/heatmap",
			record_info_filename = "infos/info";

	BufferedWriter record_speeds, record_sentiment, record_heatmap;
	public BufferedWriter record_info;

	//// END Data Sources ////////////////////////
	
	/////////////// Containers ///////////////////////////////////////

	public GeomVectorField baseLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField roadLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField buildingLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField agentsLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField shelterLayer = new GeomVectorField(grid_width, grid_height);
	
	public GeomVectorField networkLayer = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField networkEdgeLayer = new GeomVectorField(grid_width, grid_height);	
	public GeomVectorField majorRoadNodesLayer = new GeomVectorField(grid_width, grid_height);
/*	public GeomVectorField evacuationAreas = new GeomVectorField(grid_width, grid_height);
	public GeomVectorField fireLayer = new GeomVectorField(grid_width, grid_height);
	public ArrayList <GeomVectorField> firePoints = new ArrayList <GeomVectorField>();
	*/

	ArrayList <ListEdge> badRoads = null;
	
	public GeomGridField heatmap = new GeomGridField();
	public HashMap <String, Integer> roadUsageRecord = new HashMap <String, Integer> ();

	public GeomVectorField hi_roadLayer = new GeomVectorField(grid_width, grid_height);
	public Network hiNetwork = new Network();

	/////////////// End Containers ///////////////////////////////////////

	/////////////// Objects //////////////////////////////////////////////

	
	public Bag roadNodes = new Bag();
	public Network roads = new Network(false);
	HashMap <MasonGeometry, ArrayList <GeoNode>> localNodes;
	public Bag terminus_points = new Bag();

	MediaInstance media = new MediaInstance();
	public ArrayList <Person> agents = new ArrayList <Person> (200000);
	public Network agentSocialNetwork = new Network();
	
	public GeometryFactory fa = new GeometryFactory();
	
	long mySeed = 0;
	
	Envelope MBR = null;
	
	boolean verbose = false;
	
	public int numEvacuated = 0;
	public int numDied = 0;
	
	/////////////// END Objects //////////////////////////////////////////

	///////////////////////////////////////////////////////////////////////////
	/////////////////////////// BEGIN functions ///////////////////////////////
	///////////////////////////////////////////////////////////////////////////	
	
	/**
	 * Default constructor function
	 * @param seed
	 */
	public TakamatsuSim(long seed) {
		super(seed);
		random = new MersenneTwisterFast(12345);
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
		
			InputCleaning.readInVectorLayer(baseLayer, dirName + "water.shp", "water", new Bag());
			InputCleaning.readInVectorLayer(buildingLayer, dirName + "centralBuildings/buildings.shp", "buildings", new Bag());
			InputCleaning.readInVectorLayer(roadLayer, dirName + "roadsCleanSubset.shp", "road network", new Bag());
			
			GeomVectorField shelterRaw = new GeomVectorField(grid_width, grid_height);
			Bag shelterAtts = new Bag();
			shelterAtts.add("parkingNum"); shelterAtts.add("entranceX"); shelterAtts.add("entranceY");
			InputCleaning.readInVectorLayer(shelterRaw, dirName + "shelters.shp", "shelters", shelterAtts);
			
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
			
			roads = NetworkUtilities.multipartNetworkCleanup(roadLayer, roadNodes, resolution, fa, random, 0);
			roadNodes = roads.getAllNodes();
			RoadNetworkUtilities.testNetworkForIssues(roads);
			
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
					networkEdgeLayer.addGeometry( (MasonGeometry) edge.info);
					roadLayer.addGeometry((MasonGeometry) edge.info);
					((MasonGeometry)edge.info).addAttribute("ListEdge", edge);
					
					String type = ((MasonGeometry)edge.info).getStringAttribute("highway");
					if(type.equals("motorway") || type.equals("primary") || type.equals("trunk"))
						potential_terminus = true;
				}
				
				// check to see if it's a terminus
				if(potential_terminus && !MBR.contains(n.geometry.getCoordinate()) && roads.getEdges(n, null).size() == 1){
					terminus_points.add(n);
				}

			}

			// add shelter entrance info
			
			for(Object o: shelterRaw.getGeometries()){
				MasonGeometry shelter = (MasonGeometry)o;
				Shelter myShelter = new Shelter(shelter, (int) shelter.getIntegerAttribute("parkingNum"), this);
				shelterLayer.addGeometry(myShelter);
			}
			
			/////////////////////
			///////// Clean up roads for Persons to use ///////////
			/////////////////////
						
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

			System.gc();
			
			agents.addAll(PersonUtilities.setupHouseholdsAtRandom(networkLayer, schedule, this, fa));
			for(Person p: agents){
				agentsLayer.addGeometry(p);
			}


			// reset MBRS in case it got messed up during all the manipulation
			baseLayer.setMBR(MBR);
			buildingLayer.setMBR(MBR);
			roadLayer.setMBR(MBR);			
			networkLayer.setMBR(MBR);
			networkEdgeLayer.setMBR(MBR);
			majorRoadNodesLayer.setMBR(MBR);
			agentsLayer.setMBR(MBR);
			shelterLayer.setMBR(MBR);
			heatmap.setMBR(MBR);
			
			System.out.println("done");

			
			//////////////////////////////////////////////
			////////////////// AGENTS ///////////////////
			//////////////////////////////////////////////

			// set up the agents in the simulation
/*			setupPersonsFromFile(dirName + agentFilename);
			agentsLayer.setMBR(MBR);
			
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
		
			// seed the simulation randomly
			seedRandom(System.currentTimeMillis());

			// schedule the reporter to run
			setupReporter();

		} catch (Exception e) { e.printStackTrace();}
    }
	
	/**
	 * Schedule the regular 
	 */
	public void setupReporter() {

/*		// set up the reporting files
		try {
			String mySettings = communication_success_prob + "_" + contact_success_prob + "_" 
					+ tweet_prob + "_" + retweet_prob + "_" + comfortDistance + "_" + 
					observationDistance + "_" + decayParam + "_" + speed + "_";

			record_sentiment = new BufferedWriter(new FileWriter(dirName
					+ record_sentiment_filename + mySettings + mySeed + ".txt"));
			record_speeds = new BufferedWriter(new FileWriter(dirName
					+ record_speeds_filename + mySettings + mySeed + ".txt"));

			record_info = new BufferedWriter(new FileWriter(dirName
					+ record_info_filename + mySettings + mySeed + ".txt"));
		} catch (IOException e) {
			e.printStackTrace();
		}

		// schedule the simulation to report on Persons every tick
		this.schedule.scheduleRepeating(0, 10000, new Steppable() {

			DecimalFormat formatter = new DecimalFormat("#.##");

			@Override
			synchronized public void step(SimState state) {
				try {
					int time = (int) state.schedule.getTime();

					String speeds = time + "", sentiments = "";
					int numSentPersons = 0;
					for (Person a : agents) {
						if (a.getActivity() == Person.activity_evacuate || a.getActivity() == Person.activity_travel)
							speeds += "\t" + Math.max(0, a.myLastSpeed);
						if (a.getValence() > 0) {
							sentiments += "\t" + formatter.format(a.getValence());
							numSentPersons++;
						}
					}
					record_sentiment.write(time + "\t" + numSentPersons + sentiments + "\n");
					record_speeds.write(speeds + "\n");

					record_sentiment.flush();
					record_speeds.flush();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}, 12);
		*/
	}
	

	/**
	 * Finish the simulation and clean up
	 */
	public void finish(){
		super.finish();
		for(Person p: agents){
			System.out.print(p.getEvacuatingTime() + "\t");
		}
		try{
			
			// create part of the title to record all the paramters used in this simulation
		//	String mySettings = communication_success_prob + "_" + contact_success_prob + "_" + tweet_prob + "_" + 
		//			retweet_prob + "_" + comfortDistance + "_" + observationDistance + "_" + decayParam + "_" + speed + "_";
			String mySettings = "dummy";

			// SAVE THE HEATMAP
			record_heatmap = new BufferedWriter(new FileWriter(dirName + record_heatmap_filename + mySettings + mySeed + ".txt"));
			IntGrid2D myHeatmap = ((IntGrid2D) this.heatmap.getGrid());

			// write a header
			record_heatmap.write(myHeatmap.getWidth() + "\t" + myHeatmap.getHeight() + "\t" + (int)schedule.getTime() + "\n");
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

			// print a record out
			System.out.println(this.mySeed + "\t" + this.numDied + "\t" + this.numEvacuated);
			
			// SAVE ALL AGENT INFO
			record_info = new BufferedWriter(new FileWriter(dirName + record_info_filename + mySettings + mySeed + ".txt"));

			for(Person a: agents){
				String myID = a.getMyID();
/*				for(Object o: a.knowledge.keySet()){
					Information i = a.knowledge.get(o);
					Object source = i.getSource();
					String sourceStr;
					if(source instanceof Person)
						sourceStr = ((Person)source).toString();
					else if(source == null)
						sourceStr = "null";
					else
						sourceStr = source.toString();
					
					try {
						record_info.write(myID + "\t" + sourceStr + "\t" + i.getTime() + "\t" + o.toString() + "\n");
					} catch (IOException e) {e.printStackTrace();}
				}
*/
				record_info.write(myID + "\t" + a.getEvacuatingTime() + "\t" + "\n");
			}

			this.record_info.close();

			for(Object o: this.roadLayer.getGeometries()){
				MasonGeometry mg = (MasonGeometry) o;
				String blahh = "2";
			}
		} catch (IOException e){
			e.printStackTrace();
		}
	}
	

	
	/**
	 * Set up the evacuation orders from the given file
	 * @param filename
	 */
/*	public void setupCommunicators(String filename){
		try {
			
			// Open the communicators file
			FileInputStream fstream = new FileInputStream(filename);
			
			// Convert our input stream to a BufferedReader
			BufferedReader communicatorData = new BufferedReader(new InputStreamReader(fstream));
			String s;
			
			while ((s = communicatorData.readLine()) != null) {
				String[] bits = s.split("\t");
				
				int time = Integer.parseInt(bits[0]);
				
				// create the evacuation orders as appropriate
				if(bits[1].equals("EvacuationOrder")){
					Geometry evacZone = null;
					for(Object o: evacuationAreas.getGeometries()){
						if(((MasonGeometry)o).getStringAttribute("zone").equals(bits[2])){
							evacZone = ((MasonGeometry)o).geometry;
							break;
						}
					}
					
					// if the area has a proper evacuation zone, store that in the media object
					if(evacZone != null)
						media.learnAbout(null, new EvacuationOrder(evacZone, time, null));;
				}
					
			}
			
			// schedule the media object to push out the information when appropriate
			if(media.storage.size() > 0)
				schedule.scheduleOnce(media.storage.get(0).getTime(), media);
			
			// clean up
			communicatorData.close();
			
		} catch (Exception e) {
			System.err.println("File input error: " + filename);
		}

	}
*/	
	/**
	 * Media object which pushes information out into the social media network
	 * upon a pre-appointed timetable
	 * @author swise
	 *
	 */
	public class MediaInstance implements Communicator, Steppable {

		ArrayList <Information> storage = new ArrayList <Information> ();
		ArrayList <Information> socialMediaPosts = new ArrayList <Information> ();

		@Override
		public ArrayList getInformationSince(double time) {
			ArrayList <Object> result = new ArrayList <Object> ();
			for(Object o: socialMediaPosts){
				long myTime = ((Information)o).getTime();
				if(myTime >= time)
					result.add(o);
			}
			return result;
		}

		@Override
		public void learnAbout(Object o, Information i) {
			storage.add(i);			
		}

		@Override
		public void step(SimState state) {
			Information i = storage.get(0);
			if(i.getTime() <= state.schedule.getTime()){
				socialMediaPosts.add(i);
				storage.remove(0);
			}
			if(storage.size() > 0)
				schedule.scheduleOnce(storage.get(0).getTime(), this);
		}
		
	}
	


	


	/**
	 * RoadClosure structure holds information about a road closure
	 */
	public class RoadClosure extends Information {
		public RoadClosure(Object o, long time, Object source) {
			super(o, time, source, 5);
		}
	}
	
	/**
	 * EvacuationOrder structure holds information about an evacuation order
	 */
	public class EvacuationOrder extends Information {
		public Geometry extent = null;
		public EvacuationOrder(Object o, long time, Object source) {
			super(o, time, source, 8);
			extent = (Geometry) o;
		}		
	}
	
	/** set the seed of the random number generator */
	void seedRandom(long number){
		random = new MersenneTwisterFast(number);
		mySeed = number;
	}
	
	// reset the agent layer's MBR
	public void resetLayers(){
		MBR = baseLayer.getMBR();
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
		
		TakamatsuSim hspot = new TakamatsuSim(System.currentTimeMillis());
		
/*		hspot.communication_success_prob = Double.parseDouble(args[0]);
		hspot.contact_success_prob = Double.parseDouble(args[1]);
		hspot.tweet_prob = Double.parseDouble(args[2]);
		hspot.retweet_prob = Double.parseDouble(args[3]);
		hspot.comfortDistance = Double.parseDouble(args[4]);
		hspot.observationDistance = Double.parseDouble(args[5]);
		hspot.decayParam = Double.parseDouble(args[6]);
		hspot.speed = Double.parseDouble(args[7]);
*/		
		System.out.println("Loading...");

		hspot.start();

		System.out.println("Running...");

		for(int i = 0; i < 288 * 3; i++){
			hspot.schedule.step(hspot);
		}
		
		hspot.finish();
		
		System.out.println("...run finished");

		System.exit(0);
    }


	public void updateRoadUseage(String usedRoad) {
		Integer i = roadUsageRecord.get(usedRoad);
		if(i == null)
			roadUsageRecord.put(usedRoad, 1);
		else
			roadUsageRecord.put(usedRoad, i + 1);
	}
}