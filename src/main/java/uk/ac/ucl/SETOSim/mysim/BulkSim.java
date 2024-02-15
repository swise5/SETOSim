package uk.ac.ucl.SETOSim.mysim;

public class BulkSim {
	
	public static void main(String [] args){
		
		boolean [] ageEnabled = new boolean [] {true};
		boolean [] neighbourPolicy = new boolean [] {false, true};
		boolean [] designatedHelperPolicy = new boolean [] {true};
		String [] floodwaterFilename = new String [] {//"RitsurinDemo/emptyFloodingFile.shp", 
				"RitsurinDemo/TakamatsuTyphoon16.shp"
				};
		String agentFilename = "RitsurinDemo/synthPop_Ritsurin.txt";
		boolean [] isTsunami = new boolean [] {//true,  
				false};
		Integer [] timelengths = new Integer [] {720 + 180, 720 + 180};
		
		int index = 0;
		for(int j = 0; j < floodwaterFilename.length; j++) {
			for(int i = 0; i < 10; i++) {
				for(boolean k: neighbourPolicy) {
					runInstance(i, true, k, false, floodwaterFilename[j], timelengths[j], isTsunami[j]);
					String tsunamiFriend = "standard";
					if(isTsunami[j])
						tsunamiFriend = "tsunami";
					System.out.println(index + "\t" + i + "\t" + false + "\t" 
							+ "/home/uceswis/Scratch/takamatsu/sweep_" + tsunamiFriend + "_" // output filename
							+ "\t" + floodwaterFilename[j] + "\t" 
							+ agentFilename + "\t" + false + "\t" + false + "\t" + isTsunami[j] + "\t" + timelengths[j]);
					index++;
					
				}
			}
		}
		
		/*
		for(boolean helper: designatedHelperPolicy) {
			for(boolean neighbour: neighbourPolicy) {
				for(boolean age: ageEnabled) {
					for(String filename: floodwaterFilename) {
						for(int i = 10; i < 20; i++){
							runInstance(i, age, neighbour, helper, filename);
						}
					}
				}
			}
		}
*/
	}
	
	public static void runInstance(int seed, boolean ageEnabled, boolean neighbourPolicy, 
			boolean designatedHelperPolicy, String floodFilename, int time, Boolean isTsunami) {
		
		TakamatsuSim takamatsuModel = new TakamatsuSim(seed);
		takamatsuModel.ageSpecificSpeeds = ageEnabled;
		takamatsuModel.evacuationPolicy_neighbours = neighbourPolicy;
		takamatsuModel.evacuationPolicy_designatedPerson = designatedHelperPolicy;
		takamatsuModel.floodedFilename = floodFilename;

		String moddedFloodFilename = floodFilename.replace("/", "-");
		String outputFilename = "/Users/swise/Projects/hitomi/data/tsunami/output/multiOutputsTyphoon_" + 
				ageEnabled + "_" + neighbourPolicy + "_" + designatedHelperPolicy + "_" + moddedFloodFilename + "_";
		takamatsuModel.outputPrefix = outputFilename;
		
		if(isTsunami)
			takamatsuModel.resetForTsunamiScenario();
		takamatsuModel.start();

		System.out.print("Running...");

		while(takamatsuModel.schedule.getTime() < time){ // ONLY 3 DAYS
			takamatsuModel.schedule.step(takamatsuModel);
			//System.out.println(takamatsuModel.schedule.getTime());
		}
		
		takamatsuModel.finish();
		
		System.gc();

	}
}