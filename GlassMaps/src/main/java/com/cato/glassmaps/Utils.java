package com.cato.glassmaps;

import org.maplibre.navigation.core.models.ManeuverModifier;
import org.maplibre.navigation.core.models.StepManeuver;
import org.oscim.core.GeoPoint;

public class Utils {
    static LocationInfo selectedInfo;
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
                break;
            case OFF_RAMP:
                if (modifier == ManeuverModifier.Type.RIGHT) {
                    image = R.drawable.da_turn_ramp_right;
                } else if (modifier == ManeuverModifier.Type.LEFT) {
                    image = R.drawable.da_turn_ramp_left;
                }
                break;
            case USE_LANE:
                break;
            case END_OF_ROAD:
                break;
            case NOTIFICATION:
                break;
            case EXIT_ROTARY:
            case EXIT_ROUNDABOUT:
                image = R.drawable.da_turn_roundabout_exit;
                break;
        }
        return image;
    }
}
