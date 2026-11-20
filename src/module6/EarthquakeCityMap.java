package module6;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import de.fhpotsdam.unfolding.UnfoldingMap;
import de.fhpotsdam.unfolding.data.Feature;
import de.fhpotsdam.unfolding.data.GeoJSONReader;
import de.fhpotsdam.unfolding.data.PointFeature;
import de.fhpotsdam.unfolding.geo.Location;
import de.fhpotsdam.unfolding.marker.AbstractShapeMarker;
import de.fhpotsdam.unfolding.marker.Marker;
import de.fhpotsdam.unfolding.marker.MultiMarker;
import de.fhpotsdam.unfolding.providers.MBTilesMapProvider;
import de.fhpotsdam.unfolding.utils.MapUtils;
import de.fhpotsdam.unfolding.utils.ScreenPosition;
import parsing.ParseFeed;
import processing.core.PApplet;

/** EarthquakeCityMap
 * An application with an interactive map displaying earthquake data.
 * Author: UC San Diego Intermediate Software Development MOOC team
 * @author Your name here
 * Date: July 17, 2015
 * */
public class EarthquakeCityMap extends PApplet {
	
	// We will use member variables, instead of local variables, to store the data
	// that the setUp and draw methods will need to access (as well as other methods)
	// You will use many of these variables, but the only one you should need to add
	// code to modify is countryQuakes, where you will store the number of earthquakes
	// per country.
	
	// You can ignore this.  It's to get rid of eclipse warnings
	private static final long serialVersionUID = 1L;

	// IF YOU ARE WORKING OFFILINE, change the value of this variable to true
	private static final boolean offline = false;
	
	/** This is where to find the local tiles, for working without an Internet connection */
	public static String mbTilesString = "blankLight-1-3.mbtiles";
	
	

	//feed with magnitude 2.5+ Earthquakes
	private String earthquakesURL = "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_week.atom";
	
	// The files containing city names and info and country names and info
	private String cityFile = "city-data.json";
	private String countryFile = "countries.geo.json";
	
	// The map
	private UnfoldingMap map;
	
	// Markers for each city
	private List<Marker> cityMarkers;
	// Markers for each earthquake
	private List<Marker> quakeMarkers;

	// A List of country markers
	private List<Marker> countryMarkers;
	
	// NEW IN MODULE 5
	private CommonMarker lastSelected;
	private CommonMarker lastClicked;

	// EXTENSION: country heat map, live statistics and magnitude filter
	private CountryHeatMap heatMap;
	private float minMagnitude = 0.0f;
	private int totalQuakes;
	private int landQuakes;
	private int oceanQuakes;
	private int shallowQuakes;
	private int intermediateQuakes;
	private int deepQuakes;
	private int countriesAffected;
	private EarthquakeMarker strongestQuake;

