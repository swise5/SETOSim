package takamatsu.utilities;

import com.vividsolutions.jts.geom.Coordinate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.TreeSet;

import sim.field.network.Edge;
import sim.field.network.Network;
import sim.util.geo.AttributeValue;
import sim.util.geo.MasonGeometry;
import swise.objects.network.GeoNode;
import swise.objects.network.ListEdge;

/**
 * An object held by the overall environment, upon which agents can plan their routes. Holds
 * the structures so that they need not be remade. When a node is "discovered", the index of the 
 * search is compared to see whether it has been found in the current active search. This prevents
 * having to regenerate the A* infrastructure each time.
 * 
 * @author swise
 *
 */
public class AStar
{

	HashMap <GeoNode, AStarNodeWrapper> previouslyFoundNodes;
	int searchIndex = -1;
	
	public AStar(){
		previouslyFoundNodes = new HashMap <GeoNode, AStarNodeWrapper> ();
	}
	
	/**
	 * Adds a new node to the infrastructure, so that it doesn't have to be regenerated
	 * 
	 * @param gn - the node to be wrapped and saved
	 * @return the new wrapper object
	 */
	AStarNodeWrapper addNewNode(GeoNode gn){
		AStarNodeWrapper newWrapper = new AStarNodeWrapper(gn);
		previouslyFoundNodes.put(gn, newWrapper);
		return newWrapper;
	}
	
	/**
	 * Finds a path between the start and goal nodes within the given network
	 * 
	 * @param start - the node from which to start
	 * @param goal - the goal node
	 * @param network - the network in which the path should exist
	 * @return either an ordered list of the connecting edges or else null
	 */
	public ArrayList<Edge> astarPath(GeoNode start, GeoNode goal, Network network)
   {
		// update the overall object
		searchIndex++;

        // initial check
        if (start == null || goal == null){
            System.out.println("Error: invalid node provided to AStar");
            return null;
        }

        // if they're the same place, the path is empty but certainly exists
        if(start == goal)
        	return new ArrayList<Edge> ();

        
        // either fetch the starting node objects or create new ones
        AStarNodeWrapper startNode = previouslyFoundNodes.get(start);
        if(startNode == null)
        	startNode = addNewNode(start);
        else
        	startNode.reset(searchIndex);
        
        startNode.bestFoundCostToHere = 0;
        startNode.estimatedCostFromHereToGoal = heuristic(start, goal);
        startNode.estimatedOverallCost = startNode.estimatedCostFromHereToGoal;

        // either fetch the goal node objects or create new ones
        AStarNodeWrapper goalNode = previouslyFoundNodes.get(goal);
        if(goalNode == null)
        	goalNode = addNewNode(goal);
        else
        	goalNode.reset(searchIndex);
        
        
        // A* containers: nodes to be investigated, nodes that have been investigated
        HashSet <AStarNodeWrapper> closedSet = new HashSet <AStarNodeWrapper>();
        
        TreeSet <AStarNodeWrapper> myOpenSet = new TreeSet <AStarNodeWrapper> (new Comparator<Object>(){

			@Override
			public int compare(Object o1, Object o2) {
				double o1Cost = ((AStarNodeWrapper)o1).estimatedOverallCost,
						o2Cost = ((AStarNodeWrapper)o2).estimatedOverallCost;
				if(o1Cost < o2Cost) return -1;
				else if(o1Cost == o2Cost) return 0;
				return 1;
			}
        	
        });
        myOpenSet.add(startNode);

        // LOOP OVER THE FOUND NODES UNTIL A PATH IS FOUND (or you run out of nodes to search)
        while (myOpenSet.size() > 0) { // while there are reachable nodes to investigate

            AStarNodeWrapper x = myOpenSet.pollFirst(); // find the shortest path so far
            if (x.node == goal)
            { // we have found the shortest possible path to the goal!
                // Reconstruct the path and send it back.
                return reconstructPath(goalNode);
            }
            closedSet.add(x);

            // check all the edges out from this Node
           for (Object o : network.getEdges(x.node, null))
           {
            	Edge l = (Edge) o;
                GeoNode next = null;
                next = (GeoNode) l.getOtherNode(x.node);
                
                // get the A* meta information about this Node
                AStarNodeWrapper nextNode = previouslyFoundNodes.get(next);
                if(nextNode == null)
                	nextNode = addNewNode(next);
                else if(nextNode.indexOfSearch < searchIndex)
                	nextNode.reset(searchIndex);

                // make sure we haven't already considered and rejected it
                if (closedSet.contains(nextNode)) // it has already been considered
                    continue;

                // otherwise evaluate the cost of this node/edge combo
                double currentPathCost = x.bestFoundCostToHere + length(l);
                
                boolean updateInfo = false;

                // previously unfound? add it to the list!
                if (!myOpenSet.contains(nextNode)) {

                	nextNode.estimatedCostFromHereToGoal = heuristic(next, goal);

                    nextNode.cameFrom = x;
                    nextNode.edgeFrom = l;
                    nextNode.bestFoundCostToHere = currentPathCost;
                    nextNode.estimatedOverallCost = nextNode.bestFoundCostToHere + nextNode.estimatedCostFromHereToGoal;

                    myOpenSet.add(nextNode);
                } 
                
                // better than the existing value? Update it!
                else if (currentPathCost < nextNode.bestFoundCostToHere){
                	myOpenSet.remove(nextNode);
                    nextNode.cameFrom = x;
                    nextNode.edgeFrom = l;
                    nextNode.bestFoundCostToHere = currentPathCost;
                    nextNode.estimatedOverallCost = nextNode.bestFoundCostToHere + nextNode.estimatedCostFromHereToGoal;
                    myOpenSet.add(nextNode);
                }
                
            }
        }

//        System.out.println("A* Problem: graph has only " + closedSet.size() + " nodes associated with it");
        return null;
    }


