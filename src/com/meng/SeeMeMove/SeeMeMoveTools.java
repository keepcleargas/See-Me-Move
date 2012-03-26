package com.meng.SeeMeMove;

import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import android.util.Log;
import android.widget.Toast;

/**
 * @author Richard Flanagan
 */

public class SeeMeMoveTools 
{
    // FOR TESTING
	private static AccelerometerListener listener;
	
	// Parameter Variables THESE MUST BE SET!!
	private int sampleRate = 100; // desired sample rate which is set when instantiating the object or defined after
	private int window = 10000; // in milliseconds default set to 10 seconds
	private boolean smooth = false; 
	private int smoothOrder = 20; // Order of lowpass filter
	
	// Time Variables 
	private static final long NANO_IN_SECOND = 1000000000l; // Number of nanoseconds in a second
	private static final long NANO_IN_MILISECOND = 1000000l; // number of nanoseconds in 1 millisecond
    private long windowInNano; // length of window in nanoseconds calculated by constructor or set by user
    private float startTime = 0;
    private float timeDiff = 0;	
	
	// Contains corresponding time stamp
	private ArrayList<Long> timevalues;
	// Array index values of raw samples per window 
	private ArrayList<Integer> rawWindowIndexs;
	// Interpolated times
	private ArrayList<Long> timestampInter;
	
	// Raw sensor values 
	private ArrayList<Float> xRawValues;
	private ArrayList<Float> yRawValues;
	private ArrayList<Float> zRawValues;
	
	// Interpolated sensor values 
	private ArrayList<Float> xInterValues;
	private ArrayList<Float> yInterValues;
	private ArrayList<Float> zInterValues;
	
	// Magnitude
	private ArrayList<Float> magnitude;
	private int previousMagIndex = 0;
	
	// RMS 
	private ArrayList<Float> RMS;
	private int previousRMSIndex = 0;
	
	private float avgValue = 0;
		
    /**
     * Constructor
     */
	public SeeMeMoveTools() {
		this.xRawValues = new ArrayList<Float>();
		this.yRawValues = new ArrayList<Float>();
		this.zRawValues = new ArrayList<Float>();
		this.xInterValues = new ArrayList<Float>();
		this.yInterValues = new ArrayList<Float>();
		this.zInterValues = new ArrayList<Float>();
		this.timevalues = new ArrayList<Long>();
		this.timestampInter = new ArrayList<Long>();
		this.RMS = new ArrayList<Float>();
		this.magnitude = new ArrayList<Float>();
		
		this.rawWindowIndexs = new ArrayList<Integer>();
		this.rawWindowIndexs.add(0);
		
		windowInNano = NANO_IN_MILISECOND * window;
	}
	
	public SeeMeMoveTools(int sampleRate) {
		this.sampleRate = sampleRate;
		
		this.xRawValues = new ArrayList<Float>();
		this.yRawValues = new ArrayList<Float>();
		this.zRawValues = new ArrayList<Float>();
		this.zRawValues = new ArrayList<Float>();
		this.xInterValues = new ArrayList<Float>();
		this.yInterValues = new ArrayList<Float>();
		this.zInterValues = new ArrayList<Float>();
		this.timevalues = new ArrayList<Long>();
		this.timestampInter = new ArrayList<Long>();
		this.RMS = new ArrayList<Float>();
		this.magnitude = new ArrayList<Float>();

		this.rawWindowIndexs = new ArrayList<Integer>();
		this.rawWindowIndexs.add(0);
		
		windowInNano = NANO_IN_MILISECOND * window;
	}
	
	public void clear() {
		this.xRawValues.clear();
		this.yRawValues.clear();
		this.zRawValues.clear();
		this.timevalues.clear();
	}
	
	public void setSampleRate(int sampleRate) {
		this.sampleRate = sampleRate;
		Log.i("Parameter Set", "Sample Rate: " + Integer.toString(sampleRate));
	}
	
	public void setWindow(int window) {
		this.window = window;
		Log.i("Parameter Set", "Window Size: " + Integer.toString(window) + " milliseconds");
		this.windowInNano = window * NANO_IN_MILISECOND;
		Log.i("Parameter Set", "Window Size: " + Long.toString(windowInNano) + " nanoseconds");
	}
	
	public float getAverage() throws IllegalAccessException {
		//if(values.isEmpty() == true)
		//	throw new IllegalAccessException("No Values have been added to this object");
		return this.avgValue;
	}
	
