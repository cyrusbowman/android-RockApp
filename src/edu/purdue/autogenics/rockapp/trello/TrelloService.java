package edu.purdue.autogenics.rockapp.trello;

import java.util.ArrayList;
import java.util.UUID;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.android.maps.GeoPoint;

import edu.purdue.autogenics.libcommon.db.RockDB;
import edu.purdue.autogenics.libcommon.provider.RockProvider;
import edu.purdue.autogenics.libcommon.rock.Rock;
import edu.purdue.autogenics.libcommon.trello.IntentBoard;
import edu.purdue.autogenics.libcommon.trello.IntentList;
import edu.purdue.autogenics.libcommon.trello.TrelloRequest;
import edu.purdue.autogenics.rockapp.R;

import android.app.Service;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

public class TrelloService extends Service {
	static final int registerClient = 9999;
	static final int unregisterClient= 9998;
	static final int shutdownService = 9994;
		
	private Boolean serviceIsRunning = false; // Is service started already
	private serviceHandler myServiceHandler = new serviceHandler(this);
	final Messenger mMessenger = new Messenger(myServiceHandler);
	
	public static final String Board1 = "Rocks Test Board";
	public static final String List1 = "Rocks In Fields";
	public static final String List1Desc = "These are the rocks that are still in the field and need to be removed.";
	public static final String List2 = "Rocks Removed";
	public static final String List2Desc = "These are the rocks that have been picked up.";
	
