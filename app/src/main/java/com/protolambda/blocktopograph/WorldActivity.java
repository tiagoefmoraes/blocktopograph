package com.protolambda.blocktopograph;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.text.Editable;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.support.design.widget.NavigationView;

import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.protolambda.blocktopograph.map.CustomMarkerManager;
import com.protolambda.blocktopograph.map.Dimension;
import com.protolambda.blocktopograph.map.marker.AbstractMarker;
import com.protolambda.blocktopograph.nbt.convert.NBTConstants;
import com.protolambda.blocktopograph.nbt.tags.FloatTag;
import com.protolambda.blocktopograph.nbt.tags.IntTag;
import com.protolambda.blocktopograph.nbt.tags.ListTag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import com.protolambda.blocktopograph.chunk.ChunkData;
import com.protolambda.blocktopograph.chunk.NBTChunkData;
import com.protolambda.blocktopograph.chunk.RegionDataType;
import com.protolambda.blocktopograph.map.MapFragment;
import com.protolambda.blocktopograph.map.renderer.MapType;
import com.protolambda.blocktopograph.nbt.EditableNBT;
import com.protolambda.blocktopograph.nbt.EditorFragment;
import com.protolambda.blocktopograph.nbt.convert.DataConverter;
import com.protolambda.blocktopograph.nbt.tags.CompoundTag;
import com.protolambda.blocktopograph.nbt.tags.Tag;

public class WorldActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener, WorldActivityInterface {

    private World world;

    private MapFragment mapFragment;

    private FirebaseAnalytics mFirebaseAnalytics;

    synchronized public FirebaseAnalytics getFirebaseAnalytics() {
        if (mFirebaseAnalytics == null) {
            mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

            //don't measure the test devices in analytics
            mFirebaseAnalytics.setAnalyticsCollectionEnabled(!BuildConfig.DEBUG);
        }
        return mFirebaseAnalytics;
    }

    // Firebase events, these are meant to be as anonymous as possible,
    //  pure counters for usage analytics.
    // Do not remove! Removing analytics in a fork skews the results to the original userbase!
    // Forks should stay in touch, all new features are welcome.
    public enum CustomFirebaseEvent {

        //max 32 chars:     "0123456789abcdef0123456789abcdef"
        MAPFRAGMENT_OPEN(   "map_fragment_open"),
        MAPFRAGMENT_RESUME( "map_fragment_resume"),
        MAPFRAGMENT_RESET(  "map_fragment_reset"),
        NBT_EDITOR_OPEN(    "nbt_editor_open"),
        NBT_EDITOR_SAVE(    "nbt_editor_save"),
        WORLD_OPEN(         "world_open"),
        WORLD_RESUME(       "world_resume"),
        GPS_PLAYER(         "gps_player"),
        GPS_MULTIPLAYER(    "gps_multiplayer"),
        GPS_SPAWN(          "gps_spawn"),
        GPS_MARKER(         "gps_marker"),
        GPS_COORD(          "gps_coord");

        public final String eventID;

        CustomFirebaseEvent(String eventID){
            this.eventID = eventID;
        }
    }

    @Override
    public void logFirebaseEvent(CustomFirebaseEvent firebaseEvent){
        getFirebaseAnalytics().logEvent(firebaseEvent.eventID, new Bundle());
    }

    @Override
    public void logFirebaseEvent(CustomFirebaseEvent firebaseEvent, Bundle eventContent){
        getFirebaseAnalytics().logEvent(firebaseEvent.eventID, eventContent);
    }

    @Override
    public void showActionBar() {
        ActionBar bar = getSupportActionBar();
        if(bar != null) bar.show();
    }

