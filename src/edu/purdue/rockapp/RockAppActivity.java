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
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
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
import com.google.android.maps.MyLocationOverlay;

import edu.purdue.libwaterapps.rock.Rock;
import edu.purdue.libwaterapps.view.maps.RockMapOverlay;
import edu.purdue.rockapp.view.RockMenu;
import edu.purdue.rockapp.view.RockMove;

public class RockAppActivity extends MapActivity {
	private MapView mMapView;
	private MapController mMapController;
	private MyLocationOverlay mMyLocation;
	private RockMapOverlay mRockOverlay;
	private RockMenu mRockMenu;
	private RockMove mRockMove;
	private int mCurrentState;
	private boolean mKnowLocation = false;
	private Bundle bundle = null;
	private RockBroadcastReciever rockBroadcastReciever;
	private RockMenuBroadcastReciever rockMenuBroadcastReciever;
	
	// UI States
	private static final int STATE_DEFAULT = 0;
	private static final int STATE_ROCK_EDIT = 1;
	private static final int STATE_ROCK_MOVE = 2; 
	
	// Request codes for activity results
	private static final int REQUEST_PICTURE = 1;
	
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
		
		// Get a myLocationOverlay to manage the GPS
		mMyLocation = new MyLocationOverlay(this, mMapView);
		mMyLocation.enableCompass();
		mMapView.getOverlays().add(mMyLocation);
		
		// Add the rocks to the map 
		mRockOverlay = new RockMapOverlay(this);
		mMapView.getOverlays().add(mRockOverlay);
		
		mRockMove = (RockMove)findViewById(R.id.rock_move);
		
		// Receiver used to listen to rocks
		rockBroadcastReciever = new RockBroadcastReciever();
		// Receiver used to listen to rockMenu
		rockMenuBroadcastReciever = new RockMenuBroadcastReciever();
		
