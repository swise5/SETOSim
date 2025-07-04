package uk.ac.ucl.SETOSim.myobjects;

import java.util.HashSet;

import sim.field.network.Edge;
import uk.ac.ucl.swise.objects.network.GeoNode;

public class SpatialMentalModel {
	HashSet knownNodes;
	HashSet knownEdges;
	HashSet unexploredNodes;
	
	public SpatialMentalModel() {
		knownNodes = new HashSet <GeoNode> ();
		knownEdges = new HashSet <Edge> ();
		unexploredNodes = new HashSet <GeoNode> ();
	}
	
	public boolean knowsAboutNode(GeoNode node) { return knownNodes.contains(node); }
	public boolean knowsAboutEdge(Edge edge) { return knownEdges.contains(edge); }
	
	// it has explored it if it knows about it AND IT HAS EXPLORED IT
	public boolean hasExploredNode(GeoNode node) { return knownNodes.contains(node) && !unexploredNodes.contains(node); }
	
	public void addNode(Object node) { knownNodes.add(node);}
	public void addEdge(Object edge) { knownEdges.add(edge);}
	public void addEdge(Edge edge) {
		knownEdges.add(edge);
		GeoNode from = (GeoNode) edge.getFrom(), to = (GeoNode) edge.getTo();
		
		// add any node that is newly found to the set of unexplored nodes
		if(!knownNodes.contains(from)) unexploredNodes.add(from);
		if(!knownNodes.contains(to)) unexploredNodes.add(to);
		
		// record these nodes; it's a hashset, so it would be more expensive to check than it is
		// to just shove 'em in there and see what happens
		addNode(from); addNode(to);
	}
	public void addEdges(Object [] edges) {
		for(Object o: edges)
			addEdge(o);
	}
	
	public void exploreNode(GeoNode node) { unexploredNodes.remove(node); }
	
	public HashSet getAllNodes() { return knownNodes; }
	public HashSet getAllEdges() { return knownEdges; }
}