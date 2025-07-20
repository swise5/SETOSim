package uk.ac.ucl.SETOSim.utilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

import sim.field.geo.GeomGridField;
import sim.field.geo.GeomVectorField;
import sim.field.geo.GeomGridField.GridDataType;
import sim.field.grid.IntGrid2D;
import sim.field.network.Network;
import sim.io.geo.ArcInfoASCGridImporter;
import sim.io.geo.ShapeFileImporter;
import sim.util.Bag;
import sim.util.geo.MasonGeometry;
import uk.ac.ucl.swise.objects.NetworkUtilities;
import uk.ac.ucl.swise.objects.network.GeoNode;

public class InputCleaning {
	
	//////////////////////////////////////////////
	////////// UTILITIES /////////////////////////
	//////////////////////////////////////////////

	
	/**
	 * Coordinate reader helper function
	 * @param s
	 * @return
	 */
	public static Coordinate readCoordinateFromFile(String s){
		if(s.equals("")) 
			return null;
		
		String [] bits = s.split(" ");
		Double x = Double.parseDouble( bits[1].substring(1) );
		Double y = Double.parseDouble(bits[2].substring(0, bits[2].length() - 2));
		return new Coordinate(x,y);
	}
	
	/**
	 * Method to read in a vector layer
	 * @param layer
	 * @param filename
	 * @param layerDescription
	 * @param attributes - optional: include only the given attributes
	 */
	public static synchronized GeomVectorField readInVectorLayer(String filename, int width, int height, String layerDescription, Bag attributes){
		GeomVectorField layer = new GeomVectorField(width, height);
		try {
				System.out.print("Reading in " + layerDescription + "from " + filename + "...");
				File file = new File(filename);
				if(attributes == null || attributes.size() == 0)
					ShapeFileImporter.read(file.toURL(), layer);
				else
					ShapeFileImporter.read(file.toURL(), layer, attributes);
				System.out.println("done");	

		} catch (Exception e) {
			e.printStackTrace();
		}
		return layer;
	}
	
	/**
	 * Method ot read in a raster layer
	 * @param layer
	 * @param filename
	 * @param layerDescription
	 * @param type
	 */
	public static synchronized GeomGridField readInRasterLayer(String filename, String layerDescription, GridDataType type){
		GeomGridField layer = new GeomGridField();
		try {
				
				System.out.print("Reading in " + layerDescription + "from " + filename + "...");
				layer = new GeomGridField();
				FileInputStream fstream = new FileInputStream(filename);
				ArcInfoASCGridImporter.read(fstream, type, layer);
				fstream.close();
				System.out.println("done");

		} catch (Exception e) {
			e.printStackTrace();
		}
		return layer;
	}
	
	public static synchronized ArrayList <String> readInTextDataAsLines(String filename, String layerDescription){
		ArrayList <String> results = new ArrayList <String> ();
		try {				
				System.out.print("Reading in " + layerDescription + "from " + filename + "...");
				
				//Path path = FileSystems.getDefault().getPath(filename);
			  //  BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_16);
				
				FileInputStream fstream = new FileInputStream(filename);
				BufferedReader rawFile = new BufferedReader(new InputStreamReader(fstream));
				String s;
				while((s = rawFile.readLine()) != null) {
					String lessRaw = s.replaceAll("[^\\p{Graph}\n\r\t ]", "");
					results.add(lessRaw);
				};
				rawFile.close();
				//Path path = Paths.get(filename);
				//List <String> nonsense  = Files.readAllLines(path, StandardCharsets.ISO_8859_1); //.UTF_8);
				System.out.println("done");

//				results.addAll(nonsense);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return results;
	}
		
	/**
	 * Convenient method for incrementing the heatmap
	 * @param geom - the geometry of the object that is impacting the heatmap
	 */
	public static void incrementHeatmap(Geometry geom, GeomGridField heatmap, Envelope MBR){
		Point p = geom.getCentroid();
		
		int x = (int)(heatmap.getGrid().getWidth()*(MBR.getMaxX() - p.getX())/(MBR.getMaxX() - MBR.getMinX())), 
				y = (int)(heatmap.getGrid().getHeight()*(MBR.getMaxY() - p.getY())/(MBR.getMaxY() - MBR.getMinY()));
		if(x >= 0 && y >= 0 && x < heatmap.getGrid().getWidth() && y < heatmap.getGrid().getHeight())
			((IntGrid2D) heatmap.getGrid()).field[x][y]++;
	}
	
	
}