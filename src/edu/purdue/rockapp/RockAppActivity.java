package edu.purdue.rockapp;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.ItemizedOverlay.OnFocusChangeListener;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MapView.ReticleDrawMode;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.OverlayItem;

import edu.purdue.libwaterapps.rock.Rock;
import edu.purdue.libwaterapps.utils.NotifyArrayList;
import edu.purdue.libwaterapps.view.SlideLayout;
import edu.purdue.libwaterapps.view.maps.RockMapOverlay;


public class RockAppActivity extends MapActivity {
	private MapView mMapView;
	private MapController mMapController;
	private MyLocationOverlay mMyLocation;
	private NotifyArrayList<Rock> mRockList;
	private RockMapOverlay mRockOverlay;
	private boolean mKnowLocation = false;
	private EditText comments;
	private ImageButton picked;
	private ImageButton picture;
	private Bundle bundle = null;
	private SlideLayout menu;
	private String lastPicture;
	
	static final int SPAN_LAT = 3000;
	static final int SPAN_LONG = 3000;

	/* Called by Android when application is created */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		bundle = savedInstanceState;
		// Set the view for the application
		
		setContentView(R.layout.main);
		
		// Store the menu view
		this.menu = (SlideLayout)findViewById(R.id.menu);

		// Find the MapView and setup some defaults
		mMapView = (MapView) findViewById(R.id.map);
		mMapView.setSatellite(true);
		mMapView.setReticleDrawMode(ReticleDrawMode.DRAW_RETICLE_OVER);

		// Save the map controller for later map animation
		mMapController = mMapView.getController();

		// Get a myLocationOverlay to manage the GPS
		mMyLocation = new MyLocationOverlay(this, mMapView);
		mMapView.getOverlays().add(mMyLocation);
		
		// Get a hold of all the views we need to manage
		this.comments = (EditText)this.findViewById(R.id.comments);
		this.picked = (ImageButton)this.findViewById(R.id.button_picked);
		this.picture = (ImageButton)this.findViewById(R.id.button_picture);
		
