package edu.purdue.rockapp;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapView;
import com.google.android.maps.OverlayItem;

import edu.purdue.libwaterapps.rock.Rock;

public class RockMapOverlay extends ItemizedOverlay<OverlayItem> {
	private ArrayList<Rock> rocks;
	private ArrayList<RockOverlayItem> overlays;
	private int current;
	private Context context;
	private Drawable pickedMarker;
	private Drawable notPickedMarker;
	private RockOverlayItem inDrag=null;
	private ImageView dragImagePicked=null;
	private ImageView dragImageNotPicked=null;
	private ImageView dragImage;
	private int xDragImageOffset=0;
	private int yDrawImageOffset=0;
	private int xDragTouchOffset=0;
	private int yDragTouchOffset=0;
	private int overlayIndex;
	private Point overlayPoint;
	private boolean didMove;
	
	public RockMapOverlay(Context context, Drawable notPickedMarker, Drawable pickedMarker,
			ImageView dragImagePicked, ImageView dragImageNotPicked)  {
		super(notPickedMarker);
		
		this.current = -1;
		this.context = context;
		this.notPickedMarker = boundCenter(notPickedMarker);
		this.pickedMarker = boundCenter(pickedMarker);
		
		this.dragImagePicked = dragImagePicked;
		this.dragImageNotPicked = dragImageNotPicked;
		
		// Create a DB connection and get all the rocks
		rocks = Rock.getAllRocks(this.context);
		overlays = new ArrayList<RockOverlayItem>();
		
		for( Rock rock : rocks ) {
			RockOverlayItem overlay = new RockOverlayItem(new GeoPoint(rock.getLat(), rock.getLon()), "Rock", "Rock");
			
			overlays.add(overlay);
		}
		
		// Known work around to Android ArrayIndexOutOfBounds exception when
		// list is empty but added to a MapView
		this.populate();
	}
	
	
	/* This is called by Android after a call to populate. It is asking 
	 * for each OverlayItem individually to draw them */
	@Override
	protected OverlayItem createItem(int i) {
		return overlays.get(i);
	}
	
	/* This is called by Android to get the size of the overlay list
	 * so that it can safely call createItem() */
	@Override
	public int size() {
		return overlays.size();
	}
	
	/* Provides the index of the overlay item which was last touched. */
	@Override
	protected boolean onTap(int i) {
		if(inDrag != null) 
			return false;
		
		this.current = i;
		
		return true;
	}
	
	
	public int getCurrent() {
		return this.current;
	}
	
	public void setCurrent(int i) {
		this.current = i; 
	}
	
	public void setCurrent(Rock rock) {
		for(int i = 0; i < this.rocks.size(); i++) {
			if(this.rocks.get(i).getId() == rock.getId()) {
 				this.current = i;
 				break;
			}
		}
		
		return;
	}
	
	public Rock getClosestRock(double sLat, double sLon) {
		float shortest_distance = Float.POSITIVE_INFINITY;
		Rock closest_rock = null;
		float[] results = {};
		
		
		
		for( Rock rock : rocks ) {
			Location.distanceBetween(sLat, sLon, ((double)rock.getLat())/1e6, ((double)rock.getLon())/1e6, results);
			
			if(results[0] < shortest_distance) {
				shortest_distance = results[0];
				closest_rock = rock;
			}
		}
		
		return closest_rock;
		
	}
	
	public OverlayItem getCurrentOverlayItem() {
		OverlayItem overlayItem = null;
		
		if(this.current >= 0 && this.current < this.overlays.size()) {
		 overlayItem = this.overlays.get(this.current);
		}
		
		return overlayItem;
	}
	
	public Rock getCurrentRock() {
		Rock rock = null;
		
		if(this.current >= 0 && this.current < this.rocks.size()) {
		 rock = this.rocks.get(this.current);
		}
		
		return rock;
	}
	
