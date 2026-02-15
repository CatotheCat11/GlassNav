package com.cato.glassmaps

import android.content.Context
import android.location.LocationListener
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.maplibre.navigation.core.location.Location
import org.maplibre.navigation.core.location.engine.LocationEngine
import org.maplibre.navigation.core.location.toLocation

class KotlinUtils {

    companion object {
        private var navigationLocationListener: ProducerScope<Location>? = null

        @JvmStatic
        fun locationFlow(request: LocationEngine.Request): Flow<Location> = callbackFlow {
            // store the producer scope so external callers can emit into this flow
            navigationLocationListener = this
            // suspend until the flow collector is cancelled, then clear the reference
            awaitClose { navigationLocationListener = null }
        }

        @JvmStatic
        fun sendLocation(location: Location) {
            navigationLocationListener?.trySend(location)
        }
    }
}