		this.comments.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) {
				updateRockFromControls();
			}

			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
				// Not Needed
			}

			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				// Not Needed
			}
		});
		
	}

	/* Called by Android when application comes from not in view to in view */
	@Override
	protected void onResume() {
		super.onResume();
		
		enableLocation();
		
		// Get a list of rocks
		mRockList = Rock.getAllRocks(this);
		
		// Make the rock overlays
		mRockOverlay = new RockMapOverlay(mRockList, this.getResources().getDrawable(R.drawable.rock_not_picked),
				this.getResources().getDrawable(R.drawable.rock_picked));
		
		// Hide menu when overlay item loses focus
		mRockOverlay.setOnFocusChangeListener(new OnFocusChangeListener() {
			@SuppressWarnings("rawtypes")
			public void onFocusChanged(ItemizedOverlay overlay, OverlayItem newFocus) {
				if(newFocus == null) {
					mRockOverlay.setCurrent(null);
					((SlideLayout)findViewById(R.id.menu)).hide();
				} else {
					updateControlsWithNewRock(mRockOverlay.getCurrent());
					((SlideLayout)findViewById(R.id.menu)).show();
				}
			}
		});
	
		// Add Overlays to MapView
		mMapView.getOverlays().add(mRockOverlay);
		
		if(bundle != null) {
			if(bundle.containsKey("current")){
				this.mRockOverlay.setCurrent(bundle.getInt("current"));
				this.selectCurrentRock();
			}
			
			if(bundle.containsKey("lastPicture")) {
				this.lastPicture = bundle.getString("lastPicture");
			}
		} 
	}

	/*
	 * Called by Android when application comes from in view to no longer in view
	 */
	@Override
	protected void onPause() {
		super.onPause();
		
		disableLocation();
	}
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
		
		if(this.menu.isOpen()) {
			savedInstanceState.putInt("current", this.mRockOverlay.getCurrent().getId());
		}
		
		if(lastPicture != null) {
			savedInstanceState.putString("lastPicture", lastPicture);
		}
	}

	/* Called by MapActivity to see if a route should be displayed */
	@Override
	public boolean isRouteDisplayed() {
		return false;
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		
		return super.onCreateOptionsMenu(menu);
	}
	
	public void selectCurrentRock() {
		Rock rock = this.mRockOverlay.getCurrent();
		
		this.menu.show();
		
		// Ask map to animate
		mMapController.animateTo(new GeoPoint(rock.getLat(), rock.getLon()));

		// Make sure the animation starts ASAP
		mMapView.postInvalidate();			
	}

	/*
	 * Called as a onTap handler to the move_to_gps button on screen. Handler is
	 * associated by the on_click="" over the view xml
	 */
	public void moveToGps(View view) {
		// We should show a waiting message here....
		if (!mKnowLocation) {
			Toast.makeText(this, "Please wait for GPS Lock", Toast.LENGTH_SHORT).show();
		} else {
			// Ask map to animate
			mMapController.animateTo(mMyLocation.getMyLocation());
			mMapController.zoomToSpan(SPAN_LAT, SPAN_LONG);

			// Make sure the animation starts ASAP
			mMapView.postInvalidate();
		}
	}

	/*
	 * Called as a onTap handler to the rock_add button on screen. Handler is
	 * associated by the on_click="" over the view xml
	 */
	public void addRock(View view) {
		if (!mKnowLocation) {
			Toast.makeText(this, "Please wait for GPS Lock", Toast.LENGTH_SHORT).show();
		} else {
			Rock rock = new Rock(this, mMyLocation.getMyLocation(), false);
			rock.save();
			mRockList.add(rock);
		}
	}

	/* Helper function to stop tracking current location */
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
	
	private void updateControlsWithNewRock(Rock rock) {
		if(rock == null) {
			return;
		}
		
		this.comments.setText(rock.getComments());
		
		if(rock.isPicked()) {
			this.picked.setSelected(true);
		} else {
			this.picked.setSelected(false);
		}
		
		String imagePath = rock.getPicture();
		if(imagePath != null) {
			picture.setImageBitmap(sizePicture(imagePath));
		} else {
			picture.setImageDrawable(
					this.getResources().getDrawable(R.drawable.rock_picture));
		}
	}
	
	private void updateRockFromControls() {
		if(this.mRockOverlay == null) {
			return;
		}
		
		Rock rock = this.mRockOverlay.getCurrent();
		
		if(rock == null || this.comments == null) {
			return;
		}
		
		rock.setComments(this.comments.getText().toString());
		
		rock.save();
	}
	
	public void toggleCurrentRock(View view) {
		ImageButton button = (ImageButton)view;
		Rock rock = this.mRockOverlay.getCurrent();
		
		if(rock == null) {
			return;
		}
		
		if(!rock.isPicked()) {
			rock.setPicked(true);
			button.setSelected(true);
		} else {
			rock.setPicked(false);
			button.setSelected(false);
		}
		
		button.invalidate();
		mMapView.invalidate();
	}
	
	public void showRockList(View view) {
		ArrayList<Rock> rockList = new ArrayList<Rock>();
		
		mRockOverlay.setFocus(null);
		
		rockList.addAll(Rock.getAllNonPickedRocks(this));
		rockList.addAll(Rock.getAllPickedRocks(this));
		
		String[] titleList = new String[rockList.size()];
		
		for(int i = 0; i < rockList.size(); i++) {
			titleList[i] = rockList.get(i).toString();
		}
		
		GeoPoint currentLoc = null;
		if (mKnowLocation) {
			currentLoc = mMyLocation.getMyLocation();
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
				
				mRockOverlay.setCurrent(raa.getRock(position));
				selectCurrentRock();
				dialog.dismiss();
			}
		});
	}
	
	public void takePicture(View view) {
		
		File image = new File(
				Environment.getExternalStorageDirectory() + "/" +
				Environment.DIRECTORY_PICTURES,
				"rock_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".jpg");
		
		lastPicture = image.getAbsolutePath();
				
		Intent takePic = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		takePic.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(image));
		startActivityForResult(Intent.createChooser(takePic, "Capture Image"), 1);
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		switch(requestCode) {
			case 1:
				if(resultCode == -1) {
					Bitmap pic = sizePicture(lastPicture);
					picture.setImageBitmap(pic);
					
					Rock rock = mRockOverlay.getCurrent();
					rock.setPicture(lastPicture);
					rock.save();
				}
			break;	
		}
	}
	
	private Bitmap sizePicture(String imageFile) {
		Drawable camPic = this.getResources().getDrawable(R.drawable.rock_picture);
		int targetW = camPic.getIntrinsicWidth();
		int targetH = camPic.getIntrinsicHeight();
		
		BitmapFactory.Options bmOptions = new BitmapFactory.Options();
		bmOptions.inJustDecodeBounds = true;
		
		BitmapFactory.decodeFile(imageFile, bmOptions);
		int photoW = bmOptions.outWidth;
		int photoH = bmOptions.outHeight;
		
		int scaleFactor = Math.min(photoW/targetW, photoH/targetH);
		
		bmOptions.inJustDecodeBounds = false;
		bmOptions.inSampleSize = scaleFactor;
		bmOptions.inPurgeable = true;
		
		Bitmap bitmap = BitmapFactory.decodeFile(imageFile, bmOptions);
		return bitmap;
	}
}