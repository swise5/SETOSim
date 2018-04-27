package utilities;

import java.util.ArrayList;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;

import sim.field.geo.GeomVectorField;
import sim.field.network.Edge;
import sim.field.network.Network;
import sim.util.Bag;
import sim.util.geo.AttributeValue;
import sim.util.geo.MasonGeometry;
import swise.objects.AStar;
import swise.objects.NetworkUtilities;
import swise.objects.network.GeoNode;
import swise.objects.network.ListEdge;

public class RoadNetworkUtilities {
	
	/**
	 * Connect the GeoNode to the given subnetwork using the complete road network
	 * 
	 * @param n - the target node
	 * @param subNetwork - the existing subnetwork
	 */
	public static void connectToMajorNetwork(GeoNode n, Network subNetwork, Network roads) {

		try {
			Bag subNetNodes;			
			subNetNodes = (Bag) subNetwork.allNodes.clone();
			
			// find a path using the whole set of roads in the environment 
			AStar pathfinder = new AStar();
			ArrayList <Edge> edges = pathfinder.astarPath(n, new ArrayList <GeoNode> (subNetNodes), roads);
			
			if(edges == null) return; // maybe no such path exists!

			//  otherwise, add the edges into the subnetwork
			for(Edge e: edges){
				GeoNode a = (GeoNode) e.getFrom(), b = (GeoNode) e.getTo();
				if(!subNetwork.nodeExists(a) || !subNetwork.nodeExists(b))
					subNetwork.addEdge(a, b, e.info);
			}

		} catch (CloneNotSupportedException e1) {
			e1.printStackTrace();
		}
	}
	
	/**
	 * Make sure the network doesn't have any problems
	 * 
	 * @param n - the network to be tested
	 */
	public static void testNetworkForIssues(Network n){
		System.out.println("testing");
		for(Object o: n.allNodes){
			GeoNode node = (GeoNode) o;
			for(Object p: n.getEdgesOut(node)){
				sim.field.network.Edge e = (sim.field.network.Edge) p;
				LineString ls = (LineString)((MasonGeometry)e.info).geometry;
				Coordinate c1 = ls.getCoordinateN(0);
				Coordinate c2 = ls.getCoordinateN(ls.getNumPoints()-1);
				GeoNode g1 = (GeoNode) e.getFrom();
				GeoNode g2 = (GeoNode) e.getTo();
				if(c1.distance(g1.geometry.getCoordinate()) > 1)
					System.out.println("found you");
				if(c2.distance(g2.geometry.getCoordinate()) > 1)
					System.out.println("found you");
			}
		}
	}

	
	/**
	 * Return the GeoNode in the road network which is closest to the given coordinate
	 * 
	 * @param c
	 * @return
	 */
	public static GeoNode getClosestGeoNode(Coordinate c, double resolution, GeomVectorField networkLayer, 
			GeomVectorField networkEdgeLayer, GeometryFactory fa){
		
		// find the set of all nodes within *resolution* of the given point
		Bag objects = networkLayer.getObjectsWithinDistance(fa.createPoint(c), resolution);
		if(objects == null || networkLayer.getGeometries().size() <= 0) 
			return null; // problem with the network layer

		// among these options, pick the best
		double bestDist = resolution; // MUST be within resolution to count
		GeoNode best = null;
		for(Object o: objects){
			double dist = ((GeoNode)o).geometry.getCoordinate().distance(c);
			if(dist < bestDist){
				bestDist = dist;
				best = ((GeoNode)o);
			}
		}
		
		// if there is a best option, return that!
		if(best != null && bestDist == 0) 
			return best;
		
		// otherwise, closest GeoNode is associated with the closest Edge, so look for that!
		
		ListEdge edge = getClosestEdge(c, resolution, networkEdgeLayer, fa);
		
		// find that edge
		if(edge == null){
			edge = getClosestEdge(c, resolution * 10, networkEdgeLayer, fa);
			if(edge == null)
				return null;
		}
		
		// of that edge's endpoints, find the closer of the two and return it
		GeoNode n1 = (GeoNode) edge.getFrom();
		GeoNode n2 = (GeoNode) edge.getTo();
		
		if(n1.geometry.getCoordinate().distance(c) <= n2.geometry.getCoordinate().distance(c))
			return n1;
		else 
			return n2;
	}

	/**
	 * Return the ListEdge in the road network which is closest to the given coordinate, within the given resolution
	 * 
	 * @param c
	 * @param resolution
	 * @return
	 */
	public static ListEdge getClosestEdge(Coordinate c, double resolution, GeomVectorField networkEdgeLayer, GeometryFactory fa){

		// find the set of all edges within *resolution* of the given point
		Bag objects = networkEdgeLayer.getObjectsWithinDistance(fa.createPoint(c), resolution);
		if(objects == null || networkEdgeLayer.getGeometries().size() <= 0) 
			return null; // problem with the network edge layer
		
		Point point = fa.createPoint(c);
		
		// find the closest edge among the set of edges
		double bestDist = resolution;
		ListEdge bestEdge = null;
		for(Object o: objects){
			double dist = ((MasonGeometry)o).getGeometry().distance(point);
			if(dist < bestDist){
				bestDist = dist;
				bestEdge = (ListEdge) ((AttributeValue) ((MasonGeometry) o).getAttribute("ListEdge")).getValue();
			}
		}
		
		// if it exists, return it
		if(bestEdge != null)
			return bestEdge;
		
		// otherwise return failure
		else
			return null;
	}
	
	
	
	/**
	 * Extract the major roads from the road network
	 * @return a connected network of major roads
	 */
	public static Network extractMajorRoads(Network roads){
		Network majorRoads = new Network();
		
		// go through all nodes
		for(Object o: roads.getAllNodes()){
		
			GeoNode n = (GeoNode) o;
			
			// go through all edges
			for(Object p: roads.getEdgesOut(n)){
				
				sim.field.network.Edge e = (sim.field.network.Edge) p;
				String type = ((MasonGeometry)e.info).getStringAttribute("highway");
				
				// save major roads
				if(type.equals("major"))
						majorRoads.addEdge(e.from(), e.to(), e.info);
			}
		}
		
		// merge the major roads into a connected component
		NetworkUtilities.attachUnconnectedComponents(majorRoads, roads);
		
		return majorRoads;
	}
}