package main.java.mysim;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import javax.swing.JFrame;

import ec.util.MersenneTwisterFast;
import main.java.utilities.InputCleaning;
import sim.display.Console;
import sim.display.Controller;
import sim.display.Display2D;
import sim.display.GUIState;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.field.geo.GeomVectorField;
import sim.portrayal.geo.GeomPortrayal;
import sim.portrayal.geo.GeomVectorFieldPortrayal;
import sim.portrayal.grid.FastValueGridPortrayal2D;
import sim.portrayal.DrawInfo2D;
import sim.portrayal.FieldPortrayal2D;
import sim.util.Bag;
import sim.util.gui.SimpleColorMap;
import sim.util.media.chart.TimeSeriesChartGenerator;
import swise.disasters.Wildfire;
import swise.visualization.AttributePolyPortrayal;
import swise.visualization.FilledPolyPortrayal;
import swise.visualization.GeomNetworkFieldPortrayal;
//import swise.visualization.TextPortrayal;

/**
 * A visualization of the Hotspots simulation.
 * 
 * @author swise
 */
public class TakamatsuGUI extends GUIState {

	TakamatsuSim sim;
	public Display2D display;
	public JFrame displayFrame;
	
	// Map visualization objects
	private GeomVectorFieldPortrayal water = new GeomVectorFieldPortrayal();
	private GeomVectorFieldPortrayal roads = new GeomVectorFieldPortrayal();
	private GeomVectorFieldPortrayal buildings = new GeomVectorFieldPortrayal();
	private GeomVectorFieldPortrayal agents = new GeomVectorFieldPortrayal();
	private GeomVectorFieldPortrayal shelters = new GeomVectorFieldPortrayal();
	private GeomVectorFieldPortrayal names = new GeomVectorFieldPortrayal();
	
	private GeomNetworkFieldPortrayal network = new GeomNetworkFieldPortrayal();
	private FastValueGridPortrayal2D heatmap = new FastValueGridPortrayal2D();	
		
	///////////////////////////////////////////////////////////////////////////
	/////////////////////////// BEGIN functions ///////////////////////////////
	///////////////////////////////////////////////////////////////////////////	
	
	/** Default constructor */
	public TakamatsuGUI(SimState state) {
		super(state);
		sim = (TakamatsuSim) state;
	}

	/** Begins the simulation */
	public void start() {
		sim.resetForTsunamiScenario();
		sim.evacuationPolicy_designatedPerson = false;
		sim.evacuationPolicy_neighbours = false;
		super.start();
		
		// set up portrayals
		setupPortrayals();
	}

	/** Loads the simulation from a point */
	public void load(SimState state) {
		super.load(state);
		
		// we now have new grids. Set up the portrayals to reflect that
		setupPortrayals();
	}

	/**
	 * Sets up the portrayals of objects within the map visualization. This is called by both start() and by load()
	 */
	public void setupPortrayals() {
		
		TakamatsuSim world = (TakamatsuSim) state;
		water.setField(world.waterLayer);
		water.setPortrayalForAll(new GeomPortrayal(new Color(180,200,250), true));
		water.setImmutableField(true);
		
		roads.setField(world.roadLayer);
		roads.setPortrayalForAll(new GeomPortrayal(new Color(180,180,180), false));
		roads.setImmutableField(true);
		
		buildings.setField(world.buildingLayer);
		buildings.setPortrayalForAll(new GeomPortrayal(new Color(210,210,210), true));
		buildings.setImmutableField(true);
		
		agents.setField(world.agentsLayer);
		agents.setPortrayalForAll( new AttributePolyPortrayal(
				new SimpleColorMap(TakamatsuSim.speed_pedestrian,TakamatsuSim.speed_vehicle, new Color(255,0,0,50), new Color(0,0,255,100)),
				"speed", new Color(0,0,0,0), true, 5));
		
		shelters.setField(world.shelterLayer);
		shelters.setPortrayalForAll(new GeomPortrayal(new Color(255,255,0,90), true));
		shelters.setImmutableField(true);;
		
		names.setField(world.namesLayer);
		//names.setPortrayalForAll(new TextPortrayal("name", new Color(50,50,50), 14));
	//	names.setImmutableField(true);
		
/*		network.setField( world.agentsLayer, world.agentSocialNetwork );
		network.setImmutableField(false);
		network.setPortrayalForAll(new GeomPortrayal(new Color(200,200,50), false));
*/
		heatmap.setField(world.heatmap.getGrid()); 
		heatmap.setMap(new SimpleColorMap(0, 200, Color.black, Color.red));
		
		// reset stuff
		// reschedule the displayer
		display.reset();
		display.setBackdrop(new Color(200,250,240));

		// redraw the display
		display.repaint();
	}

	/** Initializes the simulation visualization */
	public void init(Controller c) {
		super.init(c);

		// the map visualization
		display = new Display2D((int)(1.5 * sim.grid_width), (int)(1.5 * sim.grid_height), this);

		display.attach(heatmap, "Heatmap", false);
		display.attach(water, "Water");
		display.attach(buildings, "Buildings");
		display.attach(shelters, "Shelters");
		display.attach(roads, "Roads");
		display.attach(names, "Names");
		//display.attach(network, "Network", false);
		display.attach(agents, "Agents");
		
		
		// ---TIMESTAMP---
		display.attach(new FieldPortrayal2D()
	    {
			private static final long serialVersionUID = 1L;
			
			Font font = new Font("SansSerif", 0, 24);  // keep it around for efficiency
		    SimpleDateFormat ft = new SimpleDateFormat ("HH:mm zzz");
		    public void draw(Object object, Graphics2D graphics, DrawInfo2D info)
		        {
		        String s = "";
		        if (state !=null) // if simulation has not begun or has finished, indicate this
		            s = state.schedule.getTimestamp("Before Simulation", "Simulation Finished");
		        graphics.setColor(Color.white);
		        if (state != null){
		        	// specify the timestep here
		        	Date startDate;
					try {
						startDate = ft.parse("00:00 GMT");
				        Date time = new Date((int)state.schedule.getTime() * 60000 + startDate.getTime());
				        s = ft.format(time);	
					} catch (ParseException e) {
						e.printStackTrace();
					}
		        }

		        graphics.drawString(s, (int)info.clip.x + 10, 
		                (int)(info.clip.y + 10 + font.getStringBounds(s,graphics.getFontRenderContext()).getHeight()));

		        }
		    }, "Time");
		
		displayFrame = display.createFrame();
		c.registerFrame(displayFrame); // register the frame so it appears in the "Display" list
		displayFrame.setVisible(true);
	}

	/** Quits the simulation and cleans up.*/
	public void quit() {
		super.quit();

		if (displayFrame != null)
			displayFrame.dispose();
		displayFrame = null; // let gc
		display = null; // let gc
	}

	/** Runs the simulation */
	public static void main(String[] args) {
		TakamatsuGUI gui =  null;
		
		try {
			TakamatsuSim lb = new TakamatsuSim(12345);//System.currentTimeMillis());
			gui = new TakamatsuGUI(lb);
		} catch (Exception ex){
			System.out.println(ex.getStackTrace());
		}
		
		Console console = new Console(gui);
		console.setVisible(true);
	}

	/** Returns the name of the simulation */
	public static String getName() { return "SETOSim"; }

	/** Allows for users to modify the simulation using the model tab */
	public Object getSimulationInspectedObject() { return state; }

}