	/**
	 * Finds a path between the start and goal nodes within the given network
	 * 
	 * @param start - the node from which to start
	 * @param goal - the goal node
	 * @param network - the network in which the path should exist
	 * @return either an ordered list of the connecting edges or else null
	 */
	public ArrayList<Edge> astarWeightedPath(GeoNode start, GeoNode goal, Network network, String weightedField,
			HashMap <String, Double> weightings){
		
		// update the overall object
		searchIndex++;

        // initial check
        if (start == null || goal == null){
            System.out.println("Error: invalid node provided to AStar");
            return null;
        }

        // if they're the same place, the path is empty but certainly exists
        if(start == goal)
        	return new ArrayList<Edge> ();

        
        // either fetch the starting node objects or create new ones
        AStarNodeWrapper startNode = previouslyFoundNodes.get(start);
        if(startNode == null)
        	startNode = addNewNode(start);
        else
        	startNode.reset(searchIndex);
        
        startNode.bestFoundCostToHere = 0;
        startNode.estimatedCostFromHereToGoal = heuristic(start, goal);
        startNode.estimatedOverallCost = startNode.estimatedCostFromHereToGoal;

        // either fetch the goal node objects or create new ones
        AStarNodeWrapper goalNode = previouslyFoundNodes.get(goal);
        if(goalNode == null)
        	goalNode = addNewNode(goal);
        else
        	goalNode.reset(searchIndex);
        
        
        // A* containers: nodes to be investigated, nodes that have been investigated
        HashSet <AStarNodeWrapper> closedSet = new HashSet <AStarNodeWrapper>();
        
        TreeSet <AStarNodeWrapper> myOpenSet = new TreeSet <AStarNodeWrapper> (new Comparator<Object>(){

			@Override
			public int compare(Object o1, Object o2) {
				double o1Cost = ((AStarNodeWrapper)o1).estimatedOverallCost,
						o2Cost = ((AStarNodeWrapper)o2).estimatedOverallCost;
				if(o1Cost < o2Cost) return -1;
				else if(o1Cost == o2Cost) return 0;
				return 1;
			}
        	
        });
        myOpenSet.add(startNode);

        // LOOP OVER THE FOUND NODES UNTIL A PATH IS FOUND (or you run out of nodes to search)
        while (myOpenSet.size() > 0) { // while there are reachable nodes to investigate

            AStarNodeWrapper x = myOpenSet.pollFirst(); // find the shortest path so far
            if (x.node == goal)
            { // we have found the shortest possible path to the goal!
                // Reconstruct the path and send it back.
                return reconstructPath(goalNode);
            }
            closedSet.add(x);

            // check all the edges out from this Node
           for (Object o : network.getEdges(x.node, null))
           {
            	Edge l = (Edge) o;
                GeoNode next = null;
                next = (GeoNode) l.getOtherNode(x.node);
                
                // get the A* meta information about this Node
                AStarNodeWrapper nextNode = previouslyFoundNodes.get(next);
                if(nextNode == null)
                	nextNode = addNewNode(next);
                else if(nextNode.indexOfSearch < searchIndex)
                	nextNode.reset(searchIndex);

                // make sure we haven't already considered and rejected it
                if (closedSet.contains(nextNode)) // it has already been considered
                    continue;

                // otherwise evaluate the cost of this node/edge combo
                double currentPathCost = x.bestFoundCostToHere + 
                		applyWeighting(weightedField, weightings, (MasonGeometry)((ListEdge) l).info);
                
                boolean updateInfo = true;

                // previously unfound? add it to the list!
                if (!myOpenSet.contains(nextNode))
                	nextNode.estimatedCostFromHereToGoal = heuristic(next, goal);

                // better than the existing value? Update it!
                else if (currentPathCost < nextNode.bestFoundCostToHere)
                	myOpenSet.remove(nextNode);
                
                // otherwise, no need to update!
                else
                	updateInfo = false;
                
                // update if necessary
                if(updateInfo){
                    nextNode.cameFrom = x;
                    nextNode.edgeFrom = l;
                    nextNode.bestFoundCostToHere = currentPathCost;
                    nextNode.estimatedOverallCost = nextNode.bestFoundCostToHere + nextNode.estimatedCostFromHereToGoal;
                    myOpenSet.add(nextNode);
                }
            }
        }

        return null;
    }

