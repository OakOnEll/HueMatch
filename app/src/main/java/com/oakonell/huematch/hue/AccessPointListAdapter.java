package com.oakonell.huematch.hue;


import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.oakonell.huematch.R;
import com.philips.lighting.hue.sdk.PHAccessPoint;
import com.philips.lighting.hue.sdk.PHHueSDK;
import com.philips.lighting.model.PHBridge;
import com.philips.lighting.model.PHBridgeConfiguration;
import com.philips.lighting.model.PHGroup;

import java.util.List;

/**
 * This class provides adapter view for a list of Found Bridges.
 *
 * @author SteveyO.
 */
public class AccessPointListAdapter extends BaseAdapter {
    private final LayoutInflater mInflater;
    private final PHHueSDK hueSdk;
    private List<PHAccessPoint> accessPoints;

    /**
     * View holder class for access point list.
     *
     * @author SteveyO.
     */
    class BridgeListItem {
        private TextView bridgeIp;
        private TextView bridgeMac;
        private TextView rooms;
    }

    /**
     * creates instance of {@link AccessPointListAdapter} class.
     *
     * @param context      the Context object.
     * @param accessPoints an array list of {@link PHAccessPoint} object to display.
     */
    public AccessPointListAdapter(Context context, PHHueSDK hueSdk, List<PHAccessPoint> accessPoints) {
        // Cache the LayoutInflate to avoid asking for a new one each time.
        mInflater = LayoutInflater.from(context);
        this.accessPoints = accessPoints;
        this.hueSdk = hueSdk;
    }

    /**
     * Get a View that displays the data at the specified position in the data set.
     *
     * @param position    The row index.
     * @param convertView The row view.
     * @param parent      The view group.
     * @return A View corresponding to the data at the specified position.
     */
    @SuppressLint("InflateParams")
    public View getView(final int position, View convertView, ViewGroup parent) {
        BridgeListItem item;

        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.selectbridge_item, null);

            item = new BridgeListItem();
            item.bridgeMac = (TextView) convertView.findViewById(R.id.bridge_mac);
            item.bridgeIp = (TextView) convertView.findViewById(R.id.bridge_ip);
            item.rooms = (TextView) convertView.findViewById(R.id.rooms);

            convertView.setTag(item);
        } else {
            item = (BridgeListItem) convertView.getTag();
        }

        PHAccessPoint accessPoint = accessPoints.get(position);

        String bridgeId = accessPoint.getBridgeId();

        StringBuilder groupsBuilder = new StringBuilder();
        // Huh, this doesn't do anything, probably because not "authenticated"
        for (PHBridge each : hueSdk.getAllBridges()) {
            final PHBridgeConfiguration configuration = each.getResourceCache().getBridgeConfiguration();
            if (!configuration.getBridgeID().equals(bridgeId)) continue;

            boolean first = true;
            for (PHGroup group : each.getResourceCache().getAllGroups()) {
                if (!first) groupsBuilder.append(", ");
                groupsBuilder.append(group.getName());
                first = false;
            }
        }

        item.bridgeIp.setTextColor(Color.BLACK);
        item.bridgeIp.setText(accessPoint.getIpAddress());
        item.bridgeMac.setTextColor(Color.DKGRAY);
        item.bridgeMac.setText(accessPoint.getMacAddress());
        item.rooms.setTextColor(Color.DKGRAY);
        item.rooms.setText(groupsBuilder.toString());

        return convertView;
    }

    /**
     * Get the row id associated with the specified position in the list.
     *
     * @param position The row index.
     * @return The id of the item at the specified position.
     */
    @Override
    public long getItemId(int position) {
        return 0;
    }

    /**
     * How many items are in the data set represented by this Adapter.
     *
     * @return Count of items.
     */
    @Override
    public int getCount() {
        return accessPoints.size();
    }

    /**
     * Get the data item associated with the specified position in the data set.
     *
     * @param position Position of the item whose data we want within the adapter's data set.
     * @return The data at the specified position.
     */
    @Override
    public Object getItem(int position) {
        return accessPoints.get(position);
    }

    /**
     * Update date of the list view and refresh listview.
     *
     * @param accessPoints An array list of {@link PHAccessPoint} objects.
     */
    public void updateData(List<PHAccessPoint> accessPoints) {
        this.accessPoints = accessPoints;
        notifyDataSetChanged();
    }

}