package uk.ac.ucl.SETOSim.utilities;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import sim.util.distribution.*;
import sim.field.geo.GeomVectorField;
import sim.field.grid.IntGrid2D;
import sim.field.network.Network;
import sim.io.geo.ShapeFileExporter;
import sim.io.geo.ShapeFileImporter;
import sim.util.Bag;
import sim.util.geo.MasonGeometry;
import swise.objects.NetworkUtilities;
import swise.objects.network.GeoNode;
import swise.objects.network.ListEdge;
import uk.ac.ucl.SETOSim.myobjects.Shelter;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.linearref.LengthIndexedLine;

import ec.util.MersenneTwisterFast;

/**
 * Population Synthesis Method
 * 
 * @author swise
 *
 * based on Japanese Census Data, see https://www.stat.go.jp/english/data/index.html for more context

 */
public class PopulationSynthesis {
	
	String dirName = "/Users/wendi/eclipse-workspace/takamatsu/data/canberraDemo/";//"/Users/swise/Projects/hitomi/data/CanberraDemoData/";

	String demoFilename = "elderDemo/TakamatsuEstimated10YearDemo.tsv";//"TakamatsuDemoBasic.tsv";
	String roadsFilename = "bushfireWodenRoads.shp";//"ACTGOV_ROAD_CENTRELINES_-8699904174011627171/ACTGOV_ROAD_CENTRELINES.shp";
	String buildingsFilename = "";

	String householdsFilename = "KagawaHouseholdsBasic.tsv";

	int targetNumIndividualsToGenerate = 24630;//466000;//140000;//427942; // TODO should ideally/potentially be reading from file!!!! 
	int targetNumHouseholdsToGenerate =  9945;//168000;//68700;//197030 ;

	
	Network roadNetwork;
	MersenneTwisterFast random;

	GeometryFactory gf = new GeometryFactory();
	
	public static double resolution = 5;// // the granularity of the simulation
	public static double distanceToRoads = 15; // m, based on mucking around wiht it
	
	public static double numYearsPerBin = 100;//5.;
	public static int maxAge = 100;

	public static int familyWeight = 10;
	public static int friendWeight = 5;
	public static int acquaintenceWeight = 1;
	public static int maxNumCoworkers = 35;
	
	/**
	 * A structure to hold information about the generated individuals
	 */
	class Agent {//extends MasonGeometry {
		
		int age;
		int sex;
		MasonGeometry home;
		Point work;
		HashMap <Agent, Integer> socialTies; // range: 0-10 with 10 being strongest
		long myID;
		//ArrayList <Agent> socialMediaTies;
		
		
		
		public Agent(int a, int s){
			age = a;
			sex = s;
			socialTies = new HashMap <Agent, Integer> ();
			//socialMediaTies = null; // set to null until we decide it's active
	//		this.addStringAttribute("ID", "" + random.nextLong()); // HIGHLY UNLIKELY to collide
			myID = random.nextLong();
		}
		
		public void addContact(Agent a, int weight){
			if(socialTies.containsKey(a))
				socialTies.put(a, weight + socialTies.get(a));
			else
				socialTies.put(a, weight);
		}
		
/*		public void addMediaContact(Agent a){
			if(socialMediaTies == null)
				socialMediaTies = new ArrayList <Agent> ();
			socialMediaTies.add(a);
		}*/
		
		/**
		 * Set simple social distance to another agent based on age, sex, and distance between homes
		 * @param a
		 * @return
		 */
		public double getSocialDistance(Agent a){
			double similarity = 0;
			if(a.sex != sex) similarity += 2;
			double ageDiff = 10 * (a.age - age)/18; 
			if(ageDiff > 0)
				similarity += ageDiff;
			else
				similarity -= ageDiff;
			
			if(home != null){
				double distance = home.geometry.distance(a.home.geometry);
				similarity += distance * 1000;
			}
			
			return Math.max(0, similarity);
		}
		
		public boolean equals(Object o){
			if(! (o instanceof Agent)) return false;
			return this.myID == ((Agent)o).myID;
		}
		
		public int hashCode(){
			return (int) myID;
		}
	}
	
	double [] ageSexConstraints = null;
	
	public PopulationSynthesis(int seed){

		random = new MersenneTwisterFast(seed);
		
		// read in data
		GeomVectorField roads = readInVectors(dirName + roadsFilename);
		roadNetwork = NetworkUtilities.multipartNetworkCleanup(roads, new Bag(), resolution, gf, random, 0);

		// construct the houses into which individuals are to be slotted
		HashSet <MasonGeometry> candidateHouses;
		GeomVectorField buildings;
		if(buildingsFilename.length() == 0) {
			buildings = new GeomVectorField();
			buildings.setMBR(roads.getMBR());
			candidateHouses = generateHousesFromScratch(buildings, roadNetwork);
		}
		else {
			buildings = readInVectors(dirName + buildingsFilename);
			candidateHouses = generateHouses(buildings, roadNetwork);			
		}
		
		//
		// Generate the households
		//
		
		// generate the individuals and assemble them into households
		ArrayList <Agent> allIndividuals  = generateIndividuals();
		if (allIndividuals == null)
			return;
			
		ArrayList<ArrayList<Agent>> allHouseholds = generateHouseholds(allIndividuals);
			
		assignHouseholdsToHouses(allHouseholds, candidateHouses);
/* TODO add meeeeee
		ArrayList <Agent> noAssignedHome = new ArrayList <Agent> ();
		for(Agent a: individuals){
			if(a.home == null)
				noAssignedHome.add(a);
		}
			
		ArrayList <ArrayList<Agent>> emptyHouseholds = new ArrayList <ArrayList <Agent>> ();
		for(ArrayList <Agent> household: households){
			if(household.size() == 0) 
				emptyHouseholds.add(household);
		}
		households.removeAll(emptyHouseholds);
			
		allIndividuals.removeAll(noAssignedHome);
			
	*/			
//		ArrayList <Agent> socialMediaUsers = getSocialMediaUsers(allIndividuals);

		System.out.println("Finished with picking social media users");

		//
		// Assign individuals to workplaces
		//

		//generateWorkplaces(demo, roadNetwork, tractToCountyMapping, householdsPerCounty);
		System.out.println("Finished with generating workplaces");
		
		System.gc();
		
		//
		// Create friendship-based social ties
		//

		allIndividuals = new ArrayList <Agent> ();
		for(ArrayList <Agent> household: allHouseholds){
			allIndividuals.addAll(household);
		}
		
		//sociallyCluster(allIndividuals, acquaintenceWeight);

		System.out.println("Finished with social clustering");
		
//		sociallyMediaCluster(socialMediaUsers, acquaintenceWeight, 15);

		System.out.println("Finished with social media clustering");
		
		//
		// Write out the findings
		//

		writeOutHouseholds(allHouseholds);
	}

