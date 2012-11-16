package edu.purdue.rockapp.location;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.location.LocationManager;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Projection;

public class RockLocationManager {
	Context context;
	boolean mHaveFix = false;
	MyLocationOverlay mLocationOverlay;
	MapView mMapView;
	
	public RockLocationManager(Context context, MapView mapView) {
		this.context = context;
		this.mMapView = mapView;
		
		// Create the google maps API location overlay
		mLocationOverlay = new MyLocationOverlay(context, mapView);
		mLocationOverlay.enableCompass();
	}
	
	// Allow the overlay to show on the map
	public void showLocationOverlay() {
		mMapView.getOverlays().add(mLocationOverlay);
	}
	
	// Hide the overlay on the map
	public void hideLocationOverlay() {
		mMapView.getOverlays().remove(mLocationOverlay);
	}
	
	public void enable() {
		// Make sure we mark that we do not have a fix
		mHaveFix = false;

		// Allow the overlay to track the user location
		mLocationOverlay.enableMyLocation();
		
		mLocationOverlay.runOnFirstFix(new Runnable() {
			public void run() {
				// We now have a fix
				mHaveFix = true;
				
				/* TODO: Make this happen somewhere else
				// We have not moved so lets move to the current fix
				GeoPoint p = mMapView.getMapCenter();
				
				if(p.equals(startingCenter) && 
				   startingZoom == mMapView.getZoomLevel() && 
				   mMapView.getLatitudeSpan() > DEFAULT_START_ZOOM_LAT_SPAN &&
				   mMapView.getLongitudeSpan() > DEFAULT_START_ZOOM_LONG_SPAN) {
					moveMapTo(mMyLocation.getMyLocation(), true);
				}
				
				// Update the action bar (on the UI thread) for the location button
				invalidateActionBar();
				*/
			}
		});
	}
	
	// Make the overlay stop tracking the user
	public void disable() {
		mHaveFix = false;
		mLocationOverlay.disableMyLocation();
	}
	
	// Get the "user" location regardless 
	public GeoPoint getUserLocation() {
		return mLocationOverlay.getMyLocation();
	}
	
	public boolean haveUserLocation() {
		if(hasLocationProvider()) {
			return mHaveFix; 
		} else {
			return false;
		}
	}
	
	// Indicates if the user marker is visible or not
	public boolean isUserOnMap() {
		// If we have no fix then we can't know if the user is or not
		if(!haveUserLocation()) {
			return false;
		}
		
		// From the center point and spans determine the coordinate of the bottom right 
		GeoPoint c = mMapView.getMapCenter();
		GeoPoint br_gp = new GeoPoint(c.getLatitudeE6() - mMapView.getLatitudeSpan()/2,
								   c.getLongitudeE6() + mMapView.getLongitudeSpan()/2);
		// Get the projection from coordinate to screen pixels
		Projection proj = mMapView.getProjection();
		
		// Get the screen pixels of bottom right, top-left = (0,0) by definition
		Point br = proj.toPixels(br_gp, null);
		Rect r = new Rect(0, 0, br.x, br.y);
		
		// See if our user point is in the screen rect or not
		Point user = proj.toPixels(mLocationOverlay.getMyLocation(), null);
		return r.contains(user.x, user.y);
	}
	
	public GeoPoint getBestLocation() {
		if(isUserOnMap()) {
			// User is on map so give that
			return getUserLocation();
		} else {
			// We have no fix show use the center of what's currently in view
			return mMapView.getMapCenter();
		}
	}
	
	/* A helper function to check to see if there is some sort of location provider or not */
	public boolean hasLocationProvider() {
		LocationManager locMan = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
		
		// See if we have either GPS or network locations
		if(!locMan.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
		   !locMan.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
			return false;
		} else {
			return true;
		}
	}
}
