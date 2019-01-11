package com.mapbox.services.android.navigation.ui.v5.map;

import android.content.Context;

import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.services.android.navigation.ui.v5.camera.NavigationCamera;
import com.mapbox.services.android.navigation.v5.navigation.MapboxNavigation;
import com.mapbox.services.android.navigation.v5.navigation.NavigationConstants;
import com.mapbox.services.android.navigation.v5.routeprogress.ProgressChangeListener;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteLegProgress;
import com.mapbox.services.android.navigation.v5.routeprogress.RouteProgress;

class MapFpsDelegate {

  private static final int VALID_DURATION_IN_SECONDS_UNTIL_NEXT_MANEUVER = 7;
  private static final int VALID_DURATION_IN_SECONDS_SINCE_PREVIOUS_MANEUVER = 5;
  private static final int DEVICE_MAX_FPS = 120;
  private static final int DEFAULT_MAX_FPS_THRESHOLD = 20;

  private final MapView mapView;
  private final MapBatteryMonitor batteryMonitor;
  private final ProgressChangeListener fpsProgressListener = new FpsDelegateProgressChangeListener(this);
  private MapboxNavigation navigation;
  private int maxFpsThreshold = DEFAULT_MAX_FPS_THRESHOLD;
  private int currentFpsThreshold = DEFAULT_MAX_FPS_THRESHOLD;
  private boolean isTracking = true;
  private boolean isEnabled = true;

  MapFpsDelegate(MapView mapView, MapBatteryMonitor batteryMonitor) {
    this.mapView = mapView;
    this.batteryMonitor = batteryMonitor;
  }

  void addProgressChangeListener(MapboxNavigation navigation) {
    this.navigation = navigation;
    navigation.addProgressChangeListener(fpsProgressListener);
  }

  void onStart() {
    if (navigation != null) {
      navigation.addProgressChangeListener(fpsProgressListener);
    }
  }

  void onStop() {
    if (navigation != null) {
      navigation.removeProgressChangeListener(fpsProgressListener);
    }
  }

  void updateEnabled(boolean isEnabled) {
    this.isEnabled = isEnabled;
    if (!isEnabled) {
      resetMaxFps();
    }
  }

  boolean isEnabled() {
    return isEnabled;
  }

  void updateMaxFpsThreshold(int maxFps) {
    this.maxFpsThreshold = maxFps;
  }

  int retrieveMaxFpsThreshold() {
    return maxFpsThreshold;
  }

  void updateCameraTracking(@NavigationCamera.TrackingMode int trackingMode) {
    isTracking = trackingMode != NavigationCamera.NAVIGATION_TRACKING_MODE_NONE;
    if (!isTracking) {
      resetMaxFps();
    }
  }

  void adjustFpsFor(RouteProgress routeProgress) {
    if (!isEnabled || !isTracking) {
      return;
    }

    int maxFps = determineMaxFpsFrom(routeProgress, mapView.getContext());
    if (currentFpsThreshold != maxFps) {
      mapView.setMaximumFps(maxFps);
      currentFpsThreshold = maxFps;
    }
  }

  private void resetMaxFps() {
    mapView.setMaximumFps(DEVICE_MAX_FPS);
  }

  private int determineMaxFpsFrom(RouteProgress routeProgress, Context context) {
    final boolean isPluggedIn = batteryMonitor.isPluggedIn(context);
    RouteLegProgress routeLegProgress = routeProgress.currentLegProgress();

    if (isPluggedIn) {
      return DEVICE_MAX_FPS;
    } else if (validLowFpsManeuver(routeLegProgress) || validLowFpsDuration(routeLegProgress)) {
      return maxFpsThreshold;
    } else {
      return DEVICE_MAX_FPS;
    }
  }

  private boolean validLowFpsManeuver(RouteLegProgress routeLegProgress) {
    final String maneuverModifier = routeLegProgress.currentStep().maneuver().modifier();
    return maneuverModifier != null
      && (maneuverModifier.equals(NavigationConstants.STEP_MANEUVER_MODIFIER_STRAIGHT)
      || maneuverModifier.equals(NavigationConstants.STEP_MANEUVER_MODIFIER_SLIGHT_LEFT)
      || maneuverModifier.equals(NavigationConstants.STEP_MANEUVER_MODIFIER_SLIGHT_RIGHT));
  }

  private boolean validLowFpsDuration(RouteLegProgress routeLegProgress) {
    final double expectedStepDuration = routeLegProgress.currentStep().duration();
    final double durationUntilNextManeuver = routeLegProgress.currentStepProgress().durationRemaining();
    final double durationSincePreviousManeuver = expectedStepDuration - durationUntilNextManeuver;
    return durationUntilNextManeuver > VALID_DURATION_IN_SECONDS_UNTIL_NEXT_MANEUVER
      && durationSincePreviousManeuver > VALID_DURATION_IN_SECONDS_SINCE_PREVIOUS_MANEUVER;
  }
}
