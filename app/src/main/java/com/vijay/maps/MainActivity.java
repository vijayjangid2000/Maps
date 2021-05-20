package com.vijay.maps;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.ButtCap;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.JointType;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.PlacesClient;
import com.google.android.libraries.places.widget.Autocomplete;
import com.google.android.libraries.places.widget.AutocompleteActivity;
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.maps.android.PolyUtil;
import com.google.maps.android.SphericalUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    /* INTEGERS */
    private static final int
            AUTOCOMPLETE_REQUEST_CODE = 1,
            REQUEST_CODE = 200,
            PLAY_SERVICES_ERROR_CODE = 300,
            NEAR_DESTINATION_LENGTH = 20; // if we reach 20

    /* STRINGS */
    public static final String
            TAG = "XXX",
            MAP_API = "https://maps.googleapis.com/";

    private final LatLng defaultLocation = new LatLng(-33.8523341, 151.2106085);

    private GoogleMap map;
    private PlacesClient placesClient;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private Location lastLocation, currentLocation;

    private LatLng origin, destination;

    private ArrayList<LatLng> polyLineList;
    private ArrayList<LatLng> listLivePolylinePoints;
    private ArrayList<LatLng> listPolygonPoints;
    private ArrayList<Polyline> listAllPolyLines; // for removing polylines
    private ArrayList<Marker> listAllMarkers; // for removing markers

    private ApiInterface apiInterface;
    int currentPointOnPolyLine; // initially zero in oncreate

    private boolean needRecenter, isDriving, isMarkingDestination, areRealUpdates;

    Bitmap bitmapCar;
    Marker markerCar;

    Timer timer; // for fake location updates

    // polyLine properties while driving
    int colorPolyLine = R.color.veryLightGreen, widthPolyLine = 15;

    final int DEFAULT_ZOOM = 18; // 10 shows India, 15 shows streets
    int zoomCurrent = 18;
    float tiltCurrent = 45f;
    double tripDistance, distanceCovered;

    final long intervalLocationUpdate = 5000,
            intervalFastestUpdate = 2000,
            MOVE_ANIMATION_DURATION = 1000;

    SwitchCompat fakeSwitch;

    AlertDialog dialogView; // show/hide progress bar

    // views here
    TextView tvb_search, tvb_startDriving, tvb_getDirections,
            tvb_recenter, tvb_markDestination, tvb_resetMarker,
            tvb_openGoogleMaps, tv_dataRealtime, tv_zoomLevel, tv_tips;
    SeekBar seekbar_zoomSet;

    LinearLayout linearL_setZoom;
    private boolean save;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SupportMapFragment supportMapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.googleMaps);

        supportMapFragment.getMapAsync(this);

        listAllPolyLines = new ArrayList<>();
        listAllMarkers = new ArrayList<>();
        listPolygonPoints = new ArrayList<>();
        listLivePolylinePoints = new ArrayList<>();
    }

    // ask for permission and play services availability?
    private void initMapWork(GoogleMap googleMap) {

        if (!checkLocationPermission()) {
            toast("Please give permissions to use location.");
        } else if (!isServicesOk()) {
            toast("Play Services not available.");
        }
        bitmapCar = getBitmap(R.drawable.ic_car);

        map = googleMap;
        map.setTrafficEnabled(true);
        map.setMapType(GoogleMap.MAP_TYPE_SATELLITE);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        getDeviceLocation();

        // here are the results:
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@androidx.annotation.NonNull @NonNull
                                                 LocationResult locationResult) {
                super.onLocationResult(locationResult);
                Location location = locationResult.getLastLocation();
                currentLocation = location;

                //LatLng latLngLastKnown = new LatLng(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
                LatLng lastLngCurrent = new LatLng(location.getLatitude(), location.getLongitude());

                LatLng prev, next, current;

                current = listPolygonPoints.get(currentPointOnPolyLine);

                if (currentPointOnPolyLine == 0) prev = current;
                else prev = listPolygonPoints.get(currentPointOnPolyLine - 1);

                if (currentPointOnPolyLine == listPolygonPoints.size() - 1) next = current;
                else next = listPolygonPoints.get(currentPointOnPolyLine + 1);

                ArrayList<LatLng> listSegment = new ArrayList<>();
                listSegment.add(prev);
                listSegment.add(current);
                listSegment.add(next);

                if (PolyUtil.isLocationOnPath(toLatLng(currentLocation),
                        listSegment, true, 30)) {

                    if (areRealUpdates) {
                        LatLng snappedToSegment = getMarkerProjectionOnSegment(
                                toLatLng(currentLocation), listSegment, map.getProjection());
                        currentLocation = toLocation(snappedToSegment);
                    }
                    updateCameraPosition(currentLocation);
                } else {
                    Toast.makeText(MainActivity.this, "Not on path", Toast.LENGTH_SHORT).show();
                }

            }
        };

        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        locationRequest.setInterval(intervalLocationUpdate);
        locationRequest.setFastestInterval(intervalFastestUpdate);

        tvb_recenter = findViewById(R.id.tv_recenter);
        map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(@androidx.annotation.NonNull @NonNull LatLng latLng) {
                if (isMarkingDestination) {
                    listAllMarkers.add(map.addMarker(new MarkerOptions().position(latLng)));
                    destination = latLng;
                    isMarkingDestination = false;
                    tvb_markDestination.setText("Mark On Map");
                    hideViews(new View[]{tvb_search, tvb_markDestination});
                    showViews(new View[]{tvb_getDirections, tvb_resetMarker, tvb_openGoogleMaps});
                } else {
                    needRecenter = false;
                    showViews(new View[]{tvb_recenter});
                }
            }
        });

        tvb_search = findViewById(R.id.tv_searchResult);
        tvb_search.setText("Search Location");
        tvb_search.setOnClickListener(view -> {
            searchGoogleMap();
        });

        tvb_startDriving = findViewById(R.id.tv_startDriving);
        tvb_startDriving.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDriving();
            }
        });

        tvb_getDirections = findViewById(R.id.tv_getDirections);
        tvb_getDirections.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                drawDirections();
            }
        });

        tvb_recenter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recenterMap();
            }
        });

        tvb_markDestination = findViewById(R.id.tv_markOnMap);
        tvb_markDestination.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                tvb_markDestination.setText("Please select a location on map.");
                isMarkingDestination = true;
                tvb_search.setVisibility(View.GONE);
            }
        });

        fakeSwitch = findViewById(R.id.switchUpdateType);
        fakeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                areRealUpdates = b;
                if (b) toast("You will receive REAL Location Updates");
                else toast("You will receive FAKE location Updates");
            }
        });

        tvb_resetMarker = findViewById(R.id.tv_resetMarkers);
        tvb_resetMarker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                for (Marker marker : listAllMarkers) marker.remove();
                for (Polyline polyline : listAllPolyLines) polyline.remove();
                listAllPolyLines.clear();
                listAllMarkers.clear();
                listPolygonPoints.clear();
                listLivePolylinePoints.clear();

                showViews(new View[]{tvb_search, tvb_markDestination});
                hideViews(new View[]{tvb_resetMarker, tvb_getDirections, tvb_startDriving,
                        tvb_recenter, tvb_openGoogleMaps, tv_dataRealtime});
            }
        });

        tvb_openGoogleMaps = findViewById(R.id.tv_openInMaps);
        tvb_openGoogleMaps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openInGoogleMaps();
            }
        });

        tv_dataRealtime = findViewById(R.id.tv_data); // for speed, distance etc.

        tv_zoomLevel = findViewById(R.id.tv_zoomLevel);
        tv_zoomLevel.setText("Drving Zoom Level: " + zoomCurrent);

        seekbar_zoomSet = findViewById(R.id.seekbar_zoomSet);
        seekbar_zoomSet.setProgress(zoomCurrent, true);
        seekbar_zoomSet.setMax(24);
        seekbar_zoomSet.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                zoomCurrent = i;
                tv_zoomLevel.setText("Driving Zoom Level: " + zoomCurrent);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        linearL_setZoom = findViewById(R.id.ll_setZoom);

        tv_tips = findViewById(R.id.tv_tips);
    }

    @SuppressLint("MissingPermission")
    private void startDriving() {
        // was not driving now we start driving
        if (!isDriving) {
            isDriving = true;
            if (currentLocation != null) {
                map.animateCamera(CameraUpdateFactory.newCameraPosition(
                        CameraPosition.builder()
                                .target(toLatLng(currentLocation))
                                .zoom(DEFAULT_ZOOM).build()));
            }
            needRecenter = true;
            tvb_startDriving.setCompoundDrawablesWithIntrinsicBounds(
                    toDrawable(R.drawable.ic_baseline_cancel_24), null, null, null);
            map.setMyLocationEnabled(false);
            startLocationCallback(areRealUpdates);
            tvb_startDriving.setText(" Stop Driving?");
            hideViews(new View[]{tvb_markDestination, tvb_getDirections, tvb_search,
                    tvb_resetMarker, fakeSwitch});
            showViews(new View[]{tvb_recenter, linearL_setZoom});
        } else {
            stopDriving();
        }
    }

    @SuppressLint("MissingPermission")
    private void stopDriving() {
        isDriving = false;
        Toast.makeText(this, "Trip Completed", Toast.LENGTH_LONG).show();
        removePolyLinesAndMarkers();
        //user cancelled driving
        tvb_startDriving.setText(" Start Driving");
        tvb_startDriving.setCompoundDrawablesWithIntrinsicBounds(
                toDrawable(R.drawable.ic_baseline_directions_car_24), null, null, null);
        showViews(new View[]{tvb_search, tvb_markDestination, tv_tips});
        hideViews(new View[]{tvb_getDirections, tvb_recenter, tvb_startDriving,
                tvb_openGoogleMaps, tv_dataRealtime, linearL_setZoom});
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        if (timer != null) timer.cancel();
        isMarkingDestination = false;
        needRecenter = false;
        currentPointOnPolyLine = 0; // do not do that in reset driving
        listAllPolyLines.clear();
        listAllMarkers.clear();
        listPolygonPoints.clear();
        listLivePolylinePoints.clear();
        map.setMyLocationEnabled(true);
        fakeSwitch.setVisibility(View.VISIBLE);
    }

    private void updateCameraPosition(Location currentLocation) {

        if (lastLocation == null) lastLocation = currentLocation;

        double headingDirection = SphericalUtil.computeHeading(
                toLatLng(lastLocation), toLatLng(currentLocation));

        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(toLatLng(currentLocation))
                .zoom(zoomCurrent)
                .tilt(tiltCurrent)
                .bearing((float) headingDirection)
                .build();

        if (needRecenter) map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

        // this list contain latLng to create a polyline
        listLivePolylinePoints.add(toLatLng(currentLocation));

        //drawPolyLineWhereWalked(listLivePolylinePoints);
        updateCarLocation(this.currentLocation);

        //stopping if we have reached in a radius of 20m from destination
        if (SphericalUtil.computeDistanceBetween(toLatLng(currentLocation), destination)
                < NEAR_DESTINATION_LENGTH) {
            stopDriving();
        }

        // make sure to update previousLoc at last
        lastLocation = currentLocation;
    }

    private void drawPolyLineWhereWalked(ArrayList<LatLng> latLngArrayList) {
        PolylineOptions polyLineOptions = new PolylineOptions()
                .color(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorPrimary))
                .width(16)
                .startCap(new ButtCap())
                .jointType(JointType.ROUND)
                .addAll(latLngArrayList);

        listAllPolyLines.add(map.addPolyline(polyLineOptions));
    }

    // starts Handler for getting current location every 5 seconds
    private void startLocationCallback(boolean real) {

        if (!real) {
            fakeLocationCallbacks(); // for testing using polyline points
        } else {
            if (checkLocationPermission()) fusedLocationProviderClient.requestLocationUpdates
                    (locationRequest, locationCallback, null);
        }
    }

    private void fakeLocationCallbacks() {

        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (polyLineList == null) polyLineList = new ArrayList<>();
                if (currentPointOnPolyLine >= polyLineList.size()) {
                    timer.cancel();
                    return;
                }
                currentLocation = toLocation(polyLineList.get(currentPointOnPolyLine++));
                runOnUiThread(() -> {
                    LatLng lastLngCurrent = toLatLng(currentLocation);

                    if (PolyUtil.containsLocation(lastLngCurrent, polyLineList, true)) {
                        updateCameraPosition(currentLocation);
                    } else {
                        Toast.makeText(MainActivity.this, "Driver outside of path", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }, 1000, 3000);

    }

    // Why needed? check below link
    //https://stackoverflow.com/questions/54903593
    private LatLng getMarkerProjectionOnSegment(
            LatLng carPos, List<LatLng> segment, Projection projection) {
        LatLng markerProjection = null;

        Point carPosOnScreen = projection.toScreenLocation(carPos);
        Point p1 = projection.toScreenLocation(segment.get(0));
        Point p2 = projection.toScreenLocation(segment.get(1));
        Point carPosOnSegment = new Point();

        float denominator = (p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y);
        // p1 and p2 are the same
        if (Math.abs(denominator) <= 1E-10) {
            markerProjection = segment.get(0);
        } else {
            float t = (carPosOnScreen.x * (p2.x - p1.x) - (p2.x - p1.x) * p1.x
                    + carPosOnScreen.y * (p2.y - p1.y) - (p2.y - p1.y) * p1.y) / denominator;
            carPosOnSegment.x = (int) (p1.x + (p2.x - p1.x) * t);
            carPosOnSegment.y = (int) (p1.y + (p2.y - p1.y) * t);
            markerProjection = projection.fromScreenLocation(carPosOnSegment);
        }
        return markerProjection;
    }

    private void recenterMap() {

        /* It means we animate camera with current Location
         * Reason - Suppose want to see the path ahead of them,
         * so they try to move map with finger but they can't because
         * we are changing the camera position every time we get location update
         *
         * So here we pause using that boolean and in update Camera position method w
         * we do required code. */

        needRecenter = true;
        zoomCurrent = 17;
        tiltCurrent = 45f;

        if (lastLocation != null && currentLocation != null) {
            float bearing = (float) SphericalUtil.computeHeading(toLatLng(lastLocation), toLatLng(currentLocation));
            map.animateCamera(CameraUpdateFactory.newCameraPosition
                    (new CameraPosition.Builder().target(toLatLng(currentLocation)).zoom(zoomCurrent).bearing(bearing).tilt(45f).build()));
        }

        tvb_recenter.setVisibility(View.GONE);
    }

    private void drawDirections() {
        tvb_getDirections.setEnabled(false);

        Retrofit retrofit = new Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
                .baseUrl(MAP_API)
                .build();

        apiInterface = retrofit.create(ApiInterface.class);
        showProgressBar(true, "Please wait\n We are finding best directions for you...");

        try {
            String origin = this.origin.latitude + "," + this.origin.longitude;
            String destination = this.destination.latitude + "," + this.destination.longitude;
            getDirection(origin, destination);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Problem Occurred - " + e.getMessage(), Toast.LENGTH_SHORT).show();
            dialogView.cancel();
            tvb_getDirections.setEnabled(true);
        }

    }

    // this gets direction and draws polyline also
    private void getDirection(String origin, String destination) {

        apiInterface.getDirectionFrom("driving", "less_driving"
                , origin, destination
                , getString(R.string.map_api_key))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleObserver<Result>() {

                    @Override
                    public void onSuccess(@NonNull Result result) {

                        polyLineList = new ArrayList<>();
                        System.out.println("Apple -> " + result.toString());

                        for (int i = 0; i < result.getRoutes().size(); i++) {
                            String polyLine = result.getRoutes().get(i)
                                    .getOverview_polyline().getPoints();
                            polyLineList.addAll(PolyUtil.decode(polyLine));
                        }

                        PolylineOptions polyLineOptions = new PolylineOptions()
                                .color(ContextCompat.getColor(getApplicationContext(),
                                        colorPolyLine))
                                .width(widthPolyLine)
                                .startCap(new ButtCap())
                                .jointType(JointType.ROUND)
                                .addAll(polyLineList);

                        // copying polyline for smooth car movement
                        listPolygonPoints.addAll(polyLineList);

                        //adding polyline in a list (to remove in future)
                        listAllPolyLines.add(map.addPolyline(polyLineOptions));


                        // polyline created
                        LatLngBounds.Builder builder = new LatLngBounds.Builder();
                        builder.include(MainActivity.this.origin);
                        builder.include(MainActivity.this.destination);
                        // animating camera to our navigation route
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));

                        // adding the car marker
                        markerCar = map.addMarker(new MarkerOptions()
                                .position(MainActivity.this.origin)
                                .icon(BitmapDescriptorFactory.fromBitmap(bitmapCar))
                                // Specifies the anchor to be at a particular point in the marker image.
                                .anchor(0.5f, 1));
                        listAllMarkers.add(markerCar); // to remove when stop driving

                        if (listPolygonPoints.size() == 0) {
                            toast("Sorry: No directions for this route");
                        } else {
                            tripDistance = SphericalUtil.computeLength(listPolygonPoints) / 1000;
                            tv_dataRealtime.setText("Trip Distance: " + String.format("%.2f", tripDistance) + " Km");
                            hideViews(new View[]{tvb_getDirections});
                            showViews(new View[]{tvb_startDriving, tv_dataRealtime});
                        }
                        dialogView.cancel();
                        tvb_getDirections.setEnabled(true);
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        toast("Error in drawing directions");
                        dialogView.cancel();
                        tvb_getDirections.setEnabled(true);
                    }

                    @Override
                    public void onSubscribe(@NonNull Disposable d) {

                    }

                });
    }

    // For Roads api
    // https://stackoverflow.com/questions/47329243 (Why needed? check this link)
    private String buildRequestUrl(List<LatLng> trackPoints) {
        StringBuilder url = new StringBuilder();
        url.append("https://roads.googleapis.com/v1/snapToRoads?path=");

        for (LatLng trackPoint : trackPoints) {
            url.append(String.format("%8.5f", trackPoint.latitude));
            url.append(",");
            url.append(String.format("%8.5f", trackPoint.longitude));
            url.append("|");
        }
        url.delete(url.length() - 1, url.length());
        url.append("&interpolate=true");
        url.append(String.format("&key=%s", R.string.map_api_key));

        return url.toString();
    }

    private class GetSnappedPointsAsyncTask extends AsyncTask<List<LatLng>, Void, List<LatLng>> {

        protected void onPreExecute() {
            super.onPreExecute();
        }

        protected List<LatLng> doInBackground(List<LatLng>... params) {

            List<LatLng> snappedPoints = new ArrayList<>();

            HttpURLConnection connection = null;
            BufferedReader reader = null;

            try {
                URL url = new URL(buildRequestUrl(params[0]));
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();

                InputStream stream = connection.getInputStream();

                reader = new BufferedReader(new InputStreamReader(stream));
                StringBuilder jsonStringBuilder = new StringBuilder();

                StringBuffer buffer = new StringBuffer();
                String line = "";

                while ((line = reader.readLine()) != null) {
                    buffer.append(line + "\n");
                    jsonStringBuilder.append(line);
                    jsonStringBuilder.append("\n");
                }

                JSONObject jsonObject = new JSONObject(jsonStringBuilder.toString());
                JSONArray snappedPointsArr = jsonObject.getJSONArray("snappedPoints");

                for (int i = 0; i < snappedPointsArr.length(); i++) {
                    JSONObject snappedPointLocation = ((JSONObject) (snappedPointsArr.get(i))).getJSONObject("location");
                    double lattitude = snappedPointLocation.getDouble("latitude");
                    double longitude = snappedPointLocation.getDouble("longitude");
                    snappedPoints.add(new LatLng(lattitude, longitude));
                }

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (JSONException e) {
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
                try {
                    if (reader != null) {
                        reader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            return snappedPoints;
        }

        @Override
        protected void onPostExecute(List<LatLng> result) {
            super.onPostExecute(result);

            PolylineOptions polyLineOptions = new PolylineOptions();
            polyLineOptions.addAll(result);
            polyLineOptions.width(5);
            polyLineOptions.color(Color.RED);
            map.addPolyline(polyLineOptions);

            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            builder.include(result.get(0));
            builder.include(result.get(result.size() - 1));
            LatLngBounds bounds = builder.build();
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 10));

        }
    }

    // open intent to search and result in activity results (override)
    private void searchGoogleMap() {

        //First initialize the Places library
        Places.initialize(this, getString(R.string.map_api_key));
        placesClient = Places.createClient(this);

        // Set the fields to specify which types of place data to
        // return after the user has made a selection.
        List<Place.Field> fields = Arrays.asList(Place.Field.LAT_LNG, Place.Field.ID, Place.Field.NAME);

        // Start the autocomplete intent.
        Intent intent = new Autocomplete.
                IntentBuilder(AutocompleteActivityMode.FULLSCREEN, fields)
                .setCountries(Arrays.asList((new String[]{"IN"}).clone()))
                .build(this);
        startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE);

    }

    private void updateCarLocation(Location currentLoc) {

        boolean isValidIndex = currentPointOnPolyLine < listPolygonPoints.size() - 1;
        if (isValidIndex) {
            LatLng prevLatLng;
            if (currentPointOnPolyLine == 0)
                prevLatLng = listPolygonPoints.get(currentPointOnPolyLine);
            else prevLatLng = listPolygonPoints.get(currentPointOnPolyLine - 1);

            animateCarMove(markerCar, toLatLng(lastLocation),
                    toLatLng(currentLoc), MOVE_ANIMATION_DURATION);
            double bearing = SphericalUtil.computeHeading(prevLatLng, toLatLng(currentLoc));
            markerCar.setRotation((float) bearing + 180); // reason - our svg need to rotate
        }
    }

    private void animateCarMove(final Marker marker, final LatLng beginLatLng
            , final LatLng endLatLng, final long myDuration) {

        final long startTime = SystemClock.uptimeMillis();
        final Interpolator interpolator = new LinearInterpolator();

        final Handler handler = new Handler();
        handler.post(new Runnable() {
            @Override
            public void run() {
                // calculate phase of animation
                long elapsed = SystemClock.uptimeMillis() - startTime;

                // if phase is > 1, it means animation time is completed
                float phase = interpolator.getInterpolation((float) elapsed / myDuration);

                double newLat, newLng;  // calculate new position for marker

                newLat = (endLatLng.latitude - beginLatLng.latitude)
                        * phase + beginLatLng.latitude;

                double lngDelta = endLatLng.longitude - beginLatLng.longitude;

                if (Math.abs(lngDelta) > 180) lngDelta -= Math.signum(lngDelta) * 360;
                newLng = lngDelta * phase + beginLatLng.longitude;

                // here we change marker position on map
                marker.setPosition(new LatLng(newLat, newLng));

                if (phase < 1.0) handler.postDelayed(this, 16);
                // call next marker position
            }
        });
    }

    private void toast(String message) {
        // shortcut for toast
        Toast.makeText(this, message,
                Toast.LENGTH_SHORT).show();
    }

    private void createLog(String message) {
        Log.d(TAG, "log: " + message);
    }

    // checking google play services available?
    private boolean isServicesOk() {

        GoogleApiAvailability googleApi = GoogleApiAvailability.getInstance();
        int result = googleApi.isGooglePlayServicesAvailable(this);

        if (result == ConnectionResult.SUCCESS) {
            return true;
        } else if (googleApi.isUserResolvableError(result)) {

            Dialog dialog = googleApi.getErrorDialog(this, result,
                    PLAY_SERVICES_ERROR_CODE, new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialogInterface) {
                            toast("Dialog is cancelled by user");
                        }
                    });

            dialog.show();

        } else {
            toast("Play services are not available");
        }

        return false;
    }

    private void getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        if (currentLocation != null) {
            LatLng latLng = new LatLng(currentLocation.getLatitude(),
                    currentLocation.getLongitude());

            map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    latLng, DEFAULT_ZOOM));
            //listMarkers.add(map.addMarker(new MarkerOptions().position(latLng)));

            // we are just updating the origin for getting directions
            origin = latLng;

        }
        try {
            if (checkLocationPermission()) {
                Task<Location> locationResult = fusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(
                        this, new OnCompleteListener<Location>() {
                            @Override
                            public void onComplete(@NonNull Task<Location> task) {
                                if (task.isSuccessful()) {
                                    // Set the map's camera position to the current location of the device.

                                    currentLocation = task.getResult();

                                    if (currentLocation != null) {
                                        LatLng latLng = new LatLng(currentLocation.getLatitude(),
                                                currentLocation.getLongitude());

                                        map.setMyLocationEnabled(true);
                                        map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                                latLng, DEFAULT_ZOOM));
                                        // we are just updating the origin for getting directions
                                        origin = latLng;

                                    }
                                } else {
                                    Log.d(TAG, "Current location is null. Using defaults.");
                                    Log.e(TAG, "Exception: %s", task.getException());
                                    map.animateCamera(CameraUpdateFactory
                                            .newLatLngZoom(defaultLocation, DEFAULT_ZOOM));
                                    map.getUiSettings().setMyLocationButtonEnabled(false);
                                }
                            }
                        });
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage(), e);
        }
    }

    boolean checkLocationPermission() {
        boolean isLocationGranted = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE);
        }

        return isLocationGranted;
    }

    private LatLng toLatLng(Location location) {
        return new LatLng(location.getLatitude(), location.getLongitude());
    }

    private String toStringLatLng(LatLng latLng) {
        return latLng.latitude + "," + latLng.longitude;
    }

    private String toStringLatLng(Location location) {
        return location.getLatitude() + "," + location.getLongitude();
    }

    private Location toLocation(LatLng latLng) {
        Location location = new Location("");
        location.setLatitude(latLng.latitude);
        location.setLongitude(latLng.longitude);
        return location;
    }

    public Drawable toDrawable(int id) {
        return ContextCompat.getDrawable(getContext(), R.drawable.ic_baseline_cancel_24);
    }

    public Context getContext() {
        return MainActivity.this;
    }

    private Bitmap getBitmap(int drawableRes) {
        Drawable drawable = getResources().getDrawable(drawableRes);
        Canvas canvas = new Canvas();
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        canvas.setBitmap(bitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String @NonNull [] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            toast("Permission Granted");
        } else toast("Permission Denied");

    }

    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, @Nullable Intent data) {

        if (requestCode == AUTOCOMPLETE_REQUEST_CODE) {

            if (resultCode == RESULT_OK) {
                Place place = Autocomplete.getPlaceFromIntent(data);
                //Log.i(TAG, "Place: " + place.getName() + ", " + place.getId());

                LatLng latLngSearched = place.getLatLng();
                // because places is not working
                //LatLng latLngSearched = new LatLng(26.900531, 75.742189);
                listAllMarkers.add(map.addMarker(new MarkerOptions()
                        .position(latLngSearched)));

                //.title(place.getName()));

                CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(latLngSearched)
                        .zoom(zoomCurrent)
                        .build();

                destination = latLngSearched;

                map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

                showViews(new View[]{tvb_getDirections, tvb_resetMarker, tvb_openGoogleMaps});
                hideViews(new View[]{tvb_markDestination, tvb_search});

            } else if (resultCode == AutocompleteActivity.RESULT_ERROR) {
                Status status = Autocomplete.getStatusFromIntent(data);
                Log.i(TAG, status.getStatusMessage());
                toast("Error: Search Results :" + status.getStatusMessage());

            } else if (resultCode == RESULT_CANCELED) {
                // The user canceled the operation.
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        initMapWork(googleMap);
    }

    private void hideViews(View[] viewsArray) {
        int visibility = View.GONE;
        for (View view : viewsArray) {
            view.setVisibility(visibility);
        }
    }

    private void showViews(View[] viewsArray) {
        int visibility = View.VISIBLE;
        for (View view : viewsArray) {
            view.setVisibility(visibility);
        }
    }

    private void removePolyLinesAndMarkers() {
        // removing all markers, polylines when user stop driving
        for (Polyline polyLine : listAllPolyLines)
            polyLine.remove();

        for (Marker marker : listAllMarkers)
            marker.remove();

        listAllPolyLines.clear();
        listAllMarkers.clear();
        // also clearing lists for next searches and trips
    }

    void showProgressBar() {
        showProgressBar(false, "");
    }

    void showProgressBar(boolean showText, String text) {

        /*to customize the progress bar then go to
         * progressbar_viewxml.xml in layout folder*/

        View view = getLayoutInflater().inflate(R.layout.layout_progressbar, null);
        if (view.getParent() != null) ((ViewGroup) view.getParent()).removeView(view);

        CircularProgressIndicator lpi = view.findViewById(R.id.home_progress_bar);
        TextView textView = view.findViewById(R.id.progress_text_tv);
        if (showText) textView.setText(text);
        AlertDialog.Builder alertBldr_loading = new AlertDialog.Builder(this)
                .setCancelable(false);
        dialogView = alertBldr_loading.create();
        dialogView.setView(view);
        Window window = dialogView.getWindow();
        if (window != null) window.setBackgroundDrawableResource(R.color.Transparent);
        dialogView.show();
    }

    void openInGoogleMaps() {
        if (destination == null) {
            toast("Please select destination then try again");
            return;
        }

        String geoUri = "http://maps.google.com/maps?q=loc:" +
                destination.latitude + "," + destination.longitude + " (" + "" + ")";
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(geoUri));
        if (mapIntent.resolveActivity(getContext().getPackageManager()) != null) {
            getContext().startActivity(mapIntent);
        }
    }
}