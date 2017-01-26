package com.oakonell.huematch;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.philips.lighting.hue.sdk.PHHueSDK;
import com.philips.lighting.model.PHBridge;
import com.philips.lighting.model.PHBridgeConfiguration;
import com.philips.lighting.model.PHGroup;
import com.philips.lighting.model.PHLight;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ControlledLightsActivity extends AppCompatActivity {
    private final String TAG = "ControlledLights";

    private RecyclerView lightsListView;

    private PHHueSDK phHueSDK;
    private RoomLightsAdapter lightsAdapter;
    private TextView bridgeNameView;
    private HueSharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controlled_lights);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

//        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
//        fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
//            }
//        });

        phHueSDK = PHHueSDK.create();

        Button okButton = (Button) findViewById(R.id.ok);
        Button cancelButton = (Button) findViewById(R.id.cancel);
        bridgeNameView = (TextView) findViewById(R.id.bridge_name);

        lightsListView = (RecyclerView) findViewById(R.id.lights_list);

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // save checked lights to prefs
                Set<String> result = new HashSet<String>();
                for (LightOrRoom each : lightsAdapter.list) {
                    if (each.getType() == Type.ROOM) continue;
                    Light light = (Light) each;
                    if (light.isControlled()) {
                        result.add(light.getId());
                    }
                }
                prefs.setControlledLightIds(result);

                finish();
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // exit without making any changes
                finish();
            }
        });

        lightsAdapter = new RoomLightsAdapter(this, new ArrayList<LightOrRoom>());
        lightsListView.setAdapter(lightsAdapter);
        lightsListView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        lightsListView.setHasFixedSize(false);

        DividerItemDecoration itemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        lightsListView.addItemDecoration(itemDecoration);

    }

    @Override
    protected void onResume() {
        super.onResume();

        final PHBridge bridge = phHueSDK.getSelectedBridge();

        final PHBridgeConfiguration bridgeConfiguration = bridge.getResourceCache().getBridgeConfiguration();
        String bridgeName = bridgeConfiguration.getName();

        bridgeNameView.setText(bridgeName);

        prefs = HueSharedPreferences.getInstance(getApplicationContext());
        Set<String> controlledIds = prefs.getControlledLightIds();

        List<LightOrRoom> list = new ArrayList<>();

        final Map<String, PHLight> lightMap = bridge.getResourceCache().getLights();

        final List<PHGroup> groups = bridge.getResourceCache().getAllGroups();
        for (PHGroup each : groups) {
            list.add(new Room(each));
            for (String lightId : each.getLightIdentifiers()) {
                final PHLight phLight = lightMap.get(lightId);
                Light light = new Light(phLight);
                list.add(light);
                if (controlledIds.contains(lightId)) {
                    light.setControlled(true);
                }
            }
        }

        lightsAdapter.list = list;
        lightsAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.w(TAG, "Inflating controlled lights menu");
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_controlled_lights, menu);
        return true;
    }

    /**
     * Called when option is selected.
     *
     * @param item the MenuItem object.
     * @return boolean Return false to allow normal menu processing to proceed,  true to consume it here.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
//            case R.id.action_settings:
//                Intent intent = new Intent(getApplicationContext(), ControlledLightsActivity.class);
//                startActivity(intent);
//                break;
        }
        return true;
    }

    @Override
    protected void onDestroy() {
        PHBridge bridge = phHueSDK.getSelectedBridge();
        if (bridge != null) {

            if (phHueSDK.isHeartbeatEnabled(bridge)) {
                phHueSDK.disableHeartbeat(bridge);
            }

            phHueSDK.disconnect(bridge);
            super.onDestroy();
        }
    }

    private abstract static class ItemViewHolder extends RecyclerView.ViewHolder {
        public ItemViewHolder(View itemView) {
            super(itemView);
        }

        public void bind(RoomLightsAdapter roomLightsAdapter, LightOrRoom lightOrRoom) {
        }
    }

    private static class LightViewHolder extends ItemViewHolder {
        private final TextView name;
        private final CheckBox checked;
        private final ImageView image;

        public LightViewHolder(View itemView) {
            super(itemView);
            checked = (CheckBox) itemView.findViewById(R.id.checked);
            image = (ImageView) itemView.findViewById(R.id.image);
            name = (TextView) itemView.findViewById(R.id.name);
        }

        public void bind(RoomLightsAdapter roomLightsAdapter, LightOrRoom lightOrRoom) {
            checked.setOnCheckedChangeListener(null);

            final Light light = (Light) lightOrRoom;
            name.setText(light.getName());
            image.setImageResource(light.getImageResource());
            if (!light.canBeControlled()) {
                checked.setChecked(false);
                checked.setEnabled(false);
            } else {
                checked.setEnabled(true);
                // handle checking the box
                checked.setChecked(light.isControlled());
                checked.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        light.setControlled(isChecked);
                    }
                });
            }
        }
    }

    private static class RoomViewHolder extends ItemViewHolder {
        private final TextView name;

        public RoomViewHolder(View itemView) {
            super(itemView);
            name = (TextView) itemView.findViewById(R.id.name);
        }

        public void bind(RoomLightsAdapter roomLightsAdapter, LightOrRoom lightOrRoom) {
            name.setText(lightOrRoom.getName());
            Room room = (Room) lightOrRoom;
            // TODO set room image based on group
            room.phGroup.getGroupClass();
        }

    }

    private static class RoomLightsAdapter extends RecyclerView.Adapter<ItemViewHolder> {
        private static final int VIEW_TYPE_ROOM = 1;
        private static final int VIEW_TYPE_LIGHT = 2;
        private final Activity activity;
        List<LightOrRoom> list;

        public RoomLightsAdapter(Activity activity, List<LightOrRoom> list) {
            this.list = list;
            this.activity = activity;
        }

        @Override
        public int getItemViewType(int position) {
            final LightOrRoom lightOrRoom = list.get(position);
            if (lightOrRoom.getType() == Type.ROOM) {
                return VIEW_TYPE_ROOM;
            }
            return VIEW_TYPE_LIGHT;
        }

        @Override
        public ItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == VIEW_TYPE_ROOM) {
                View newView = LayoutInflater.from(parent.getContext()).inflate(R.layout.room_item, parent, false);
                return new RoomViewHolder(newView);
            }
            View newView = LayoutInflater.from(parent.getContext()).inflate(R.layout.light_item, parent, false);
            return new LightViewHolder(newView);
        }

        @Override
        public void onBindViewHolder(ItemViewHolder holder, int position) {
            holder.bind(this, list.get(position));
        }

        @Override
        public int getItemCount() {
            return list.size();
        }
    }

    enum Type {LIGHT, ROOM}

    private abstract static class LightOrRoom {

        abstract Type getType();

        abstract String getName();
    }

    private class Light extends LightOrRoom {
        private final PHLight phLight;
        private final int imageResource;
        private final boolean controllable;
        private boolean controlled;

        Light(PHLight phLight) {
            this.phLight = phLight;
            switch (phLight.getLightType()) {
                case DIM_LIGHT:
                    imageResource = R.drawable.dimming_bulb;
                    controllable = false;
                    break;
                case CT_LIGHT:
                    // TODO perhaps a CT bulb can be handled?
                    imageResource = R.drawable.dimming_bulb;
                    controllable = false;
                    break;
                case COLOR_LIGHT:
                case CT_COLOR_LIGHT:
                    imageResource = R.drawable.color_bulb;
                    controllable = true;
                    break;
                case ON_OFF_LIGHT:
                    imageResource = R.drawable.white_e27;
                    controllable = false;
                    break;
                case UNKNOWN_LIGHT:
                    imageResource = R.drawable.white_e27;
                    controllable = false;
                    break;
                default:
                    imageResource = R.drawable.white_e27;
                    controllable = false;
                    break;

            }
        }

        @Override
        Type getType() {
            return Type.LIGHT;
        }

        @Override
        String getName() {
            return phLight.getName();
        }

        public boolean isControlled() {
            return controlled;
        }

        public void setControlled(boolean controlled) {
            this.controlled = controlled;
        }

        public String getId() {
            return phLight.getIdentifier();
        }

        public boolean canBeControlled() {
            // some lights can't be controlled- none color, or non dimming..?
            return controllable;
        }

        public int getImageResource() {
            return imageResource;
        }
    }


    private class Room extends LightOrRoom {
        private final PHGroup phGroup;

        Room(PHGroup phRoom) {
            this.phGroup = phRoom;
        }

        @Override
        Type getType() {
            return Type.ROOM;
        }

        @Override
        String getName() {
            return phGroup.getName();
        }
    }
}