	public void setup() {
		// (1) Initializing canvas and map tiles
		// NOTE: JAVA2D is used instead of OPENGL because the bundled JOGL 2
		// library does not work with newer Java runtimes.
		size(900, 700);
		if (offline) {
		    // The map fills the window; the information panels are drawn on
		    // top of its left edge.
		    map = new UnfoldingMap(this, 0, 0, 900, 700, new MBTilesMapProvider(mbTilesString));
		    earthquakesURL = "2.5_week.atom";  // The same feed, but saved August 7, 2015
		}
		else {
			// Google's tile service used by the original starter code now
			// requires an API key, so an Esri basemap is used instead.
			map = new UnfoldingMap(this, 0, 0, 900, 700, new HttpsEsriProvider());
			// IF YOU WANT TO TEST WITH A LOCAL FILE, uncomment the next line
		    //earthquakesURL = "2.5_week.atom";
		}
		MapUtils.createDefaultEventDispatcher(this, map);
		
		// FOR TESTING: Set earthquakesURL to be one of the testing files by uncommenting
		// one of the lines below.  This will work whether you are online or offline
		//earthquakesURL = "test1.atom";
		//earthquakesURL = "test2.atom";
		
		// Uncomment this line to take the quiz
		//earthquakesURL = "quiz2.atom";
		
		
		// (2) Reading in earthquake data and geometric properties
	    //     STEP 1: load country features and markers
		List<Feature> countries = GeoJSONReader.loadData(this, countryFile);
		countryMarkers = MapUtils.createSimpleMarkers(countries);
		
		//     STEP 2: read in city data
		List<Feature> cities = GeoJSONReader.loadData(this, cityFile);
		cityMarkers = new ArrayList<Marker>();
		for(Feature city : cities) {
		  cityMarkers.add(new CityMarker(city));
		}
	    
		//     STEP 3: read in earthquake RSS feed
	    List<PointFeature> earthquakes = ParseFeed.parseEarthquake(this, earthquakesURL);
	    quakeMarkers = new ArrayList<Marker>();
	    
	    for(PointFeature feature : earthquakes) {
		  //check if LandQuake
		  if(isLand(feature)) {
		    quakeMarkers.add(new LandQuakeMarker(feature));
		  }
		  // OceanQuakes
		  else {
		    quakeMarkers.add(new OceanQuakeMarker(feature));
		  }
	    }

	    // could be used for debugging
	    printQuakes();

	    // EXTENSION: count quakes per country and colour the country polygons
	    heatMap = new CountryHeatMap(countryMarkers);
	    heatMap.update(quakeMarkers);
	    computeStatistics();

	    // (3) Add markers to map
	    //     Country markers are now drawn as a translucent heat-map layer,
	    //     so they go under the earthquakes and cities.
	    map.addMarkers(countryMarkers);
	    map.addMarkers(quakeMarkers);
	    map.addMarkers(cityMarkers);

	    // Show almost the whole world so that earthquakes in the Americas,
	    // Europe and Asia are all visible.  At zoom 2 one viewport cannot
	    // show all 360 degrees, so the map is centred slightly west.
	    map.zoomAndPanTo(2, new Location(0, -20));

	    // Print the five strongest earthquakes (module 4/5 requirement).
	    sortAndPrint(5);

	}  // End setup


	public void draw() {
		background(0);
		map.draw();
		drawThreatCircle();
		addKey();
		drawDashboard();
		drawDetailsPanel();
	}

	/** Prints the numToPrint strongest earthquakes, strongest first.
	 *  Uses the Comparable implementation in EarthquakeMarker. */
	private void sortAndPrint(int numToPrint) {
		List<EarthquakeMarker> sorted = new ArrayList<EarthquakeMarker>();
		for (Marker marker : quakeMarkers) {
			sorted.add((EarthquakeMarker) marker);
		}
		Collections.sort(sorted);  // weakest to strongest

		int start = Math.max(0, sorted.size() - numToPrint);
		for (int i = sorted.size() - 1; i >= start; i--) {
			System.out.println(sorted.get(i));
		}
	}

	/** Computes the numbers shown in the live statistics panel. */
	private void computeStatistics() {
		totalQuakes = quakeMarkers.size();
		landQuakes = 0;
		oceanQuakes = 0;
		shallowQuakes = 0;
		intermediateQuakes = 0;
		deepQuakes = 0;
		strongestQuake = null;

		for (Marker marker : quakeMarkers) {
			EarthquakeMarker quake = (EarthquakeMarker) marker;

			if (quake.isOnLand()) {
				landQuakes++;
			}
			else {
				oceanQuakes++;
			}

			float depth = quake.getDepth();
			if (depth < EarthquakeMarker.THRESHOLD_INTERMEDIATE) {
				shallowQuakes++;
			}
			else if (depth < EarthquakeMarker.THRESHOLD_DEEP) {
				intermediateQuakes++;
			}
			else {
				deepQuakes++;
			}

			if (strongestQuake == null
					|| quake.getMagnitude() > strongestQuake.getMagnitude()) {
				strongestQuake = quake;
			}
		}

		countriesAffected = heatMap.getAffectedCountryCount();
	}

