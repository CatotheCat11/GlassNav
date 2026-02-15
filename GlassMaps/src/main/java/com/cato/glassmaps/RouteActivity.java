package com.cato.glassmaps;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;

import com.google.android.glass.media.Sounds;
import com.google.android.glass.widget.CardBuilder;
import com.google.android.glass.widget.CardScrollAdapter;
import com.google.android.glass.widget.CardScrollView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.maplibre.geojson.model.Point;
import org.maplibre.navigation.core.models.DirectionsResponse;
import org.maplibre.navigation.core.models.DirectionsRoute;
import org.maplibre.navigation.core.models.RouteOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class RouteActivity extends Activity {
    private List<CardBuilder> mCards;
    private CardScrollView mCardScrollView;
    private ExampleCardScrollAdapter mAdapter;
    private Utils.LocationInfo locationInfo;
    private final static String TAG = "RouteActivity";
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCards = new ArrayList<>();
        createCards();
        mCardScrollView = new CardScrollView(this);
        mAdapter = new ExampleCardScrollAdapter();
        mCardScrollView.setAdapter(mAdapter);
        mCardScrollView.activate();
        setupClickListener();
        setContentView(mCardScrollView);
        //TODO: Add user selection for costing
    }

    private void createCards(){
        mCards.add(new CardBuilder(this, CardBuilder.Layout.TEXT)
                .setText(Utils.selectedInfo.displayName));
        /*mCards.add(new CardBuilder(this, CardBuilder.Layout.MENU)
                .setText("Show route overview"));*/
        //TODO: Add icons
        mCards.add(new CardBuilder(this, CardBuilder.Layout.MENU)
                .setText("Start walking"));
        mCards.add(new CardBuilder(this, CardBuilder.Layout.MENU)
                .setText("Start cycling"));
        mCards.add(new CardBuilder(this, CardBuilder.Layout.MENU)
                .setText("Start driving"));
        mCards.add(new CardBuilder(this, CardBuilder.Layout.MENU)
                .setText("Save"));

    }
    private class ExampleCardScrollAdapter extends CardScrollAdapter {

        @Override
        public int getPosition(Object item) {
            return mCards.indexOf(item);
        }

        @Override
        public int getCount() {
            return mCards.size();
        }

        @Override
        public Object getItem(int position) {
            return mCards.get(position);
        }

        @Override
        public int getViewTypeCount() {
            return CardBuilder.getViewTypeCount();
        }

        @Override
        public int getItemViewType(int position){
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return mCards.get(position).getView(convertView, parent);
        }
    }

    private void setupClickListener() {
        mCardScrollView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                am.playSoundEffect(Sounds.TAP);
                /*if (position == 1) { //Show route overview
                    MainActivity.showRouteOverview();
                    Intent intent = new Intent(RouteActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                }*/
                //TODO: Set MainActivity mode (walking, cycling, driving)
                if (position == 1) { // Start walking
                    getRoute(Utils.selectedInfo.location.getLatitude(), Utils.selectedInfo.location.getLongitude(), Utils.selectedInfo.name, "pedestrian");
                } else if (position == 2) { // Start walking
                    getRoute(Utils.selectedInfo.location.getLatitude(), Utils.selectedInfo.location.getLongitude(), Utils.selectedInfo.name, "bicycle");
                } else if (position == 3) { // Start driving
                    getRoute(Utils.selectedInfo.location.getLatitude(), Utils.selectedInfo.location.getLongitude(), Utils.selectedInfo.name, "auto");
                } else if (position == 4) { // Save TODO: Add ability to unsave and indicate that save was successful
                    SharedPreferences sharedPreferences = getSharedPreferences("places", Context.MODE_PRIVATE);
                    SharedPreferences.Editor editor = sharedPreferences.edit();
                    try {
                        JSONArray placesArray = new JSONArray(sharedPreferences.getString("places", "[]"));
                        JSONObject place = new JSONObject();
                        place.put("name", Utils.selectedInfo.name);
                        place.put("display_name", Utils.selectedInfo.displayName);
                        place.put("lat", Utils.selectedInfo.location.getLatitude());
                        place.put("lon", Utils.selectedInfo.location.getLongitude());
                        placesArray.put(place);
                        editor.putString("places", placesArray.toString());
                        editor.apply();
                        Log.i(TAG, "Saved a place");
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });
    }

    private void getRoute(double latitude, double longitude, String name, String costing){
        //FIXME: Crash if lastlocation is null
        Log.d(TAG, "Getting route to " + latitude + ", " + longitude);
        JSONObject request = new JSONObject();
        try {
            JSONArray locations = new JSONArray();
            JSONObject startPos = new JSONObject();
            JSONObject endPos = new JSONObject();
            startPos.put("lat", MainActivity.lastLocation.getLatitude());
            startPos.put("lon", MainActivity.lastLocation.getLongitude());
            endPos.put("lat", latitude);
            endPos.put("lon", longitude);
            endPos.put("name", name);
            locations.put(startPos);
            locations.put(endPos);
            //JSONObject costingOptions = new JSONObject();
            //JSONObject costingType = new JSONObject();
            //costingType.put("top_speed", 130); //
            //costingOptions.put(costing, costingType);
            //request.put("costing_options", costingOptions);
            request.put("locations", locations);
            request.put("costing", costing);
            request.put("format", "osrm");
            request.put("banner_instructions", true);
            request.put("voice_instructions", true);
            Log.i(TAG, "Route request: " + request.toString());
        } catch (JSONException e) {
            Log.e(TAG, "An error occurred: " + e);
        }
        HttpsUtils.makePostRequest(MainActivity.client, "https://valhalla1.openstreetmap.de/route", request, "POST", new HttpsUtils.HttpCallback() {

            @Override
            public void onSuccess(String response) {
                Log.i(TAG, "Route response: " + response);
                DirectionsRoute route = DirectionsResponse.fromJson(response).getRoutes().get(0);

                List<Point> coordinates = Arrays.asList(new Point(List.of(MainActivity.lastLocation.getLongitude(), MainActivity.lastLocation.getLatitude())), new Point(List.of(longitude, latitude)));
                RouteOptions routeOptions = new RouteOptions(
                        "https://valhalla.routing",   // baseUrl
                        "valhalla",                   // user
                        "valhalla",                   // profile
                        coordinates,                  // coordinates
                        null,                         // alternatives
                        Locale.getDefault().getLanguage(), // language
                        null,                         // radiuses
                        null,                         // bearings
                        null,                         // continueStraight
                        null,                         // roundaboutExits
                        null,                         // geometries
                        null,                         // overview
                        null,                         // steps
                        null,                         // annotations
                        null,                         // exclude
                        true,                         // voiceInstructions
                        true,                         // bannerInstructions
                        null,                         // voiceUnits
                        "valhalla",                   // accessToken
                        "0000-0000-0000-0000",         // requestUuid
                        null,                         // approaches
                        null,                         // waypointIndices
                        null,                         // waypointNames
                        null,                         // waypointTargets
                        null,                         // walkingOptions
                        null                          // snappingClosures
                );


                MainActivity.route = route.toBuilder().withRouteOptions(routeOptions).build();

                //Start navigation and close this activity
                Intent intent = new Intent(RouteActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                MainActivity.startRouteNavigation();
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Route request error: " + errorMessage);
            }
        });
    }
}
