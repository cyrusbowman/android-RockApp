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
			
			distanceView.setText(Float.toString(results[0]) + " meters away");
		}
		
		return rowView;
	}
	
	public Rock getRock(int i) {
		return this.rocks.get(i);
	}
}