	/** Event handler that gets called automatically when the
	 * mouse moves.
	 */
	@Override
	public void mouseMoved()
	{
		// clear the last selection
		if (lastSelected != null) {
			lastSelected.setSelected(false);
			lastSelected = null;
		
		}
		selectMarkerIfHover(quakeMarkers);
		selectMarkerIfHover(cityMarkers);
		//loop();
	}
	
	// If there is a marker selected 
	private void selectMarkerIfHover(List<Marker> markers)
	{
		// Abort if there's already a marker selected
		if (lastSelected != null) {
			return;
		}
		
		for (Marker m : markers)
		{
			CommonMarker marker = (CommonMarker)m;
			// Do not show a title for a marker that the filter has removed.
			if (marker.isHidden()) {
				continue;
			}
			if (marker.isInside(map,  mouseX, mouseY)) {
				lastSelected = marker;
				marker.setSelected(true);
				return;
			}
		}
	}
	
	/** The event handler for mouse clicks
	 * It will display an earthquake and its threat circle of cities
	 * Or if a city is clicked, it will display all the earthquakes 
	 * where the city is in the threat circle
	 */
	@Override
	public void mouseClicked()
	{
		if (lastClicked != null) {
			unhideMarkers();
			lastClicked = null;
		}
		else if (lastClicked == null) 
		{
			checkEarthquakesForClick();
			if (lastClicked == null) {
				checkCitiesForClick();
			}
		}
	}
	
	// Helper method that will check if a city marker was clicked on
	// and respond appropriately
	private void checkCitiesForClick()
	{
		if (lastClicked != null) return;
		// Loop over the earthquake markers to see if one of them is selected
		for (Marker marker : cityMarkers) {
			if (!marker.isHidden() && marker.isInside(map, mouseX, mouseY)) {
				lastClicked = (CommonMarker)marker;
				// Hide all the other earthquakes and hide
				for (Marker mhide : cityMarkers) {
					if (mhide != lastClicked) {
						mhide.setHidden(true);
					}
				}
				for (Marker mhide : quakeMarkers) {
					EarthquakeMarker quakeMarker = (EarthquakeMarker)mhide;
					if (quakeMarker.getDistanceTo(marker.getLocation()) 
							> quakeMarker.threatCircle()) {
						quakeMarker.setHidden(true);
					}
				}
				return;
			}
		}		
	}
	
	// Helper method that will check if an earthquake marker was clicked on
	// and respond appropriately
	private void checkEarthquakesForClick()
	{
		if (lastClicked != null) return;
		// Loop over the earthquake markers to see if one of them is selected
		for (Marker m : quakeMarkers) {
			EarthquakeMarker marker = (EarthquakeMarker)m;
			if (!marker.isHidden() && marker.isInside(map, mouseX, mouseY)) {
				lastClicked = marker;
				// Hide all the other earthquakes and hide
				for (Marker mhide : quakeMarkers) {
					if (mhide != lastClicked) {
						mhide.setHidden(true);
					}
				}
				for (Marker mhide : cityMarkers) {
					if (mhide.getDistanceTo(marker.getLocation()) 
							> marker.threatCircle()) {
						mhide.setHidden(true);
					}
				}
				return;
			}
		}
	}
	
	// loop over and unhide all markers
	private void unhideMarkers() {
		for(Marker marker : quakeMarkers) {
			marker.setHidden(false);
		}

		for(Marker marker : cityMarkers) {
			marker.setHidden(false);
		}

		// The magnitude filter still applies after a click is cleared.
		applyMagnitudeFilter();
	}

	/** Keyboard controls for the extension:
	 *  H toggles the country heat map, UP/DOWN adjust the magnitude filter. */
	@Override
	public void keyPressed() {
		if (key == 'h' || key == 'H') {
			heatMap.toggle();
		}
		else if (keyCode == UP) {
			setMinMagnitude(minMagnitude + 0.5f);
		}
		else if (keyCode == DOWN) {
			setMinMagnitude(minMagnitude - 0.5f);
		}
	}