	/* Provides the movement events so we can move rocks around */
	@Override
	public boolean onTouchEvent(MotionEvent event, MapView view) {
		final int action=event.getAction();
		final int x=(int)event.getX();
		final int y=(int)event.getY();
		boolean result=false;
		
		if(action==MotionEvent.ACTION_DOWN){
			for(RockOverlayItem item : overlays) {
				overlayPoint = new Point(0,0);
				
				view.getProjection().toPixels(item.getPoint(), overlayPoint);
				
				if(hitTest(item, item.getMarker(OverlayItem.ITEM_STATE_SELECTED_MASK), x-overlayPoint.x, y-overlayPoint.y)) {
					overlayIndex = overlays.indexOf(item);
					Rock rock = rocks.get(overlayIndex);
					
					setCurrent(overlayIndex);
					setFocus(item);
					populate();
					
					if(rock.isPicked()) {
						dragImage = dragImagePicked;
					} else {
						dragImage = dragImageNotPicked;
					}
					
					dragImage.setSelected(true);
					
					result = false;
					inDrag = item;
					inDrag.setHide(true);
					//overlays.remove(inDrag);
					populate();
					
					xDragTouchOffset=0;
					yDragTouchOffset=0;
					
					didMove = false;
					setDragImagePosition(overlayPoint.x, overlayPoint.y);
					dragImage.setVisibility(View.VISIBLE);
					
					xDragTouchOffset=x-overlayPoint.x;
					yDragTouchOffset=y-overlayPoint.y;
					
					break;
				}
			}
		} else if (action==MotionEvent.ACTION_MOVE && inDrag!=null) {
			
			if(didMove || (Math.abs(overlayPoint.x - x) > 30 || Math.abs(overlayPoint.y - y) > 30)) {
				didMove = true;
				setDragImagePosition(x,y);
			}
			result=true;
		} else if (action==MotionEvent.ACTION_UP && inDrag!=null) {
			dragImage.setVisibility(View.GONE);
			
			GeoPoint pt;
			if(didMove || (Math.abs(overlayPoint.x - x) > 30 || Math.abs(overlayPoint.y - y) > 30)) {
				pt=view.getProjection().fromPixels(x-xDragTouchOffset,
						y-yDragTouchOffset);
			} else {
				pt = view.getProjection().fromPixels(overlayPoint.x, overlayPoint.y);
			}
			
			RockOverlayItem toDrop=new RockOverlayItem(pt, inDrag.getTitle(),
					inDrag.getSnippet());
			
			Rock rock = rocks.get(overlayIndex);
			rock.setLat(pt.getLatitudeE6());
			rock.setLon(pt.getLongitudeE6());
			rock.save();
			
			overlays.set(overlayIndex, toDrop);
			
			setCurrent(overlayIndex);
			populate();
			
			inDrag = null;
			result = true;
		}
		
		return (result || super.onTouchEvent(event, view));
	}
	
	private void setDragImagePosition(int x, int y) {
		dragImage.setAlpha(255);
		
		RelativeLayout.LayoutParams lp = 
			(RelativeLayout.LayoutParams)dragImage.getLayoutParams();
		
		
		lp.setMargins(x-xDragImageOffset-xDragTouchOffset-dragImage.getWidth()/2,
				y-yDrawImageOffset-yDragTouchOffset-dragImage.getHeight()/2, 0, 0);
		
		dragImage.setLayoutParams(lp);
	}
	
	/* Add a new rock to the overlay list */
	public void addRock(GeoPoint location) {
		Rock rock = new Rock(this.context, location, false);
		RockOverlayItem overlay = new RockOverlayItem(location, "Rock", "Rock");
		
		// Make a new rock and add it to the list
		rocks.add(rock);
		overlays.add(overlay);
		
		// Save the rock now that its on the screen
		rock.save();
		
		// Known work around to Android ArrayIndexOutOfBounds exception when
		// list is empty but added to a MapView
		this.setLastFocusedIndex(-1);
		
		// Update the display after changing the list
		
		this.populate();
	}
	
	public void setCurrentPicked(boolean picked) {
		Rock rock = getCurrentRock();
		
		if(picked) {
			rock.setPicked(true);
		} else {
			rock.setPicked(false);
		}
		
		rock.save();
	}
	
	class RockOverlayItem extends OverlayItem {
		private boolean hide;
		
		public RockOverlayItem(GeoPoint point, String title, String snippet) {
			super(point, title, snippet);
			
			hide = false;
		}

		@Override  
		public Drawable getMarker(int stateBitset) {  
			Drawable marker;
			
			int i = overlays.indexOf(this);
			Rock rock = rocks.get(i);
			
			if(rock.isPicked()) {
				marker = pickedMarker;
			} else {
				marker = notPickedMarker;
			}
			
			if(hide) {
				marker.setAlpha(0);
			} else {
				marker.setAlpha(255);
			}
			
			if(current == i) {
				OverlayItem.setState(marker, OverlayItem.ITEM_STATE_SELECTED_MASK);
			} else {
				OverlayItem.setState(marker, 0);
			}
			
			return marker;
		}
		
		public void setHide(boolean hide) { 
			this.hide = hide;
		}
	}
}
