# GlassNav
A navigation app for the Google Glass.

<img width="640" height="360" alt="screenshot" src="https://github.com/user-attachments/assets/e7dbe512-69ff-483f-9403-f043087b67ac" />

## Features
- See nearby places on the map view.
- Navigate anywhere, with real-time turn-by-turn voice and on screen directions
## Setup
1. Install the app using [`adb`](https://developer.android.com/tools/help/adb.html)
```
    adb install GlassNav.apk
```
2. Install an app on your phone to send location data to the Glass. You can use GlassNav Companion, which is designed to work with the GlassNav app. 
Other apps that pass location data to the Glass should work as well, such as [AnotherGlass](https://github.com/inckie/AnotherGlass).
3. GlassNav displays maps from the online [OpenStreetMap US Tileservice](https://tiles.openstreetmap.us/), but this loads slowly on the Glass due to its limited processing power.
   It's recommended to download a map file to improve performance. To do so:
   - Download a .map file for your region from https://download.mapsforge.org/ and save it as Map.map
   - Place the file in the root folder of your Glass storage. GlassNav will find and use it automatically.
## Usage
### Connecting your phone to Glass
You have to connect your phone to Glass to send location data. If you're using GlassNav Companion:
  1. Make sure your phone and Glass are paired and connected with Bluetooth.
  2. Open GlassNav on your Glass.
  3. Open GlassNav Companion on your phone.
  4. The companion app should connect automatically to the Glass. If it doesn't, select your Glass from the list of paired bluetooth devices shown.
### Finding places
- GlassNav uses [Nominatim](https://nominatim.org/) to search for places. To search, tap the touchpad while in the map view to open the menu, and select Search. This will start speech recognition; say the name of the place you're searching for.
  Then select from the list of search results.
- You can also use text search, or select a specific location on the map, from the GlassNav Companion app.
- When you've selected a place, you'll get the options to navigate to it by walking, cycling or driving. You can also save the place, so you can quickly access it later.
### Navigation
- During navigation, the screen will automatically turn off to save battery. It will turn on when directions are given.
- To stop navigation, tap the touchpad to open the menu, and select Stop navigation.

## Credits
- [Mapsforge V™](https://github.com/mapsforge/vtm): Vector map rendering library.
- [MapLibre Navigation SDK](https://github.com/maplibre/maplibre-navigation-android/): Contains the logic needed to get timed navigation instructions. GlassNav uses a modification of this library that supports Android 4.4.
- [MapLibre Compose](https://github.com/maplibre/maplibre-compose): Used for map view in location selector in GlassNav Companion.
- [Nominatim](https://nominatim.org/): Uses OpenStreetMap data to find locations on Earth by name and address (geocoding). 
- [Valhalla](https://github.com/valhalla): Open Source Routing Engine for OpenStreetMap. GlassNav currently uses Valhalla's online API to get routes.
