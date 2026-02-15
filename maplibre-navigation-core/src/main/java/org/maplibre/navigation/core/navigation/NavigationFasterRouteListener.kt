package org.maplibre.navigation.core.navigation

import android.util.Log
import org.maplibre.navigation.core.models.DirectionsResponse
import org.maplibre.navigation.core.route.FasterRoute
import org.maplibre.navigation.core.route.RouteListener
import org.maplibre.navigation.core.routeprogress.RouteProgress

open class NavigationFasterRouteListener(
    private val eventDispatcher: NavigationEventDispatcher,
    private val fasterRouteEngine: FasterRoute
) : RouteListener {

    override fun onResponseReceived(response: DirectionsResponse, routeProgress: RouteProgress) {
        if (fasterRouteEngine.isFasterRoute(response, routeProgress)) {
            eventDispatcher.onFasterRouteEvent(response.routes.first())
        }
    }

    override fun onErrorReceived(throwable: Throwable) {
        Log.e("MapLibre", "Error occurred fetching a faster route" )
    }
}