	/** 
	* A constructor is required, and must call the super IntentService(String)
	* constructor with a name for the worker thread.
	*/
	public TrelloService() {
		super();
	}

	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		//android.os.Debug.waitForDebugger();
		Log.d("TrelloService","Binding");
		if(serviceIsRunning == false){
			//Do initial startups, most done in serviceHandler constructor
			serviceIsRunning = true;
		}
		return mMessenger.getBinder();
	}
	
	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
	}

	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// TODO Auto-generated method stub
		Log.d("RockAppTrelloService", "RECEIVED INTENT");
		
		if(intent.getAction() == this.getString(edu.purdue.autogenics.libcommon.R.string.trello_compatiable)){
			Log.d("RockAppTrelloService", "I AM TRELLO COMPATABLE");
		}
		
		Bundle data = intent.getExtras();
		if(data.containsKey("request")){
			if(data.getString("request").contentEquals("updateData")){
				Log.d("RockAppTrelloService", "updateData request received");
				Log.d("RockAppTrelloService","type:" +  data.getString("type"));
				Log.d("RockAppTrelloService","id:" +  data.getString("id"));
				Log.d("RockAppTrelloService","name:" +  data.getString("name"));
				Log.d("RockAppTrelloService","desc:" +  data.getString("desc"));
				Log.d("RockAppTrelloService","listid:" +  data.getString("list_id"));
				
				String strLatLng = data.getString("name");
				Boolean picked = data.getString("list_id").contentEquals("1") ? false : true;
				//Lat lng parsing
				Pattern p = Pattern.compile("Lat: ([-]?)([0-9]+[.][0-9]+) Lng: ([-]?)([0-9]+[.][0-9]+).*");
				Matcher m = p.matcher(strLatLng);
				if(m.find()){
				    MatchResult mr=m.toMatchResult();
				    String neglat=mr.group(1);
				    String lat=mr.group(2).replace(".", "");
				    String neglng=mr.group(3);
				    String lng=mr.group(4).replace(".", "");
				    
				    while(lat.length() < 8){
				    	lat = lat + "0";
					}
					while(lng.length() < 8){
						lng = lng + "0";
					}
				    Integer intLat = Integer.parseInt(neglat + lat);
				    Integer intLng = Integer.parseInt(neglng + lng);
				    
					Rock rock = Rock.getRockByTrelloId(this.getApplicationContext(), data.getString("id"));
					rock.setTrelloId(data.getString("id"));
					rock.setPicked(picked);
					rock.setLat(intLat);
					rock.setLon(intLng);
					rock.setActualLat(intLat);
					rock.setActualLon(intLng);
					rock.setComments(data.getString("desc"));
					rock.save();
				}
			} else if(data.getString("request").contentEquals("dataRequest")){
				Log.d("RockAppTrelloService", "dataRequest request received");
				Log.d("RockAppTrelloService","type:" +  data.getString("type"));
				Log.d("RockAppTrelloService","id:" +  data.getString("id"));
				Log.d("RockAppTrelloService","oldlistid:" +  data.getString("list_id"));
				
				//Pass back board data for this trello id
				Rock rock = Rock.getRockByTrelloId(this.getApplicationContext(), data.getString("id"));
				
				if(rock != null){
					Intent sendIntent = new Intent();
					Bundle extras = new Bundle();
					extras.putString("UpdateData", "Nothing matters");
					extras.putString("type", "card");
					extras.putString("id", data.getString("id"));
					
					
					String negLat = "";
					Integer actLat = rock.getActualLat();
					if(rock.getActualLat() < 0){
						negLat = "-";
						actLat = actLat * -1;
					}
					Integer bigLat = actLat / 1000000;
					Integer smallLat = actLat  - (bigLat * 1000000);
					
					String negLng = "";
					Integer actLng = rock.getActualLon();
					if(rock.getActualLon() < 0){
						negLng = "-";
						actLng = actLng * -1;
					}
					Integer bigLng = actLng / 1000000;
					Integer smallLng = actLng  - (bigLng * 1000000);
					
					String doneLat = Integer.toString(bigLat) + "." + Integer.toString(smallLat);
					String doneLng =  Integer.toString(bigLng) + "." + Integer.toString(smallLng);
					
					while(doneLat.length() < 8){
						doneLat = doneLat + "0";
					}
					while(doneLng.length() < 8){
						doneLng = doneLng + "0";
					}
					
					String newName = "Lat: " + negLat + doneLat + " Lng: "+ negLng + doneLng;
					
					Log.d("RockAppTrelloService","new name:" +  newName);
					extras.putString("name", newName);
					extras.putString("desc", rock.getComments());
					
					String picked = (rock.isPicked() == true) ? "2" : "1";
					
					extras.putString("list_id", picked);
					
					Log.d("RockAppTrelloService","listid:" + picked);
					
					sendIntent.setPackage("edu.purdue.autogenics.trello");
					sendIntent.setAction(Intent.ACTION_SEND);
					sendIntent.putExtras(extras);
					this.startService(sendIntent);
				}
			} else if(data.getString("request").contentEquals("updateId")){
				Log.d("RockAppTrelloService", "updateId request received");
				Log.d("RockAppTrelloService","type:" +  data.getString("type"));
				Log.d("RockAppTrelloService","id:" +  data.getString("id"));
				Log.d("RockAppTrelloService","newid:" +  data.getString("new_id"));
				Log.d("RockAppTrelloService","listid:" +  data.getString("list_id"));
				
				Rock new_rock = Rock.getRockByTrelloId(this.getApplicationContext(), data.getString("id"));
				if(new_rock != null){
					Log.d("RockAppTrelloService", "Setting new_id");
					ContentValues vals = new ContentValues();
					vals.put(RockProvider.Constants.TRELLO_ID, data.getString("new_id"));
					String where = RockProvider.Constants._ID + "=?";
					String[] whereArgs = {Integer.toString(new_rock.getId())};
					this.getApplicationContext().getContentResolver().update(RockProvider.Constants.CONTENT_URI,vals, where, whereArgs);
				
					Intent actionIntent = new Intent(Rock.ACTION_UPDATED);
					// Send a broadcast that a rock was added/updated
					actionIntent.putExtra("id", new_rock.getId());
					LocalBroadcastManager.getInstance(this.getApplicationContext()).sendBroadcast(actionIntent);
				}
			} else if(data.getString("request").contentEquals("addCard")){
				Log.d("RockAppTrelloService", "addCard request received");
				Log.d("RockAppTrelloService","type:" +  data.getString("type"));
				Log.d("RockAppTrelloService","id:" +  data.getString("id"));
				Log.d("RockAppTrelloService","listid:" +  data.getString("list_id"));
				Log.d("RockAppTrelloService","name:" +  data.getString("name"));
				Log.d("RockAppTrelloService","desc:" +  data.getString("desc"));
				
				String strLatLng = data.getString("name");
				Boolean picked = data.getString("list_id").contentEquals("1") ? false : true;
				//Lat lng parsing
				Pattern p = Pattern.compile("Lat: ([-]?)([0-9]+[.][0-9]+) Lng: ([-]?)([0-9]+[.][0-9]+).*");
				Matcher m = p.matcher(strLatLng);
				if(m.find()){
				    MatchResult mr=m.toMatchResult();
				    String neglat=mr.group(1);
				    String lat=mr.group(2).replace(".", "");
				    String neglng=mr.group(3);
				    String lng=mr.group(4).replace(".", "");
				    
				    while(lat.length() < 8){
				    	lat = lat + "0";
					}
					while(lng.length() < 8){
						lng = lng + "0";
					}
				    Integer intLat = Integer.parseInt(neglat + lat);
				    Integer intLng = Integer.parseInt(neglng + lng);
				    
				    Rock rock = new Rock(this.getApplicationContext(), new GeoPoint(intLat, intLng), picked);
					rock.setTrelloId(data.getString("id"));
					rock.save();
				}
			}
		}
		
		//Newer stuff
		if(data.containsKey(TrelloRequest.KEY)){
			TrelloRequest r = data.getParcelable(TrelloRequest.KEY);
			if(r.getRequest() == TrelloRequest.REQUEST_DATA){
				if(r.getType() == TrelloRequest.TYPE_BOARD){
					//Send the data for the board with supplied id (Stored in prefs)
					Log.d("TrelloService", "Sending Board Data");
					//Only one static board so send that back
					IntentBoard board = new IntentBoard();
					
					board.setId(r.getId()); //Random udid
					board.setName(Board1);
					board.setOwner("edu.purdue.autogenics.rockapp");
					
					Intent sendIntent = new Intent();
					Bundle extras = new Bundle();
					extras.putParcelable(IntentBoard.KEY, board);
					sendIntent.setAction(Intent.ACTION_SEND);
					sendIntent.putExtras(extras);
					this.startService(sendIntent);
				} else if(r.getType() == TrelloRequest.TYPE_LIST){
					//Send the data for the list with supplied id (Stored in prefs)
					Log.d("TrelloService", "Sending List Data");
					SharedPreferences settings = getSharedPreferences(getString(R.string.preferences_name), 0);
					if(r.getId().contentEquals(settings.getString(List1, ""))){
						//List 1
						IntentList list = new IntentList();
						list.setId(r.getId());
						list.setName(List1);
						list.setDesc(List1Desc);
						list.setBoardId(settings.getString(Board1, "")); //Board id
						list.setOwner("edu.purdue.autogenics.rockapp");
						
						Intent sendIntent = new Intent();
						Bundle extras = new Bundle();
						extras.putParcelable(IntentBoard.KEY, list);
						sendIntent.setAction(Intent.ACTION_SEND);
						sendIntent.putExtras(extras);
						this.startService(sendIntent);
					} else if(r.getId().contentEquals(settings.getString(List2, ""))) {
						//List 2
						IntentList list = new IntentList();
						list.setId(r.getId());
						list.setName(List2);
						list.setDesc(List2Desc);
						list.setBoardId(settings.getString(Board1, "")); //Board id
						list.setOwner("edu.purdue.autogenics.rockapp");
						
						Intent sendIntent = new Intent();
						Bundle extras = new Bundle();
						extras.putParcelable(IntentBoard.KEY, list);
						sendIntent.setAction(Intent.ACTION_SEND);
						sendIntent.putExtras(extras);
						this.startService(sendIntent);
					}
				} else if(r.getType() == TrelloRequest.TYPE_CARD){
					//Send the data for the card with supplied id (Rock)
					Log.d("TrelloService", "Sending Card Data");
				}
			}
			
			if(false && data.getString("request").contentEquals("data")){
				if(data.containsKey("type")){
					if(data.getString("type").contentEquals("card")){
						
					} else if(data.getString("type").contentEquals("list")){
						
					} else if(data.getString("type").contentEquals("board")){
						Log.d("TestService", "Passing back board data");
						//Pass back board data for this trello id
						Intent sendIntent = new Intent();
						
						Bundle extras = new Bundle();
						extras.putString("request", "data");
						extras.putString("type", "board");
						extras.putString("id", data.getString("id"));
						
						extras.putString("name", "TestBoard");
						extras.putString("desc", "Description");
						
						sendIntent.setAction(Intent.ACTION_SEND);
						sendIntent.putExtras(extras);
						this.startService(sendIntent);
					}
				}
			}
		}
		return START_REDELIVER_INTENT;
	}

	/*
	Class that handles connection between Activities and the service
	*/
	private static class serviceHandler extends Handler {
		//Handlers all messages to activities
		ArrayList<Messenger> mClients = new ArrayList<Messenger>();
		TrelloService parentService = null;
		
		
		public  serviceHandler(TrelloService service){
			Log.d("TrelloService","Created service handler");
			parentService = service;
		}
		public void handleMessage(Message msg){
			//arg1 is the destination
			if(msg.what == registerClient){
				Log.d("TrelloService","Registered");
				//Link a UI handler
				mClients.add(msg.replyTo);
			} else if(msg.what == unregisterClient){
				mClients.remove(msg.replyTo);
			} else if(msg.what == shutdownService){
				//Check if should stop service
				shutdownService();
			} else {
				//Send message to all clients
				//super.handleMessage(msg);
				
				for (int i = mClients.size() - 1; i >= 0; i--) {
					try {
						// Send message to registered activities
						Message sendMsg = Message.obtain();
						sendMsg.copyFrom(msg);
						mClients.get(i).send(sendMsg);
					} catch (RemoteException e) {
						//The client is dead, remove
						mClients.remove(i);
					}
				}
			}
		}
		private void shutdownService(){
			Log.d("TrelloService","Shutting down");
			parentService.stopSelf();
		}
	}
}