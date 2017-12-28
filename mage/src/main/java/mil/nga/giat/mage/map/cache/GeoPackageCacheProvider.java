package mil.nga.giat.mage.map.cache;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mil.nga.geopackage.GeoPackage;
import mil.nga.geopackage.GeoPackageManager;
import mil.nga.geopackage.extension.link.FeatureTileTableLinker;
import mil.nga.geopackage.factory.GeoPackageFactory;
import mil.nga.geopackage.features.index.FeatureIndexManager;
import mil.nga.geopackage.features.user.FeatureDao;
import mil.nga.geopackage.tiles.user.TileDao;
import mil.nga.geopackage.validate.GeoPackageValidate;
import mil.nga.giat.mage.R;
import mil.nga.giat.mage.sdk.utils.MediaUtility;
import mil.nga.giat.mage.sdk.utils.StorageUtility;
import mil.nga.wkb.geom.GeometryType;

public class GeoPackageCacheProvider implements CacheProvider {

    private static final String LOG_NAME = GeoPackageCacheProvider.class.getName();

    /**
     * Get a cache name for the cache file
     *
     * @param manager
     * @param cacheFile
     * @return cache name
     */
    private static String makeUniqueCacheName(GeoPackageManager manager, File cacheFile) {
        String cacheName = MediaUtility.getFileNameWithoutExtension(cacheFile);
        final String baseCacheName = cacheName;
        int nameCount = 0;
        while (manager.exists(cacheName)) {
            cacheName = baseCacheName + "_" + (++nameCount);
        }
        return cacheName;
    }

    private final Context context;
    private final GeoPackageManager geoPackageManager;

    public GeoPackageCacheProvider(Context context) {
        this.context = context;
        this.geoPackageManager = GeoPackageFactory.getManager(context);
    }

    @Override
    public boolean isCacheFile(File cacheFile) {
        // Handle GeoPackage files by linking them to their current location
        return GeoPackageValidate.hasGeoPackageExtension(cacheFile);
    }

    @Override
    public CacheOverlay importCacheFromFile(File cacheFile) throws CacheImportException {
        String cacheName = getOrImportGeoPackageDatabase(cacheFile);
        return createCacheOverlay(cacheName);
    }

    @Override
    public Set<CacheOverlay> refreshCaches(Set<CacheOverlay> existingCaches) {
        Set<CacheOverlay> refreshed = new HashSet<>(existingCaches.size());
        for (CacheOverlay cache : existingCaches) {
            GeoPackageCacheOverlay gpCache = (GeoPackageCacheOverlay) cache;
            File dbFile = geoPackageManager.getFile(gpCache.getOverlayName());
            if (!dbFile.exists() || !dbFile.canRead()) {
                gpCache = null;
            }
            if (dbFile.lastModified() > gpCache.getRefreshTimestamp()) {
                gpCache = createCacheOverlay(gpCache.getOverlayName());
            }
            else {
                gpCache.updateRefreshTimestamp();
            }

            if (gpCache != null) {
                refreshed.add(gpCache);
            }
        }

        return refreshed;

        // TODO: test getting rid of this in favor of above to keep records of
        // unavailable databases along with a persistent database name that
        // can be stored in preferences to persist z-order.  otherwise, there's
        // no guarantee that the database/cache name will be the same across
        // different imports because of makeUniqueCacheName() above
//        Set<CacheOverlay> overlays = new HashSet<>();
//        geoPackageManager.deleteAllMissingExternal();
//        List<String> externalDatabases = geoPackageManager.externalDatabases();
//        for (String database : externalDatabases) {
//            GeoPackageCacheOverlay cacheOverlay = createCacheOverlay(database);
//            if (cacheOverlay != null) {
//                overlays.add(cacheOverlay);
//            }
//        }
//        return overlays;
    }

    /**
     * Import the GeoPackage file as an external link if it does not exist
     *
     * @param cacheFile
     * @return cache name when imported, null when not imported
     */
    @NonNull
    private String getOrImportGeoPackageDatabase(File cacheFile) throws CacheImportException {
        String databaseName = geoPackageManager.getDatabaseAtExternalFile(cacheFile);
        if (databaseName != null) {
            return databaseName;
        }

        databaseName = makeUniqueCacheName(geoPackageManager, cacheFile);
        CacheImportException fail;
        try {
            // import the GeoPackage as a linked file
            if (geoPackageManager.importGeoPackageAsExternalLink(cacheFile, databaseName)) {
                return databaseName;
            }
            fail = new CacheImportException(cacheFile, "GeoPackage import failed: " + cacheFile.getName());
        }
        catch (Exception e) {
            Log.e(LOG_NAME, "Failed to import file as GeoPackage. path: " + cacheFile.getAbsolutePath() + ", name: " + databaseName + ", error: " + e.getMessage());
            fail = new CacheImportException(cacheFile, "GeoPackage import threw exception", e);
        }

        if (cacheFile.canWrite()) {
            try {
                cacheFile.delete();
            }
            catch (Exception deleteException) {
                Log.e(LOG_NAME, "Failed to delete file: " + cacheFile.getAbsolutePath() + ", error: " + deleteException.getMessage());
            }
        }

        throw fail;
    }

