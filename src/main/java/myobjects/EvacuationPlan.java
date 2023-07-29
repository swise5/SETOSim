package main.java.myobjects;

import java.util.ArrayList;

import com.vividsolutions.jts.geom.Coordinate;

public class EvacuationPlan {
	Shelter shelter;
	int travelMode;
	double targetDepartureTime;
	boolean assistanceNeeded;
	ArrayList <Coordinate> intermediateLocations;
	ArrayList <Shelter> fullShelters;
}