package com.example.skipthegas;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;

/**
 *  Driver's map activity fragment, driver can view maps from this fragment
 */
public class DriverMapFragment extends Fragment implements OnMapReadyCallback{

    private GoogleMap mMap;
    private View mapView;

    private boolean mLocationPermissionGranted = false;
    private static final String TAG = "DriverMapFragment";

    private static final String FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    private static final String COURSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 9001;
    private static final float DEFAULT_ZOOM = 15f;

    FirebaseFirestore firebaseFirestore;

    /**
     * onCreateView method for DriverMapFragment fragment
     * @param inflater
     * @param container
     * @param savedInstanceState
     * @return view
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View view =  inflater.inflate(R.layout.fragment_driver_map, null, false);

        initMap();

        return view;
    }


    /**
     * Initializes the map and displays it
     */
    private void initMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.driver_map);
        assert mapFragment != null;
        mapFragment.getMapAsync(this);
        mapView = mapFragment.getView();
    }

    /**
     * Sets up the map camera and allows one to select start/end locations on a map
     * Set my current location to true
     * @param googleMap
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            getDeviceLocation();
            mMap.setMyLocationEnabled(true);
        }


        // Set My Location Button to Bottom Right
        if (mapView != null && mapView.findViewById(Integer.parseInt("1")) != null) {
            View locationButton = ((View) mapView.findViewById(Integer.parseInt("1")).getParent()).findViewById(Integer.parseInt("2"));
            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) locationButton.getLayoutParams();
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
            layoutParams.setMargins(0, 0, 30, 30);
        }

        firebaseFirestore = FirebaseFirestore.getInstance();
        firebaseFirestore
                .collection("all_requests")
                .addSnapshotListener(new EventListener<QuerySnapshot>() {
                    /**
                     * Fetches the ride requests that are currently open and displays them on the map
                     * So driver can view the start locations of all the current open requests relative to his location
                     * @param queryDocumentSnapshots
                     * @param e
                     */
                    @Override
                    public void onEvent(@javax.annotation.Nullable QuerySnapshot queryDocumentSnapshots, @javax.annotation.Nullable FirebaseFirestoreException e) {
                        if (e != null) {
                            Log.d(TAG, "Message: " + e.getMessage());
                            return;
                        }
                        if (queryDocumentSnapshots != null){
                            for (QueryDocumentSnapshot doc: queryDocumentSnapshots) {
                                boolean accepted = (boolean) doc.getData().get("is_accepted");
                                boolean cancel = (boolean) doc.getData().get("is_cancel");
                                if (!accepted && !cancel) {
                                    GeoPoint origin = (GeoPoint) doc.getData().get("ride_origin");
                                    String riderName = (String) doc.getData().get("rider_name");
                                    MarkerOptions startLocation;
                                    startLocation = new MarkerOptions().position(new LatLng(origin.getLatitude(), origin.getLongitude())).title(riderName);
                                    mMap.addMarker(startLocation);
                                }
                            }
                        } else {
                            Log.d(TAG, "Document does not exist.");
                        }


                    }
                });

    }

    /**
     * Get the device's current location for driver
     */
    private void getDeviceLocation() {
        Log.d(TAG, "getDeviceLocation: getting the devices location");

        FusedLocationProviderClient mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(getActivity());

        try {
            final Task location = mFusedLocationProviderClient.getLastLocation();
            location.addOnCompleteListener(new OnCompleteListener() {
                /**
                 * onComplete method for finding current location
                 * @param task
                 */
                @Override
                public void onComplete(@NonNull Task task) {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "onComplete: found location!");
                        Location currentLocation = (Location) task.getResult();

                        assert currentLocation != null;
                        moveCamera(new LatLng(currentLocation.getLatitude(), currentLocation.getLongitude()), DEFAULT_ZOOM);

                    } else {
                        Log.d(TAG, "onComplete: current location is null");
                        Toast.makeText(getActivity(), "unable to get current location", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "getDeviceLocation: SecurityException: " + e.getMessage());
        }
    }

    /**
     * Moves Camera to current location
     * @param latLng
     * @param zoom
     */
    private void moveCamera(LatLng latLng, float zoom) {
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, zoom));
    }

    /**
     * Get device permission to get current location
     * @param requestCode
     * @param permissions
     * @param grantResults
     */
    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mMap.setMyLocationEnabled(true);
            }
        }
    }
}
