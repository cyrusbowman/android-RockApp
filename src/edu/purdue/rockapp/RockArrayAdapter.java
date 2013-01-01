package edu.purdue.rockapp;

import java.util.ArrayList;

import android.content.Context;
import android.location.Location;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.maps.GeoPoint;

import edu.purdue.libcommon.rock.Rock;

// ArrayAdapter used to drive the rock list
public class RockArrayAdapter extends ArrayAdapter<String> {
	private final Context context;
	
	private ArrayList<Rock> rocks;
	
	private GeoPoint currentLoc;
	private boolean fromUser;
	
	private static final double METERS_TO_FEET = 3.28084;
	private static final double FEET_TO_MILES = 0.00018939393;
	
	// Array Adapter constructor for displaying rocks in list form
	public RockArrayAdapter(Context context, String[] values, ArrayList<Rock> rocks, GeoPoint currentLoc, boolean fromUser) {
		this(context, values);
		
		this.rocks = rocks;
		this.currentLoc = currentLoc;
		this.fromUser = fromUser;
	}
	
	private RockArrayAdapter(Context context, String[] values) {
		super(context, R.layout.rock_list, values);
		this.context = context;
	}

	/*
	 * Returns the view for each list item in the rock list
	 */
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		Rock rock = this.rocks.get(position);
		
		// Get the layout in class form
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(R.layout.rock_list, parent, false);
		
		// Get the image, type, and distance away views for modification
		ImageView imageView = (ImageView) rowView.findViewById(R.id.rock_list_image);
		TextView typeView = (TextView) rowView.findViewById(R.id.rock_list_type);
		TextView distanceView = (TextView) rowView.findViewById(R.id.rock_list_distance);
		
		// Get the image and type based on the rock picked status
		if(rock.isPicked()) {
			imageView.setImageDrawable(this.context.getResources().getDrawable(R.drawable.rock_up));
			typeView.setText(R.string.rock_picked_up);
		} else {
			imageView.setImageDrawable(this.context.getResources().getDrawable(R.drawable.rock_down));
			typeView.setText(R.string.rock_not_picked_up);
		}

		if(currentLoc == null) {
			distanceView.setText("");
		} else {
			float[] results = new float[1];
			Location.distanceBetween(currentLoc.getLatitudeE6()/1e6, currentLoc.getLongitudeE6()/1e6, 
					rock.getLat()/1e6, rock.getLon()/1e6, results);
			
			// Convert to feet
			results[0] *= METERS_TO_FEET;
			
			// Format the message based on how far away it is
			String text;
			if(results[0] < 10) {
				text = String.format("%.1f %s", results[0], context.getString(R.string.rock_list_feet));
			} else if(results[0] < 500) {
				text = String.format("%d %s", (int)results[0], context.getString(R.string.rock_list_feet));
			} else {
				text = String.format("%.1f %s", results[0] * FEET_TO_MILES, context.getString(R.string.rock_list_miles));
			}
			
			if(fromUser) {
				text = text.concat(String.format(" %s", context.getString(R.string.rock_list_from_you)));
			} else {
				text = text.concat(String.format(" %s", context.getString(R.string.rock_list_from_center)));
			}
			
			// Set the text value
			distanceView.setText(text);
		}
		
		return rowView;
	}
	
	public Rock getRock(int i) {
		return this.rocks.get(i);
	}
}