    /**
     * Get the GeoPackage database as a cache overlay
     *
     * @param database
     * @return cache overlay
     */
    private GeoPackageCacheOverlay createCacheOverlay(String database) {

        GeoPackageCacheOverlay cacheOverlay = null;
        GeoPackage geoPackage = null;

        // Add the GeoPackage overlay
        try {
            geoPackage = geoPackageManager.open(database);
            List<GeoPackageTableCacheOverlay> tables = new ArrayList<>();

            // GeoPackage tile tables, build a mapping between table name and the created cache overlays
            Map<String, GeoPackageTileTableCacheOverlay> tileCacheOverlays = new HashMap<>();
            List<String> tileTables = geoPackage.getTileTables();
            for (String tableName : tileTables) {
                String tableCacheName = CacheOverlay.buildChildCacheName(database, tableName);
                TileDao tileDao = geoPackage.getTileDao(tableName);
                int count = tileDao.count();
                int minZoom = (int) tileDao.getMinZoom();
                int maxZoom = (int) tileDao.getMaxZoom();
                GeoPackageTileTableCacheOverlay tableCache = new GeoPackageTileTableCacheOverlay(tableCacheName, database, tableName, count, minZoom, maxZoom);
                tileCacheOverlays.put(tableName, tableCache);
            }

            // Get a linker to find tile tables linked to features
            FeatureTileTableLinker linker = new FeatureTileTableLinker(geoPackage);
            Map<String, GeoPackageTileTableCacheOverlay> linkedTileCacheOverlays = new HashMap<>();

            // GeoPackage feature tables
            List<String> featureTables = geoPackage.getFeatureTables();
            for (String tableName : featureTables) {
                String tableCacheName = CacheOverlay.buildChildCacheName(database, tableName);
                FeatureDao featureDao = geoPackage.getFeatureDao(tableName);
                int count = featureDao.count();
                GeometryType geometryType = featureDao.getGeometryType();
                FeatureIndexManager indexer = new FeatureIndexManager(context, geoPackage, featureDao);
                boolean indexed = indexer.isIndexed();
                int minZoom = 0;
                if (indexed) {
                    minZoom = featureDao.getZoomLevel() + context.getResources().getInteger(R.integer.geopackage_feature_tiles_min_zoom_offset);
                    minZoom = Math.max(minZoom, 0);
                    minZoom = Math.min(minZoom, GeoPackageFeatureTableCacheOverlay.MAX_ZOOM);
                }
                GeoPackageFeatureTableCacheOverlay tableCache = new GeoPackageFeatureTableCacheOverlay(tableCacheName, database, tableName, count, minZoom, indexed, geometryType);

                // If indexed, check for linked tile tables
                if(indexed){
                    List<String> linkedTileTables = linker.getTileTablesForFeatureTable(tableName);
                    for(String linkedTileTable: linkedTileTables){
                        // Get the tile table cache overlay
                        GeoPackageTileTableCacheOverlay tileCacheOverlay = tileCacheOverlays.get(linkedTileTable);
                        if(tileCacheOverlay != null){
                            // Remove from tile cache overlays so the tile table is not added as stand alone, and add to the linked overlays
                            tileCacheOverlays.remove(linkedTileTable);
                            linkedTileCacheOverlays.put(linkedTileTable, tileCacheOverlay);
                        }else{
                            // Another feature table may already be linked to this table, so check the linked overlays
                            tileCacheOverlay = linkedTileCacheOverlays.get(linkedTileTable);
                        }

                        // Add the linked tile table to the feature table
                        if(tileCacheOverlay != null){
                            tableCache.addLinkedTileTable(tileCacheOverlay);
                        }
                    }
                }

                tables.add(tableCache);
            }

            // Add stand alone tile tables that were not linked to feature tables
            tables.addAll(tileCacheOverlays.values());

            // Create the GeoPackage overlay with child tables
            cacheOverlay = new GeoPackageCacheOverlay(database, tables);
        } catch (Exception e) {
            Log.e(LOG_NAME, "Could not get geopackage cache", e);
        } finally {
            if (geoPackage != null) {
                geoPackage.close();
            }
        }

        return cacheOverlay;
    }


    /**
     * Delete the GeoPackage cache overlay
     * @param geoPackageCacheOverlay
     *
     * TODO: this was originally in TileOverlayPreferenceActivity to handle deleting on long press
     * this logic to go searching through directories to delete the cache file should be reworked
     */
    private void deleteGeoPackageCacheOverlay(GeoPackageCacheOverlay geoPackageCacheOverlay){

        String database = geoPackageCacheOverlay.getOverlayName();

        // Get the GeoPackage file
        GeoPackageManager manager = GeoPackageFactory.getManager(context);
        File path = manager.getFile(database);

        // Delete the cache from the GeoPackage manager
        manager.delete(database);

        // Attempt to delete the cache file if it is in the cache directory
        File pathDirectory = path.getParentFile();
        if(path.canWrite() && pathDirectory != null) {
            Map<StorageUtility.StorageType, File> storageLocations = StorageUtility.getWritableStorageLocations();
            for (File storageLocation : storageLocations.values()) {
                File root = new File(storageLocation, context.getString(R.string.overlay_cache_directory));
                if (root.equals(pathDirectory)) {
                    path.delete();
                    break;
                }
            }
        }

        // Check internal/external application storage
        File applicationCacheDirectory = DefaultCacheLocationProvider.getApplicationCacheDirectory(context);
        if (applicationCacheDirectory != null && applicationCacheDirectory.exists()) {
            for (File cache : applicationCacheDirectory.listFiles()) {
                if (cache.equals(path)) {
                    path.delete();
                    break;
                }
            }
        }
    }
}
