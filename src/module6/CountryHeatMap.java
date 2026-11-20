package module6;

import java.util.HashMap;
import java.util.List;

import de.fhpotsdam.unfolding.marker.Marker;

/** A choropleth overlay for the earthquake map.
 *
 *  The class counts how many earthquakes happened on land in each country and
 *  colours the country polygons accordingly.  Countries with no earthquakes
 *  are left transparent so that the basemap remains visible.
 *
 *  It is deliberately separate from EarthquakeCityMap: the map class asks this
 *  class for the data and the colours, but does not need to know how they are
 *  computed.
 */
public class CountryHeatMap {

	// Processing 2 treats a colour whose alpha byte is 0 as fully opaque, so
	// a nearly invisible white (alpha = 1) is used instead of 0x00000000.
	private static final int EMPTY_FILL = 0x01FFFFFF;
	// Faint dark border so that country outlines stay visible on the light map.
	private static final int EMPTY_STROKE = 0x602F4F4F;
	// Countries with earthquakes get a red fill; the alpha channel is adjusted
	// for each country to show relative earthquake frequency.
	private static final int HOT_RGB = 0xC0392B;
	private static final int HOT_STROKE = 0x90FFFFFF;

	private List<Marker> countryMarkers;
	private HashMap<String, Integer> counts;
	private int maxCount;
	private boolean visible;

	public CountryHeatMap(List<Marker> countryMarkers) {
		this.countryMarkers = countryMarkers;
		this.counts = new HashMap<String, Integer>();
		this.maxCount = 0;
		this.visible = true;
	}

	/** Recomputes the per-country counts from the current earthquake markers. */
	public void update(List<Marker> quakeMarkers) {
		counts.clear();
		maxCount = 0;

		for (Marker marker : quakeMarkers) {
			EarthquakeMarker quake = (EarthquakeMarker) marker;
			if (quake.isOnLand()) {
				String country = quake.getStringProperty("country");
				if (country != null) {
					int newCount = getCount(country) + 1;
					counts.put(country, newCount);
					if (newCount > maxCount) {
						maxCount = newCount;
					}
				}
			}
		}

		updateCountryColors();
	}

	/** Applies a fill colour to every country polygon. */
	private void updateCountryColors() {
		for (Marker country : countryMarkers) {
			String name = country.getStringProperty("name");
			int count = getCount(name);

			if (count == 0) {
				country.setColor(EMPTY_FILL);
				country.setStrokeColor(EMPTY_STROKE);
			}
			else {
				// Square root makes small differences visible even when one
				// country (usually the USA) has far more quakes than the rest.
				float intensity = (float) (Math.sqrt(count) / Math.sqrt(maxCount));
				int alpha = 35 + (int) (135 * intensity);
				country.setColor((alpha << 24) | HOT_RGB);
				country.setStrokeColor(HOT_STROKE);
			}
			country.setStrokeWeight(1);
		}
	}

	public int getCount(String country) {
		Integer value = counts.get(country);
		return value == null ? 0 : value.intValue();
	}

	public int getMaxCount() {
		return maxCount;
	}

	/** Number of countries that had at least one earthquake. */
	public int getAffectedCountryCount() {
		return counts.size();
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
		for (Marker country : countryMarkers) {
			country.setHidden(!visible);
		}
	}

	public void toggle() {
		setVisible(!visible);
	}
}