    /**
     * Add value with corresponding timestamp
     * @param value
     *             uninterpolated data value
     * @param timevalue
     *             corresponding time value for data
     */
	public void addValue(float xValue, float yValue, float zValue, long timevalue) {
		//TODO Add checks to see if all parameters have been set
				
		xRawValues.add(xValue);
		yRawValues.add(yValue);
		zRawValues.add(zValue);
		timevalues.add(timevalue);
		
    	if(this.startTime == 0)
    		this.startTime = timevalue;       	
    	this.timeDiff = timevalue - this.startTime;
    	
    	if(this.timeDiff >= windowInNano) {	 
    		rawWindowIndexs.add(xRawValues.size());
    		Log.i("Data", "Window Index: " + Integer.toString(rawWindowIndexs.get(rawWindowIndexs.size()-1)));
            
    		// Create a new worker thread so as not to block the UI thread
    		new Thread(new Runnable() {
                public void run() {	     	
                	// Local variables to store window data to be passed  
                	ArrayList<Long> timevaluesWindow = new ArrayList<Long>();
                	ArrayList<Float> rawXWindowValues = new ArrayList<Float>();
                	ArrayList<Float> rawYWindowValues = new ArrayList<Float>();
                	ArrayList<Float> rawZWindowValues = new ArrayList<Float>();
                	
                	for(int i = rawWindowIndexs.get(rawWindowIndexs.size()-2); i < rawWindowIndexs.get(rawWindowIndexs.size()-1) ; i ++) {
                		timevaluesWindow.add(timevalues.get(i));
                		rawXWindowValues.add(xRawValues.get(i));
                		rawYWindowValues.add(yRawValues.get(i));
                		rawZWindowValues.add(zRawValues.get(i));
                	}
                	
            		try {               		                   
            			xInterValues.addAll(interpolateData(rawXWindowValues, timevaluesWindow));
                    	yInterValues.addAll(interpolateData(rawYWindowValues, timevaluesWindow));
						zInterValues.addAll(interpolateData(rawZWindowValues, timevaluesWindow));
					} catch (DataFormatException e) {e.printStackTrace();}
            		
            		Log.i("Array Size", "Interpolated X: " + Integer.toString(xInterValues.size()));
                	Log.i("Array Size", "Interpolated Y: " + Integer.toString(yInterValues.size()));
                	Log.i("Array Size", "Interpolated Z: " + Integer.toString(zInterValues.size()));
                	
            		calculateMagnitude();
            		
            		calculateRMS();
            		                	
                	String postMessage = Float.toString(avgValue);
                	new ConnectToServer(postMessage);
                }
            }).start(); 
            this.startTime = timevalue;
    	}
	}	
	
	public ArrayList<Float> interpolateData(List<Float> rawData, ArrayList<Long> rawTime) throws DataFormatException {
		// Error Checking
		if(sampleRate == 0)
			throw new NullPointerException("No Sample rate set: Set in constructor or use method");
		if(xRawValues.size() == 0)
			throw new NullPointerException("No data in array to interpolate");
		if((NANO_IN_SECOND/sampleRate) > ((timevalues.get(rawWindowIndexs.get(rawWindowIndexs.size()-2)+1))-(timevalues.get(rawWindowIndexs.get(rawWindowIndexs.size()-2)))))
			throw new DataFormatException("Sampling rate is greater then data range");
		
		ArrayList<Float> interpolatedData = new ArrayList<Float>(); // ArrayList to store new interpolated data				
		long time = rawTime.get(0);		
		
		// Interpolate Data
		while(time < rawTime.get(rawTime.size()-1)) {			
			float retval = 0;
			// Loops through to find corresponding time in rawTime
			find:
				for(int i = 0 ; i < rawTime.size()-1 ; i++) {
					long firsttimeval = rawTime.get(i);
					long secondtimeval = rawTime.get(i+1);
					// When a match had been found
					if(time >= firsttimeval && time <= secondtimeval) {
						float firstval = rawData.get(i);
						float secondval = rawData.get(i+1);
						if(secondval >= firstval) {
							retval = ((time-firsttimeval)*(secondval-firstval)/(secondtimeval-firsttimeval))+firstval;
						}
						else {
							retval = ((secondtimeval-time)*((firstval-secondval)/(secondtimeval-firsttimeval)))+secondval;
						}
						interpolatedData.add(retval);
						break find;
					}
				}		
			time += (NANO_IN_SECOND/sampleRate);
			//interpolatedData.add(retval);
		}
		// Smooth data if required 
		if(smooth == true)
			interpolatedData = lowPassFilter(interpolatedData);
		// Return Data
		return interpolatedData;
	}
	
	private ArrayList<Float> lowPassFilter(ArrayList<Float> notSmooth) {
		ArrayList<Float> smooth = new ArrayList<Float>() ;
		float value = notSmooth.get(0);
		for(int i = 1 ; i < notSmooth.size() ; i++) {
		    float currentValue = notSmooth.get(i);
		    value += (currentValue - value) / this.smoothOrder;
		    smooth.add(value);
		}
		return smooth;
	}
	
	private void calculateMagnitude() {
    	for(int i = this.previousMagIndex ; i < this.xInterValues.size() ; i++) {
    		this.magnitude.add((float) Math.sqrt(Math.pow(this.xInterValues.get(i), 2) + Math.pow(this.yInterValues.get(i), 2) + Math.pow(this.zInterValues.get(i), 2)));   	                              
    	}
    	this.previousMagIndex = this.magnitude.size();
    	Log.i("Array Size", "Magnitude: " + Integer.toString(this.magnitude.size()));
	}
		
	private void calculateRMS() {
		float newRMS = 0f;
    	for(int i = this.previousRMSIndex ; i < this.magnitude.size() ; i++) {
    		newRMS += (float) Math.pow(this.magnitude.get(i), 2);
    	}
    	newRMS = (float) Math.sqrt((newRMS/(this.magnitude.size()-this.previousRMSIndex)));
		  	                              
		this.RMS.add(newRMS);
		Log.i("Data", Float.toString(newRMS));
		this.previousRMSIndex = this.magnitude.size();
	}
	
	private void calculateAverage(ArrayList<Float> values) {
		float average = 0;
		for(int i = 0 ; i < values.size() ; i++) {
			average += values.get(i);
		}
		average = average/values.size();
		this.avgValue = average;
	}
	
	private void postToServer(String string) {
    	listener.postResult(string);
	}
}	