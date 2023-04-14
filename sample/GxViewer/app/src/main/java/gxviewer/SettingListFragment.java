package gxviewer;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.SlidingPaneLayout;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import java.util.Objects;

import galaxy.Device;

public class SettingListFragment extends BaseSettingFragment implements NavigationView.OnNavigationItemSelectedListener {

    final private String[] m_title = {"TriggerMode", "Exposure&Gain", "WhiteBalance", "ImageProcessing", "UserSetControl", "VersionInformation"};
    private MenuItem m_lastCheckedItem = null;
    private BaseSettingFragment m_fragment = null;

    final String FRAGMENT_TAG = "fragment_tag";

    /**
     * brief setting list
     */
    public SettingListFragment() {

    }

    @Override
    void initUI() {

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_setting_list, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final MainActivity mainActivity = (MainActivity)getActivity();
        if(mainActivity == null) {
            return;
        }

        // add navigation item to navigation view by m_data
        NavigationView navigationView =
                    mainActivity.findViewById(R.id.setting_navigation);

        final int groupID = 0;
        for(int i = 0; i < m_title.length; i++) {
            navigationView.getMenu().add(groupID, i, i, m_title[i]);
        }

        // set default fragment
        if(m_lastCheckedItem == null) {
            final int defaultItemId = 0;
            m_lastCheckedItem = navigationView.getMenu().getItem(defaultItemId);
            m_lastCheckedItem.setChecked(true);

            // when m_fragment is not null clear it
            if(m_fragment != null) {
                m_fragment.clearFragment();
                m_fragment = null;
            }

            if(m_fragment == null) {
                m_fragment = new TriggerModeFragment();
            }

            m_fragment.setDeviceAndThreadShareObj(m_device, m_threadShareObj);
            mainActivity.replaceFragment(R.id.setting_fragment, m_fragment, FRAGMENT_TAG);
        }

        // register navigation item selected event
        navigationView.setNavigationItemSelectedListener(this);

        // register title bar event
        // when touch title bar's close btn close setting menu
        SettingTitleBar settingTitleBar = mainActivity.findViewById(R.id.settingtitle);
        settingTitleBar.setTitleClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SlidingPaneLayout slidingPaneLayout = mainActivity.findViewById(R.id.sliding_pane_layout);
                if(slidingPaneLayout != null) {
                    slidingPaneLayout.closePane();
                }
            }
        });
    }

    /**
     * brief    when touch a setting function replace setting list fragment by select setting fragment
     */
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem menuItem) {

        int index = 0;
        for(; index < m_title.length; index++) {
            if(m_title[index].equals(menuItem.toString())) {
                break;
            }
        }

        // disable last checked item
        if(m_lastCheckedItem != null) {
            m_lastCheckedItem.setChecked(false);
        }

        // reference current item, then enable this item
        m_lastCheckedItem = menuItem;
        menuItem.setChecked(true);

        NavigationView navigationView;
        try {
            navigationView =
                    Objects.requireNonNull(getActivity()).findViewById(R.id.setting_navigation);
        } catch (NullPointerException e) {
            // navigation view is null, close pane then return this function
            SlidingPaneLayout slidingPaneLayout =
                    Objects.requireNonNull(getActivity()).findViewById(R.id.sliding_pane_layout);
            if(slidingPaneLayout != null) {
                slidingPaneLayout.closePane();
            }
            return false;
        }

        navigationView.setCheckedItem(menuItem);

        if(m_fragment != null) {
            m_fragment.clearFragment();
            m_fragment = null;
        }

        // get current fragment by index
        switch(index) {
            case 0:
                m_fragment = new TriggerModeFragment();
                break;
            case 1:
                m_fragment = new ExposureGainFragment();
                break;
            case 2:
                m_fragment = new WhiteBalanceFragment();
                break;
            case 3:
                m_fragment = new ImageImproveFragment();
                break;
            case 4:
                m_fragment = new CamParamsFragment();
                break;
            case 5:
                m_fragment = new AboutInfoFragment();
                break;
            default:break;
        }

        if(m_fragment != null) {
            m_fragment.setDeviceAndThreadShareObj(m_device, m_threadShareObj);
            final MainActivity mainActivity = (MainActivity)getActivity();
            mainActivity.replaceFragment(R.id.setting_fragment, m_fragment, FRAGMENT_TAG);
        }

        return false;
    }

    /**
     * brief    when hide fragment, do something to clear member obj in fragment
     *          e.g. Timer, Thread...
     */
    public void clearFragment() {
        if(m_fragment != null) {
            m_fragment.clearFragment();
        }
        super.clearFragment();
    }

    @Override
    public void setDeviceAndThreadShareObj(Device dev, ThreadShareObj threadShareObj) {
        super.setDeviceAndThreadShareObj(dev, threadShareObj);
        if(m_fragment != null) {
            m_fragment.setDeviceAndThreadShareObj(dev, threadShareObj);
            final MainActivity mainActivity = (MainActivity)getActivity();
            mainActivity.replaceFragment(R.id.setting_fragment, m_fragment, FRAGMENT_TAG);
        }
    }
}
