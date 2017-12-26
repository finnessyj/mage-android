package mil.nga.giat.mage.map.cache;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.model.LatLng;

import java.util.Collections;
import java.util.List;

/**
 * A <code>CacheOverlay</code> represents cached data set which can appear on a map.
 * A {@link CacheProvider} implementation will create instances of its associated
 * <code>CacheOverlay</code> subclass.  Note that this class provides default
 * {@link #equals(Object)} and {@link #hashCode()} implementations because
 * {@link CacheManager} places <code>CacheOverlay</code> instances in sets and they
 * may also be used as {@link java.util.HashMap} keys.  Subclasses must take care
 * those methods work properly if overriding those or other methods on which
 * <code>equals()</code> and <code>hashCode()</code> depend.
 *
 * @author osbornb
 */
public abstract class CacheOverlay {

    /**
     * Build the cache overlayName of a child
     *
     * @param name      cache overlay name
     * @param childName child cache overlay name
     * @return
     */
    static String buildChildCacheName(String name, String childName) {
        return name + "-" + childName;
    }

    /**
     * Name of the cache overlay
     */
    private final String overlayName;

    /**
     * Cache type
     */
    private final Class<? extends CacheProvider> type;

    /**
     * True when enabled
     */
    private boolean enabled = false;

    /**
     * True when the cache was newly added, such as a file opened with MAGE
     */
    private boolean added = false;

    /**
     * True if the cache type supports child caches
     */
    private final boolean supportsChildren;

    /**
     * Constructor
     *
     * @param type the {@link CacheProvider provider} that creates and manages this overlay
     * @param overlayName a unique, persistent name for the overlay
     * @param supportsChildren true if this cache overlay can have child cache overlays
     */
    protected CacheOverlay(Class<? extends CacheProvider> type, String overlayName, boolean supportsChildren) {
        this.type = type;
        this.overlayName = overlayName;
        this.supportsChildren = supportsChildren;
    }

    /**
     * Remove the cache overlay from the map
     */
    public abstract void removeFromMap();

    public String getOverlayName() {
        return overlayName;
    }

    public Class<? extends CacheProvider> getType() {
        return type;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAdded() {
        return added;
    }

    public void setAdded(boolean added) {
        this.added = added;
    }

    /**
     * Get the icon image resource id for the cache
     *
     * @return
     */
    public Integer getIconImageResourceId() {
        return null;
    }

    /**
     * Does the cache type support children
     *
     * @return
     */
    public boolean isSupportsChildren() {
        return supportsChildren;
    }

    /**
     * Get the children cache overlays
     *
     * @return
     */
    public List<CacheOverlay> getChildren() {
        return Collections.emptyList();
    }

    /**
     * Get the child's parent cache overlay
     *
     * @return parent cache overlay
     */
    public CacheOverlay getParent(){
        return null;
    }

    /**
     * Get information about the cache to display
     *
     * @return
     */
    public String getInfo() {
        return null;
    }

    /**
     * On map click
     *
     * @param latLng  map click location
     * @param mapView map view
     * @param map     Google map
     * @return map click message
     */
    public String onMapClick(LatLng latLng, MapView mapView, GoogleMap map) {
        return null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CacheOverlay)) {
            return false;
        }
        CacheOverlay other = (CacheOverlay)obj;
        return getType().equals(other.getType()) && getOverlayName().equals(other.getOverlayName());
    }

    @Override
    public int hashCode() {
        return getOverlayName().hashCode();
    }

    public boolean isTypeOf(Class<? extends CacheProvider> providerType) {
        return providerType.isAssignableFrom(getType());
    }
}