	/** Changes the smallest magnitude that is displayed, then refreshes
	 *  the map.  Any open selection is cleared so that the two features do
	 *  not hide markers for conflicting reasons. */
	private void setMinMagnitude(float newMinimum) {
		minMagnitude = constrain(newMinimum, 0, 9);

		if (lastSelected != null) {
			lastSelected.setSelected(false);
			lastSelected = null;
		}
		lastClicked = null;

		unhideMarkers();
	}

	/** Hides every earthquake weaker than the current filter threshold. */
	private void applyMagnitudeFilter() {
		for (Marker marker : quakeMarkers) {
			EarthquakeMarker quake = (EarthquakeMarker) marker;
			quake.setHidden(quake.getMagnitude() < minMagnitude);
		}
	}

	/** Draws the circle within which the selected earthquake is considered
	 *  dangerous.  A location one threat-radius north of the epicentre is
	 *  used to convert kilometres into screen pixels. */
	private void drawThreatCircle() {
		if (!(lastClicked instanceof EarthquakeMarker)) {
			return;
		}

		EarthquakeMarker quake = (EarthquakeMarker) lastClicked;
		if (quake.isHidden()) {
			return;
		}

		Location centre = quake.getLocation();
		// 111.32 km is one degree of latitude.
		float degrees = (float) (quake.threatCircle() / 111.32);
		Location edge = new Location(centre.getLat() + degrees, centre.getLon());

		ScreenPosition centreOnScreen = map.getScreenPosition(centre);
		ScreenPosition edgeOnScreen = map.getScreenPosition(edge);
		float radiusOnScreen = Math.abs(centreOnScreen.y - edgeOnScreen.y);

		pushStyle();
		noFill();
		stroke(255, 140, 0, 220);
		strokeWeight(2);
		ellipse(centreOnScreen.x, centreOnScreen.y,
				2 * radiusOnScreen, 2 * radiusOnScreen);
		popStyle();
	}

	/** Number of cities that lie inside an earthquake's threat circle. */
	private int countCitiesAtRisk(EarthquakeMarker quake) {
		int count = 0;
		for (Marker marker : cityMarkers) {
			if (marker.getDistanceTo(quake.getLocation()) <= quake.threatCircle()) {
				count++;
			}
		}
		return count;
	}

	/** Number of earthquakes whose threat circle covers a city. */
	private int countQuakesThreatening(Location cityLocation) {
		int count = 0;
		for (Marker marker : quakeMarkers) {
			EarthquakeMarker quake = (EarthquakeMarker) marker;
			if (marker.getDistanceTo(cityLocation) <= quake.threatCircle()) {
				count++;
			}
		}
		return count;
	}
	
	// helper method to draw key in GUI
	private void addKey() {
		pushStyle();
		stroke(120);
		// Remember you can use Processing's graphics methods here
		fill(255, 250, 240);
		
		int xbase = 25;
		int ybase = 50;
		
		rect(xbase, ybase, 150, 250);
		
		fill(0);
		textAlign(LEFT, CENTER);
		textSize(12);
		text("Earthquake Key", xbase+25, ybase+25);
		
		fill(150, 30, 30);
		int tri_xbase = xbase + 35;
		int tri_ybase = ybase + 50;
		triangle(tri_xbase, tri_ybase-CityMarker.TRI_SIZE, tri_xbase-CityMarker.TRI_SIZE, 
				tri_ybase+CityMarker.TRI_SIZE, tri_xbase+CityMarker.TRI_SIZE, 
				tri_ybase+CityMarker.TRI_SIZE);

		fill(0, 0, 0);
		textAlign(LEFT, CENTER);
		text("City Marker", tri_xbase + 15, tri_ybase);
		
		text("Land Quake", xbase+50, ybase+70);
		text("Ocean Quake", xbase+50, ybase+90);
		text("Size ~ Magnitude", xbase+25, ybase+110);
		
		fill(255, 255, 255);
		ellipse(xbase+35, 
				ybase+70, 
				10, 
				10);
		rect(xbase+35-5, ybase+90-5, 10, 10);
		
		fill(color(255, 255, 0));
		ellipse(xbase+35, ybase+140, 12, 12);
		fill(color(0, 0, 255));
		ellipse(xbase+35, ybase+160, 12, 12);
		fill(color(255, 0, 0));
		ellipse(xbase+35, ybase+180, 12, 12);
		
		textAlign(LEFT, CENTER);
		fill(0, 0, 0);
		text("Shallow", xbase+50, ybase+140);
		text("Intermediate", xbase+50, ybase+160);
		text("Deep", xbase+50, ybase+180);

		text("Past hour", xbase+50, ybase+200);
		
		fill(255, 255, 255);
		int centerx = xbase+35;
		int centery = ybase+200;
		ellipse(centerx, centery, 12, 12);

		strokeWeight(2);
		line(centerx-8, centery-8, centerx+8, centery+8);
		line(centerx-8, centery+8, centerx+8, centery-8);

		popStyle();
	}

