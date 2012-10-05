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

import edu.purdue.libwaterapps.rock.Rock;

public class RockArrayAdapter extends ArrayAdapter<String> {
	private final Context context;
	private ArrayList<Rock> rocks;
	private GeoPoint currentLoc;
	private static final double METERS_TO_FEET = 3.28084;
	private static final double FEET_TO_MILES = 0.00018939393;
	
	public RockArrayAdapter(Context context, String[] values, ArrayList<Rock> rocks, GeoPoint currentLoc) {
		this(context, values);
		
		this.rocks = rocks;
		this.currentLoc = currentLoc;
		
	}
	
	private RockArrayAdapter(Context context, String[] values) {
		super(context, R.layout.rock_list, values);
		this.context = context;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		Rock rock = this.rocks.get(position);
		
		LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(R.layout.rock_list, parent, false);
		ImageView imageView = (ImageView) rowView.findViewById(R.id.rock_list_image);
		TextView typeView = (TextView) rowView.findViewById(R.id.rock_list_type);
		TextView distanceView = (TextView) rowView.findViewById(R.id.rock_list_distance);
		
		if(rock.isPicked()) {
			imageView.setImageDrawable(this.context.getResources().getDrawable(R.drawable.rock_picked));
			typeView.setText(R.string.rock_picked_up);
		} else {
			imageView.setImageDrawable(this.context.getResources().getDrawable(R.drawable.rock_not_picked));
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
			
			String text;
			if(results[0] == 1) {
				text = "1 foot away";
			} else if(results[0] < 10) {
				text = String.format("%.1f feet away", results[0]);
			} else if(results[0] < 500) {
				text = String.format("%d feet away", (int)results[0]);
			} else {
				results[0] *= FEET_TO_MILES;
				if(results[0] == 1) {
					text = "1 mile away";
				} else {
					text = String.format("%.1f miles away", results[0]);
				}
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
