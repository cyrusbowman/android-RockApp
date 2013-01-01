package edu.purdue.rockapp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MapView.ReticleDrawMode;

import edu.purdue.libcommon.rock.Rock;
import edu.purdue.libcommon.view.maps.RockMapOverlay;
import edu.purdue.rockapp.location.RockLocationManager;
import edu.purdue.rockapp.view.RockMenu;

public class RockAppActivity extends MapActivity {
	private MapView mMapView;
	private MapController mMapController;
	private RockMapOverlay mRockMapOverlay;
	
	private RockMenu mRockMenu;
	
	private RockLocationManager mRockLocationManager;
	
	private int mCurrentState;
	private GeoPoint startingCenter = null;
	private int startingZoom = 0;
	
	private Bundle bundle = null;
	private final Handler uiHandler = new Handler();
	
	private RockBroadcastReciever rockBroadcastReciever;
	private RockMenuBroadcastReciever rockMenuBroadcastReciever;
	private LocationBroadcastReciever locationBroadcastReciever;
	private RockMapGroupBroadcastReciever rockMapGroupBroadcastReciever;
	
	// UI States
	private static final int STATE_DEFAULT = 0;
	private static final int STATE_ROCK_EDIT = 1;
	
	// Request codes for activity results
	private static final int REQUEST_PICTURE = 1;
	
	// Default zoom span
	private static final int DEFAULT_ZOOM_LAT_SPAN = 15000;
	private static final int DEFAULT_ZOOM_LONG_SPAN = 15000;
	private static final int DEFAULT_START_ZOOM_LAT_SPAN = 50000;
	private static final int DEFAULT_START_ZOOM_LONG_SPAN = 50000;  
	private static final double DEFAULT_LAT_LON_SPAN_OVER_MULTIPLIER = 1.2;
	
	/* Called by Android when application is created */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		bundle = savedInstanceState;
		
		// Set the view for the application
		setContentView(R.layout.main);
		
		// Store the rock menu view
		mRockMenu = (RockMenu)findViewById(R.id.menu);

		// Find the MapView and setup some defaults
		mMapView = (MapView) findViewById(R.id.map);
		mMapView.setSatellite(true);
		mMapView.setReticleDrawMode(ReticleDrawMode.DRAW_RETICLE_OVER);
		mMapView.setBuiltInZoomControls(false);
		
		// Save the map controller for later map animation
		mMapController = mMapView.getController();
		
		// Add the rocks to the map 
		mRockMapOverlay = new RockMapOverlay(this, mMapView);
		mMapView.getOverlays().add(mRockMapOverlay);
		
		// Start the location manager
		mRockLocationManager = new RockLocationManager(this, mMapView);
		mRockLocationManager.showLocationOverlay();
		
		// Receiver used to listen to rocks
		rockBroadcastReciever = new RockBroadcastReciever();
		// Receiver used to listen to rockMenu
		rockMenuBroadcastReciever = new RockMenuBroadcastReciever();
		// Receiver used to listen to location announcements 
		locationBroadcastReciever = new LocationBroadcastReciever();
		// Receiver used to listen to rock group announcements
		rockMapGroupBroadcastReciever = new RockMapGroupBroadcastReciever();
		