	/** Draws the live statistics panel under the key. */
	private void drawDashboard() {
		int xbase = 25;
		int ybase = 312;
		int width = 150;
		int height = 232;

		pushStyle();
		stroke(120);
		fill(255, 250, 240, 245);
		rect(xbase, ybase, width, height);

		fill(0);
		textAlign(LEFT, TOP);
		textSize(12);
		text("Live Statistics", xbase + 10, ybase + 10);

		textSize(11);
		text("Total quakes: " + totalQuakes, xbase + 10, ybase + 32);
		text("Land " + landQuakes + " / Ocean " + oceanQuakes, xbase + 10, ybase + 48);

		String strongest = "-";
		if (strongestQuake != null) {
			strongest = "M " + String.format("%.1f", strongestQuake.getMagnitude());
		}
		text("Strongest: " + strongest, xbase + 10, ybase + 64);
		text("Shallow: " + shallowQuakes, xbase + 10, ybase + 82);
		text("Intermediate: " + intermediateQuakes, xbase + 10, ybase + 98);
		text("Deep: " + deepQuakes, xbase + 10, ybase + 114);
		text("Countries hit: " + countriesAffected, xbase + 10, ybase + 130);

		// Heat-map status and a small red gradient legend.
		String state = heatMap.isVisible() ? "ON" : "OFF";
		text("Heat map: " + state + " (H)", xbase + 10, ybase + 150);

		textSize(9);
		for (int i = 0; i < 5; i++) {
			int alpha = 35 + (int) (135 * i / 4.0);
			fill((alpha << 24) | 0xC0392B);
			rect(xbase + 12 + i * 24, ybase + 164, 24, 10);
		}
		fill(0);
		text("0", xbase + 12, ybase + 182);
		text("" + heatMap.getMaxCount(), xbase + 112, ybase + 182);

		textSize(11);
		text("Min magnitude: " + String.format("%.1f", minMagnitude),
				xbase + 10, ybase + 198);
		text("(UP / DOWN keys)", xbase + 10, ybase + 212);

		popStyle();
	}