	public void writeOutHouseholds(ArrayList <ArrayList <Agent>> households){
		String fout = dirName + "synthPop_hh_" + System.currentTimeMillis() + ".txt";
		
		BufferedWriter w;
		try {
			w = new BufferedWriter(new FileWriter(fout));
			
			int index = 0;
			for(ArrayList <Agent> h: households){
				String myHH = "HOUSEHOLD_" + index++;
				MasonGeometry myHHLocation = h.get(0).home;
				String myName = myHHLocation.getStringAttribute("fid");
//				Coordinate c = myHHLocation.geometry.getCoordinate();
//				myHH += "\t" + c.x + "\t" + c.y + "\t";
				myHH += "\t" + myName;
				//Agent a = h.get(0);
				//myHH += "\t" + a.myID + "\t" + a.age + "\t" + a.sex;
				myHH += "\t" + h.size();
				for(Agent a: h){
					myHH += "\t" + a.myID + ":" + a.age + ":" + a.sex;
				}
				w.write(myHH + "\n");
			}

			w.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}

	}	

	public void writeOutAggregated(ArrayList <ArrayList <Agent>> households){
		String fout = dirName + "synthPop_" + System.currentTimeMillis() + ".txt";
		
		BufferedWriter w;
		try {
			w = new BufferedWriter(new FileWriter(fout));
			
			HashMap <Agent, Agent> agentsToHouseAgents = new HashMap <Agent, Agent> ();
			ArrayList <Agent> houseAgents = new ArrayList <Agent> ();
			
			for (ArrayList<Agent> household: households) {
				
				ArrayList <Agent> employees = new ArrayList <Agent> ();
				
				// first, make a list of everyone who gets their own agents ------------
				for (Agent a : household) {
					if(a.work != null) employees.add(a);
				}
				
				// perhaps there aren't any employed persons in the house - then add the 
				// oldest tenant 
				if(employees.size() == 0){
					int maxAge = -1;
					Agent defaultAgent = null;
					for(Agent a: household){
						if(maxAge < a.age){
							maxAge = a.age;
							defaultAgent = a;
						}
					}
					employees.add(defaultAgent);
				}
				
				// error check - there must be SOMEone!
				if(employees.size() == 0)
					System.out.println("synth pop problem");

				// now generate HouseAgents for each of the relevant agents! ----------
				
				Agent head = employees.get(0);
				if(head == null) 
					continue;
				Agent houseAgent = new Agent(head.age, head.sex);
				houseAgent.home = head.home;
				houseAgent.work = head.work;
				houseAgent.myID = head.myID;
				//houseAgent.addStringAttribute("ID", head.getStringAttribute("ID"));
				houseAgents.add(houseAgent);
				
				for(Agent b: household)
					agentsToHouseAgents.put(b, houseAgent);
			}
			
			for(ArrayList <Agent> household: households){
				
				HashSet <Agent> connectedHouses = new HashSet <Agent> (),
						connectedMediaHouses = new HashSet <Agent> ();
				
				// socially connect the HouseAgent objects that already exist -------
				for(Agent a: household){
					
					for(Agent b: a.socialTies.keySet()){
						if(household.contains(b)) continue;
						else if(! agentsToHouseAgents.containsKey(b)) 
							continue;
						Agent bHouse = agentsToHouseAgents.get(b);
						connectedHouses.add(bHouse);
					}
					
/*					if(a.socialMediaTies != null)
					for(Agent b: a.socialMediaTies){
						if(household.contains(b)) continue;
						else if(! agentsToHouseAgents.containsKey(b)) 
							continue;
						Agent bHouse = agentsToHouseAgents.get(b);
						connectedMediaHouses.add(bHouse);
					}*/
				}
			
				Agent thisHouse = agentsToHouseAgents.get(household.get(0));
				for(Agent a: connectedHouses)
					thisHouse.addContact(a, friendWeight);
				//for(Agent a: connectedMediaHouses)
				//	thisHouse.addMediaContact(a);
			}
			
			
			// WRITE OUT THESE NEW HOUSE AGENTS
			for(Agent a: houseAgents){
				w.write(a.myID + "\t" + a.age + "\t" + a.sex + "\t");// + a.home.toString() + "\t");
				long myId = a.myID;
				if (a.work != null)
					w.write(a.work.toString() + "\t");
				else
					w.write("\t");

				String contacts = "";
				int numContacts = 0;
				
				for (Agent tie : a.socialTies.keySet()) {
					if (tie.myID < myId){
						contacts += tie.myID + " " + a.socialTies.get(tie) + "\t";
						numContacts++;
					}
				}

				w.write(numContacts + "\t" + contacts);

				
				/*if (a.socialMediaTies != null) {
					contacts = "";
					numContacts = 0;
					for (Agent tie : a.socialMediaTies) {
						if (Long.parseLong(tie.getStringAttribute("ID")) < myId){
							contacts += tie.getStringAttribute("ID") + "\t";
							numContacts++;
						}
					}
					w.write(numContacts + "\t" + contacts);
				}*/
				
				w.newLine();
			}

			w.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	void assignHouseholdsToHouses(ArrayList <ArrayList <Agent>> households, HashSet <MasonGeometry> houses){
		
		ArrayList <MasonGeometry> availableHouses = new ArrayList <MasonGeometry> ();
		availableHouses.addAll(houses);
		
		// make sure that we don't have too many houses!
		int numHouses = houses.size();
		if(numHouses < households.size())
			System.out.println("ERROR: not enough housing units given the number of households");
		
		for(ArrayList <Agent> household: households){
			int myIndex = random.nextInt(numHouses);
			MasonGeometry assignedHouse = availableHouses.get(myIndex);
			while(assignedHouse == null){
				myIndex = random.nextInt(numHouses);
				assignedHouse = availableHouses.get(myIndex);
			}
			for(Agent a: household)
				a.home = assignedHouse;
			//availableHouses.set(myIndex, null);
		}
	}

	/**
	 * Generate the set of workplaces, given the road network, set of nodes in the tract, number of households,
	 * and the flow of individuals
	 * 
	 * @param field
	 * @param roadNetwork
	 * @param tractToCountyMapping
	 * @param householdsPerCounty
	 */
/*	void generateWorkplaces(GeomVectorField field, Network roadNetwork, HashMap <MasonGeometry, String> tractToCountyMapping, 
			HashMap <String, ArrayList<ArrayList<Agent>>> householdsPerCounty){
		
		HashMap <String, ArrayList <MasonGeometry>> countyToTractMappingCompare = new HashMap <String, ArrayList <MasonGeometry>> ();
		for(MasonGeometry mg: tractToCountyMapping.keySet()){
			String c = tractToCountyMapping.get(mg);
			if(!countyToTractMappingCompare.containsKey(c))
				countyToTractMappingCompare.put(c, new ArrayList <MasonGeometry>());
			countyToTractMappingCompare.get(c).add(mg);
		}
		
		HashMap <String, ArrayList <MasonGeometry>> countyToTractMapping = new HashMap <String, ArrayList <MasonGeometry>> ();
		for (Object o : field.getGeometries()) {

			MasonGeometry tract = (MasonGeometry) o;
			String tractName = tract.getStringAttribute("GEOID10");
			String countyName = tractName.substring(0, 5);

			if(!countyToTractMapping.containsKey(countyName))
				countyToTractMapping.put(countyName, new ArrayList <MasonGeometry>());
			countyToTractMapping.get(countyName).add(tract);
			
		}			
		
		HashMap <String, HashMap <String, Double>> populationFlows = new HashMap <String, HashMap <String, Double>> ();
		HashMap <String, Double> ratioEmployed = new HashMap <String, Double> ();
		try {
			// Open the tracts file
			FileInputStream fstream = new FileInputStream(travelToWorkFilename);
			
			// Convert our input stream to a BufferedReader
			BufferedReader flowData = new BufferedReader(new InputStreamReader(fstream));
			String s;

			flowData.readLine(); // get rid of header
			while ((s = flowData.readLine()) != null) {
				String[] bits = s.split(",");
				String from = bits[0] + bits[1];
				String to = bits[2].substring(1,3) + bits[3];
				Double count = Double.parseDouble(bits[4]);
				
				// DON'T ADD IT if we're not interested in that area!
				if(!countyToTractMapping.containsKey(from))
					continue;
				
				if(!populationFlows.containsKey(from))
					populationFlows.put(from, new HashMap <String, Double>() );
				populationFlows.get(from).put(to, count);
			}

			// clean up
			flowData.close();
			
		} catch (Exception e) {
			System.err.println("File input error");
		}
		
		// if there was a problem with the input, return without generating flows
		if(populationFlows.keySet().size() == 0) 
			return;
		
		// normalize the flows
		for(String from: populationFlows.keySet()){
			
			// determine the total number of jobs associated with this country
			double totalJobs = 0.;
			for(String to: populationFlows.get(from).keySet())
				totalJobs += populationFlows.get(from).get(to);
			
			// for each of these flows, normalize to a percent of the total population working in that area
			for(String to: populationFlows.get(from).keySet())
				populationFlows.get(from).put(to, (populationFlows.get(from).get(to)/totalJobs));
			
			// determine the proportional number of jobs to generate in this county
			double pop = 0.;
			for(MasonGeometry mg: countyToTractMapping.get(from))
				pop += mg.getIntegerAttribute("DP0010001");
			
			ratioEmployed.put(from, totalJobs/pop);
		}

		// generate a mapping of road nodes to counties, soas to assign individuals to workplaces in the appropriate county
		HashMap <String, ArrayList <GeoNode>> nodesToCountyMapping = new HashMap <String, ArrayList <GeoNode>> ();
		for(Object o: roadNetwork.getAllNodes()){ // go through nodes one by one
			
			GeoNode node = (GeoNode) o;
			MasonGeometry tract = getCovering(node, field);
			String county = tractToCountyMapping.get(tract);
			if(!nodesToCountyMapping.containsKey(county))
				nodesToCountyMapping.put(county, new ArrayList <GeoNode> ());
			nodesToCountyMapping.get(county).add(node);
		}
		
		// go through each county and assign household members to jobs based on job flow
		for(String county: householdsPerCounty.keySet()){
			
			// get the set of households associated with this county
			ArrayList <ArrayList<Agent>> households = new ArrayList <ArrayList<Agent>> (householdsPerCounty.get(county));

			// no need to generate any flows from irrelevant counties
			if(populationFlows.get(county) == null)
				continue;
			
			int pop = 0;
			for(ArrayList <Agent> household: households){
				pop += household.size();
			}
			
			int numJobs = (int)(pop * ratioEmployed.get(county));
				
			ArrayList <Agent> workers = new ArrayList <Agent> ();
			for(ArrayList <Agent> household: households){
				for(Agent a: household)
					if(a.age > 3 && a.work == null) workers.add(a);
			}

			HashMap <String, Double> jobDistribution = populationFlows.get(county);

			int workerSize = workers.size();
			if(workerSize == 0){ // if there are no workers, just go on;
				System.out.println("ERROR: no workers for this tract");
				continue;
			}

			for(int i = 0; i < numJobs; i++){

				String toCounty = getIndex(jobDistribution, random.nextDouble());
				
				// select a random node in the county as a worksite
				ArrayList <GeoNode> countyNodes = nodesToCountyMapping.get(toCounty);
				
				if(countyNodes == null) // if no one here will be involved in the simulation, don't bother to generate them
					continue;

				Point workPoint = gf.createPoint(countyNodes.get(random.nextInt(countyNodes.size())).geometry.getCoordinate());
					
				// select a random worker (who is of age to be employed) 
				boolean unassigned = true;
				while(unassigned){
					
					int workerIndex = random.nextInt(workerSize);
					Agent a = workers.get(workerIndex);
					if(a.age > 3 && a.work == null){
						a.work = workPoint;
						workers.remove(workerIndex);
						unassigned = false;
						workerSize--;
					}
	
				}
				
				// terminate it if there's no one left
				if(workers.size() == 0)
					i = numJobs + 1;
			}
		}
	}
	*/
	
	/**
	 * Generate the set of possible houses in the environment, given the residential roads
	 *  
	 * @param field
	 * @param roadNetwork
	 * @return
	 */
	HashSet <MasonGeometry> generateHouses(GeomVectorField field, Network roadNetwork){
		
		HashMap <MasonGeometry, ArrayList <Point>> result = new HashMap <MasonGeometry, ArrayList <Point>> (); 
		
		// set up the location to hold the objects
		GeomVectorField roadGeoms = new GeomVectorField();
		roadGeoms.setMBR(field.getMBR());
		
		HashSet <ListEdge> discoveredEdges = new HashSet <ListEdge> ();
		HashSet <MasonGeometry> houseCandidates = new HashSet <MasonGeometry> ();
		
		//
		// match all the edges to the areas that completely contain them.
		//
		for(Object o: roadNetwork.getAllNodes()){ // go through nodes one by one
			
			GeoNode node = (GeoNode) o;
			
			// don't look if the node is outside the building area!
			if(!field.getMBR().contains(node.getGeometry().getCoordinate()))
				continue;

			// go through the edges for this node
			for(Object p: roadNetwork.getEdgesOut(node)){
				
				// get the associated edges!
				ListEdge edge = (ListEdge) p;
				
				// if the road is the wrong type, or it has already been found, continue!
				String type = ((MasonGeometry)edge.getInfo()).getStringAttribute("highway");
				if(!(type.equals("residential") || type.equals("living")))
					continue;
				if(discoveredEdges.contains(edge))
					continue;
				
				// add it to the field!
				MasonGeometry mg = (MasonGeometry)edge.getInfo();
				roadGeoms.addGeometry(mg);
				discoveredEdges.add(edge);
				
				// attempt to add buildings, based on size
				Bag b = field.getObjectsWithinDistance(mg, distanceToRoads);
				for(Object raw_house: b){
					MasonGeometry possibleHouse = (MasonGeometry) raw_house;
					
					 // don't take overly large buildings!
					if(possibleHouse.geometry.getArea() > 200) continue;
					
					// if it's close to a Residential or Living street and smaller than 200m^2, add it!
					// https://en.wikipedia.org/wiki/Housing_in_Japan suggests that the average is 94.85 - doubled here in case!
					houseCandidates.add(possibleHouse);
				}
			}
		}
		
		return houseCandidates;
	}
	
	/**
	 * Generate the set of possible houses in the environment, given the residential roads
	 *  
	 * @param field
	 * @param roadNetwork
	 * @return
	 */
	HashSet <MasonGeometry> generateHousesFromScratch(GeomVectorField field, Network roadNetwork){
		
		// set up the location to hold the objects
		GeomVectorField roadGeoms = new GeomVectorField();
		roadGeoms.setMBR(field.getMBR());
		
		HashSet <ListEdge> discoveredEdges = new HashSet <ListEdge> ();
		HashSet <MasonGeometry> houseCandidates = new HashSet <MasonGeometry> ();
		
		GeometryFactory gf = new GeometryFactory();
		
		int houseIndex = 0;
		
		//
		// match all the edges to the areas that completely contain them.
		//
		for(Object o: roadNetwork.getAllNodes()){ // go through nodes one by one
			
			GeoNode node = (GeoNode) o;
			
			// don't look if the node is outside the building area!
			if(!field.getMBR().contains(node.getGeometry().getCoordinate()))
				continue;

			// go through the edges for this node
			for(Object p: roadNetwork.getEdgesOut(node)){
				
				// get the associated edges!
				ListEdge edge = (ListEdge) p;
				
				// if the road is the wrong type, or it has already been found, continue!
				String type = ((MasonGeometry)edge.getInfo()).getStringAttribute("HIERARCHY");

				// we only want residential roads and we don't want the type 1 roads (from survey)
				if(!(type.contains("RESIDENTIAL")))
					continue;
				if(discoveredEdges.contains(edge))
					continue;
				
				// add it to the field!
				MasonGeometry mg = (MasonGeometry)edge.getInfo();
				roadGeoms.addGeometry(mg);
				discoveredEdges.add(edge);
				
				LineString ls = (LineString)((MasonGeometry)edge.info).geometry;
				
				LengthIndexedLine segment = new LengthIndexedLine(ls);
				double endIndex = segment.getEndIndex();
				
				double distanceBetweenBuildings = 15;
				
				for(double i = 5; i <= endIndex - 5; i += distanceBetweenBuildings) {
					MasonGeometry newHouse = new MasonGeometry(gf.createPoint(segment.extractPoint(i)));
					newHouse.addStringAttribute("fid", "house_" + houseIndex);
					houseIndex++;
					field.addGeometry(newHouse);
					houseCandidates.add(newHouse);
				}
				
				/*
				// attempt to add buildings, based on size
				Bag b = field.getObjectsWithinDistance(mg, distanceToRoads);
				for(Object raw_house: b){
					MasonGeometry possibleHouse = (MasonGeometry) raw_house;
					
					 // don't take overly large buildings!
					if(possibleHouse.geometry.getArea() > 200) continue;
					
					// if it's close to a Residential or Living street and smaller than 200m^2, add it!
					// https://en.wikipedia.org/wiki/Housing_in_Japan suggests that the average is 94.85 - doubled here in case!
					houseCandidates.add(possibleHouse);
				}
				*/
			}
		}
		
		try {
			System.out.println(houseCandidates.size());
			
			String housesFilename = dirName + "dummyHouses.txt";
			BufferedWriter record_houses = new BufferedWriter(new FileWriter(housesFilename));
			
			record_houses.write("fid\tx\ty\n");
			int index = 0;
			for(Object o: field.getGeometries()) {
				Point mg = (Point)((MasonGeometry) o).geometry;
				record_houses.write("house_" + index + "\t" + mg.getX() + "\t" + mg.getY() + "\n");
				index++;
			}
			record_houses.close();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return houseCandidates;
	}
	
	double [] getAgeSexConstraints(){
		
		FileInputStream fstream;
		try {
			
			// open the demographic file
			fstream = new FileInputStream(dirName + demoFilename);

			// Convert our input stream to a BufferedReader
			BufferedReader demoData = new BufferedReader(new InputStreamReader(fstream));
			
			// set up the holder
			String s = demoData.readLine();
			
			// check the header
			if(!s.toLowerCase().startsWith("age")){
				// what! Formatted weirdly! 
				System.out.println("ERROR: demographic constraints improperly formatted, generation terminated");
				return null; // complain and exit
			}
			
			System.out.println(dirName + demoFilename);

			// otherwise let's rock
			double [] results = new double [(int)(maxAge / numYearsPerBin) * 2 + 2]; // add an extra 2 for the upper limit 
			// age categories of given size from 0-maxAge years for, currently, 2 sexes (to expand with better data)
			
			// iterate over and take out each line
			while ((s = demoData.readLine()) != null) {
				String [] bits = s.split("\t");
				
				// calculate the age range for partitioning into these smaller categories
				String [] rawAges = bits[0].split("-");
				int minAge = Integer.parseInt(rawAges[0]), maxAge = Integer.parseInt(rawAges[1]);
				int span = maxAge - minAge;
				
				// get the sex ratios
				int numMalesPerYear = Integer.parseInt(bits[1].replace(",", "")) / span, 
					numFemalesPerYear = Integer.parseInt(bits[2].replace(",", "")) / span;
				
				if(minAge > 60)
					System.out.println("seniors");
				// iterate over each year and put the appropriate number in the appropriate category
				for(int i = minAge; i <= maxAge; i++){
					int bin = (int)Math.floor(i / numYearsPerBin);
					results[bin] += numMalesPerYear;
					results[bin + results.length / 2] += numFemalesPerYear;
				}
			}

			return results;
		} catch (Exception e) {
			System.out.println("ERROR reading in demographic data - proceeding using dummy data");
			
			double [] dummyResults = new double [2];
			dummyResults[0] = this.targetNumIndividualsToGenerate / 2;
			dummyResults[1] = this.targetNumHouseholdsToGenerate / 2;
			
			//e.printStackTrace();
			return dummyResults;
		}
	}
	
	/**
	 * From the demographic information associated with a given census tract, generate a set
	 * of individuals who match these parameters. Report on the fit of this generated population.
	 *  
	 * @return a set of individuals
	 */
	ArrayList <Agent> generateIndividuals(){
		
		// get the joint distribution of age and sex constraints on the population
		double [] ageSexConstraints = getAgeSexConstraints();
		
		ArrayList <Agent> individuals = new ArrayList <Agent> ();
		
		// get the total number of individuals in the area
		int totalPop = (int) getSum(ageSexConstraints);
		if(totalPop == 0)
			return null;
		
		// for every individual in the area, generate a representative agent
		int bins = ageSexConstraints.length / 2;
		for(int i = 0; i < targetNumIndividualsToGenerate; i++){
			
			// generate information about this agent
			double val = random.nextDouble() * totalPop;
			int index = getIndex(ageSexConstraints, val);
			int sex = index / bins;
			int age = index % bins;
			
			// create the agent
			Agent a = new Agent(age, sex);
			
			// record this individual
			individuals.add(a);
		}
		
		// print out report on the quality of fit
//		System.out.println("Fit for " + area.getStringAttribute("NAMELSAD10") + ": " + fitIndividuals(individuals, ageSexConstraints));	
//		System.out.println("HOUSEHOLDS: " + area.getIntegerAttribute("DP0120002") + "\tGROUP QUARTERS: " + area.getIntegerAttribute("DP0120014") + "\tTOTAL: " + totalPop);
		this.ageSexConstraints = ageSexConstraints;
		
		return individuals;
	}

	/**
	 * Extract the household type constraints from the provided demographic information
	 * @return a set of household type constraints consistent with the coding provided at
	 * 		http://www.census.gov/prod/cen2010/doc/sf1.pdf
	 */	
	double [] getHouseholdConstraints(){
			
			FileInputStream fstream;
			try {
				
				// open the demographic file
				fstream = new FileInputStream(dirName + householdsFilename);

				// Convert our input stream to a BufferedReader
				BufferedReader householdData = new BufferedReader(new InputStreamReader(fstream));
				
				// set up the holder
				String s = householdData.readLine();
				
				// let's rock
				double [] results = new double [16];
				int index = 0;
				
				// iterate over and take out each line
				while ((s = householdData.readLine()) != null) {
					String [] bits = s.split("\t");
					
					// calculate the age range for partitioning into these smaller categories
					results[index++] = Integer.parseInt(bits[3].replace(",", ""));
				}

				return results;
			} catch (Exception e) {
				System.out.println("ERROR reading in demographic data - proceeding with dummy data");

				double [] dummyResults = new double [16];
				for(int i = 0; i < 16; i++) {
					dummyResults[i] = 0;
				}
				dummyResults[14] = this.targetNumHouseholdsToGenerate;
				return dummyResults;
				//e.printStackTrace();
			}			
		}
	
	/**
	 * Generate a set of households based on the provided set of individuals and household parameters
	 * @param individuals - the set of individuals generated from this census area
	 * @return sets of Agents grouped into households
	 */
	ArrayList <ArrayList<Agent>> generateHouseholds(ArrayList <Agent> individuals){
				
		ArrayList <ArrayList<Agent>> allHouseholds = new ArrayList <ArrayList <Agent>> ();
		ArrayList <ArrayList<Agent>> familyHouseholds = new ArrayList <ArrayList <Agent>>();

		// read in the constraints on household types
		double [] householdTypeRatios = getHouseholdConstraints();
				
		
		// GENERATE THE HOUSEHOLDS
		double numHouseholds = getSum(householdTypeRatios);
		
		System.out.println("Generating households...");
		for(int i = 0; i < targetNumHouseholdsToGenerate; i++){
			
			if(i % 100 == 0)
				System.out.print('.');
			
			// if the set of individuals is now empty, no need to keep trying to create households!
			if(individuals.size() == 0){ 
				i = (int) targetNumHouseholdsToGenerate;
				continue;
			}
			
			ArrayList <Agent> household = new ArrayList <Agent> ();
			Agent a;

			//////////////////////////////////////////////////////////////////////////////////////////
			// determine the household type //////////////////////////////////////////////////////////
			//////////////////////////////////////////////////////////////////////////////////////////

			double val = random.nextDouble() * numHouseholds;
			int index = getIndex(householdTypeRatios, val);
			
			// given the type, set up the household
			int hh1 = -1, hh2 = -1; // 0 for male, 1 for female
			int numChildren = 0;
			boolean familyGroup = true;
			
			switch (index) {
			case 0: // couple household
				hh1 = 0; hh2 = 1;
				break;
			case 1: // couple family, OWN CHILDREN
				hh1 = 0; hh2 = 1;
				numChildren = ownChildrenDistribution();
				break;
			case 2: // single male householder WITH OWN CHILDREN
				hh1 = 0;
				numChildren = ownChildrenDistribution();
				break;
			case 3: // single female householder WITH OWN CHILDREN
				hh1 = 1;
				numChildren = ownChildrenDistribution();
				break;
			case 4: // couple household WITH PARENTS
				hh1 = 1; hh2 = 0;
				break;
			case 5: // couple household WITH SINGLE PARENT
				hh1 = 1; hh2 = 0;
				break;
			case 6: // couple household WITH PARENTS AND CHILD(REN)
				hh1 = 1; hh2 = 0;
				numChildren = ownChildrenDistribution();
				break;
			case 7: // couple household WITH PARENT AND CHILD(REN)
				hh1 = 1; hh2 = 0;
				numChildren = ownChildrenDistribution();
				break;
			case 8: // couple and other relatives (NOT CHILDREN, PARENTS)
				hh1 = 1; hh2 = 0;
				break;
			case 9: // couple, children, and other relatives (NOT PARENTS)
				hh1 = 1; hh2 = 0;
				numChildren = ownChildrenDistribution();
				break;
			case 10: // couple, parents, and other relatives (NOT CHILDREN)
				hh1 = 1; hh2 = 0;
				break;
			case 11: // couple, child, parents, and other relatives
				hh1 = 1; hh2 = 0;
				break;
			case 12: // household, siblings only
				break;
			case 13: // other relative family
				break;
			case 14: // non-related
				familyGroup = false;
				break;
			case 15: // single person
				hh1 = random.nextInt(2);
				break;
			}
			
			//////////////////////////////////////////////////////////////////////////////////////////
			// construct the household ///////////////////////////////////////////////////////////////
			//////////////////////////////////////////////////////////////////////////////////////////

			int spouseAge = -1;

			// select the Householder /////////////////////////////////////////////

			int indivIndex = 0; // go through the randomly generated individuals one by one!
			while(indivIndex < individuals.size() && hh1 >= 0){
				a = individuals.get(indivIndex);
				if(a.sex == hh1 && a.age >= Math.floor(18./numYearsPerBin)){ // basic requirements

					// if the householder needs to have children under 18, need to be old enough to have produced a kid!
					if(numChildren > 0 && a.age < 15./numYearsPerBin){
						indivIndex++;
						continue;
					}

					household.add(a);
					spouseAge = a.age + (int)(1.5 * random.nextGaussian());	
					individuals.remove(indivIndex);
					indivIndex = Integer.MAX_VALUE;
				}
				else
					indivIndex++;
			}
			if(hh1 >=0 && indivIndex != Integer.MAX_VALUE){ // it has failed!!
				continue;
			}

			// add spouse, if appropriate /////////////////////////////////////////////
			indivIndex = 0;
			while(indivIndex < individuals.size() && hh2 >= 0){
				a = individuals.get(indivIndex);
				if(a.sex == hh2 && a.age == spouseAge){ // basic requirements						
					household.add(a);
					individuals.remove(indivIndex);
					indivIndex = Integer.MAX_VALUE;
				}
				else
					indivIndex++;
			}

			// add children, if appropriate /////////////////////////////////////////////
			if(numChildren > 0){

				// get rough age parameters for the children of the householder and spouse
				int minAge = (int) (17./numYearsPerBin), maxAge = 0;
				for(Agent member: household){
					if(member.age > maxAge) maxAge = member.age;
					if(member.age < minAge) minAge = member.age;
				}
				// children should be younger than the minimum age of a parent minus 15 years (ASSUMPTION)
				int maxChildAge = minAge - (int)(15 / numYearsPerBin);
				// children can be born to parents at no older than 50 (ASSUMPTION)
				int minChildAge = Math.max(0, maxAge - (int)(9 / numYearsPerBin));//10);

				int previousChildAge = -1; // use to try to cluster child ages together
				int fulfilled = 0;
				
				indivIndex = 0;
				while(indivIndex < individuals.size() && fulfilled < numChildren){
					a = individuals.get(indivIndex);
					if(a.age >= minChildAge && a.age <= maxChildAge){ // basic requirements

						// prefer children to be closer in age to one another (normal distribution --> within 5 years, certainly within 10)
						if(Math.abs(a.age - previousChildAge) > Math.abs(random.nextGaussian())){
							indivIndex++;
							continue;							
						}

						household.add(a);
						individuals.remove(indivIndex);
						indivIndex = Integer.MAX_VALUE;
						fulfilled++;
						previousChildAge = a.age;
					}
					else
						indivIndex++;
				}

			}

			// NON-FAMILY HOUSEHOLD: add roommates who are adults //////////////////////
			if(hh1 == -1 && hh2 == -1 && !familyGroup){

				int numMembers = otherAdultsDistribution();
				int fulfilled = 0;
				indivIndex = 0;

				while(indivIndex < individuals.size() && fulfilled < numMembers){
					a = individuals.get(indivIndex);
					if(a.age >= Math.floor(4./numYearsPerBin)){ 
						household.add(a);
						individuals.remove(indivIndex);
						indivIndex = Integer.MAX_VALUE;
						fulfilled++;
					}
					else
						indivIndex++;
				}
			}

			if(household.size() > 0){
				if(familyGroup)
					familyHouseholds.add(household);
				else
					allHouseholds.add(household);
			}
			else {
				i--;
			}

		}

		//////////////////////////////////////////////////////////////////////////////////////////
		// allocated unassigned individuals //////////////////////////////////////////////////////
		//////////////////////////////////////////////////////////////////////////////////////////

		int leftoverIndividualsToAllocate = individuals.size();
		int allHouseholdsSize = allHouseholds.size(); 
		while(leftoverIndividualsToAllocate > 0){
			Agent member = individuals.remove(random.nextInt(leftoverIndividualsToAllocate));
			//familyHouseholds.get(random.nextInt(familyHouseholds.size())).add(member); TODO this was stupid
			allHouseholds.get(random.nextInt(allHouseholdsSize)).add(member);
			leftoverIndividualsToAllocate--;
		}
	
		//////////////////////////////////////////////////////////////////////////////////////////
		// set up basic household social networks ////////////////////////////////////////////////
		//////////////////////////////////////////////////////////////////////////////////////////

		for(ArrayList <Agent> household: familyHouseholds){
			int weight = familyWeight;
			for(int i = 0; i < household.size()-1; i++){				
				for(int j = i+1; j < household.size(); j++){
					household.get(i).addContact(household.get(j), weight);
					household.get(j).addContact(household.get(i), weight);					
				}
			}
		}
		
		for(ArrayList <Agent> household: allHouseholds){
			int weight = friendWeight;
			for(int i = 0; i < household.size()-1; i++){				
				for(int j = i+1; j < household.size(); j++){
					household.get(i).addContact(household.get(j), weight);
					household.get(j).addContact(household.get(i), weight);					
				}
			}			
		}
		
		//////////////////////////////////////////////////////////////////////////////////////////
		// clean up the structures ///////////////////////////////////////////////////////////////
		//////////////////////////////////////////////////////////////////////////////////////////

		allHouseholds.addAll(familyHouseholds); // combine the two sets of household types
	//	fitOfHouseholds(allHouseholds, familyHouseholds); // report on the fit of households
		
		return allHouseholds;
	}
	

	/**
	 * Cluster the individuals, giving the generated relationships the given weight
	 * @param individuals
	 * @param weight
	 */
	void sociallyCluster(ArrayList <Agent> individuals, int weight){

		int indexy = -1;

		HashMap <Agent, Integer> assignedFriends = new HashMap <Agent, Integer> ();
		for(Agent a: individuals){
			int numConnections = 3 + 100 - (int) Distributions.nextPowLaw(6, 100, this.random);
			assignedFriends.put(a, numConnections);
			indexy++;
			if(indexy % 100 == 0) System.out.println(indexy + " out of " + individuals.size() + " distrib. assigned...");
		}
		
		indexy = -1;
		for(Agent a: individuals){
			indexy++;
			if(indexy % 100 == 0)
				System.out.println(indexy + " out of " + individuals.size());
			// power law-distributed number of connections, ranging from about 4 to 100
			int numConnections = assignedFriends.get(a) - a.socialTies.size();
			if(numConnections <= 0) continue; // this agent is already all set!
			
			// collect a group of unconnected individuals and weight them according to closeness and similarity
			HashMap <Agent, Double> socialTree = new HashMap <Agent, Double> ();
			
			// get social connections
			for(Agent b: a.socialTies.keySet()){
				for(Agent c: b.socialTies.keySet()){
					if(c == a) continue;  // don't add self
					if(!a.socialTies.containsKey(c) && c.socialTies.size() < assignedFriends.get(c)){
						double distance = a.getSocialDistance(c);
						socialTree.put(c, distance);
					}
				}
			}
			
			// get random connections
			for(int i = socialTree.size(); i < numConnections + 100; i++){
				Agent b = individuals.get(random.nextInt(individuals.size()));
				// don't add self, an existing friend, or someone already under consideration
				if(b == a || a.socialTies.containsKey(b) || socialTree.containsKey(b) || b.socialTies.size() >= assignedFriends.get(b)){
					continue; // don't add self
				}
				double distance = a.getSocialDistance(b);
				socialTree.put(b, distance);
			}

			// add the heaviest weighted individuals!
			for(int i = 0; i < numConnections; i++){
				Agent b = getMin(socialTree);
				if(b == null) continue;
				a.addContact(b, weight);
				b.addContact(a, weight);
				socialTree.remove(b);
			}
			
		}
		
	}
	
	/**
	 * Remember, it's DIRECTED. Following someone does not ensure that they'll follow you back!
	 * 
	 * /@param individuals
	 * /@param weight
	 * /@param averageDegree
	 */
/*	void sociallyMediaCluster(ArrayList <Agent> individuals, int weight, double averageDegree){
		
		HashMap <Agent, Integer> assignedFriends = new HashMap <Agent, Integer> ();
		for(Agent a: individuals){
			int numConnections = 3 + 100 - (int) Distributions.nextPowLaw(6, 100, this.random);
			assignedFriends.put(a, numConnections);
		}

		for(Agent a: individuals){
			
			// power law-distributed number of connections, ranging from about 4 to 100
			int numConnections = 5 + 100 - (int) Distributions.nextPowLaw(6, 100, this.random)
					- a.socialMediaTies.size(); // don't count beyond what the agent already has!
			
			// collect a group of unconnected individuals and weight them according to closeness and similarity
			HashMap <Agent, Double> socialTree = new HashMap <Agent, Double> ();
			
			// get social connections
			for(Agent b: a.socialTies.keySet()){
				for(Agent c: b.socialTies.keySet()){
					if(c == a) continue;  // don't add self
					if(c.socialMediaTies == null) continue; // don't add non-users!
					if(!a.socialMediaTies.contains(c) && c.socialMediaTies.size() < assignedFriends.get(c)){
						double distance = a.getSocialDistance(c);
						socialTree.put(c, distance);
					}
				}
			}
			
			// get random connections
			for(int i = socialTree.size(); i < numConnections + 100; i++){
				Agent b = individuals.get(random.nextInt(individuals.size()));
				if(b == a) continue; // don't add self
				if(a.socialMediaTies.contains(b) || b.socialMediaTies.size() < assignedFriends.get(b)) continue;
				double distance = a.getSocialDistance(b);
				socialTree.put(b, distance);
			}

			// add the heaviest weighted individuals!
			for(int i = 0; i < numConnections; i++){
				Agent b = getMin(socialTree);
				if(b == null) continue;
				a.addMediaContact(b);
				socialTree.remove(b);
			}
		}
		
	}*/
	
	Agent getMin(HashMap <Agent, Double> map){
		double minVal = Double.MAX_VALUE;
		Agent best = null;
		for(Agent a: map.keySet()){
			if(map.get(a) < minVal){
				minVal = map.get(a);
				best = a;
			}
		}
		return best;
	}
	
	///////////////////////////////////////////////////////////////
	// UTILITIES
	///////////////////////////////////////////////////////////////

	int ownChildrenDistribution(){ return random.nextInt(2) + 1;}	
	int otherAdultsDistribution(){ return random.nextInt(2) + 2;}
	
	/**
	 * Test the fit between a generated population and the age and sex constraints upon it
	 * 
	 * @param individuals - the list of individuals
	 * @param ageSexConstraints - a double array representing the ideal ratios of population. Here, the 
	 * 		array is assumed to be structured so that all the ratios of each age group of one sex are presented,
	 * 		then the next. This could easily be extended to deal with more than two sexes.
	 *  
	 * @return a Pearson's Chi-squared fit of the results with the expected ratios  
	 */
	double fitIndividuals(ArrayList <Agent> individuals, double [] ageSexConstraints){

		// structures to hold information
		double [] counts = new double [ageSexConstraints.length];
		int ageCategories = ageSexConstraints.length / 2; // CHANGE HERE to increase # sexes
		
		// calculate the distribution based on the set of individuals
		for(Agent a: individuals){
			int index = ageCategories * a.sex + a.age;
			counts[index]++;
		}
		
	//	System.out.println("FIT MEASURE:");
		// calculate Pearson's chi-squared
		double total = 0;
		double totalPop = individuals.size();
		for(int i = 0; i < ageSexConstraints.length; i++){
			double expected = totalPop * ageSexConstraints[i];
			if(expected == 0)
				expected = .001;
				//continue;
			total += Math.pow((counts[i] - expected), 2)/expected;
		//	System.out.println(counts[i] +  "\t" + expected);
		}
		
		return total;
	}
	
	/**
	 * Test how well the generated households compare to the data
	 * 
	 * @param households
	 * @param familyHouseholds
	 */
	void fitOfHouseholds(ArrayList <ArrayList <Agent>> households, ArrayList <ArrayList<Agent>> familyHouseholds){

		// TEST THE FIT
		int peopleInFamily = 0;
		int peopleInHouseholds= 0;
		int householdsUnder18 = 0;
		int householdsOver65 = 0;
		ArrayList <Agent> individuals = new ArrayList <Agent> ();
		
		for(ArrayList <Agent> h: households){
			if(h.size() == 0)
				System.out.print("EMPTY HOUSE");
			if(familyHouseholds.contains(h)) peopleInFamily += h.size();
			peopleInHouseholds += h.size();
			
			boolean under18 = false;
			boolean over65 = false;
			
			for(Agent ha: h){
				
				// Verbose household output
/*				System.out.print((1 + ha.age) * 5 + " ");
				if(ha.sex == 0)
					System.out.print("M\t");
				else if(ha.sex == 1)
					System.out.print("F\t");
	*/			 
				if(ha.age < 4) under18 = true;
				if(ha.age > 12) over65 = true;
				individuals.add(ha);
			}
			if(under18) householdsUnder18++;
			if(over65) householdsOver65++;
		//	 System.out.println(); // used for verbose output
		}

		/*
		// VERBOSE 
		System.out.println("TRACT: " + area.getStringAttribute("NAMELSAD10"));
		System.out.println("Total Pop: " + individuals.size() + " vs " + area.getIntegerAttribute("DP0120002"));
		System.out.println("Avg In Family: " + (peopleInFamily / (double) familyHouseholds.size()) + " vs " + area.getDoubleAttribute("DP0170001"));
		System.out.println("Avg In Household: " + (peopleInHouseholds / (double) households.size()) + " vs " + area.getDoubleAttribute("DP0160001"));
		System.out.println("Households w/ under 18: " + householdsUnder18 + " vs " + area.getIntegerAttribute("DP0140001"));
		System.out.println("Households w/ over 65: " + householdsOver65 + " vs " + area.getIntegerAttribute("DP0150001"));
		System.out.println("FIT OF HOUSEHOLDS: " + fitIndividuals(individuals, ageSexConstraints));
		System.out.println();
		*/
		
		
		// TAB-DELIMITED
/*		System.out.print(individuals.size() + "\t" + area.getIntegerAttribute("DP0120002") + "\t" + 
				area.getIntegerAttribute("DP0120014") + "\t"); // total vs in households vs group
		System.out.print(households.size() + "\t" + area.getIntegerAttribute("DP0130001") + "\t"); // number of households + generated households
		System.out.print((peopleInFamily / (double) familyHouseholds.size())  + "\t" + area.getDoubleAttribute("DP0170001") + "\t"); // avg in family vs TRUE
		System.out.print((peopleInHouseholds / (double) households.size())  + "\t" + area.getDoubleAttribute("DP0160001") + "\t"); // avg in household vs TRUE
		System.out.print(householdsUnder18  + "\t" + area.getIntegerAttribute("DP0140001") + "\t"); // households < 18 vs TRUE
		System.out.print(householdsOver65  + "\t" + area.getIntegerAttribute("DP0150001") + "\t"); // households > 65 vs TRUE
		*/System.out.println(fitIndividuals(individuals, getAgeSexConstraints()));
		
	}

	
	/**
	 * @param vals - a set of ratios which sum to 1
	 * @param val - a value between 0 and 1
	 * @return the bin of vals into which val fits
	 */
	int getIndex(double [] vals, double val){
		double count = 0;
		for(int j = 0; j < vals.length; j++){
			count += vals[j];
			if(val <= count) return j;
		}
		return vals.length - 1;
	}

	/**
	 * 
	 * @param distribution
	 * @param val
	 * @return
	 */
	String getIndex(HashMap <String, Double> distribution, double val){
		double count = 0;
		for(String index: distribution.keySet()){
			count += distribution.get(index);
			if(val <= count) return index;
		}
		return null;
	}

	/**
	 * read in the geometries associated with a shapefile
	 * 
	 * @param filename - the file to read in
	 * @return a GeomVectorField containing the geometries
	 */
	GeomVectorField readInVectors(String filename){
		GeomVectorField field = new GeomVectorField();
		try {
			System.out.print("Reading in file...");
			File file = new File(filename);
			ShapeFileImporter.read(file.toURL(), field);
			System.out.println("done");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return field;
	}
	
	double getSum(double [] popMatrix){
		double result = 0;
		for(int i = 0; i < popMatrix.length; i++){
			result += popMatrix[i];
		}
		return result;
	}
	
	double [] getSumByCol(double [] popMatrix){
		
		int halfLen = popMatrix.length / 2;
		double [] result = new double [halfLen];
		
		for(int i = 0; i < result.length; i++)
			result[i] = popMatrix[i] + popMatrix[i + halfLen];
		
		return result;
	}
	
	double [] getSumByRow(double [] popMatrix){
		double [] result = new double [2];

		double halfLen = popMatrix.length / 2.;

		for(int i = 0; i < popMatrix.length; i++)
			result[ (int)Math.floor(i / halfLen)] += popMatrix[i];
		
		return result;
	}
	
	/*
	ArrayList <Agent> getSocialMediaUsers(ArrayList <Agent> individuals){//double [] ageSexPopConstraints){

		try {

			double[] ageSexPopConstraints = new double[36];
			for (Agent a : individuals) {
				int aIndex = a.age + a.sex * 18;
				ageSexPopConstraints[aIndex]++;
			}

			double[] totalPopSexCounts = getSumByRow(ageSexPopConstraints);
			double[] totalPopAgeCounts = getSumByCol(ageSexPopConstraints);

			// Open the tracts file
			FileInputStream fstream = new FileInputStream(
					socialMediaUsageFilename);

			// Convert our input stream to a BufferedReader
			BufferedReader d = new BufferedReader(
					new InputStreamReader(fstream));

			String s;
			d.readLine(); // header

			// read in sex ratios
			// //////////////////////////////////////////////////////

			d.readLine(); // section header: sex

			double[] sexSocialMediaUsageCounts = new double[2];

			// multiply the percent of internet users by the percent of social media users to get the percent of total pop using 
			s = d.readLine();
			String[] bits = s.split("\t");
			if (bits[0].equals("Men")){ // 
				sexSocialMediaUsageCounts[0] = Integer.parseInt(bits[2])  * totalPopSexCounts[0] * Integer.parseInt(bits[1]) / 10000.;
			}
			s = d.readLine();
			bits = s.split("\t");
			if (bits[0].equals("Women")){
				sexSocialMediaUsageCounts[1] = Integer.parseInt(bits[2]) * totalPopSexCounts[1] * Integer.parseInt(bits[1]) / 10000.;
			}

			// read in age ratios
			// //////////////////////////////////////////////////////

			d.readLine(); // next section header
			int index = 0;
			double[] ageSocialMediaUsageCounts = new double[18];

			while ((s = d.readLine()) != null) {
				bits = s.split("\t");
				int minAgeInGroup = (int) Math.floor(Integer.parseInt(bits[0]) / 5); // get the appropriate bin
				int maxAgeInGroup = (int) Math.floor(Integer.parseInt(bits[1]) / 5); // get the appropriate bin

				// an unconsidered group, apparently
				while (index < minAgeInGroup) {
					ageSocialMediaUsageCounts[index] = 0;
					index++;
				}

				// multiply the percent of internet users by the percent of social media users to get the percent of total pop using 
				double perHundredSocialMedia = Integer.parseInt(bits[3]) * Integer.parseInt(bits[2]) / 10000.;

				for (; index <= maxAgeInGroup; index++) {
					ageSocialMediaUsageCounts[index] = perHundredSocialMedia * totalPopAgeCounts[index];
				}
			}
			while (index < 18) {
				ageSocialMediaUsageCounts[index] = 0;
				index++;
			}

			// IPF
			// //////////////////////////////////////////////////////////////////

			double[] oldRatios = new double[36], newCounts = new double[36];

			// initially set everyone with the same number of people
			double initialVal = (totalPopSexCounts[0] + totalPopSexCounts[1]) / 36;
			for (int i = 0; i < 36; i++)
				newCounts[i] = initialVal;
			boolean finished = false;

			while (!finished) {
				oldRatios = newCounts;
				newCounts = new double[36];

				// sex constraints

				// calculate current sex parameters
				double[] currentSexSizes = new double[2];
				for (int i = 0; i < currentSexSizes.length; i++) {
					for (int j = 0; j < 18; j++) {
						currentSexSizes[i] += oldRatios[j + i * 18];
					}
				}
				// modify the matrix so that it reflects both the existing sex
				// distribution and the true sex distribution
				for (int i = 0; i < currentSexSizes.length; i++) {
					for (int j = 0; j < 18; j++) {
						newCounts[j + i * 18] = (oldRatios[j + i * 18] / currentSexSizes[i])
								* sexSocialMediaUsageCounts[i];
					}
				}

				// age constraints

				// calculate current age parameters
				double[] currentAgeSizes = new double[18];
				for (int i = 0; i < currentAgeSizes.length; i++) {
					for (int j = 0; j < 2; j++) {
						currentAgeSizes[i] += oldRatios[i + j * 18];
					}
				}
				// modify the matrix so that it reflects both the existing sex
				// distribution and the true sex distribution
				for (int i = 0; i < currentAgeSizes.length; i++) {
					for (int j = 0; j < 2; j++) {
						newCounts[i + j * 18] = (oldRatios[i + j * 18] / currentAgeSizes[i])
								* ageSocialMediaUsageCounts[i];
					}
				}

				finished = true; // unless found otherwise
				for (int i = 0; i < 36; i++) {
					if (Math.abs(oldRatios[i] - newCounts[i]) > 5) {
						finished = false;
						break;
					}
				}
			}

			// clean up
			d.close();

			for (int i = 0; i < newCounts.length; i++) {
				newCounts[i] /= ageSexPopConstraints[i];
			}

			ArrayList<Agent> socialMediaUsers = new ArrayList<Agent>();
			for (Agent a : individuals) {
				int aIndex = a.age + a.sex * 18;
				if (random.nextDouble() < newCounts[aIndex]) {
					a.socialMediaTies = new ArrayList<Agent>();
					socialMediaUsers.add(a);
				}
			}

			return socialMediaUsers;
		} catch (Exception e) {
			System.err.println("File input error");
		}

		return null;
	} */
	
	public static void main(String [] args){
		PopulationSynthesis newpop = new PopulationSynthesis(12345);		
	}
}