		// Restore from bundle
		if(bundle != null) {
			int rockId = bundle.getInt("rock_edit.currentRock", -1);
			if(rockId > 0) {
				mRockMapOverlay.setSelected(rockId);
			}
			
			switch(bundle.getInt("state", STATE_DEFAULT)) {
				case STATE_ROCK_EDIT:
					// Get RockId and restore view states for setState()
					mRockMenu.editRock(Rock.getRock(this, rockId));
				break;
			}
			
			// Restore previous state
			setState(bundle.getInt("state", STATE_DEFAULT));
			
		} else {
			// Otherwise set default initial state
			setState(STATE_DEFAULT);
			
			// Store the  and center where we started to see if we should zoom
			// when we get our first lock
			// Only on new load not one screen rotate
			startingCenter = mMapView.getMapCenter();
			startingZoom = mMapView.getZoomLevel();
		}
	
	}

	/* Called by Android when application comes from not in view to in view */
	@Override
	protected void onResume() {
		super.onResume();
		
		// Start looking for current location
		mRockLocationManager.enable();
		
		// Update the action bar
		invalidateOptionsMenu();
		
		// Listen to changes in the rocks and automatically update from them
		mRockMapOverlay.registerListeners();
		mRockMenu.registerListeners();
		
		// Listen in on rocks being selected so to show the edit menu
		LocalBroadcastManager.getInstance(this).registerReceiver(rockBroadcastReciever, new IntentFilter(RockMapOverlay.ACTION_ROCK_SELECTED));
		
		// Listen for image requests from RockMenu
		LocalBroadcastManager.getInstance(this).registerReceiver(rockMenuBroadcastReciever, new IntentFilter(RockMenu.ACTION_TAKE_PICTURE));
		
		// Listen for location announcements 
		LocalBroadcastManager.getInstance(this).registerReceiver(locationBroadcastReciever, new IntentFilter(RockLocationManager.ACTION_FIRSTFIX));
		
		// Listen for rock group announcements 
		LocalBroadcastManager.getInstance(this).registerReceiver(rockMapGroupBroadcastReciever, new IntentFilter(RockMapOverlay.ACTION_GROUP_SELECTED));
	}

	/*
	 * Called by Android when application comes from in view to no longer in view
	 */
	@Override
	protected void onPause() {
		super.onPause();
		
		// No need for location when not on the screen
		mRockLocationManager.disable();
		
		// No need to react to new rocks when not on screen (a new list will be generated in onResume)
		mRockMapOverlay.unregisterListeners();
		mRockMenu.unregisterListeners();
		
		// No need to listen to rock messages (no map to generate any)
		LocalBroadcastManager.getInstance(this).unregisterReceiver(rockBroadcastReciever);
		
		// Don't listen for image requests when paused
		LocalBroadcastManager.getInstance(this).unregisterReceiver(rockMenuBroadcastReciever);
		
		// Don't listen for location announcements 
		LocalBroadcastManager.getInstance(this).unregisterReceiver(locationBroadcastReciever);
		
		// Don't listen for rock group announcements
		LocalBroadcastManager.getInstance(this).unregisterReceiver(rockMapGroupBroadcastReciever);
		
		// Flush rock edit menu to db
		mRockMenu.flush();
	}
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
		
		savedInstanceState.putInt("state", mCurrentState);
		
		switch(mCurrentState) {
			case STATE_ROCK_EDIT:
				savedInstanceState.putInt("rock_edit.currentRock", mRockMapOverlay.getSelected().getId());
			break;
		}
	}
	
	/* Called by MapActivity to see if a route should be displayed */
	@Override
	public boolean isRouteDisplayed() {
		return false;
	}
	
	/*
	 * Creates the ActionBar with the main menu
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		
		return super.onCreateOptionsMenu(menu);
	}
	
	/*
	 * A method which is called by Android to give the app the change to modify the menu.
	 * Call when context menu is display or after a invalidateOptionsMenu() for the ActionBar
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		
		if(mCurrentState == STATE_DEFAULT) {
			menu.clear();
			inflater.inflate(R.menu.main, menu);
			
			MenuItem showHideItem = menu.findItem(R.id.show_hide);
			MenuItem currentShowHideItem;	
			
			switch(mRockMapOverlay.getShowHide()) {
				case RockMapOverlay.SHOW_ALL_ROCKS:
					currentShowHideItem = menu.findItem(R.id.all_rocks);
				break;
				
				case RockMapOverlay.SHOW_NOT_PICKED_ROCKS:
					currentShowHideItem = menu.findItem(R.id.not_picked_rocks);
				break;
				
				case RockMapOverlay.SHOW_PICKED_ROCKS:
					currentShowHideItem = menu.findItem(R.id.picked_rocks);
				break;
				
				default:
					// We are some how lost, just revert back to showing everything
					mRockMapOverlay.setShowHide(RockMapOverlay.SHOW_ALL_ROCKS);
					currentShowHideItem = menu.findItem(R.id.all_rocks);
				break;
			}
			
			// Copy the current selection to the action bar
			showHideItem.setTitle(currentShowHideItem.getTitle());
			
			// Mark the current one as checked 
			currentShowHideItem.setChecked(true);
			
			// The location button changes depending the current state
			// of location
			MenuItem gps = menu.findItem(R.id.gps);
			if(mRockLocationManager.hasLocationProvider()) {
				if(mRockLocationManager.haveUserLocation()) {
					gps.setIcon(R.drawable.gps_found);
					gps.setTitle(R.string.menu_gps);
				} else {
					gps.setIcon(R.drawable.gps_searching);
					gps.setTitle(R.string.menu_gps_searching);
				}
			} else {
				gps.setIcon(R.drawable.gps_off);
				gps.setTitle(R.string.menu_gps_off);
			}
			
		// Delete options
		} else if (mCurrentState == STATE_ROCK_EDIT) {
			menu.clear();
			inflater.inflate(R.menu.rock_edit, menu);
		}
		
		return true;
	}
	
	/*
	 * Handles when a user selects an ActionBar menu options
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		boolean result = false;
		
		switch(item.getItemId()) {
			case R.id.add:
				addRock();
				result = true;
			break;
			
			case R.id.gps:
				moveToGps();
				result = true;
			break;
			
			case R.id.all_rocks:
				// set the new showHide, update the map, and update the action bar
				changeRockTypeShowHide(RockMapOverlay.SHOW_ALL_ROCKS);
				result = true;
			break;
			
			case R.id.not_picked_rocks:
				// set the new showHide, update the map, and update the action bar
				changeRockTypeShowHide(RockMapOverlay.SHOW_NOT_PICKED_ROCKS);
				result = true;
			break;
			
			case R.id.picked_rocks:
				// set the new showHide, update the map, and update the action bar
				changeRockTypeShowHide(RockMapOverlay.SHOW_PICKED_ROCKS);
				result = true;
			break;
				
			case R.id.list:
				showRockList();
				result = true;
			break;
			
			case R.id.rock_delete:
				showConfirmRockDeleteAlert();
				result = true;
			break;
		}
		
		// If we didn't handle, let the super version try
		return result | super.onOptionsItemSelected(item);
		
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		switch(requestCode) {
			// Get the result of taking a picture
			case REQUEST_PICTURE:
				if(resultCode == RESULT_OK) {
					Rock rock = mRockMapOverlay.getSelected();
					if(rock != null) {
						// Update the rock model and save it
						File image = new File(Rock.IMAGE_PATH, String.format(Rock.IMAGE_FILENAME_PATTERN, rock.getId()));
						rock.setPicture(image.getAbsolutePath());
						rock.save(false);
						mRockMenu.editRock(rock);
					}
				}
			break;	
		}
	}
	
	/*
	 * A helper function which knows how to transition between states of the views
	 */
	private void setState(int newState) {
		if(mCurrentState == newState) {
			return;
		}
		
		// Exit current state
		switch(mCurrentState) {
			case STATE_ROCK_EDIT:
				mRockMenu.hide((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE));
			break;
		}
	
		
		// Enter new state
		switch(newState) {
			case STATE_DEFAULT:
				if(mRockMenu.isOpen()) {
					mRockMenu.hide();
				}
			break;
			
			case STATE_ROCK_EDIT:
				mRockMenu.show();
			break;
		}
		
		// Officially in new state
		mCurrentState =  newState;
	}
	
	/*
	 * A helper function to set what rock type to show/hide
	 */
	private void changeRockTypeShowHide(int type) {
		// set the new showHide, update the map, and update the action bar
		mRockMapOverlay.setShowHide(type);
		mMapView.postInvalidate();
		invalidateOptionsMenu();
	}
	
	/*
	 * Moves the map to current GPS location (if a fix is known)
	 */
	public void moveToGps() {
		if(!mRockLocationManager.hasLocationProvider()) {
			showEnableLocationAlert();
		} else {
			if (!mRockLocationManager.haveUserLocation()) {
				Toast.makeText(this, R.string.location_wait, Toast.LENGTH_SHORT).show();
			} else {
				moveMapTo(mRockLocationManager.getUserLocation(), true);
			}
		}
	}
	
	/*
	 * This function can move the map to a GeoPoint and if you want can adjust the zoom with reason.
	 * If you are already zoomed in more then the default it does not change the view. Otherwise
	 * it zooms you to the default zoom
	 * 
	 * When getMaxZoomLevel is fixed so it truly returns the max zoom level 
	 * (http://code.google.com/p/android/issues/detail?id=2761) we can also zoom out until images are
	 * available if the default zoom is too close for an area
	 */
	private void moveMapTo(GeoPoint p, boolean zoom) {
		// See if we want to play with the zoom level
		if(zoom) {
			// If we are zoomed out too much zoom in to default span
			if(mMapView.getLatitudeSpan() > DEFAULT_ZOOM_LAT_SPAN && mMapView.getLongitudeSpan() > DEFAULT_ZOOM_LONG_SPAN) {
				mMapController.zoomToSpan(DEFAULT_ZOOM_LAT_SPAN, DEFAULT_ZOOM_LONG_SPAN);
			}
		}
		
		// Ask for the animation
		mMapController.animateTo(p);
		// Make sure the animation starts now
		mMapView.postInvalidate();
	}

	/*
	 * Try to create a new rock and add it to the rock list
	 * If it fails because the current location is unknown then use the center of the screen.
	 */
	public void addRock() {
		// Rock and save in DB (triggering it to display on the map)
		Rock rock = new Rock(this, mRockLocationManager.getBestLocation(), false);
		rock.save();
	}

	/* Creates a dialog which the user can use to enable a location service. 
	 * You should call when you think a location service needs to be enabled
	 */
	private void showEnableLocationAlert() {
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		builder.setTitle(R.string.enable_location_title);
		builder.setMessage(R.string.enable_location_message);
		
		builder.setPositiveButton(R.string.enable_location_settings, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				// Show the Android location settings menu
				Intent settingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
				startActivity(settingsIntent);
			}
		});
		
		builder.setNegativeButton(R.string.enable_location_cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				// Do nothing... The app is much less interesting...
			}
		});
		
		builder.create().show();
	}

	/* Creates a dialog which the user can use to confirm a rock delete
	 */
	private void showConfirmRockDeleteAlert() {
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		
		builder.setTitle(R.string.rock_delete);
		builder.setMessage(R.string.rock_delete_title);
		
		builder.setPositiveButton(R.string.rock_delete_yes, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				// Delete the currently selected rock
				Rock rock = mRockMapOverlay.getSelected();
				
				if(rock != null) {
					mRockMenu.stopEdit();
					rock.delete();
				}
			}
		});
		
		builder.setNegativeButton(R.string.rock_delete_no, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				// Do nothing... 
			}
		});
		
		builder.create().show();
	}
	
	// Invalidate the action bar on the UI thread
	private void invalidateActionBar() {
		uiHandler.post(new Runnable() {
			public void run() {
				invalidateOptionsMenu();
			}
		});
	}
	
	/* Helper function to display the rock list */
	public void showRockList() {
		ArrayList<Rock> rockList;
		
		rockList = Rock.getRocks(this);
		
		// Only how the type of rocks that are currently displayed in the list
		int showHide = mRockMapOverlay.getShowHide();
		if(showHide == RockMapOverlay.SHOW_NOT_PICKED_ROCKS || showHide == RockMapOverlay.SHOW_PICKED_ROCKS) {
			ArrayList<Rock> rockList2 = new ArrayList<Rock>();
			for(Rock rock : rockList) {
				if(rock.isPicked() && showHide == RockMapOverlay.SHOW_NOT_PICKED_ROCKS) {
					continue;
				}
				
				if(!rock.isPicked() && showHide == RockMapOverlay.SHOW_PICKED_ROCKS) { 
					continue;
				}
				
				rockList2.add(rock);
			}
			
			rockList = rockList2;
		}
		
		final GeoPoint currentLoc = mRockLocationManager.getBestLocation();
		
		// Sort the rocks by distance and type
		Collections.sort(rockList, new Comparator<Rock>() {
			
			public int compare(Rock lhs, Rock rhs) {
				// Put not picked before picked
				if(lhs.isPicked() != rhs.isPicked()) {
					if(lhs.isPicked()) {
						return 1;
					} else {
						return -1;
					}
				}
				
				// If we do not have a current location sort by ID for lack of anything less (should be roughly order of creation)
				if(currentLoc == null) {
					if(lhs.getId() > rhs.getId()) {
						return 1;
					} else {
						return -1;
					}
				} else {
					// If we have location sort by distance from current location
					float[] resultLhs = new float[1];
					float[] resultRhs = new float[1];
					
					Location.distanceBetween(currentLoc.getLatitudeE6()/1e6,currentLoc.getLongitudeE6()/1e6,
							lhs.getLat()/1e6, lhs.getLon()/1e6, resultLhs);
					Location.distanceBetween(currentLoc.getLatitudeE6()/1e6,currentLoc.getLongitudeE6()/1e6,
							rhs.getLat()/1e6, rhs.getLon()/1e6, resultRhs);
					
					if(resultLhs[0] > resultRhs[0]) {
						return 1;
					} else {
						return -1;
					}
				}
			}
		});
		
		// Remove focus from overlay
		mRockMapOverlay.setSelected(-1);
		
		// Build titles for list
		String[] titleList = new String[rockList.size()];
		for(int i = 0; i < rockList.size(); i++) {
			titleList[i] = rockList.get(i).toString();
		}
		
		// Drive a AlertDialog with a RockArrayAdapter
		RockArrayAdapter adapter = new RockArrayAdapter(this, titleList, rockList, currentLoc, mRockLocationManager.isUserOnMap());
		ListView listView = new ListView(this);
		listView.setAdapter(adapter);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.rock_list_title);
		builder.setView(listView);
		
		// Show the dialog
		final Dialog dialog = builder.create();
		dialog.show();
		
		// React to selecting a menu option
		listView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				RockArrayAdapter raa = (RockArrayAdapter)parent.getAdapter();
				
				Rock rock = raa.getRock(position);
				
				// Select the rock and move to it
				mRockMapOverlay.setSelected(rock.getId());
				moveMapTo(new GeoPoint(rock.getLat(), rock.getLon()), true);
				
				if(mRockMapOverlay.getShowHide() == RockMapOverlay.SHOW_NOT_PICKED_ROCKS && rock.isPicked() ||
						mRockMapOverlay.getShowHide() == RockMapOverlay.SHOW_PICKED_ROCKS && !rock.isPicked()) {
					changeRockTypeShowHide(RockMapOverlay.SHOW_ALL_ROCKS);
				}
				
				// Close the dialog
				dialog.dismiss();
			}
		});
	}
	
	/*
	 * Broadcast receiver which handles messages that come out about rocks
	 */
	private class RockBroadcastReciever extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			// A message from the rock overlay that a rock was selected
			if(intent.getAction() == RockMapOverlay.ACTION_ROCK_SELECTED) {
				int rockId = intent.getExtras().getInt("id", -1);
				if(rockId <= 0) {
					setState(STATE_DEFAULT);
					
				} else {
					// Give menu the rock to edit
					mRockMenu.editRock(Rock.getRock(context, rockId));
					
					setState(STATE_ROCK_EDIT);
				}
			} 
			
			invalidateActionBar();
		}
		
	}
	
	/*
	 * Listen for requests from the RockMenu
	 */
	private class RockMenuBroadcastReciever extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if(intent.getAction() == RockMenu.ACTION_TAKE_PICTURE) {
				Bundle extras = intent.getExtras();
				
				if(!extras.containsKey("path") || !extras.containsKey("filename")) {
					Log.w("RockAppActivity", "Take picture request failed because no path/filename given!");
					return;
				}
				
				// Get the new image path
				File path = new File(extras.getString("path"));
				path.mkdirs();
				File image = new File(path, extras.getString("filename"));
				try {
					image.createNewFile();
				} catch (IOException e) {
					Log.w("RockAppActivity", "Could not make file for image. " + image.getAbsolutePath() + " " + e.toString());
					return;
				}
				
				// Put together image capture intent
				Intent takePic = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
				takePic.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image));
				
				// Fire intent
				startActivityForResult(Intent.createChooser(takePic, "Capture Image"), REQUEST_PICTURE);
			}
		}
	}
	
	/*
	 * Broadcast receiver which handles messages that come out about location
	 */
	private class LocationBroadcastReciever extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			// A message from the rock overlay that a rock was selected
			if(intent.getAction() == RockLocationManager.ACTION_FIRSTFIX) {
				GeoPoint p = mMapView.getMapCenter();
				
				if(p.equals(startingCenter) && 
				   startingZoom == mMapView.getZoomLevel() && 
				   mMapView.getLatitudeSpan() > DEFAULT_START_ZOOM_LAT_SPAN &&
				   mMapView.getLongitudeSpan() > DEFAULT_START_ZOOM_LONG_SPAN) {
					moveMapTo(mRockLocationManager.getUserLocation(), true);
				}
				
				// Update the action bar (on the UI thread) for the location button
				invalidateActionBar();
			} 
		}
	}
	
	/*
	 * Broadcast receiver which handles messages that come out about group rock 
	 */
	private class RockMapGroupBroadcastReciever extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			// A message from the rock overlay that a rock was selected
			if(intent.getAction() == RockMapOverlay.ACTION_GROUP_SELECTED) {
				GeoPoint p= mMapView.getMapCenter();
				
				GeoPoint pNew = new GeoPoint(intent.getIntExtra("lat", p.getLatitudeE6()),
											 intent.getIntExtra("lon", p.getLongitudeE6()));		
				int latSpan = (int)(Math.ceil(DEFAULT_LAT_LON_SPAN_OVER_MULTIPLIER*intent.getIntExtra("lat-span", mMapView.getLatitudeSpan())));
				int lonSpan = (int)(Math.ceil(DEFAULT_LAT_LON_SPAN_OVER_MULTIPLIER*intent.getIntExtra("lon-span", mMapView.getLongitudeSpan())));
				
				Log.d("GeoPoint", p.toString());
				Log.d("LatSpan", Integer.toString(latSpan));
				Log.d("LonSpan", Integer.toString(lonSpan));
				
				
				// Move map so that the group is displayed as individual rocks now
				mMapView.getController().zoomToSpan(latSpan, lonSpan);
				mMapView.getController().animateTo(pNew);
				// Force the map to update ASAP
				mMapView.postInvalidate();
			} 
		}
	}
}