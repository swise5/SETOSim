package main.utilities;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import sim.field.grid.DoubleGrid2D;

public class BulkComparisons {

	String dirName = "/Users/swise/Projects/hitomi/data/";
	String heatmapOutputFile = "basicResultsAggregate.txt", personOutputFile = "journeyResultsAggregate.txt";
	String heatmapInputDir = "heatmaps/", individualInputDir = "infos/", fileType = ".txt";

	public void compareHeatmaps(){

		FileInputStream fstream;
		HashMap<String, ArrayList<Double>> recordOfRoads = new HashMap<String, ArrayList<Double>>();
		int count = 0;

		try {

			// go through each of the relevant files and read in the data

			File folder = new File(dirName + heatmapInputDir);
			File[] runFiles = folder.listFiles();

			for (File f : runFiles) {

				String filename = f.getName();
				if (!filename.endsWith(fileType)) // only specific kinds of files
					continue;

				// open the demographic file
				fstream = new FileInputStream(f.getAbsoluteFile());

				// Convert our input stream to a BufferedReader
				BufferedReader rawResults = new BufferedReader(new InputStreamReader(fstream));

				// set up the holder
				String s = rawResults.readLine();

				// iterate over and take out each line
				while ((s = rawResults.readLine()) != null) {
					if (!s.startsWith("myid"))
						continue;
					String[] bits = s.split("\t");
					String key = bits[0];
					double value = Double.parseDouble(bits[1]);
					if (recordOfRoads.containsKey(key))
						recordOfRoads.get(key).add(value);
					else {
						ArrayList<Double> newList = new ArrayList<Double>();
						newList.add(value);
						recordOfRoads.put(key, newList);
					}
				}
				
				// keep track of the number of runs done
				count++;
			}

			BufferedWriter w = new BufferedWriter(new FileWriter(dirName + heatmapOutputFile));
			
			w.write("Key\tAvg\tStdev\tMedian\tMin\tMax\n");
			// print out the results!
			for (String myKey : recordOfRoads.keySet()) {
				ArrayList<Double> myRecords = recordOfRoads.get(myKey);
				
				// amend to proper size - some may have been 0!
				while(myRecords.size() < count){
					myRecords.add(0.);
				}

				double avg = avg(myRecords);
				double stdev = stdev(myRecords, avg);
				Collections.sort(myRecords);
				double med = myRecords.get(myRecords.size()/ 2), min = myRecords.get(0), max = myRecords.get(myRecords.size() - 1);
				
				w.write(myKey + "\t" + avg + "\t" + stdev + "\t" + med + "\t" + min + "\t" + max + "\n");
			}

			w.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void compareIndividuals(){

		FileInputStream fstream;
		HashMap<String, ArrayList<Double>> recordOfIndividuals = new HashMap<String, ArrayList<Double>>();
		int count = 0;

		try {

			// go through each of the relevant files and read in the data

			File folder = new File(dirName + individualInputDir);
			File[] runFiles = folder.listFiles();

			for (File f : runFiles) {

				String filename = f.getName();
				if (!filename.endsWith(fileType)) // only specific kinds of files
					continue;

				// open the demographic file
				fstream = new FileInputStream(f.getAbsoluteFile());

				// Convert our input stream to a BufferedReader
				BufferedReader rawResults = new BufferedReader(new InputStreamReader(fstream));

				// set up the holder
				String s = rawResults.readLine();

				// iterate over and take out each line
				while ((s = rawResults.readLine()) != null) {

					String[] bits = s.split("\t");
					String key = bits[0];
					String completed = bits[2];
					if(completed.equals("INCOMPLETE")) // don't read in the incomplete ones!!
						continue;
					double value = Double.parseDouble(bits[3]);
					if (recordOfIndividuals.containsKey(key))
						recordOfIndividuals.get(key).add(value);
					else {
						ArrayList<Double> newList = new ArrayList<Double>();
						newList.add(value);
						recordOfIndividuals.put(key, newList);
					}
				}
				
				// keep track of the number of runs done
				count++;
			}

			BufferedWriter w = new BufferedWriter(new FileWriter(dirName + personOutputFile));
			
			w.write("Key\tAvg\tStdev\tMedian\tMin\tMax\n");
			// print out the results!
			for (String myKey : recordOfIndividuals.keySet()) {
				ArrayList<Double> myRecords = recordOfIndividuals.get(myKey);
				Collections.sort(myRecords);
				double max = myRecords.get(myRecords.size() - 1);
				if(max == -1)
					continue; // didn't evacuate - boring!!!
				int lastIndex = myRecords.lastIndexOf(-1);
				ArrayList <Double> cleanedRecords;
				if(lastIndex >= 0)
					cleanedRecords = new ArrayList <Double> (myRecords.subList(lastIndex, myRecords.size()));
				else
					cleanedRecords = myRecords;
				
				double avg = avg(cleanedRecords);
				double stdev = stdev(cleanedRecords, avg);
				double med = cleanedRecords.get(cleanedRecords.size()/ 2), min = cleanedRecords.get(0);
				
				w.write(myKey + "\t" + avg + "\t" + stdev + "\t" + med + "\t" + min + "\t" + max + "\n");
			}

			w.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	public BulkComparisons() {
		compareIndividuals();
		compareHeatmaps();
	}

	public static double avg(ArrayList <Double> ds){
		double result = 0;
		for(Double d: ds)
			result += d;
		return result / ds.size();
	}
	
	public static double stdev(ArrayList <Double> ds){
		return stdev(ds, avg(ds));
	}
	
	public static double stdev(ArrayList <Double> ds, double avg){
		double result = 0;
		for(Double d: ds){
			result += Math.pow(d - avg, 2);
		}
		return Math.sqrt(result / (ds.size() - 1));
	}
	
	public static void main(String [] args){
		BulkComparisons bch = new BulkComparisons();
	}
}