		// Restore from bundle
		if(bundle != null) {
			int rockId = bundle.getInt("rock_edit.currentRock", Rock.BLANK_ROCK_ID);
			if(rockId != Rock.BLANK_ROCK_ID) {
				mRockOverlay.setSelected(rockId);
			}
			
			switch(bundle.getInt("state", STATE_DEFAULT)) {
				case STATE_ROCK_EDIT:
					// Get RockId and restore view states for setState()
					mRockMenu.editRock(Rock.getRock(this, rockId));
				break;
				
				case STATE_ROCK_MOVE:
					GeoPoint p = new GeoPoint(savedInstanceState.getInt("rock_move.currentMoveLocLat"),
											  savedInstanceState.getInt("rock_move.currentMoveLocLon"));
					// Set where the marker should be
					mRockMove.setCurrentMoveLocation(p);
				break;
			}
			
			// Restore previous state
			setState(bundle.getInt("state", STATE_DEFAULT));
		} else {
			// Otherwise set default initial state
			setState(STATE_DEFAULT);
		}
		
	}

	/* Called by Android when application comes from not in view to in view */
	@Override
	protected void onResume() {
		super.onResume();
		
		// Start looking for current location
		enableLocation();
		
		// Listen to changes in the rocks and automatically update from them
		mRockOverlay.registerListeners();
		mRockMenu.registerListeners();
		
		// Listen in on rocks being selected so to show the edit menu
		LocalBroadcastManager.getInstance(this).registerReceiver(rockBroadcastReciever, new IntentFilter(Rock.ACTION_SELECTED));
		LocalBroadcastManager.getInstance(this).registerReceiver(rockBroadcastReciever, new IntentFilter(Rock.ACTION_DOUBLE_TAP));
		LocalBroadcastManager.getInstance(this).registerReceiver(rockBroadcastReciever, new IntentFilter(Rock.ACTION_MOVE_DONE));
		
		// Listen for image requests from RockMenu
		LocalBroadcastManager.getInstance(this).registerReceiver(rockMenuBroadcastReciever, new IntentFilter(RockMenu.ACTION_TAKE_PICTURE));
		LocalBroadcastManager.getInstance(this).registerReceiver(rockMenuBroadcastReciever, new IntentFilter(RockMenu.ACTION_MOVE_ROCK));
	}

	/*
	 * Called by Android when application comes from in view to no longer in view
	 */
	@Override
	protected void onPause() {
		super.onPause();
		
		// No need for location when on on screen
		disableLocation();
		
		// No need to react to new rocks when not on screen (a new list will be generated in onResume)
		mRockOverlay.unregisterListeners();
		mRockMenu.unregisterListeners();
		
		// No need to listen to rock messages (no map to generate any)
		LocalBroadcastManager.getInstance(this).unregisterReceiver(rockBroadcastReciever);
		
		// Don't listen for image requests when paused
		LocalBroadcastManager.getInstance(this).unregisterReceiver(rockMenuBroadcastReciever);
		
		// Flush rock edit menu to db
		mRockMenu.flush();
	}
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
		
		savedInstanceState.putInt("state", mCurrentState);
		
		switch(mCurrentState) {
			case STATE_ROCK_EDIT:
				savedInstanceState.putInt("rock_edit.currentRock", mRockOverlay.getSelected().getId());
			break;
			
			case STATE_ROCK_MOVE:
				savedInstanceState.putInt("rock_edit.currentRock", mRockOverlay.getSelected().getId());
				
				GeoPoint p = mRockMove.getCurrentMoveLocation();
				savedInstanceState.putInt("rock_move.currentMoveLocLat", p.getLatitudeE6());
				savedInstanceState.putInt("rock_move.currentMoveLocLon", p.getLongitudeE6());
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
		
		MenuItem showHideItem = menu.findItem(R.id.show_hide);
		MenuItem currentShowHideItem;	
		
		switch(mRockOverlay.getShowHide()) {
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
				mRockOverlay.setShowHide(RockMapOverlay.SHOW_ALL_ROCKS);
				currentShowHideItem = menu.findItem(R.id.all_rocks);
			break;
		}
		
		// Copy the current selection to the action bar
		showHideItem.setIcon(currentShowHideItem.getIcon());
		showHideItem.setTitle(currentShowHideItem.getTitle());
		
		// Mark the current one as checked 
		currentShowHideItem.setChecked(true);
		
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
				mRockOverlay.setShowHide(RockMapOverlay.SHOW_ALL_ROCKS);
				mMapView.postInvalidate();
				invalidateOptionsMenu();
				result = true;
			break;
			
			case R.id.not_picked_rocks:
				// set the new showHide, update the map, and update the action bar
				mRockOverlay.setShowHide(RockMapOverlay.SHOW_NOT_PICKED_ROCKS);
				mMapView.postInvalidate();
				invalidateOptionsMenu();
				result = true;
			break;
			
			case R.id.picked_rocks:
				// set the new showHide, update the map, and update the action bar
				mRockOverlay.setShowHide(RockMapOverlay.SHOW_PICKED_ROCKS);
				mMapView.postInvalidate();
				invalidateOptionsMenu();
				result = true;
			break;
				
			case R.id.list:
				showRockList();
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
					Rock rock = mRockOverlay.getSelected();
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
			
			case STATE_ROCK_MOVE:
				// Do nothing
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
			
			case STATE_ROCK_MOVE:
				mRockMove.move(mRockOverlay.getSelected(), mMapView);
			break;
		}
		
		// Officially in new state
		mCurrentState =  newState;
	}
	
	/*
	 * Moves the map to current GPS location (if a fix is known)
	 */
	public void moveToGps() {
		if (!mKnowLocation) {
			Toast.makeText(this, "Please wait for GPS Lock", Toast.LENGTH_SHORT).show();
		} else {
			// Ask map to animate
			mMapController.animateTo(mMyLocation.getMyLocation());

			// Make sure the animation starts ASAP
			mMapView.postInvalidate();
		}
	}

	/*
	 * Try to create a new rock and add it to the rock list
	 * If it fails because the current location is unknown then use the center of the screen.
	 */
	public void addRock() {
		GeoPoint p;
		
		// Put rock at current location, otherwise at the center of the screen
		if (!mKnowLocation) {
			p = mMapView.getMapCenter();
		} else {
			p = mMyLocation.getMyLocation();
		}
		
		// Rock and save in DB (triggering it to display on the map)
		Rock rock = new Rock(this, p, false);
		rock.save();
	}

	/* 
	 * Helper function to stop tracking current location 
	 */
	private void disableLocation() {
		// Mark that we no longer know where we are
		mKnowLocation = false;

		// Ask to stop tracking location
		mMyLocation.disableMyLocation();
	}

	/*
	 * Helper function to start tracking current location. Marks when we have
	 * the first fix
	 */
	private void enableLocation() {
		// Make sure we mark that we do not know where we are
		mKnowLocation = false;

		// Ask to start find location
		mMyLocation.enableMyLocation();

		// Mark that we now know it for other use
		mMyLocation.runOnFirstFix(new Runnable() {
			public void run() {
				mKnowLocation = true;
			}
		});
	}
	
	public void showRockList() {
		ArrayList<Rock> rockList;
		
		rockList = Rock.getRocks(this);
		
		final GeoPoint currentLoc = mMyLocation.getMyLocation();
		
		Collections.sort(rockList, new Comparator<Rock>() {
			public int compare(Rock lhs, Rock rhs) {
				if(lhs.isPicked() != rhs.isPicked()) {
					if(lhs.isPicked()) {
						return 1;
					} else {
						return -1;
					}
				}
				
				if(currentLoc == null) {
					if(lhs.getId() > rhs.getId()) {
						return 1;
					} else {
						return -1;
					}
				} else {
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
		
		mRockOverlay.setFocus(null);
		
		
		String[] titleList = new String[rockList.size()];
		
		for(int i = 0; i < rockList.size(); i++) {
			titleList[i] = rockList.get(i).toString();
		}
		
		RockArrayAdapter adapter = new RockArrayAdapter(this, titleList, rockList, currentLoc);
		ListView listView = new ListView(this);
		listView.setAdapter(adapter);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.rock_list_title);
		builder.setView(listView);
		
		final Dialog dialog = builder.create();
		dialog.show();
		
		listView.setOnItemClickListener(new OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				RockArrayAdapter raa = (RockArrayAdapter)parent.getAdapter();
				
				mRockOverlay.setSelected(raa.getRock(position).getId());
				mMapController.animateTo(new GeoPoint(raa.getRock(position).getLat(), raa.getRock(position).getLon()));
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
			if(intent.getAction() == Rock.ACTION_SELECTED) {
				int rockId = intent.getExtras().getInt("id", Rock.BLANK_ROCK_ID);
				if(rockId == Rock.BLANK_ROCK_ID) {
					setState(STATE_DEFAULT);
					
				} else {
					// Give menu the rock to edit
					mRockMenu.editRock(Rock.getRock(context, rockId));
					
					setState(STATE_ROCK_EDIT);
				}
			} else if(intent.getAction() == Rock.ACTION_DOUBLE_TAP) {
			//	setState(STATE_ROCK_MOVE);
			} else if(intent.getAction() == Rock.ACTION_MOVE_DONE) {
				setState(STATE_ROCK_EDIT);
			}
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
			} else if (intent.getAction() == RockMenu.ACTION_MOVE_ROCK) {
				setState(STATE_ROCK_MOVE);
			}
		}
	}
}