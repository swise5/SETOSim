package takamatsu.myobjects;

import org.locationtech.jts.geom.Coordinate;

import java.util.ArrayList;

//import org.locationtech.jts.geom.Coordinate;

public class MyTest {

    public static void main(String [] args){
        System.out.println("herp a derp: " + args[0]);
        ArrayList <String> myArray = new ArrayList <String>();
        Coordinate c = new Coordinate(3, 2);
        System.out.println(c.toString());
    }
}