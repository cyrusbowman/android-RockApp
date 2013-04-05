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
	
	public static final String RockBoard = "Rocks Test Board";
	public static final String ListRocksLeft = "Rocks In Fields"; //Hardcoded in common lib
	public static final String ListRocksRemoved = "Rocks Removed"; //Hardcoded in common lib
	
	public static final String WatchcardIdLeftRef = "WatchCardIdLeft"; //Hardcoded in common lib
	public static final String WatchcardIdRemovedRef = "WatchCardIdRemoved"; //Hardcoded in common lib

	public static final String OWNER = "edu.purdue.autogenics.rockapp";
	
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
		if(data.containsKey("sync")){
			if(data.getString("sync").contentEquals("true")){
				//Push Board and two lists
				SharedPreferences settings = getSharedPreferences(getString(R.string.preferences_name), 0);
				SharedPreferences.Editor editor = settings.edit();
				String BoardId = UUID.randomUUID().toString();
				String ListIdLeft = UUID.randomUUID().toString();
				String ListIdRemoved = UUID.randomUUID().toString();
				String WatchCardIdLeft = UUID.randomUUID().toString();
				String WatchCardIdRemoved = UUID.randomUUID().toString();
				
				Log.d("RockAppTrelloService", "Gen BoardId:" + BoardId);
				Log.d("RockAppTrelloService", "Gen ListIdLeft:" + ListIdLeft);
				Log.d("RockAppTrelloService", "Gen ListIdRemoved:" + ListIdRemoved);
				Log.d("RockAppTrelloService", "Gen WatchCardIdLeft:" + WatchCardIdLeft);
				Log.d("RockAppTrelloService", "Gen WatchCardIdRemoved:" + WatchCardIdRemoved);

				
				//Store names for trello "data"
				editor.putString(BoardId + "name", RockBoard);
				editor.putString(ListIdLeft + "name", ListRocksLeft);
				editor.putString(ListIdRemoved + "name", ListRocksRemoved);
				
				//Save reference so this class can kno ids of the lists (trello app access by id)
				editor.putString(BoardId + "ref", RockBoard);
				editor.putString(ListIdLeft + "ref", ListRocksLeft);
				editor.putString(ListIdRemoved + "ref", ListRocksRemoved);
				
				//Store id's by reference name (this app access by ref name)
				editor.putString(RockBoard, BoardId);
				editor.putString(ListRocksLeft, ListIdLeft);
				editor.putString(ListRocksRemoved, ListIdRemoved);
				
				//Store ids of watchcards
				editor.putString(WatchcardIdLeftRef, WatchCardIdLeft);
				editor.putString(WatchcardIdRemovedRef, WatchCardIdRemoved);
				editor.commit();
				
				Log.d("RockAppTrelloService", "After set listidleft:" + settings.getString(ListIdLeft + "ref", null));
				Log.d("RockAppTrelloService", "Key:" + ListIdLeft + "ref");

				
				
				//Push board and 2 lists
				//Board
				Intent sendIntent = new Intent();
				Bundle extras = new Bundle();
				extras.putString("push", "board");
				extras.putString("id", BoardId);
				extras.putString("name", RockBoard); //This is actually stored in prefs by settings.getString(BoardId + "name")
				extras.putString("desc", "");
				extras.putString("nameKeyword", RockBoard);
				extras.putString("descKeyword", "");
				extras.putString("owner", OWNER);
				sendIntent.setPackage("edu.purdue.autogenics.trello");
				sendIntent.setAction(Intent.ACTION_SEND);
				sendIntent.putExtras(extras);
				this.startService(sendIntent);
				
				//ListRocksRemoved
				Intent sendIntent2 = new Intent();
				Bundle extras2 = new Bundle();
				extras2.putString("push", "list");
				extras2.putString("id", ListIdRemoved);
				extras2.putString("boardId", BoardId);
				extras2.putString("name", ListRocksRemoved); //This is actually stored in prefs by settings.getString(ListIdLeft + "name")
				extras2.putString("nameKeyword", ListRocksRemoved);
				extras2.putString("owner", OWNER);
				sendIntent2.setPackage("edu.purdue.autogenics.trello");
				sendIntent2.setAction(Intent.ACTION_SEND);
				sendIntent2.putExtras(extras2);
				this.startService(sendIntent2);
				
				//ListRocksLeft
				Intent sendIntent3 = new Intent();
				Bundle extras3 = new Bundle();
				extras3.putString("push", "list");
				extras3.putString("id", ListIdLeft);
				extras3.putString("boardId", BoardId);
				extras3.putString("name", ListRocksLeft); //This is actually stored in prefs by settings.getString(ListIdRemoved + "name")
				extras3.putString("nameKeyword", ListRocksLeft);
				extras3.putString("owner", OWNER);
				sendIntent3.setPackage("edu.purdue.autogenics.trello");
				sendIntent3.setAction(Intent.ACTION_SEND);
				sendIntent3.putExtras(extras3);
				this.startService(sendIntent3);	
				
				//Push 2 watchcards
				Intent sendIntent4 = new Intent();
				Bundle extras4 = new Bundle();
				extras4.putString("push", "watchcard");
				extras4.putString("id", WatchCardIdLeft);
				extras4.putString("listId", ListIdLeft);
				extras4.putString("nameKeyword", ".*");
				extras4.putString("owner", OWNER);
				sendIntent4.setPackage("edu.purdue.autogenics.trello");
				sendIntent4.setAction(Intent.ACTION_SEND);
				sendIntent4.putExtras(extras4);
				this.startService(sendIntent4);	
				
				Intent sendIntent5 = new Intent();
				Bundle extras5 = new Bundle();
				extras5.putString("push", "watchcard");
				extras5.putString("id", WatchCardIdRemoved);
				extras5.putString("listId", ListIdRemoved);
				extras5.putString("nameKeyword", ".*");
				extras5.putString("owner", OWNER);
				sendIntent5.setPackage("edu.purdue.autogenics.trello");
				sendIntent5.setAction(Intent.ACTION_SEND);
				sendIntent5.putExtras(extras5);
				this.startService(sendIntent5);
				
				//Send Sync Requests For Boards and Lists
				//If we send it to just lists it will sync boards first
				Intent sendIntent6 = new Intent();
				Bundle extras6 = new Bundle();
				extras6.putString("sync", "lists");
				sendIntent6.setPackage("edu.purdue.autogenics.trello");
				sendIntent6.setAction(Intent.ACTION_SEND);
				sendIntent6.putExtras(extras6);
				this.startService(sendIntent6);
			} else if(data.getString("sync").contentEquals("false")){
				//Get old id's for board and 2 lists
				SharedPreferences settings = getSharedPreferences(getString(R.string.preferences_name), 0);
				String BoardId = settings.getString(RockBoard, null);
				String ListIdLeft = settings.getString(RockBoard, null);
				String ListIdRemoved = settings.getString(RockBoard, null);
				
				//Remove name data and reference for board and 2 lists
				SharedPreferences.Editor editor = settings.edit();
				if(BoardId != null) editor.remove(BoardId + "name");
				if(ListIdLeft != null) editor.remove(ListIdLeft + "name");
				if(ListIdRemoved != null) editor.remove(ListIdRemoved + "name");
				if(BoardId != null) editor.remove(BoardId + "ref");
				if(ListIdLeft != null) editor.remove(ListIdLeft + "ref");
				if(ListIdRemoved != null) editor.remove(ListIdRemoved + "ref");
				editor.commit();
			}
		}
		
		if(data.containsKey("request")){
			if(data.getString("request").contentEquals("updateData")){
				Log.d("RockAppTrelloService", "updateData request received");
				Log.d("RockAppTrelloService","type:" +  data.getString("type"));
				//Log.d("RockAppTrelloService","id:" +  data.getString("id"));
				//Log.d("RockAppTrelloService","name:" +  data.getString("name"));
				//Log.d("RockAppTrelloService","desc:" +  data.getString("desc"));
				//Log.d("RockAppTrelloService","listid:" +  data.getString("listId"));
				if(data.getString("type").contentEquals("board")){
					String theId = data.getString("id");
					String newName = data.getString("name");
					String newId = data.getString("newId");
					String newDesc = data.getString("desc"); //Not used by rock app
					
					SharedPreferences settings = getSharedPreferences(getString(R.string.preferences_name), 0);
					
					SharedPreferences.Editor editor = settings.edit();
					if(newId != null){
						String name = settings.getString(theId + "name", "");
						String refName = settings.getString(theId + "ref", null);

						editor.putString(newId + "name", name);
						editor.remove(theId + "name");
						
						editor.putString(newId + "ref", refName);
						editor.remove(theId + "ref");
						
						if(refName != null){
							//Update id of this reference
							editor.putString(refName, newId); //refName should always be RockBoard value in this cause
						}
						theId = newId;
					}
					if(newName != null) editor.putString(theId + "name", newName);
					editor.commit();
				} else if(data.getString("type").contentEquals("list")){
					String theId = data.getString("id");
					String newName = data.getString("name");
					String newId = data.getString("newId");
					
					SharedPreferences settings = getSharedPreferences(getString(R.string.preferences_name), 0);
					
					SharedPreferences.Editor editor = settings.edit();
					if(newId != null){
						String name = settings.getString(theId + "name", "");
						String refName = settings.getString(theId + "ref", null);

						editor.putString(newId + "name", name);
						editor.remove(theId + "name");
						
						editor.putString(newId + "ref", refName);
						editor.remove(theId + "ref");
						
						if(refName != null){
							//Update id of this reference
							editor.putString(refName, newId); //refName should always be RockBoard value in this cause
						}
						theId = newId;
					}
					if(newName != null) editor.putString(theId + "name", newName);
					editor.commit();
				} else if(data.getString("type").contentEquals("card")){
					//Update card in database to match trello
					String theId = data.getString("id");
					String listId = data.getString("listId");
					String newId = data.getString("newId");
					String newName = data.getString("name");
					String newDesc = data.getString("desc");
					
					//Process data
					if(newName != null){
						Log.d("RockAppTrelloService", "Updating rock data:" + newName);
						SharedPreferences settings = getSharedPreferences(getString(R.string.preferences_name), 0);
						String strLatLng = newName;
						if(settings.getString(listId + "ref", null) == null){
							Log.d("RockAppTrelloService", "*****ERROR - No list reference could be found for:" + listId);
						}
						Boolean picked = settings.getString(listId + "ref", "").contentEquals(ListRocksRemoved) ? true : false;
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
						    
							Rock rock = Rock.getRockByTrelloId(this.getApplicationContext(), theId);
							if(rock == null){
								Log.d("RockAppTrelloService", "*****ERROR - Null rock" + listId);
							}
							if(newId != null) rock.setTrelloId(newId);
							rock.setPicked(picked);
							rock.setLat(intLat);
							rock.setLon(intLng);
							rock.setActualLat(intLat);
							rock.setActualLon(intLng);
							if(newDesc != null) rock.setComments(newDesc);
							rock.save(false);
							
							//Notify app because i block it to prevent push data to trello
							Intent actionIntent = new Intent(Rock.ACTION_UPDATED);
							actionIntent.putExtra("id", rock.getId());
							LocalBroadcastManager.getInstance(this.getApplicationContext()).sendBroadcast(actionIntent);
						}
					}
				}
			} else if(data.getString("request").contentEquals("dataRequest")){
				Log.d("RockAppTrelloService", "dataRequest request received");
				Log.d("RockAppTrelloService","type:" +  data.getString("type"));
				
				if(data.getString("type").contentEquals("card")){
					String theId = data.getString("id");
					String listId = data.getString("listId");
					
					//Generate data
					//Pass back rock data for this trello id
					Rock rock = Rock.getRockByTrelloId(this.getApplicationContext(), data.getString("id"));
					if(rock != null){
						
						
						
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
						
						Intent sendIntent = new Intent();
						Bundle extras = new Bundle();
						extras.putString("data", "card");
						extras.putString("id", theId);
						extras.putString("name", newName);
						extras.putString("desc", rock.getComments());
						
						SharedPreferences settings = getSharedPreferences(getString(R.string.preferences_name), 0);
						String newListId = (rock.isPicked() == true) ? settings.getString(ListRocksRemoved, listId) : settings.getString(ListRocksLeft, listId);
						
						extras.putString("listId", newListId);
						extras.putString("owner", OWNER);
						
						Log.d("RockAppTrelloService","listid:" + newListId);
						
						sendIntent.setPackage("edu.purdue.autogenics.trello");
						sendIntent.setAction(Intent.ACTION_SEND);
						sendIntent.putExtras(extras);
						this.startService(sendIntent);
					}
				}
			} else if(data.getString("request").contentEquals("updateId")){
				Log.d("RockAppTrelloService", "updateId request received");
				Log.d("RockAppTrelloService","type:" +  data.getString("type"));
				Log.d("RockAppTrelloService","id:" +  data.getString("id"));
				//Log.d("RockAppTrelloService","newid:" +  data.getString("newId"));
				//Log.d("RockAppTrelloService","listid:" +  data.getString("listId"));
				if(data.getString("type").contentEquals("board")){
					String theId = data.getString("id");
					String newId = data.getString("newId");
					SharedPreferences settings = getSharedPreferences(getString(R.string.preferences_name), 0);
					SharedPreferences.Editor editor = settings.edit();
					if(newId != null){
						String name = settings.getString(theId + "name", "");
						String refName = settings.getString(theId + "ref", null);

						editor.putString(newId + "name", name);
						editor.remove(theId + "name");
						
						editor.putString(newId + "ref", refName);
						editor.remove(theId + "ref");
						
						if(refName != null){
							//Update id of this reference
							editor.putString(refName, newId); //refName should always be RockBoard value in this cause
						}
						theId = newId;
					}
					editor.commit();
				} else if(data.getString("type").contentEquals("list")){
					String theId = data.getString("id");
					String newId = data.getString("newId");
					SharedPreferences settings = getSharedPreferences(getString(R.string.preferences_name), 0);
					SharedPreferences.Editor editor = settings.edit();
					if(newId != null){
						
						Log.d("RockAppTrelloService", "Updating Id of list, listid:" + theId);
						Log.d("RockAppTrelloService", "Updating Id of list, newId should be:" + newId);
						String name = settings.getString((theId + "name"), "");
						String refName = settings.getString((theId + "ref"), null);
						
						Log.d("RockAppTrelloService", "Key:" + theId + "ref");
						Log.d("RockAppTrelloService", "Updating Id of list, name" + name);
						Log.d("RockAppTrelloService", "Updating Id of list, refName" + refName);
						
						editor.putString(newId + "name", name);
						editor.remove(theId + "name");
						
						editor.putString(newId + "ref", refName);
						editor.remove(theId + "ref");
						
						if(refName != null){
							//Update id of this reference
							Log.d("RockAppTrelloService", "Updating Id of list, editing value now, newVal:" + newId);
							editor.putString(refName, newId); //refName should always be RockBoard value in this cause
						}
						theId = newId;
					}
					editor.commit();
				} else if(data.getString("type").contentEquals("card")){
					String theId = data.getString("id");
					String newId = data.getString("newId");
					Rock rock = Rock.getRockByTrelloId(this.getApplicationContext(), theId);
					if(rock != null){
						Log.d("RockAppTrelloService", "Setting new_id");
						ContentValues vals = new ContentValues();
						vals.put(RockProvider.Constants.TRELLO_ID, newId);
						String where = RockProvider.Constants._ID + "=?";
						String[] whereArgs = {Integer.toString(rock.getId())};
						this.getApplicationContext().getContentResolver().update(RockProvider.Constants.CONTENT_URI,vals, where, whereArgs);
					
						//TODO do i need this???
						Intent actionIntent = new Intent(Rock.ACTION_UPDATED);
						// Send a broadcast that a rock was added/updated
						actionIntent.putExtra("id", rock.getId());
						LocalBroadcastManager.getInstance(this.getApplicationContext()).sendBroadcast(actionIntent);
					}
				}
				//Send back updateIdComplete intent
				Intent sendIntent = new Intent();
				Bundle extras = new Bundle();
				extras.putString("updateIdComplete", ""); //TODO maybe include type
				extras.putString("id", data.getString("id"));
				extras.putString("newId", data.getString("newId"));
				sendIntent.setAction(Intent.ACTION_SEND);
				sendIntent.setPackage("edu.purdue.autogenics.trello");
				sendIntent.putExtras(extras);
				this.startService(sendIntent);
			} else if(data.getString("request").contentEquals("addData")){
				Log.d("RockAppTrelloService", "addData request received");
				Log.d("RockAppTrelloService","type:" +  data.getString("type"));
				//Log.d("RockAppTrelloService","id:" +  data.getString("id"));
				//Log.d("RockAppTrelloService","listid:" +  data.getString("listId"));
				//Log.d("RockAppTrelloService","name:" +  data.getString("name"));
				//Log.d("RockAppTrelloService","desc:" +  data.getString("desc"));
				
				if(data.getString("type").contentEquals("card")){
					String theId = data.getString("id");
					String theName = data.getString("name");
					String theDesc = data.getString("desc");
					String theListId = data.getString("listId");

					//Process new rock
					SharedPreferences settings = getSharedPreferences(getString(R.string.preferences_name), 0);
					Boolean picked = settings.getString(theListId + "ref", null).contentEquals(ListRocksRemoved) ? true : false;
					String strLatLng = theName;
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
						rock.setTrelloId(theId);
						rock.setComments(theDesc);
						rock.save(false);
						
						//TODO do i need it? Notify app because i block it to prevent push data to trello
						//Intent actionIntent = new Intent(Rock.ACTION_ADDED);
						//actionIntent.putExtra("id", rock.getId());
						//LocalBroadcastManager.getInstance(this.getApplicationContext()).sendBroadcast(actionIntent);
					}
				}
			} else if(data.getString("request").contentEquals("deleteData")){
				Log.d("RockAppTrelloService", "deleteData request received");
				Log.d("RockAppTrelloService","type:" +  data.getString("type"));
				if(data.getString("type").contentEquals("card")){
					String theId = data.getString("id");
					String theListId = data.getString("listId");
					
					Rock rock = Rock.getRockByTrelloId(this.getApplicationContext(), theId);
					Integer rockId = rock.getId();
					rock.setDeleted(true);
					rock.save(false);
					
					//Send back deleteComplete intent
					Intent sendIntent = new Intent();
					Bundle extras = new Bundle();
					extras.putString("deleteComplete", data.getString("id"));
					sendIntent.setAction(Intent.ACTION_SEND);
					sendIntent.setPackage("edu.purdue.autogenics.trello");
					sendIntent.putExtras(extras);
					this.startService(sendIntent);
					
					//TODO Notify others of delete is this needed???
					Intent actionIntent = new Intent(Rock.ACTION_DELETED);
					actionIntent.putExtra("id", rockId);
					LocalBroadcastManager.getInstance(this.getApplicationContext()).sendBroadcast(actionIntent);
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