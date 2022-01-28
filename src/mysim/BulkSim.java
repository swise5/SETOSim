package mysim;

public class BulkSim {
	
	public static void main(String [] args){
		
		boolean [] ageEnabled = new boolean [] {true};
		boolean [] neighbourPolicy = new boolean [] {true, false};
		boolean [] designatedHelperPolicy = new boolean [] {false,  true};
		String [] floodwaterFilename = new String [] {"RitsurinDemo/TakamatsuWaterFlooded.shp", 
				"RitsurinDemo/TakamatsuTyphoon16.shp"
				};
		
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

	}
	
	public static void runInstance(int seed, boolean ageEnabled, boolean neighbourPolicy, 
			boolean designatedHelperPolicy, String floodFilename) {
		
		TakamatsuSim takamatsuModel = new TakamatsuSim(seed);
		takamatsuModel.ageSpecificSpeeds = ageEnabled;
		takamatsuModel.evacuationPolicy_neighbours = neighbourPolicy;
		takamatsuModel.evacuationPolicy_designatedPerson = designatedHelperPolicy;
		takamatsuModel.floodedFilename = floodFilename;

		String moddedFloodFilename = floodFilename.replace("/", "-");
		String outputFilename = "/Users/swise/Projects/hitomi/data/elderDemo/output/sweeper_" + 
				ageEnabled + "_" + neighbourPolicy + "_" + designatedHelperPolicy + "_" + moddedFloodFilename + "_";
		takamatsuModel.outputPrefix = outputFilename;
		
		takamatsuModel.start();

		System.out.print("Running...");

		while(takamatsuModel.schedule.getTime() < 60 * 24 * 3){ // ONLY 3 DAYS
			takamatsuModel.schedule.step(takamatsuModel);
			//System.out.println(takamatsuModel.schedule.getTime());
		}
		
		takamatsuModel.finish();
		
		System.gc();

	}
}