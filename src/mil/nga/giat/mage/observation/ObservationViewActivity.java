package mil.nga.giat.mage.observation;

import java.util.Collection;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import mil.nga.giat.mage.R;
import mil.nga.giat.mage.sdk.database.orm.observation.Observation;
import mil.nga.giat.mage.sdk.database.orm.observation.ObservationHelper;
import mil.nga.giat.mage.sdk.database.orm.observation.Property;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.widget.TextView;

public class ObservationViewActivity extends FragmentActivity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.observation_viewer);
		try {
			Observation o = ObservationHelper.getInstance(getApplicationContext()).readObservation(1L);
			System.out.println("Observation is");
			System.out.println(o);
			System.out.println("yep");
			Collection<Property> properties = o.getProperties();
			System.out.println("properties count is: " + properties.size());
			System.out.println("properties: " + properties);
			String coordinates = o.getGeometry().getCoordinates();
			String[] coordinateSplit = coordinates.split("\\[|,|\\]");
			System.out.println("split count: " + coordinateSplit.length);
			System.out.println(coordinateSplit);
			
			
			((TextView)findViewById(R.id.location)).setText(coordinateSplit[0] + ", " + coordinateSplit[1]);
			GoogleMap map = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();
			
			// TODO debugging location
			LatLng location = new LatLng(Double.parseDouble(coordinateSplit[0]), Double.parseDouble(coordinateSplit[1]));
			
			map.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15));
			
			map.addMarker(new MarkerOptions().position(location));
			
		} catch (Exception e) {
			
		}
		
		this.setTitle("Suspicious Individual");
		
		
	}
}
