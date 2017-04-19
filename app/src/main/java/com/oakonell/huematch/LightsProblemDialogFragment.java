package com.oakonell.huematch;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.philips.lighting.hue.sdk.PHHueSDK;
import com.philips.lighting.model.PHBridge;
import com.philips.lighting.model.PHLight;
import com.philips.lighting.model.PHLightState;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Created by Rob on 2/3/2017.
 */

public class LightsProblemDialogFragment extends AppCompatDialogFragment {
    private static final String ARG_TYPE = "type";
    private static final String ARG_WAIT = "waitForUpdate";
    private static final String ARG_ERROR = "error_text";
    private PHHueSDK phHueSDK;
    private Set<String> controlledIds;
    private View turnOnLightsButton;
    private View okButton;
    private TextView unreachable_lights_TextView;
    private TextView off_lights_TextView;
    private View unreachable_layout;
    private View off_layout;
    private Collection<PHLight> okLights;
    private View wait;
    private View info_layout;

    public static LightsProblemDialogFragment create(HueMatcherActivity.CaptureState state, boolean waitForUpdate, String errorString) {
        LightsProblemDialogFragment dialog = new LightsProblemDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TYPE, state.toString());
        args.putBoolean(ARG_WAIT, waitForUpdate);
        args.putString(ARG_ERROR, errorString);
        dialog.setArguments(args);

        return dialog;
    }

    public int getTheme() {
        return R.style.MyDialogTheme;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        getDialog().setTitle(R.string.light_issues_dialog);

        View view = inflater.inflate(R.layout.dialog_lights_problem, container);

        HueMatcherActivity activity = getMainActivity();
        phHueSDK = activity.getPhHueSDK();
        controlledIds = activity.getControlledIds();
        View cancelButton = view.findViewById(R.id.cancel);
        turnOnLightsButton = view.findViewById(R.id.turn_on_lights);
        okButton = view.findViewById(R.id.ok);

        unreachable_lights_TextView = (TextView) view.findViewById(R.id.unreachable);
        unreachable_layout = view.findViewById(R.id.unreachable_layout);
        off_lights_TextView = (TextView) view.findViewById(R.id.off);
        off_layout = view.findViewById(R.id.off_layout);
        wait = view.findViewById(R.id.wait);
        info_layout = view.findViewById(R.id.info_layout);

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cleanup();
                dismiss();
            }
        });

        turnOnLightsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                turnOnLights();
            }
        });

        okButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cleanup();
                continueAsIs(okLights);
            }
        });

        boolean waitForUpdate = getArguments().getBoolean(ARG_WAIT);
        String errorText = getArguments().getString(ARG_ERROR);
        TextView error_text_view = (TextView) view.findViewById(R.id.error_text);
        if (errorText != null) {
            error_text_view.setText(getString(R.string.light_error_toast_prefix) + errorText);
        }

        if (!waitForUpdate) {
            configure();
        } else {
            wait.setVisibility(View.VISIBLE);
            info_layout.setVisibility(View.INVISIBLE);

            okButton.setEnabled(false);
            turnOnLightsButton.setEnabled(false);
        }

        getMainActivity().setBridgeUpdateListener(new HueMatcherActivity.BridgeUpdateListener() {
            @Override
            public void onCacheUpdated() {
                configure();
            }
        });

        return view;
    }


    private void configure() {
        PHBridge bridge = phHueSDK.getSelectedBridge();
        Map<String, PHLight> lightsMap = bridge.getResourceCache().getLights();

        // accumulate lights off, prompt to turn on
        // accumulate unreachable_lights_TextView lights
        // assure that at least one light is reachable/on
        final Collection<PHLight> offLights = new HashSet<>();
        final Collection<PHLight> unreachableLights = new HashSet<>();
        okLights = new HashSet<>();

        final StringBuilder offLightsBuilder = new StringBuilder("   ");
        final StringBuilder unreachableLightsBuilder = new StringBuilder("   ");

        for (String id : controlledIds) {
            PHLight light = lightsMap.get(id);
            final PHLightState state = light.getLastKnownLightState();
            if (!state.isOn()) {
                if (!offLights.isEmpty()) {
                    offLightsBuilder.append("\n,   ");
                }
                offLightsBuilder.append(light.getName());
                offLights.add(light);
            } else if (!state.isReachable()) {
                if (!unreachableLights.isEmpty()) {
                    unreachableLightsBuilder.append("\n,   ");
                }
                unreachableLightsBuilder.append(light.getName());
                unreachableLights.add(light);
            } else {
                okLights.add(light);
            }
        }


        getMainActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (unreachableLights.isEmpty() && offLights.isEmpty() && okLights.size() == controlledIds.size()) {
                    cleanup();
                    continueAsIs(okLights);
                }

                wait.setVisibility(View.GONE);
                info_layout.setVisibility(View.VISIBLE);

                okButton.setEnabled(!okLights.isEmpty());

                unreachable_lights_TextView.setText(unreachableLightsBuilder.toString());
                off_lights_TextView.setText(offLightsBuilder.toString());

                if (offLights.isEmpty()) {
                    off_layout.setVisibility(View.GONE);
                    turnOnLightsButton.setEnabled(false);
                } else {
                    off_layout.setVisibility(View.VISIBLE);
                    turnOnLightsButton.setEnabled(true);
                }

                if (unreachableLights.isEmpty()) {
                    unreachable_layout.setVisibility(View.GONE);
                } else {
                    unreachable_layout.setVisibility(View.VISIBLE);
                }

            }
        });


    }

    private void turnOnLights() {
        PHBridge bridge = phHueSDK.getSelectedBridge();
        Map<String, PHLight> lightsMap = bridge.getResourceCache().getLights();

        for (String id : controlledIds) {
            PHLight light = lightsMap.get(id);
            final PHLightState state = light.getLastKnownLightState();
            if (!state.isOn()) {
                PHLightState updateState = new PHLightState();
                updateState.setOn(true);
                bridge.updateLightState(light, updateState);
            }
        }
        // let the heartbeat show the update
    }

    private void continueAsIs(Collection<PHLight> okLights) {
        dismiss();

        HueMatcherActivity activity = getMainActivity();
        if (activity == null) {
            return;
        }

        String typeName = getArguments().getString(ARG_TYPE);
        final HueMatcherActivity.CaptureState type = HueMatcherActivity.CaptureState.valueOf(typeName);
        activity.setCurrentSessionLights(okLights);
        if (type == HueMatcherActivity.CaptureState.STILL) {
            activity.takeStill(true);
            return;
        }
        activity.takeContinuous(true);

    }

    private void cleanup() {
        if (getMainActivity() == null) {
            // not sure how/when this happens
            return;
        }
        getMainActivity().setBridgeUpdateListener(null);
    }


    private HueMatcherActivity getMainActivity() {
        return (HueMatcherActivity) super.getActivity();
    }

}
