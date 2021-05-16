package com.vijay.maps;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import io.reactivex.rxjava3.annotations.NonNull;

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
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.SingleObserver;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity
        implements OnMapReadyCallback {

    /* INTEGERS */
    private static final int
            AUTOCOMPLETE_REQUEST_CODE = 1,
            REQUEST_CODE = 200,
            DEFAULT_ZOOM = 15,
            PLAY_SERVICES_ERROR_CODE = 300;

    /* STRINGS */
    public static final String
            TAG = "XXX",
            MAP_API = "https://maps.googleapis.com/",
            KEY_CAMERA_POSITION = "camera_position",
            KEY_LOCATION = "location";

    private final LatLng defaultLocation = new LatLng(-33.8523341, 151.2106085);

    private GoogleMap map;
    private PlacesClient placesClient;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;
    private Location lastKnownLocation;
    private Location currentLocation;

    private ApiInterface apiInterface;
    private List<LatLng> polyLineList;
    private PolylineOptions polyLineOptions;
    private LatLng latLngOrigin, latLngDestination;
    private ArrayList<LatLng> al_LatLng;

    private ArrayList<Polyline> listPloyLine; // for removing polyline
    private ArrayList<Marker> listMarkers; // for removing markers

    private boolean isRecenter, isDriving, isMarkingDestination;

    // views here
    TextView tv_search, tv_startDriving, tv_getDirections,
            tv_recenter, tv_markOnMap;
    private boolean save;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        /*
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        */

        initMapWork();
        SupportMapFragment supportMapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.googleMaps);

        supportMapFragment.getMapAsync(this);
        isDriving = false;
        listPloyLine = new ArrayList<>();
        listMarkers = new ArrayList<>();
        isMarkingDestination = false;
    }

    // for ease access, called when map is ready
    private void afterOnMapReady() {
        isRecenter = true;
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        LatLng destLatLng = new LatLng(26.892481, 75.728652);
        Location destLocation = new Location("");
        destLocation.setLatitude(destLatLng.latitude);
        destLocation.setLongitude(destLatLng.latitude);

        // here are the results:
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@androidx.annotation.NonNull @NonNull
                                                 LocationResult locationResult) {
                super.onLocationResult(locationResult);
                Location location = locationResult.getLastLocation();
                updateCameraPosition(location, destLocation);
            }
        };

        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        locationRequest.setInterval(5000);
        locationRequest.setFastestInterval(2000);

        // it animates camera to current location when user start app.
        // it is one time use (onCompleteListener)
        getDeviceLocation();

        tv_search = findViewById(R.id.tv_searchResult);
        tv_search.setText("Search Location");
        tv_search.setOnClickListener(view -> {
            searchGoogleMap();
        });

        tv_startDriving = findViewById(R.id.tv_startDriving);
        tv_startDriving.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startDriving();
            }
        });

        tv_getDirections = findViewById(R.id.tv_getDirections);
        tv_getDirections.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                drawDirections();
            }
        });

        tv_recenter = findViewById(R.id.tv_recenter);
        tv_recenter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isRecenter = true;
                LatLng latLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
                float bearing = 0;
                if (lastKnownLocation != null && currentLocation != null)
                    bearing = lastKnownLocation.bearingTo(currentLocation);
                map.animateCamera(CameraUpdateFactory.newCameraPosition
                        (new CameraPosition.Builder().target(latLng).zoom(18).bearing(bearing).tilt(45f).build()));
            }
        });

        map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(@androidx.annotation.NonNull @NonNull LatLng latLng) {
                isRecenter = false;
                if (isMarkingDestination) {
                    toast("Destination Selected: " + latLng.toString());
                    listMarkers.add(map.addMarker(new MarkerOptions().position(latLng)));
                    latLngDestination = latLng;
                    isMarkingDestination = false;
                    tv_markOnMap.setVisibility(View.INVISIBLE);
                    tv_getDirections.setVisibility(View.VISIBLE);
                    tv_markOnMap.setText("Mark On Map");
                }
            }
        });

        tv_markOnMap = findViewById(R.id.tv_markOnMap);
        tv_markOnMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                tv_markOnMap.setText("Please select a location.");
                isMarkingDestination = true;
                tv_search.setVisibility(View.GONE);
            }
        });

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            return;
        }
    }

    private void startDriving() {
        getDeviceLocation();
        // was not driving now we start driving
        if (!isDriving) {
            isDriving = true;
            // now we are driving
            startLoopForCurrentLocation();
            tv_startDriving.setText("Cancel Trip?");
            tv_search.setVisibility(View.GONE);
            tv_markOnMap.setVisibility(View.GONE);
            tv_getDirections.setVisibility(View.INVISIBLE);
            tv_recenter.setVisibility(View.VISIBLE);
        } else {
            // user cancelled driving
            isDriving = false;
            tv_startDriving.setText("Start Driving");
            tv_search.setVisibility(View.VISIBLE);
            tv_markOnMap.setVisibility(View.VISIBLE);
            tv_getDirections.setVisibility(View.GONE);
            tv_recenter.setVisibility(View.GONE);
            tv_startDriving.setVisibility(View.GONE);
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);

            // removing all markers, polylines when user stop driving
            for (Polyline polyLine : listPloyLine)
                polyLine.remove();

            listPloyLine.clear();

            for (Marker marker : listMarkers)
                marker.remove();

            listMarkers.clear();
            // also clearing lists for next searches and trips
        }

    }

    private void drawPolyLineWhereWalked(ArrayList<LatLng> latLngArrayList) {
        PolylineOptions polyLineOptions = new PolylineOptions()
                .color(ContextCompat.getColor(getApplicationContext(),
                        R.color.colorPrimary))
                .width(16)
                .startCap(new ButtCap())
                .jointType(JointType.ROUND)
                .addAll(latLngArrayList);

        listPloyLine.add(map.addPolyline(polyLineOptions));
    }

    private void updateCameraPosition(Location currentLocation, Location destLocation) {

        LatLng currentLatLng = new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude());
        if (lastKnownLocation == null) lastKnownLocation = currentLocation;

        // calculate bearing to find heading direction
        float bearingValue = lastKnownLocation.bearingTo(currentLocation);

        CameraPosition cameraPosition;
        if (currentLocation.distanceTo(lastKnownLocation) > 50)
            cameraPosition = new CameraPosition.Builder()
                    .target(currentLatLng).zoom(20).tilt(45f).bearing(bearingValue).build();
        else
            cameraPosition = new CameraPosition.Builder()
                    .target(currentLatLng).zoom(20).tilt(45f).build();

        if (isRecenter) map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

        lastKnownLocation = currentLocation;

        // this list contain latLng to create a polyline
        if (al_LatLng == null) al_LatLng = new ArrayList<>();
        al_LatLng.add(currentLatLng);

        drawPolyLineWhereWalked(al_LatLng);

        // stopping if we have reached in a radius of 30m from destination
        if (currentLocation.distanceTo(destLocation) < 30) stopDriving();
    }

    private void stopDriving() {

    }

    // starts Handler for getting current location every 5 seconds
    private void startLoopForCurrentLocation() {

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationProviderClient.requestLocationUpdates
                (locationRequest, locationCallback, null);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        googleMap.setTrafficEnabled(true);
        map = googleMap;
        UiSettings uiSettings = map.getUiSettings();
        uiSettings.setMyLocationButtonEnabled(true);
        map.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        //navigationRoute();
        afterOnMapReady();
    }

    private void drawDirections() {

        Retrofit retrofit = new Retrofit.Builder()
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava3CallAdapterFactory.create())
                .baseUrl(MAP_API)
                .build();

        apiInterface = retrofit.create(ApiInterface.class);

        try {
            String origin = latLngOrigin.latitude + "," + latLngOrigin.longitude;
            String destination = latLngDestination.latitude + "," + latLngDestination.longitude;
            getDirection(origin, destination);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Problem Occurred", Toast.LENGTH_SHORT).show();
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
                            String polyLine = result.getRoutes().get(i).getOverview_polyline().getPoints();
                            polyLineList.addAll(decodePoly(polyLine));
                        }

                        polyLineOptions = new PolylineOptions()
                                .color(ContextCompat.getColor(getApplicationContext(),
                                        R.color.colorPrimary))
                                .width(8)
                                .startCap(new ButtCap())
                                .jointType(JointType.ROUND)
                                .addAll(polyLineList);

                        //adding polyline in a list (to remove in future)
                        listPloyLine.add(map.addPolyline(polyLineOptions));


                        // polyline created
                        LatLngBounds.Builder builder = new LatLngBounds.Builder();
                        builder.include(latLngOrigin);
                        builder.include(latLngDestination);
                        // animating camera to our navigation route
                        map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
                    }

                    @Override
                    public void onError(@NonNull Throwable e) {
                        Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onSubscribe(@NonNull Disposable d) {

                    }

                });
        tv_startDriving.setVisibility(View.VISIBLE);
        tv_getDirections.setVisibility(View.GONE);
    }

    // this is to decode polyline vertices
    private List<LatLng> decodePoly(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)),
                    (((double) lng / 1E5)));
            poly.add(p);
        }

        return poly;
    }

    // ask for permission and play services availability?
    private void initMapWork() {
        askLocationPermission();
        if (isServicesOk()) toast("Map Ready!");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String @NonNull [] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            toast("Permission Granted");
        } else toast("Permission Denied");

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

        if (requestCode == AUTOCOMPLETE_REQUEST_CODE) {

            if (resultCode == RESULT_OK || resultCode == RESULT_CANCELED) {
                //Place place = Autocomplete.getPlaceFromIntent(data);
                //Log.i(TAG, "Place: " + place.getName() + ", " + place.getId());

                //LatLng latLngSearched = place.getLatLng();
                // because places is not working
                LatLng latLngSearched = new LatLng(26.900531, 75.742189);
                listMarkers.add(map.addMarker(new MarkerOptions()
                        .position(latLngSearched)));

                //.title(place.getName()));

                CameraPosition cameraPosition = new CameraPosition.Builder()
                        .target(latLngSearched)
                        .zoom(15)
                        .build();

                latLngDestination = latLngSearched;

                map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

                tv_getDirections.setVisibility(View.VISIBLE);
                tv_markOnMap.setVisibility(View.INVISIBLE);

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

    private void askLocationPermission() {

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE);
        }
    }

    // return permission status
    private boolean locationPermissionGranted() {
        return ContextCompat.checkSelfPermission
                (this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void toast(String message) {
        Toast.makeText(this, message,
                Toast.LENGTH_SHORT).show();
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

    private void updateLocationUI() {
        if (map == null) {
            return;
        }
        try {
            if (locationPermissionGranted()) {
                map.getUiSettings().setMyLocationButtonEnabled(true);
            } else {
                map.setMyLocationEnabled(false);
                map.getUiSettings().setMyLocationButtonEnabled(false);
                lastKnownLocation = null;
                askLocationPermission();
            }
        } catch (SecurityException e) {
            Log.e("Exception: %s", e.getMessage());
        }
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
                    latLng, DEFAULT_ZOOM + 3));
            //listMarkers.add(map.addMarker(new MarkerOptions().position(latLng)));

            // we are just updating the origin for getting directions
            latLngOrigin = latLng;

        }
        try {
            if (locationPermissionGranted()) {
                Task<Location> locationResult = fusedLocationProviderClient.getLastLocation();
                locationResult.addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful()) {
                            // Set the map's camera position to the current location of the device.

                            currentLocation = task.getResult();

                            if (currentLocation != null) {
                                LatLng latLng = new LatLng(currentLocation.getLatitude(),
                                        currentLocation.getLongitude());

                                map.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                        latLng, DEFAULT_ZOOM + 3));
                                //listMarkers.add(map.addMarker(new MarkerOptions().position(latLng)));

                                // we are just updating the origin for getting directions
                                latLngOrigin = latLng;

                            }
                        } else {
                            Log.d(TAG, "Current location is null. Using defaults.");
                            Log.e(TAG, "Exception: %s", task.getException());
                            map.moveCamera(CameraUpdateFactory
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

    // To reduce battery usage
    @Override
    protected void onPause() {
        super.onPause();
        savePower(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        savePower(false);
    }

    @SuppressLint("MissingPermission")
    private void savePower(boolean save) {

        if (map == null && checkPermit()) return;
        map.setMyLocationEnabled(!save);

        if (!isDriving) return; // if not driving then nothing to do
        if (fusedLocationProviderClient == null) return;
        if (save) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        } else {
            fusedLocationProviderClient.requestLocationUpdates
                    (locationRequest, locationCallback, null);
        }
    }

    boolean checkPermit() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}