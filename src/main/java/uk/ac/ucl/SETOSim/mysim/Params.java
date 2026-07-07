package uk.ac.ucl.SETOSim.mysim;

import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

import java.time.LocalDateTime;

public class Params {
	
	public boolean verbose = true;
	
	// SPACE
	
	public int grid_width = 800;
	public int grid_height = 400;
	
	// spatial granularity of the simulation (fiddle to merge nodes into one another)
	public static double resolution = 1; 

	// TIME
	
	// each tick represent 1 minute
	public static double ticks_per_hour = 60; 
	public static double ticks_per_day = ticks_per_hour * 24;
	

	public static LocalDateTime simulationStart = LocalDateTime.of(2019, 10, 12, 0, 0);
	
	// SPEED

	public boolean ageSpecificSpeeds = true;

	// define speeds in terms of TICKS 
	// or here, meters per second - so convert to 1-min tick
	public static double speed_pedestrian = 1.5 * 60;
	public static double speed_elderlyYoung = 1 * 60; 
	public static double speed_vehicle = 5.5 * 60; // ~20kph

	public HashMap <String, Double> typeWeighting_vehicle;
	public HashMap <String, Double> typeWeighting_pedestrian;

	// EVACUATION BEHAVIOUR
	
	public boolean evacuationPolicy_neighbours = false;
	public boolean evacuationPolicy_designatedPerson = false;
	public boolean evacuationPolicy_verticalEvacuation = true; // turn this off for fires etc
	public static double verticalEvacMinHeightRequirement = 1; // in terms of levels of the building? 
	public static double neighbourDistance = 100; // meters

	public static double rayleigh_sigma = 2; // from Wang et al, http://dx.doi.org/10.1016/j.trc.2015.11.010
	public static double hazardThresholdDistance = 1; // meters
	public double compliance = 1.;
	public static double preparation_time = 30;

	// dummies used to test following behaviours
	public boolean leaderSet = false;
	public void setLeader() {leaderSet = true;}


	// HAZARD
	
	public boolean tsunami = false;
	public static int forecastArrivalTime = (int)(15 * ticks_per_hour); // in ticks
	public static int forecastingWidthParam = 720; // in ticks
	public static int forecastNoticePeriod = (int) (6 * ticks_per_hour); // in ticks
	public static String roadInundationColumnName = null;// "depth";
	public static double roadInundationImpassableDepth = .001; // beyond this point, roads are impassable
	
	// POPULATION SETUP
	
	public double percIgnored = .99;// percent TO OMIT
	public int numCommutersOutbound = 0;//21331; // TODO this is a hack for Okazaki demo
	public int numCommutersInbound = 0;//16458;
	
	public double likelihoodOfOwningVehicle = .6;

	// REPORTING
	public int road_reporting_step_interval = 5; // flow every 5 minutes
	
	/////////////// Data Sources ///////////////////////////////////////
	
	public String dirName = "data/takamatsuTsunamiDemo/";//okazakiDemo/";//ritsurinDemo/";
		
	public static String agentFilename = "dummyPop.txt";//"synthPop_hh.txt";//

	public static boolean verticalEvacEnabled = true;
	//public static String regionalNamesFilename = "defaultRitsurinFiles/regionalNames.shp";
	public String floodedFilename = "flooded_reproj.shp";//"simplifiedWater.shp";//"selectedWater.shp";//"TakamatsuTyphoon16.shp";
	public String waterFilename = "water.shp";//"waterBaselayer.shp";//"selectedWater.shp";//"defaultRitsurinFiles/TakamatsuWaterAll.shp";
	public String sheltersFilename = "sheltersWithParking.shp";//"/Users/swise/Projects/hitomi/data/OkazakiABM/01_Shelter_OkazakiOpenData/sheltersWithParking.shp";
	public String buildingsFilename = "buildings_reproj.shp";//"/Users/swise/Projects/hitomi/data/OkazakiABM/06_Buildings/buildings_reproj.shp";//buildings10m.shp";//"uglyHouses.shp";//"defaultRitsurinFiles/Ritsurin.shp";
	public String roadsFilename = "roads_simple.shp";//"simpleRoads_withInundation.shp";//"ACTGOV_ROAD_CENTRELINES_-8699904174011627171/ACTGOV_ROAD_CENTRELINES.shp";//"defaultRitsurinFiles/RitsurinRoads.shp";
	public String stationFilename = ""; //"trainStationsWithPassengers.shp";
	public String evacuationAreasFilename = "evac_areas.shp";//"EvacuationOrderScenario/EvacuationOrderArea_reproj.shp";//"evacZonesInMeters.shp";
	public String evacuationAreaIDColumnName = "areaID";
	public String evacuationScenarioFilename = "";//"EvacuationOrderScenario/L2-L1-10-N.csv"; // "" - if no timing info!
	
	public String buildingUniqueCodeColname = "id_text";
	public static String verticalEvacuationColname = "vert_evac";
	
	public String weightedRoadAttribute = "highway";//"HIERARCHY";//
	public Boolean universalVehicles = true;
/*
    // Woden case
	public String sheltersFilename = "bushfireWodenShelter.shp";
	public String buildingsFilename = "buildings.shp";
	public String roadsFilename = "bushfireWodenRoads.shp";
	public String weightedRoadAttribute = "HIERARCHY";
*/
	
/*	String record_speeds_filename = "output/speeds", 
			record_sentiment_filename = "output/sentiment",
			record_heatmap_filename = "output/heatmap",
			record_info_filename = "output/bifurc_info";
*/

	// EXPORTS
	
	
	public String outputPrefix = null;	
	boolean exportHeatmap = false; // export the heatmap or no?
	boolean exportRoadUsage = true;
	

	public Params(String paramsFilename, boolean isVerbose){
		this.verbose = isVerbose;
		// Read in parameter file locations
		readInParamFile(paramsFilename);
	}
	
	//
	// DATA IMPORT UTILITIES
	//
	
	public void readInParamFile(String paramFilename) {
		if(verbose)
			System.out.println("Reading in data from " + paramFilename);
		
		// Open the tracts file
		FileInputStream fstream;
		try {
			fstream = new FileInputStream(paramFilename);

			// Convert our input stream to a BufferedReader
			BufferedReader paramFile = new BufferedReader(new InputStreamReader(fstream));
			String s;

			while ((s = paramFile.readLine()) != null) {
				
				// skip comments
				if(s.length() == 0 || s.charAt(0) == '#')
					continue;
				
				// extract all other parameters
				String [] bits = s.split(":");
				Field f = this.getClass().getDeclaredField(bits[0].trim());
				f.setAccessible(true);
				String myVal = bits[1].trim();
				
				try {
					if(myVal.contains("."))
						f.set(this, Double.parseDouble(myVal));
					else
						f.set(this, Integer.parseInt(myVal));
				} catch (Exception e){
					if(myVal.equals("true") || myVal.equals("false"))
						f.set(this, Boolean.parseBoolean(myVal));
					else f.set(this, myVal);	
				}
			}			
		paramFile.close();
		} catch (Exception e) {
			e.printStackTrace();
			fail();
		}


	}
	
	// if the string begins with a /, then it should not be added to the dirname
	public String formatInputFilename(String filename) {
		if(filename.startsWith("/")) return filename;
		else
			return dirName + filename;
	}

	public static double rayleighDistrib(double unif){
		double x = rayleigh_sigma * Math.sqrt(-2 * Math.log(unif)); // see https://en.wikipedia.org/wiki/Rayleigh_distribution#Generating_random_variates
		return x;
	}
}