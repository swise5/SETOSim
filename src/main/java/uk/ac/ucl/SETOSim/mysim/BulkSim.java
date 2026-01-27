package uk.ac.ucl.SETOSim.mysim;

public class BulkSim {
	
	public static void main(String [] args){
		
/*		boolean [] ageEnabled = new boolean [] {true};
		boolean [] neighbourPolicy = new boolean [] {false};
		boolean [] designatedHelperPolicy = new boolean [] {false};
		String [] floodwaterFilename = new String [] {//"RitsurinDemo/emptyFloodingFile.shp", 
				"simplifiedWater.shp"
				};
		String agentFilename = "dummyPop.txt";
		boolean [] isTsunami = new boolean [] {//true,  
				false};

		
		boolean [] commuterPolicy = new boolean [] {false};//, true};

		Integer [] timelengths = new Integer [] {60 * 24};
		
		int index = 0;
		for(int j = 0; j < floodwaterFilename.length; j++) {
			for(int i = 51; i < 53; i++) {
				
				for(boolean commuters: commuterPolicy) {
					runInstance(i, commuters, //true, k, false, 
							floodwaterFilename[j], timelengths[j], isTsunami[j]);
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
		
		String paramFile = "src/main/resources/params_default.txt";
		Boolean [] verticalEvac = {false};
		
		for(Boolean b: verticalEvac) {
			String [] bits = paramFile.split("/");
			String scenarioName = bits[bits.length - 1].replace('.', '_');
			if(b) 
				scenarioName += "_vertEvac";
			else
				scenarioName += "_noVertEvac";
			
			for(int i = 20; i < 40; i++) {
				runInstanceFromParamFile(i, paramFile, scenarioName, 60, b);
			}
		}
	}
	
	public static void runInstance(int seed, boolean commuters,
			//boolean ageEnabled, boolean neighbourPolicy, boolean designatedHelperPolicy, 
			String floodFilename, int time, Boolean isTsunami) {
		
		TakamatsuSim takamatsuModel = new TakamatsuSim(seed);
		/*takamatsuModel.ageSpecificSpeeds = ageEnabled;
		takamatsuModel.evacuationPolicy_neighbours = neighbourPolicy;
		takamatsuModel.evacuationPolicy_designatedPerson = designatedHelperPolicy;
		*/
		takamatsuModel.params.floodedFilename = floodFilename;

		String commutersCase = "commuters";
		if(!commuters) {
			commutersCase = "noCommuters";
			takamatsuModel.params.numCommutersInbound = 0;
			takamatsuModel.params.numCommutersOutbound = 0;
		}
		
		//String moddedFloodFilename = floodFilename.replace("/", "-");
		String outputFilename = "/Users/swise/Projects/hitomi/data/transit/output/evacZones_" + commutersCase;
		
				
				//ageEnabled + "_" + neighbourPolicy + "_" + designatedHelperPolicy + "_" + moddedFloodFilename + "_";
		takamatsuModel.params.outputPrefix = outputFilename;
		takamatsuModel.params.evacuationAreasFilename = takamatsuModel.params.dirName + "evacZonesInMeters.shp";
		
//		if(isTsunami)
//			takamatsuModel.resetForTsunamiScenario();
		takamatsuModel.start();

		System.out.print("Running...");

		double lastTime = 0;
		while(takamatsuModel.schedule.getTime() < time){ // ONLY 3 DAYS
			takamatsuModel.schedule.step(takamatsuModel);
			//System.out.println(takamatsuModel.schedule.getTime());
			if(takamatsuModel.schedule.getTime() > lastTime + 100) {
				System.out.print('.');
				lastTime = takamatsuModel.schedule.getTime();
			}
		}
		
		takamatsuModel.finish();
		
		System.gc();

	}
	
	public static void runInstanceFromParamFile(int seed, String paramFileName, String stubOutputName, int time, boolean vertEvac) {
		
		TakamatsuSim takamatsuModel = new TakamatsuSim(seed, paramFileName);

		//String moddedFloodFilename = floodFilename.replace("/", "-");
		String outputFilename = "/Users/swise/Projects/hitomi/data/transit/output/results/out_" + stubOutputName;
		
		takamatsuModel.params.outputPrefix = outputFilename;
		takamatsuModel.params.verticalEvacEnabled = vertEvac;
	
		takamatsuModel.start();

		System.out.print("Running...");

		double lastTime = 0;
		while(takamatsuModel.schedule.getTime() < time){
			takamatsuModel.schedule.step(takamatsuModel);
			if(takamatsuModel.schedule.getTime() > lastTime + 100) {
				System.out.print('.');
				lastTime = takamatsuModel.schedule.getTime();
			}
		}
		
		takamatsuModel.finish();
		
		System.gc();

	}
}