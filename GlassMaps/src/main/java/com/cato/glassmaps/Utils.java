package com.cato.glassmaps;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.maplibre.geojson.model.Point;
import org.maplibre.navigation.core.models.DirectionsResponse;
import org.maplibre.navigation.core.models.DirectionsRoute;
import org.maplibre.navigation.core.models.ManeuverModifier;
import org.maplibre.navigation.core.models.RouteOptions;
import org.maplibre.navigation.core.models.StepManeuver;
import org.oscim.core.GeoPoint;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class Utils {
    static LocationInfo selectedInfo;
    private static final String TAG = "GlassMaps Utils";
    static class LocationInfo {
        String name;
        String displayName;
        GeoPoint location;
        float distance; // In meters

        public LocationInfo(String name, String displayName, GeoPoint location, float distance) {
            this.name = name;
            this.displayName = displayName;
            this.location = location;
            this.distance = distance;
        }
    }

    static org.maplibre.navigation.core.location.Location androidLocationtoMapLibreLocation(android.location.Location location) {
        return new org.maplibre.navigation.core.location.Location(
                location.getLatitude(),
                location.getLongitude(),
                location.getAccuracy(),
                location.getAltitude(),
                null,
                null,
                null,
                location.getSpeed(),
                location.getBearing(),
                System.currentTimeMillis(),
                location.getProvider()
        );
    }

    public static void getRoute(double latitude, double longitude, String name, String costing){
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
            request.put("language", "en-US");
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
                MainActivity.startRouteNavigation();
            }

            @Override
            public void onError(String errorMessage) {
                Log.e(TAG, "Route request error: " + errorMessage);
            }
        });
    }

    static boolean isNetworkConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        return cm.getActiveNetworkInfo() != null && cm.getActiveNetworkInfo().isConnected();
    }


    static Integer getImageFromManuever(StepManeuver maneuver) {
        Integer image = R.drawable.da_turn_unknown;
        ManeuverModifier.Type modifier = maneuver.getModifier();
        switch (maneuver.getType()) {
            case FORK:
                if (modifier == ManeuverModifier.Type.RIGHT || modifier == ManeuverModifier.Type.SLIGHT_RIGHT) {
                    image = R.drawable.da_turn_fork_right;
                } else if (modifier == ManeuverModifier.Type.LEFT || modifier == ManeuverModifier.Type.SLIGHT_LEFT) {
                    image = R.drawable.da_turn_fork_left;
                } else if (modifier == ManeuverModifier.Type.STRAIGHT) {
                    image = R.drawable.da_turn_straight;
                }
                break;
            case END_OF_ROAD:
            case ON_RAMP:
            case ROUNDABOUT_TURN:
            case TURN:
                if (modifier == ManeuverModifier.Type.SLIGHT_RIGHT) {
                    image = R.drawable.da_turn_slight_right;
                } else if (modifier == ManeuverModifier.Type.RIGHT) {
                    image = R.drawable.da_turn_right;
                } else if (modifier == ManeuverModifier.Type.SHARP_RIGHT) {
                    image = R.drawable.da_turn_sharp_right;
                } else if (modifier == ManeuverModifier.Type.SLIGHT_LEFT) {
                    image = R.drawable.da_turn_slight_left;
                } else if (modifier == ManeuverModifier.Type.LEFT) {
                    image = R.drawable.da_turn_left;
                } else if (modifier == ManeuverModifier.Type.SHARP_LEFT) {
                    image = R.drawable.da_turn_sharp_left;
                } else if (modifier == ManeuverModifier.Type.UTURN) {
                    image = R.drawable.da_turn_uturn;
                }
                break;
            case MERGE:
                image = R.drawable.da_turn_generic_merge;
                break;
            case ARRIVE:
                image = R.drawable.da_turn_arrive;
                break;
            case DEPART:
                image = R.drawable.da_depart;
                break;
            case ROTARY:
            case ROUNDABOUT:
                if (modifier == ManeuverModifier.Type.SLIGHT_RIGHT) {
                    image = R.drawable.da_turn_roundabout_3;
                } else if (modifier == ManeuverModifier.Type.RIGHT) {
                    image = R.drawable.da_turn_roundabout_2;
                } else if (modifier == ManeuverModifier.Type.SHARP_RIGHT) {
                    image = R.drawable.da_turn_roundabout_1;
                } else if (modifier == ManeuverModifier.Type.SLIGHT_LEFT) {
                    image = R.drawable.da_turn_roundabout_5;
                } else if (modifier == ManeuverModifier.Type.LEFT) {
                    image = R.drawable.da_turn_roundabout_6;
                } else if (modifier == ManeuverModifier.Type.SHARP_LEFT) {
                    image = R.drawable.da_turn_roundabout_7;
                } else if (modifier == ManeuverModifier.Type.STRAIGHT) {
                    image = R.drawable.da_turn_roundabout_4;
                } else if (modifier == ManeuverModifier.Type.UTURN) {
                    image = R.drawable.da_turn_uturn;
                }
                break;
            case CONTINUE:
                image = R.drawable.da_turn_straight;
                break;
            case NEW_NAME:
                Log.e(TAG, "NEW_NAME image not implemented!");
                break;
            case OFF_RAMP:
                if (modifier == ManeuverModifier.Type.RIGHT) {
                    image = R.drawable.da_turn_ramp_right;
                } else if (modifier == ManeuverModifier.Type.LEFT) {
                    image = R.drawable.da_turn_ramp_left;
                }
                break;
            case USE_LANE:
                Log.e(TAG, "USE_LANE image not implemented!");
                break;
            case NOTIFICATION:
                Log.e(TAG, "NOTIFICATION image not implemented!");
                break;
            case EXIT_ROTARY:
            case EXIT_ROUNDABOUT:
                image = R.drawable.da_turn_roundabout_exit;
                break;
        }
        return image;
    }
}