	double applyWeighting(String weightedField, HashMap <String, Double> weighting, MasonGeometry mg){
		double result = mg.getDoubleAttribute("length"); 
		if(!weightedField.equals("length")){
			Object myParam = ((AttributeValue) mg.getAttribute(weightedField)).getValue();
			if(weighting.containsKey(myParam)){
				result *= weighting.get(myParam);				
			}
		}
		return result;
	}
	
    /**
     * Takes the information about the given node n and returns the path that
     * found it.
     * @param n the end point of the path
     * @return an ArrayList of Edges that lead from the
     * given Node to the Node from which the search began
     */
    ArrayList <Edge> reconstructPath(AStarNodeWrapper n)
    {
        ArrayList<Edge> result = new ArrayList<Edge>();
        AStarNodeWrapper x = n;
        while (x.cameFrom != null)
        {
            result.add(x.edgeFrom); // add this edge to the front of the list
            x = x.cameFrom;
        }

//        Collections.reverse(result);
        
        if(result.size() < 1)
        	System.out.println("stuipd path...");
        return result;
    }


    /**
     * Measure of the estimated distance between two Nodes. Takes into account whether either of the 
     * GeoNodes entails a delay
     * @param x
     * @param y
     * @return notional "distance" between the given nodes.
     */
    double heuristic(GeoNode x, GeoNode y)
    {
        Coordinate xnode = x.geometry.getCoordinate();
        Coordinate ynode = y.geometry.getCoordinate();
        int nodeCost = 0;
        if(x.hasAttribute("delay"))
        	nodeCost += x.getIntegerAttribute("delay");
        if(y.hasAttribute("delay"))
        	nodeCost += y.getIntegerAttribute("delay");
        
        return nodeCost + Math.sqrt(Math.pow(xnode.x - ynode.x, 2)
            + Math.pow(xnode.y - ynode.y, 2));
    }

    double length(Edge e)
    {
        Coordinate xnode = ((GeoNode)e.from()).geometry.getCoordinate();
        Coordinate ynode = ((GeoNode)e.to()).geometry.getCoordinate();
        return Math.sqrt(Math.pow(xnode.x - ynode.x, 2)
            + Math.pow(xnode.y - ynode.y, 2));
    }

    /**
     * A wrapper to contain the A* meta information about the Nodes
     *
     */
    class AStarNodeWrapper
    {
        // the underlying Node associated with the metainformation
        GeoNode node;
        // the Node from which this Node was most profitably linked
        AStarNodeWrapper cameFrom;
        // the edge by which this Node was discovered
        Edge edgeFrom;
        double bestFoundCostToHere, estimatedCostFromHereToGoal, estimatedOverallCost;
        int indexOfSearch = -1;


        public AStarNodeWrapper(GeoNode n)
        {
            node = n;
            bestFoundCostToHere = 0;
            estimatedCostFromHereToGoal = 0;
            estimatedOverallCost = 0;
            cameFrom = null;
            edgeFrom = null;
        }

        public int hashCode(){
        	return node.hashCode(); 
        }
        
        public void reset(int index){
            bestFoundCostToHere = 0;
            estimatedCostFromHereToGoal = 0;
            estimatedOverallCost = 0;
            cameFrom = null;
            edgeFrom = null;
            indexOfSearch = index;
        }
    }
}