    @Override
    public void hideActionBar() {
        ActionBar bar = getSupportActionBar();
        if(bar != null) bar.hide();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // immersive fullscreen for Android Kitkat and higher
        if (hasFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            ActionBar bar = getSupportActionBar();
            if(bar != null) bar.hide();
            this.getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d("World activity creating...");

        super.onCreate(savedInstanceState);


        /*
        Retrieve world from previous state or intent
         */
        this.world = (World) (savedInstanceState == null
                ? getIntent().getSerializableExtra(World.ARG_WORLD_SERIALIZED)
                : savedInstanceState.getSerializable(World.ARG_WORLD_SERIALIZED));
        if(world == null){
            //WTF, try going back to the previous screen by finishing this hopeless activity...
            finish();
        }


        /*
                Layout
         */
        setContentView(R.layout.activity_world);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        assert toolbar != null;
        setSupportActionBar(toolbar);

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        assert navigationView != null;
        navigationView.setNavigationItemSelectedListener(this);

        // Main drawer, quick access to different menus, tools and map-types.
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        assert drawer != null;
        drawer.setDrawerListener(toggle);
        toggle.syncState();




        View headerView = navigationView.getHeaderView(0);
        assert headerView != null;

        // Title = world-name
        TextView title = (TextView) headerView.findViewById(R.id.world_drawer_title);
        assert title != null;
        title.setText(this.world.getWorldDisplayName());

        // Subtitle = world-seed (Tap worldseed to choose to copy it)
        TextView subtitle = (TextView) headerView.findViewById(R.id.world_drawer_subtitle);
        assert subtitle != null;

        /*
            World-seed analytics.

            Send world-seed to the Firebase (Google analytics for Android) server.
            This data will be pushed to Google-BigQuery.
            Google-BigQuery will crunch the world-seeds, sorting hundreds of thousands world-seeds.
            The goal is to automatically create a "Top 1000" popular seeds for every week.
            This "Top 1000" will be published as soon as it gets out of BigQuery,
             keep it for the sake of this greater goal. It barely uses internet bandwidth,
             and this makes any forced intrusive revenue alternatives unnecessary.

            TODO BigQuery is not configured yet,
             @protolambda (author of Blocktopograph) is working on it!

            *link to results will be included here for reference when @protolambda is done*
         */
        String worldSeed = String.valueOf(this.world.getWorldSeed());
        subtitle.setText(worldSeed);

        Bundle bundle = new Bundle();
        bundle.putString("seed", worldSeed);



        // anonymous global counter of opened worlds
        logFirebaseEvent(CustomFirebaseEvent.WORLD_OPEN, bundle);



        // Open the world-map as default content
        openWorldMap();


        try {
            //try to load world-data (Opens chunk-database for later usage)
            this.world.getWorldData().load();
        } catch (WorldData.WorldDataLoadException e) {
            finish();
            e.printStackTrace();
        }

        Log.d("World activity created");
    }

    @Override
    public void onStart(){
        Log.d("World activity starting...");
        super.onStart();
        Log.d("World activity started");
    }

    @Override
    public void onResume(){
        Log.d("World activity resuming...");
        super.onResume();

        // anonymous global counter of resumed world-activities
        logFirebaseEvent(CustomFirebaseEvent.WORLD_RESUME);

        try {
            this.world.resume();
        } catch (WorldData.WorldDBException e) {
            this.onFatalDBError(e);
        }

        Log.d("World activity resumed");
    }

    @Override
    public void onPause(){
        Log.d("World activity pausing...");
        super.onPause();

        try {
            this.world.pause();
        } catch (WorldData.WorldDBException e) {
            e.printStackTrace();
        }

        Log.d("World activity paused");
    }

    @Override
    public void onStop(){
        Log.d("World activity stopping...");
        super.onStop();
        Log.d("World activity stopped");
    }

    @Override
    public void onDestroy(){
        Log.d("World activity destroying...");
        super.onDestroy();
        Log.d("World activity destroyed...");
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        assert drawer != null;
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
            return;
        }

        final FragmentManager manager = getFragmentManager();
        int count = manager.getBackStackEntryCount();

        // No opened fragments, so it is using the default fragment
        // Ask the user if he/she wants to close the world.
        if (count == 0) {

            new AlertDialog.Builder(this)
                .setMessage(R.string.ask_close_world)
                .setCancelable(false)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        WorldActivity.this.finish();
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show();

        } else if(confirmContentClose != null){
            //An important fragment is opened,
            // something that couldn't be reopened in its current state easily,
            // ask the user if he/she intended to close it.
            new AlertDialog.Builder(this)
                .setMessage(confirmContentClose)
                .setCancelable(false)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        manager.popBackStack();
                        confirmContentClose = null;
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
        } else {
            //fragment is open, but it may be closed without warning
            manager.popBackStack();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.world, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        //some text pop-up dialogs, some with simple HTML tags.
        //TODO refactor into text-dialog utility (with boolean parameter for HTML content)
        switch (item.getItemId()) {
            case R.id.action_about: {

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                TextView msg = new TextView(this);
                float dpi = WorldActivity.this.getResources().getDisplayMetrics().density;
                msg.setPadding((int)(19*dpi), (int)(5*dpi), (int)(14*dpi), (int)(5*dpi));
                msg.setMaxLines(20);
                msg.setMovementMethod(LinkMovementMethod.getInstance());
                msg.setText(R.string.app_about);
                builder.setView(msg)
                        .setTitle(R.string.action_about)
                        .setCancelable(true)
                        .setNeutralButton(android.R.string.ok, null)
                        .show();

                return true;
            }
            case R.id.action_help: {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                TextView msg = new TextView(this);
                float dpi = WorldActivity.this.getResources().getDisplayMetrics().density;
                msg.setPadding((int)(19*dpi), (int)(5*dpi), (int)(14*dpi), (int)(5*dpi));
                msg.setMaxLines(20);
                msg.setMovementMethod(LinkMovementMethod.getInstance());
                msg.setText(R.string.app_help);
                builder.setView(msg)
                        .setTitle(R.string.action_help)
                        .setCancelable(true)
                        .setNeutralButton(android.R.string.ok, null)
                        .show();

                return true;
            }
            case R.id.action_changelog: {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                TextView msg = new TextView(this);
                float dpi = WorldActivity.this.getResources().getDisplayMetrics().density;
                msg.setPadding((int)(19*dpi), (int)(5*dpi), (int)(14*dpi), (int)(5*dpi));
                msg.setMaxLines(20);
                msg.setMovementMethod(LinkMovementMethod.getInstance());
                String content = String.format(getResources().getString(R.string.app_changelog), BuildConfig.VERSION_NAME);
                //noinspection deprecation
                msg.setText(Html.fromHtml(content));
                builder.setView(msg)
                        .setTitle(R.string.action_changelog)
                        .setCancelable(true)
                        .setNeutralButton(android.R.string.ok, null)
                        .show();

                return true;
            }
            default: {
                return super.onOptionsItemSelected(item);
            }
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        Log.d("World activity nav-drawer menu item selected: " + id);


        final DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        assert drawer != null;


        switch (id){
            case(R.id.nav_world_show_map):
                changeContentFragment(new OpenFragmentCallback() {
                    @Override
                    public void onOpen() {
                        openWorldMap();
                    }
                });
                break;
            case(R.id.nav_world_select):
                //close activity; back to world selection screen
                closeWorldActivity();
                break;
            case(R.id.nav_singleplayer_nbt):
                openPlayerEditor();
                break;
            case(R.id.nav_multiplayer_nbt):
                openMultiplayerEditor();
                break;
            /*case(R.id.nav_inventory):
                //TODO go to inventory editor
                //This feature is planned, but not yet implemented,
                // use the generic NBT editor for now...
                break;*/
            case(R.id.nav_world_nbt):
                openLevelEditor();
                break;
            /*case(R.id.nav_tools):
                //TODO open tools menu (world downloader/importer/exporter maybe?)
                break;*/
            case(R.id.nav_overworld_satellite):
                changeMapType(MapType.SATELLITE, Dimension.OVERWORLD);
                break;
            case(R.id.nav_overworld_cave):
                changeMapType(MapType.CAVE, Dimension.OVERWORLD);
                break;
            case(R.id.nav_overworld_slime_chunk):
                changeMapType(MapType.SLIME_CHUNK, Dimension.OVERWORLD);
                break;
            /*case(R.id.nav_overworld_debug):
                changeMapType(MapType.DEBUG); //for debugging tiles positions, rendering, etc.
                break;*/
            case(R.id.nav_overworld_heightmap):
                changeMapType(MapType.HEIGHTMAP, Dimension.OVERWORLD);
                break;
            case(R.id.nav_overworld_biome):
                changeMapType(MapType.BIOME, Dimension.OVERWORLD);
                break;
            case(R.id.nav_overworld_grass):
                changeMapType(MapType.GRASS, Dimension.OVERWORLD);
                break;
            case(R.id.nav_overworld_xray):
                changeMapType(MapType.XRAY, Dimension.OVERWORLD);
                break;
            case(R.id.nav_overworld_block_light):
                changeMapType(MapType.BLOCK_LIGHT, Dimension.OVERWORLD);
                break;
            case(R.id.nav_nether_map):
                changeMapType(MapType.NETHER, Dimension.NETHER);
                break;
            case(R.id.nav_nether_xray):
                changeMapType(MapType.XRAY, Dimension.NETHER);
                break;
            case(R.id.nav_nether_block_light):
                changeMapType(MapType.BLOCK_LIGHT, Dimension.NETHER);
                break;
            case(R.id.nav_map_opt_toggle_grid):
                //toggle the grid
                this.showGrid = !this.showGrid;
                //rerender tiles (tiles will render with toggled grid on it now)
                if(this.mapFragment != null) this.mapFragment.resetTileView();
                break;
            case(R.id.nav_map_opt_toggle_markers):
                //toggle markers
                if(this.mapFragment != null) this.mapFragment.toggleMarkers();
                break;
            case(R.id.nav_biomedata_nbt):
                changeContentFragment(new OpenFragmentCallback() {
                    @Override
                    public void onOpen() {
                        openSpecialDBEntry(World.SpecialDBEntryType.BIOME_DATA);
                    }
                });
                break;
            case(R.id.nav_overworld_nbt):
                changeContentFragment(new OpenFragmentCallback() {
                    @Override
                    public void onOpen() {
                        openSpecialDBEntry(World.SpecialDBEntryType.OVERWORLD);
                    }
                });
                break;
            case(R.id.nav_villages_nbt):
                changeContentFragment(new OpenFragmentCallback() {
                    @Override
                    public void onOpen() {
                        openSpecialDBEntry(World.SpecialDBEntryType.M_VILLAGES);
                    }
                });
                break;
            case(R.id.nav_portals_nbt):
                changeContentFragment(new OpenFragmentCallback() {
                    @Override
                    public void onOpen() {
                        openSpecialDBEntry(World.SpecialDBEntryType.PORTALS);
                    }
                });
                break;
            case(R.id.nav_open_nbt_by_name): {

                //TODO put this bit in its own method
                //TODO not translation-friendly yet

                final EditText keyEditText = new EditText(WorldActivity.this);
                keyEditText.setEms(16);
                keyEditText.setMaxEms(32);
                keyEditText.setHint(R.string.leveldb_key_here);

                new AlertDialog.Builder(WorldActivity.this)
                        .setTitle(R.string.open_nbt_from_db)
                        .setView(keyEditText)
                        .setPositiveButton(R.string.open, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                changeContentFragment(new OpenFragmentCallback() {
                                    @Override
                                    public void onOpen() {
                                        Editable keyNameEditable = keyEditText.getText();
                                        String keyName = keyNameEditable == null
                                                ? null : keyNameEditable.toString();
                                        if(keyName == null || keyName.equals("")){
                                            Snackbar.make(drawer,
                                                    R.string.invalid_keyname,
                                                    Snackbar.LENGTH_LONG)
                                                    .setAction("Action", null).show();
                                        } else {
                                            try {
                                                EditableNBT dbEntry = openEditableNbtDbEntry(keyName);
                                                if (dbEntry == null) Snackbar.make(drawer,
                                                        R.string.cannot_find_db_entry_with_name,
                                                        Snackbar.LENGTH_LONG)
                                                        .setAction("Action", null).show();//TODO maybe add option to create it?
                                                else openNBTEditor(dbEntry);
                                            } catch (Exception e){
                                                Snackbar.make(drawer,
                                                        R.string.invalid_keyname,
                                                        Snackbar.LENGTH_LONG)
                                                        .setAction("Action", null).show();
                                            }
                                        }
                                    }
                                });
                            }
                        })
                        .setCancelable(true)
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();

                break;
            }
            default:
                //Warning, we might have messed with the menu XML!
                Log.w("pressed unknown navigation-item in world-activity-drawer");
                break;
        }

        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * Short-hand for opening special entries with openEditableNbtDbEntry(keyName)
     */
    public EditableNBT openSpecialEditableNbtDbEntry(final World.SpecialDBEntryType entryType)
            throws IOException {
        return openEditableNbtDbEntry(entryType.keyName);
    }

    /**
     * Load NBT data of this key from the database, converting it into structured Java Objects.
     * These objects are wrapped in a nice EditableNBT, ready for viewing and editing.
     *
     * @param keyName Key corresponding with NBT data in the database.
     * @return EditableNBT, NBT wrapper of NBT objects to view or to edit.
     * @throws IOException when database fails.
     */
    public EditableNBT openEditableNbtDbEntry(final String keyName) throws IOException {
        final byte[] keyBytes = keyName.getBytes(NBTConstants.CHARSET);
        WorldData worldData = world.getWorldData();
        try {
            worldData.openDB();
        } catch (Exception e){
            e.printStackTrace();
        }
        byte[] entryData = worldData.db.get(keyBytes);
        if(entryData == null) return null;

        final ArrayList<Tag> workCopy = DataConverter.read(entryData);

        return new EditableNBT() {

            @Override
            public Iterable<Tag> getTags() {
                return workCopy;
            }

            @Override
            public boolean save() {
                try {
                    WorldData wData = world.getWorldData();
                    wData.openDB();
                    wData.db.put(keyBytes, DataConverter.write(workCopy));
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return false;
            }

            @Override
            public String getRootTitle() {
                return keyName;
            }

            @Override
            public void addRootTag(Tag tag) {
                workCopy.add(tag);
            }

            @Override
            public void removeRootTag(Tag tag) {
                workCopy.remove(tag);
            }
        };


    }

    //returns an editableNBT, where getTags() provides a compound tag as item with player-data

    /**
     * Loads local player data "~local-player" or level.dat>"Player" into an EditableNBT.
     * @return EditableNBT, local player NBT data wrapped in a handle to use for saving + metadata
     * @throws Exception
     */
    public EditableNBT getEditablePlayer() throws Exception {

        /*
                Logic path:
                1. try to find the player-data in the db:
                        if found -> return that
                        else -> go to 2
                2. try to find the player-data in the level.dat:
                        if found -> return that
                        else -> go to 3
                3. no player-data available: warn the user
         */

        EditableNBT editableNBT;
        try {
            editableNBT = openSpecialEditableNbtDbEntry(World.SpecialDBEntryType.LOCAL_PLAYER);
        } catch (IOException e){
            e.printStackTrace();
            throw new Exception("Failed to read \"~local_player\" from the database.");
        }

        //check if it is not found in the DB
        if(editableNBT == null) editableNBT = openEditableNbtLevel("Player");

        //check if it is not found in level.dat as well
        if(editableNBT == null) throw new Exception("Failed to find \"~local_player\" in DB and \"Player\" in level.dat!");


        return editableNBT;

    }

    /** Open NBT editor fragment for special database entry */
    public void openSpecialDBEntry(final World.SpecialDBEntryType entryType){
        //TODO not translation-friendly
        try {
            EditableNBT editableEntry = openSpecialEditableNbtDbEntry(entryType);
            if(editableEntry == null) throw new Exception("\"" + entryType.keyName + "\" not found in DB.");

            Log.i("Opening NBT editor for \"" + entryType.keyName + "\" from world database.");

            openNBTEditor(editableEntry);

        } catch (Exception e) {
            e.printStackTrace();

            String msg = e.getMessage();
            if(e instanceof IOException) msg = String.format(getString(R.string.failed_to_read_x_from_db), entryType.keyName);

            new AlertDialog.Builder(WorldActivity.this)
                    .setMessage(msg)
                    .setCancelable(false)
                    .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            changeContentFragment(new OpenFragmentCallback() {
                                @Override
                                public void onOpen() {
                                    openWorldMap();
                                }
                            });
                        }
                    }).show();
        }
    }


    public void openMultiplayerEditor(){

        // TODO not translation-friendly

        //takes some time to find all players...
        // TODO make more responsive
        // TODO maybe cache player keys for responsiveness?
        //   Or messes this too much with the first perception of present players?
        final String[] players = getWorld().getWorldData().getPlayers();

        final View content = WorldActivity.this.findViewById(R.id.world_content);
        if(players.length == 0){
            if(content != null) Snackbar.make(content,
                    R.string.no_multiplayer_data_found,
                    Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            return;
        }

        //spinner (drop-out list) of players;
        // just the database keys (loading each name from NBT could take an even longer time!)
        final Spinner spinner = new Spinner(this);
        ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, players);

        spinnerArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(spinnerArrayAdapter);


        //wrap layout in alert
        new AlertDialog.Builder(this)
                .setTitle(R.string.select_player)
                .setView(spinner)
                .setPositiveButton(R.string.open_nbt, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {

                        //new tag type
                        int spinnerIndex = spinner.getSelectedItemPosition();
                        String playerKey = players[spinnerIndex];

                        try {
                            final EditableNBT editableNBT = WorldActivity.this
                                    .openEditableNbtDbEntry(playerKey);

                            changeContentFragment(new OpenFragmentCallback() {
                                @Override
                                public void onOpen() {
                                    try {
                                        openNBTEditor(editableNBT);
                                    } catch (Exception e){
                                        new AlertDialog.Builder(WorldActivity.this)
                                            .setMessage(e.getMessage())
                                            .setCancelable(false)
                                            .setNeutralButton(android.R.string.ok,
                                                    new DialogInterface.OnClickListener() {
                                                public void onClick(DialogInterface dialog,
                                                                    int id) {
                                                    changeContentFragment(
                                                            new OpenFragmentCallback() {
                                                        @Override
                                                        public void onOpen() {
                                                            openWorldMap();
                                                        }
                                                    });
                                                }
                                            }).show();
                                    }

                                }
                            });

                        } catch (Exception e){
                            e.printStackTrace();
                            Log.d("Failed to open player entry in DB. key: "+playerKey);
                            if(content != null) Snackbar.make(content,
                                    String.format(getString(R.string.failed_read_player_from_db_with_key_x), playerKey),
                                    Snackbar.LENGTH_LONG)
                                    .setAction("Action", null).show();
                        }
                    }
                })
                //or alert is cancelled
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public void openPlayerEditor(){
        changeContentFragment(new OpenFragmentCallback() {
            @Override
            public void onOpen() {

                try {
                    openNBTEditor(getEditablePlayer());
                } catch (Exception e){
                    new AlertDialog.Builder(WorldActivity.this)
                            .setMessage(e.getMessage())
                            .setCancelable(false)
                            .setNeutralButton(android.R.string.ok,
                                    new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    changeContentFragment(new OpenFragmentCallback() {
                                        @Override
                                        public void onOpen() {
                                            openWorldMap();
                                        }
                                    });
                                }
                            }).show();
                }

            }
        });
    }

    /** Opens a editableNBT for just the subTag if it is not null.
     Opens the whole level.dat if subTag is null. **/
    public EditableNBT openEditableNbtLevel(String subTagName){

        //make a copy first, the user might not want to save changed tags.
        final CompoundTag workCopy = world.level.getDeepCopy();
        final ArrayList<Tag> workCopyContents;
        final String contentTitle;
        if(subTagName == null){
            workCopyContents = workCopy.getValue();
            contentTitle = "level.dat";
        }
        else{
            workCopyContents = new ArrayList<>();
            Tag subTag = workCopy.getChildTagByKey(subTagName);
            if(subTag == null) return null;
            workCopyContents.add(subTag);
            contentTitle = "level.dat>"+subTagName;
        }

        EditableNBT editableNBT = new EditableNBT() {

            @Override
            public Iterable<Tag> getTags() {
                return workCopyContents;
            }

            @Override
            public boolean save() {
                try {
                    //write a copy of the workCopy, the workCopy may be edited after saving
                    world.writeLevel(workCopy.getDeepCopy());
                    return true;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return false;
            }

            @Override
            public String getRootTitle() {
                return contentTitle;
            }

            @Override
            public void addRootTag(Tag tag) {
                workCopy.getValue().add(tag);
                workCopyContents.add(tag);
            }

            @Override
            public void removeRootTag(Tag tag) {
                workCopy.getValue().remove(tag);
                workCopyContents.remove(tag);
            }
        };

        //if this editable nbt is only a view of a sub-tag, not the actual root
        editableNBT.enableRootModifications = (subTagName != null);

        return editableNBT;
    }

    public void openLevelEditor(){
        changeContentFragment(new OpenFragmentCallback() {
            @Override
            public void onOpen() {
                openNBTEditor(openEditableNbtLevel(null));
            }
        });
    }

    //TODO the dimension should be derived from mapTypes.
    // E.g. split xray into xray-overworld and xray-nether, but still use the same [MapRenderer],
    //  splitting allows to pass more sophisticated use of [MapRenderer]s
    private Dimension dimension = Dimension.OVERWORLD;

    public Dimension getDimension(){
        return this.dimension;
    }

    private MapType mapType = dimension.defaultMapType;

    public MapType getMapType(){
        return this.mapType;
    }


    // TODO grid should be rendered independently of tiles, it could be faster and more responsive.
    // However, it does need to adjust itself to the scale and position of the map,
    //  which is not an easy task.
    public boolean showGrid = true;

    @Override
    public boolean getShowGrid() {
        return showGrid;
    }


    private boolean fatal = false;
    @Override
    public void onFatalDBError(WorldData.WorldDBException worldDBException) {

        //TODO not translation-friendly

        Log.d(worldDBException.getMessage());
        worldDBException.printStackTrace();

        //already dead? (happens on multiple onFatalDBError(e) calls)
        if(fatal) return;

        fatal = true;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.error_cannot_open_world_close_and_try_again)
                .setCancelable(false)
                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        WorldActivity.this.finish();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    public void changeMapType(MapType mapType, Dimension dimension){
        this.mapType = mapType;
        this.dimension = dimension;
        //don't forget to do a reset-tileview, the mapfragment should know of this change ASAP.
        mapFragment.resetTileView();
    }

    public void closeWorldActivity(){

        //TODO not translation-friendly

        new AlertDialog.Builder(this)
            .setMessage(R.string.confirm_close_world)
            .setCancelable(false)
            .setIcon(R.drawable.ic_action_exit)
            .setPositiveButton(android.R.string.yes,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            //finish this activity
                            finish();
                        }
                    })
            .setNegativeButton(android.R.string.no, null)
            .show();
    }

    public interface OpenFragmentCallback {
        void onOpen();
    }

    public String confirmContentClose = null;

    public void changeContentFragment(final OpenFragmentCallback callback){

        final FragmentManager manager = getFragmentManager();

        //TODO not translation-friendly
        // confirmContentClose shouldn't be both used as boolean and as close-message,
        //  this is a bad pattern
        if(confirmContentClose != null){
            new AlertDialog.Builder(this)
                .setMessage(confirmContentClose)
                .setCancelable(false)
                .setPositiveButton(android.R.string.yes,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            manager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                            callback.onOpen();
                        }
                    })
                .setNegativeButton(android.R.string.no, null)
                .show();
        } else {
            manager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            callback.onOpen();
        }

    }

    /** Open NBT editor fragment for the given editableNBT */
    public void openNBTEditor(EditableNBT editableNBT){

        //TODO not translation-friendly
        // see changeContentFragment(callback)

        this.confirmContentClose = getString(R.string.confirm_close_nbt_editor);

        EditorFragment editorFragment = new EditorFragment();
        editorFragment.setEditableNBT(editableNBT);

        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.world_content, editorFragment);
        transaction.addToBackStack(null);

        transaction.commit();

    }

    /** Replace current content fragment with a fresh MapFragment */
    public void openWorldMap(){

        //TODO should this use cached world-position etc.?

        this.confirmContentClose = null;
        this.mapFragment = new MapFragment();

        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.world_content, this.mapFragment);
        transaction.commit();

    }


    @Override
    public World getWorld() {
        return this.world;
    }


    @Override
    public void addMarker(AbstractMarker marker){
        mapFragment.addMarker(marker);
    }


    //TODO This stuff should be moved to the MapFragment class.
    //TODO not translation-friendly
    public enum LongClickOption {

        TELEPORT_LOCAL_PLAYER(R.string.teleport_local_player, null),
        CREATE_MARKER(R.string.create_custom_marker, null),
        //TODO TELEPORT_MULTI_PLAYER("Teleport other player", null),
        ENTITY(R.string.open_chunk_entity_nbt, RegionDataType.ENTITY),
        TILE_ENTITY(R.string.open_chunk_tile_entity_nbt, RegionDataType.TILE_ENTITY);

        public final int stringId;
        public final RegionDataType dataType;

        LongClickOption(int id, RegionDataType dataType){
            this.stringId = id;
            this.dataType = dataType;
        }

    }

    public String[] getLongClickOptions(){
        LongClickOption[] values = LongClickOption.values();
        int len = values.length;
        String[] options = new String[len];
        for(int i = 0; i < len; i++){
            options[i] = getString(values[i].stringId);
        }
        return options;
    }

    /** Open a dialog; user chooses chunk-type -> open editor for this type **/
    public void openChunkNBTEditor(final int chunkX, final int chunkZ, final ChunkData chunkData){

        //TODO we should start supporting some basic terrain data editing,
        // like a replace-block(s) function

        if(chunkData == null || !(chunkData instanceof NBTChunkData)){
            //should never happen
            Log.w("User tried to open non-nbt/null chunkData in the nbt-editor!!!");
            return;
        }

        final NBTChunkData nbtChunkData = (NBTChunkData) chunkData;

        final List<Tag> tags = nbtChunkData.tags;

        //open nbt editor for entity data
        changeContentFragment(new OpenFragmentCallback() {
            @Override
            public void onOpen() {

                //make a copy first, the user might not want to save changed tags.
                final List<Tag> workCopy = new ArrayList<>();
                for(Tag tag : tags){
                    workCopy.add(tag.getDeepCopy());
                }

                final EditableNBT editableChunkData = new EditableNBT() {

                    @Override
                    public Iterable<Tag> getTags() {
                        return workCopy;
                    }

                    @Override
                    public boolean save() {
                        try {
                            final List<Tag> saveCopy = new ArrayList<>();
                            for(Tag tag : workCopy){
                                saveCopy.add(tag.getDeepCopy());
                            }
                            nbtChunkData.tags = saveCopy;
                            world.saveChunkData(chunkX, chunkZ, chunkData);
                            return true;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        return false;
                    }

                    @Override
                    public String getRootTitle() {
                        final String format = "%s (cX:%d;cZ:%d)";
                        switch (chunkData.dataType){
                            case ENTITY: return String.format(format, getString(R.string.entity_chunk_data) , chunkX, chunkZ);
                            case TILE_ENTITY: return String.format(format, getString(R.string.tile_entity_chunk_data), chunkX, chunkZ);
                            default: return String.format(format, getString(R.string.nbt_chunk_data), chunkX, chunkZ);
                        }
                    }

                    @Override
                    public void addRootTag(Tag tag) {
                        workCopy.add(tag);
                    }

                    @Override
                    public void removeRootTag(Tag tag) {
                        workCopy.remove(tag);
                    }
                };

                openNBTEditor(editableChunkData);
            }
        });

    }


    //TODO move to MapFragment
    @Override
    public void onLongClick(final double worldX, final double worldZ){

        double chunkX = worldX / dimension.chunkW;
        double chunkZ = worldZ / dimension.chunkL;

        //negative doubles are rounded up when casting to int; floor them
        final int chunkXint = chunkX < 0 ? (((int) chunkX) - 1) : ((int) chunkX);
        final int chunkZint = chunkZ < 0 ? (((int) chunkZ) - 1) : ((int) chunkZ);

        final Dimension dim = dimension;


        final View container = this.findViewById(R.id.world_content);
        if(container == null){
            Log.w("CANNOT FIND MAIN CONTAINER, WTF");
            return;
        }

        //TODO not translation-friendly
        new AlertDialog.Builder(this)
                .setTitle(String.format(Locale.ENGLISH, "Pos: %.2f;%.2f Chunk: %d;%d Dimension: %s", worldX, worldZ, chunkXint, chunkZint, dimension.name))
                .setItems(getLongClickOptions(), new DialogInterface.OnClickListener() {
                    @SuppressLint("SetTextI18n")
                    public void onClick(DialogInterface dialog, int which) {

                        final LongClickOption chosen = LongClickOption.values()[which];

                        switch (chosen){
                            case TELEPORT_LOCAL_PLAYER:{
                                try {

                                    final EditableNBT playerEditable = getEditablePlayer();
                                    if(playerEditable == null) throw new Exception("Player is null");

                                    Iterator playerIter = playerEditable.getTags().iterator();
                                    if(!playerIter.hasNext()) throw new Exception("Player DB entry is empty!");

                                    //db entry consists of one compound tag
                                    final CompoundTag playerTag = (CompoundTag) playerIter.next();

                                    ListTag posVec = (ListTag) playerTag.getChildTagByKey("Pos");

                                    if (posVec == null) throw new Exception("No \"Pos\" specified");

                                    final List<Tag> playerPos = posVec.getValue();
                                    if(playerPos == null)
                                        throw new Exception("No \"Pos\" specified");
                                    if (playerPos.size() != 3)
                                        throw new Exception("\"Pos\" value is invalid. value: " + posVec.getValue().toString());

                                    final IntTag dimensionId = (IntTag) playerTag.getChildTagByKey("DimensionId");
                                    if(dimensionId == null || dimensionId.getValue() == null)
                                        throw new Exception("No \"DimensionId\" specified");


                                    float playerY = (float) playerPos.get(1).getValue();


                                    View yForm = LayoutInflater.from(WorldActivity.this).inflate(R.layout.y_coord_form, null);
                                    final EditText yInput = (EditText) yForm.findViewById(R.id.y_input);
                                    yInput.setText(String.valueOf(playerY));

                                    final float newX = (float) worldX;
                                    final float newZ = (float) worldZ;
                                    final Dimension newDimension = dimension;

                                    new AlertDialog.Builder(WorldActivity.this)
                                            .setTitle(chosen.stringId)
                                            .setView(yForm)
                                            .setPositiveButton("Teleport!", new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {

                                                    float newY;
                                                    Editable value = yInput.getText();
                                                    if(value == null) newY = 64f;
                                                    else {
                                                        try {
                                                            newY = (float) Float.parseFloat(value.toString());//removes excessive precision
                                                        } catch (Exception e){
                                                            newY = 64f;
                                                        }
                                                    }

                                                    ((FloatTag) playerPos.get(0)).setValue(newX);
                                                    ((FloatTag) playerPos.get(1)).setValue(newY);
                                                    ((FloatTag) playerPos.get(2)).setValue(newZ);
                                                    dimensionId.setValue(newDimension.id);

                                                    if(playerEditable.save()){

                                                        if(mapFragment != null)
                                                            mapFragment.moveMarker(
                                                                    mapFragment.localPlayerMarker,
                                                                    (int) newX, (int) newY, (int) newZ, newDimension);

                                                        Snackbar.make(container,
                                                                String.format(getString(R.string.teleported_player_to_xyz_dim), newX, newY, newZ, newDimension.name),
                                                                Snackbar.LENGTH_LONG)
                                                                .setAction("Action", null).show();
                                                    } else {
                                                        Snackbar.make(container,
                                                                R.string.failed_teleporting_player,
                                                                Snackbar.LENGTH_LONG)
                                                                .setAction("Action", null).show();
                                                    }
                                                }
                                            })
                                            .setCancelable(true)
                                            .setNegativeButton(android.R.string.cancel, null)
                                            .show();
                                    return;

                                } catch (Exception e){
                                    e.printStackTrace();
                                    Snackbar.make(container, R.string.failed_to_find_or_edit_local_player_data,
                                            Snackbar.LENGTH_LONG)
                                            .setAction("Action", null).show();
                                    return;
                                }
                            }
                            case CREATE_MARKER:{

                                View createMarkerForm = LayoutInflater.from(WorldActivity.this).inflate(R.layout.create_marker_form, null);

                                final EditText markerNameInput = (EditText) createMarkerForm.findViewById(R.id.marker_displayname_input);
                                markerNameInput.setText(R.string.default_custom_marker_name);
                                final EditText markerIconNameInput = (EditText) createMarkerForm.findViewById(R.id.marker_iconname_input);
                                markerIconNameInput.setText("blue_marker");
                                final EditText xInput = (EditText) createMarkerForm.findViewById(R.id.x_input);
                                xInput.setText(String.valueOf((int) worldX));
                                final EditText yInput = (EditText) createMarkerForm.findViewById(R.id.y_input);
                                yInput.setText(String.valueOf(64));
                                final EditText zInput = (EditText) createMarkerForm.findViewById(R.id.z_input);
                                zInput.setText(String.valueOf((int) worldZ));


                                new AlertDialog.Builder(WorldActivity.this)
                                        .setTitle(chosen.stringId)
                                        .setView(createMarkerForm)
                                        .setPositiveButton("Create marker", new DialogInterface.OnClickListener() {

                                            public void failParseSnackbarReport(int msg){
                                                Snackbar.make(container, msg,
                                                        Snackbar.LENGTH_LONG)
                                                        .setAction("Action", null).show();
                                            }

                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {

                                                try {
                                                    String displayName = markerNameInput.getText().toString();
                                                    if (displayName.equals("") || displayName.contains("\"")) {
                                                        failParseSnackbarReport(R.string.marker_invalid_name);
                                                        return;
                                                    }
                                                    String iconName = markerIconNameInput.getText().toString();
                                                    if (iconName.equals("") || iconName.contains("\"")) {
                                                        failParseSnackbarReport(R.string.invalid_icon_name);
                                                        return;
                                                    }

                                                    int xM, yM, zM;

                                                    String xStr = xInput.getText().toString();
                                                    try {
                                                        xM = Integer.parseInt(xStr);
                                                    } catch (NumberFormatException e) {
                                                        failParseSnackbarReport(R.string.invalid_x_coordinate);
                                                        return;
                                                    }
                                                    String yStr = yInput.getText().toString();
                                                    try {
                                                        yM = Integer.parseInt(yStr);
                                                    } catch (NumberFormatException e) {
                                                        failParseSnackbarReport(R.string.invalid_y_coordinate);
                                                        return;
                                                    }

                                                    String zStr = zInput.getText().toString();
                                                    try {
                                                        zM = Integer.parseInt(zStr);
                                                    } catch (NumberFormatException e) {
                                                        failParseSnackbarReport(R.string.invalid_z_coordinate);
                                                        return;
                                                    }

                                                    CustomMarkerManager mng = getWorld().getMarkerManager();
                                                    mng.addMarker(mng.markerFromData(displayName, iconName, xM, yM, zM, dimension));

                                                    if(mapFragment != null) mapFragment.reloadCustomMarkers();

                                                } catch (Exception e){
                                                    e.printStackTrace();
                                                    failParseSnackbarReport(R.string.failed_to_create_marker);
                                                }
                                            }
                                        })
                                        .setCancelable(true)
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .show();





                                return;
                            }
                            /* TODO multi player teleporting
                            case TELEPORT_MULTI_PLAYER:{

                                break;
                            }*/
                            case ENTITY:
                            case TILE_ENTITY:{

                                ChunkData chunkData;
                                try {
                                    chunkData = world.loadChunkData(chunkXint, chunkZint, chosen.dataType, dim);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    Snackbar.make(container, String.format(getString(R.string.failed_to_load_x), getString(chosen.stringId)),
                                            Snackbar.LENGTH_LONG)
                                            .setAction("Action", null).show();
                                    return;
                                }

                                //ask the user if he/she wants to create the data, if loading got nothing
                                if (chunkData == null) {

                                    new AlertDialog.Builder(WorldActivity.this)
                                            .setTitle(R.string.nbt_editor)
                                            .setMessage(R.string.data_does_not_exist_for_chunk_ask_if_create)
                                            .setIcon(R.drawable.ic_action_save_b)
                                            .setPositiveButton(android.R.string.yes,
                                                    new DialogInterface.OnClickListener() {
                                                        public void onClick(DialogInterface dialog, int whichButton) {
                                                            Snackbar.make(container, R.string.creating_and_saving_chunk_NBT_data, Snackbar.LENGTH_LONG)
                                                                    .setAction("Action", null).show();
                                                            ChunkData newChunkData = world.createEmptyChunkData(chunkXint, chunkZint, chosen.dataType, dim);
                                                            if (newChunkData != null) {
                                                                Snackbar.make(container, R.string.created_and_saved_chunk_NBT_data, Snackbar.LENGTH_LONG)
                                                                        .setAction("Action", null).show();
                                                                openChunkNBTEditor(chunkXint, chunkZint, newChunkData);
                                                            } else {
                                                                Snackbar.make(container, R.string.failed_to_create_or_save_chunk_NBT_data, Snackbar.LENGTH_LONG)
                                                                        .setAction("Action", null).show();
                                                            }
                                                        }
                                                    })
                                            .setNegativeButton(android.R.string.no, null)
                                            .show();

                                } else {

                                    //just open the editor if the data is there for us to edit it
                                    openChunkNBTEditor(chunkXint, chunkZint, chunkData);
                                }
                            }
                        }


                    }
                })
                .setCancelable(true)
                .setNegativeButton(android.R.string.cancel, null)
                .show();

    }

}