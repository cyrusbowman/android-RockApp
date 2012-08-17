package edu.purdue.rockapp.view;

import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.support.v4.content.LocalBroadcastManager;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Projection;

import edu.purdue.libwaterapps.rock.Rock;
import edu.purdue.rockapp.R;

public class RockMove extends FrameLayout {
	private Context context; 
	private boolean moving;
	private Rock rock;
	private MapView mapView;
	private ImageView rockImage;
	private ActionMode actionMode;
	private GeoPoint currentLoc;
	
	public RockMove(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		this.context = context;
	}
	
	public void move(Rock rock, MapView mapView) {
		if(moving) {
			return;
		}
		moving = true;
		
		// Keep track of the rock and mapVew
		this.rock = rock;
		this.mapView = mapView;
		
		// Put a image view to move around
		rockImage = new ImageView(context);
		rockImage.setImageDrawable(rock.getDrawable());
		
		// Add it to layout to show
		this.addView(rockImage, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
		
		// Move the image view to the right spot on the screen
		if(currentLoc == null) {
			setCurrentMoveLocation(new GeoPoint(rock.getLat(), rock.getLon()));
		}
		
		// Show everything
		setVisibility(VISIBLE);
		
		updatePadding();
		
		// Show the accept/reject CAB
		actionMode = startActionMode(new RockMoveActionMode());
	}
	
	public void stopMove(boolean saveResult) {
		if(!moving) {
			return;
		}
		moving = false;
		
		setVisibility(INVISIBLE);
		
		if(saveResult) {
			rock.setLat(currentLoc.getLatitudeE6());
			rock.setLon(currentLoc.getLongitudeE6());
			rock.save();
		}
		
		// Don't need the image view anymore
		removeViewInLayout(rockImage);
		
		// Hide CAB
		if(actionMode != null) {
			actionMode.finish();
		}
			
		rockImage = null;
		rock = null;
		mapView = null;
		currentLoc = null;
		
		LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(Rock.ACTION_MOVE_DONE));
	}
	
	public void setCurrentMoveLocation(GeoPoint gp) {
		currentLoc = gp;
		
		updatePadding();
	}
	
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		super.onTouchEvent(event);
		
		int x = (int)event.getX();
		int y = (int)event.getY();
		
		Projection proj = mapView.getProjection();
		
		Point curP = new Point();
		proj.toPixels(currentLoc, curP);
		
		currentLoc = proj.fromPixels(x, y);
	
		updatePadding();
	
		// Capture all touches
		return true;
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		updatePadding();
	}
	
	private void updatePadding() {
		
		if(rockImage == null) {
			return;
		}
		
		Point p = new Point();
		Projection proj = mapView.getProjection();
		proj.toPixels(currentLoc, p);
		
		// Stay in x bounds
		if(p.x < rockImage.getDrawable().getIntrinsicWidth()/2) {
			p.x = rockImage.getDrawable().getIntrinsicWidth()/2;
		} else if( p.x > getMeasuredWidth() - rockImage.getDrawable().getIntrinsicWidth()/2) {
			p.x = getMeasuredWidth() - rockImage.getDrawable().getIntrinsicWidth()/2;
		}
		
		// Stay in y bounds
		if(p.y < rockImage.getDrawable().getIntrinsicHeight()/2) {
			p.y = rockImage.getDrawable().getIntrinsicHeight()/2;
		} else if(p.y > getMeasuredHeight() - rockImage.getDrawable().getIntrinsicHeight()/2) {
			p.y = getMeasuredHeight() - rockImage.getDrawable().getIntrinsicHeight()/2;
		}
		
		// Update display
		rockImage.setPadding(p.x, p.y, 0, 0);
		invalidate();
	}
	
	public GeoPoint getCurrentMoveLocation() {
		return currentLoc;
	}
	
	private class RockMoveActionMode implements ActionMode.Callback {


		public boolean onCreateActionMode(ActionMode mode, Menu menu) {
			MenuInflater inflater = mode.getMenuInflater();
			inflater.inflate(R.menu.rock_move, menu);
			
			return true;
		}

		public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
			return false;
		}
		
		public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
			boolean result = false;
			switch(item.getItemId()) {
				case R.id.accept:
					stopMove(true);
					
					result = true;
				break;
				
				case R.id.reject:
					stopMove(false);
					
					result = true;
				break;
			}
			
			return result;
		}

		public void onDestroyActionMode(ActionMode mode) {
			// Clean reference to this now destroyed ActionMode
			actionMode = null;
			
			stopMove(false);
		}
	}
}