	/** Draws information about the last clicked earthquake or city. */
	private void drawDetailsPanel() {
		int xbase = 25;
		int ybase = 548;
		int width = 150;
		int height = 142;

		pushStyle();
		stroke(120);
		fill(255, 250, 240, 245);
		rect(xbase, ybase, width, height);

		fill(0);
		textAlign(LEFT, TOP);
		textSize(12);
		text("Selection Details", xbase + 10, ybase + 8);

		textSize(11);
		if (lastClicked == null) {
			fill(80);
			text("Click an earthquake", xbase + 10, ybase + 30);
			text("or city marker.", xbase + 10, ybase + 46);
			text("Press H for the", xbase + 10, ybase + 70);
			text("country heat map.", xbase + 10, ybase + 86);
			text("Use UP / DOWN to", xbase + 10, ybase + 110);
			text("filter by magnitude.", xbase + 10, ybase + 126);
		}
		else if (lastClicked instanceof EarthquakeMarker) {
			EarthquakeMarker quake = (EarthquakeMarker) lastClicked;
			text("Earthquake", xbase + 10, ybase + 26);
			text(quake.getPlace(), xbase + 10, ybase + 40, width - 20, 26);
			text("Magnitude: " + String.format("%.1f", quake.getMagnitude()),
					xbase + 10, ybase + 72);
			text("Depth: " + String.format("%.1f", quake.getDepth()) + " km",
					xbase + 10, ybase + 86);
			String age = quake.getAge();
			text("Age: " + (age == null ? "unknown" : age),
					xbase + 10, ybase + 100);
			text("Threat radius: "
					+ String.format("%.0f", quake.threatCircle()) + " km",
					xbase + 10, ybase + 114);
			text("Cities at risk: " + countCitiesAtRisk(quake),
					xbase + 10, ybase + 128);
		}
		else if (lastClicked instanceof CityMarker) {
			CityMarker city = (CityMarker) lastClicked;
			text(city.getCity() + ", " + city.getCountry(),
					xbase + 10, ybase + 40, width - 20, 26);
			text("Population: "
					+ String.format("%.1f", city.getPopulation()) + " M",
					xbase + 10, ybase + 72);
			text("Quakes in range: "
					+ countQuakesThreatening(city.getLocation()),
					xbase + 10, ybase + 88);
			fill(80);
			text("Click again to clear", xbase + 10, ybase + 112);
		}

		popStyle();
	}


	// Checks whether this quake occurred on land.  If it did, it sets the
	// "country" property of its PointFeature to the country where it occurred
	// and returns true.  Notice that the helper method isInCountry will
	// set this "country" property already.  Otherwise it returns false.
	private boolean isLand(PointFeature earthquake) {
		
		// IMPLEMENT THIS: loop over all countries to check if location is in any of them
		// If it is, add 1 to the entry in countryQuakes corresponding to this country.
		for (Marker country : countryMarkers) {
			if (isInCountry(earthquake, country)) {
				return true;
			}
		}
		
		// not inside any country
		return false;
	}
	
	// prints countries with number of earthquakes
	// You will want to loop through the country markers or country features
	// (either will work) and then for each country, loop through
	// the quakes to count how many occurred in that country.
	// Recall that the country markers have a "name" property, 
	// And LandQuakeMarkers have a "country" property set.
	private void printQuakes() {
		int totalWaterQuakes = quakeMarkers.size();
		for (Marker country : countryMarkers) {
			String countryName = country.getStringProperty("name");
			int numQuakes = 0;
			for (Marker marker : quakeMarkers)
			{
				EarthquakeMarker eqMarker = (EarthquakeMarker)marker;
				if (eqMarker.isOnLand()) {
					if (countryName.equals(eqMarker.getStringProperty("country"))) {
						numQuakes++;
					}
				}
			}
			if (numQuakes > 0) {
				totalWaterQuakes -= numQuakes;
				System.out.println(countryName + ": " + numQuakes);
			}
		}
		System.out.println("OCEAN QUAKES: " + totalWaterQuakes);
	}
	
	
	
	// helper method to test whether a given earthquake is in a given country
	// This will also add the country property to the properties of the earthquake feature if 
	// it's in one of the countries.
	// You should not have to modify this code
	private boolean isInCountry(PointFeature earthquake, Marker country) {
		// getting location of feature
		Location checkLoc = earthquake.getLocation();

		// some countries represented it as MultiMarker
		// looping over SimplePolygonMarkers which make them up to use isInsideByLoc
		if(country.getClass() == MultiMarker.class) {
				
			// looping over markers making up MultiMarker
			for(Marker marker : ((MultiMarker)country).getMarkers()) {
					
				// checking if inside
				if(((AbstractShapeMarker)marker).isInsideByLocation(checkLoc)) {
					earthquake.addProperty("country", country.getProperty("name"));
						
					// return if is inside one
					return true;
				}
			}
		}
			
		// check if inside country represented by SimplePolygonMarker
		else if(((AbstractShapeMarker)country).isInsideByLocation(checkLoc)) {
			earthquake.addProperty("country", country.getProperty("name"));
			
			return true;
		}
		return false;
	}

}
