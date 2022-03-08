/*
 * Copyright (C) 2017 Smap Consulting Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.smap.fragments;

import static org.odk.collect.android.utilities.ApplicationConstants.SortingOrder.BY_DATE_ASC;
import static org.odk.collect.android.utilities.ApplicationConstants.SortingOrder.BY_DATE_DESC;
import static org.odk.collect.android.utilities.ApplicationConstants.SortingOrder.BY_NAME_ASC;
import static org.odk.collect.android.utilities.ApplicationConstants.SortingOrder.BY_NAME_DESC;
import static org.odk.collect.android.utilities.ApplicationConstants.SortingOrder.BY_STATUS_ASC;
import static org.odk.collect.android.utilities.ApplicationConstants.SortingOrder.BY_STATUS_DESC;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.ListFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import org.odk.collect.android.R;
import org.odk.collect.android.activities.AboutActivity;
import org.odk.collect.android.activities.DeleteSavedFormActivity;
import org.odk.collect.android.activities.FillBlankFormActivity;
import org.odk.collect.android.activities.FormDownloadListActivity;
import org.odk.collect.android.activities.FormMapActivity;
import org.odk.collect.android.smap.activities.MainActivity;
import org.odk.collect.android.smap.activities.TaskStatusActivity;
//import org.odk.collect.android.activities.viewmodels.SurveyDataViewModel;  XXXX
import org.odk.collect.android.adapters.SortDialogAdapter;
//import org.odk.collect.android.adapters.TaskListArrayAdapter;  XXXX
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.injection.DaggerUtils;
//import org.odk.collect.android.listeners.PermissionListener;    XXXX
import org.odk.collect.android.listeners.RecyclerViewClickListener;
import org.odk.collect.android.smap.loaders.SurveyData;
import org.odk.collect.android.smap.loaders.TaskEntry;
//import org.odk.collect.android.permissions.PermissionsProvider;   XXXX
//import org.odk.collect.android.preferences.AdminKeys;   XXXX
//import org.odk.collect.android.preferences.AdminPreferencesActivity;   XXXX
//import org.odk.collect.android.preferences.GeneralKeys;    XXXX
//import org.odk.collect.android.preferences.PreferencesActivity;    XXXX
//import org.odk.collect.android.provider.FormsProviderAPI;    XXXX
//import org.odk.collect.android.smap.utilities.LocationRegister;    XXXX
//import org.odk.collect.android.utilities.MultiClickGuard;    XXXX
import org.odk.collect.android.utilities.SnackbarUtils;
import org.odk.collect.android.utilities.ThemeUtils;

import javax.inject.Inject;

import timber.log.Timber;

/**
 * Responsible for displaying tasks on the main fieldTask screen
 */
public class FormListFragment extends ListFragment {

    // request codes for returning chosen form to main menu.

    private static final int MENU_ENTERDATA = Menu.FIRST + 2;
    private static final int MENU_GETFORMS = Menu.FIRST + 3;
    private static final int MENU_SENDDATA = Menu.FIRST + 4;
    private static final int MENU_MANAGEFILES = Menu.FIRST + 5;
    private static final int MENU_EXIT = Menu.FIRST + 6;
    private static final int MENU_HISTORY = Menu.FIRST + 7;

    private static final String SEARCH_TEXT = "searchText";
    private static final String IS_SEARCH_BOX_SHOWN = "isSearchBoxShown";

    protected int[] sortingOptions;

    View rootView;

    private String filterText;

    private Integer selectedSortingOrder;
    private BottomSheetDialog bottomSheetDialog;

    private static final String TASK_MANAGER_LIST_SORTING_ORDER = "taskManagerListSortingOrder";

    private SharedPreferences adminPreferences;

    //private TaskListArrayAdapter mAdapter;  XXXX

    //SurveyDataViewModel model;   XXXX

    @Inject
    //PermissionsProvider permissionsProvider;  XXXX

    public static FormListFragment newInstance() {
        return new FormListFragment();
    }

    public FormListFragment() {
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        //DaggerUtils.getComponent(context).inject(this);   XXXX
    }

    // this method is only called once for this fragment
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // retain this fragment
        setRetainInstance(true);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        rootView = inflater.inflate(R.layout.smap_task_layout, container, false);

        setHasOptionsMenu(true);

        return rootView;
    }

    @Override
    public void onActivityCreated(Bundle b) {
        super.onActivityCreated(b);

        //mAdapter = new TaskListArrayAdapter(getActivity(), true);   XXXX
        //setListAdapter(mAdapter);  XXXX

        // Handle long item clicks
        ListView lv = getListView();
        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> av, View v, int pos, long id) {
                return onLongListItemClick(v,pos,id);
            }
        });

        //adminPreferences = getActivity().getSharedPreferences(
        //        AdminPreferencesActivity.ADMIN_PREFERENCES, 0);   XXXX

    }


    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {

        sortingOptions = new int[]{
                R.string.sort_by_name_asc, R.string.sort_by_name_desc,
                R.string.sort_by_date_asc, R.string.sort_by_date_desc,
                R.string.smap_sort_by_project_asc, R.string.smap_sort_by_project_desc
        };

        //model = new ViewModelProvider(requireActivity()).get(SurveyDataViewModel.class);  XXXX
        /* XXXX
        model.getSurveyData().observe(getViewLifecycleOwner(), surveyData -> {
            Timber.i("-------------------------------------- Form List Fragment got Data ");
            setData(surveyData);
        });

         */

        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        rootView = null;
        super.onDestroyView();
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle bundle) {
        super.onViewStateRestored(bundle);
    }

    @Override
    public void onResume() {
        super.onResume();
        Toolbar toolbar = (Toolbar) getActivity().findViewById(R.id.toolbar);
        //toolbar.setNavigationIcon(R.mipmap.ic_nav);  XXXX

        if (bottomSheetDialog == null) {
            setupBottomSheet();
        }

        // Notify the user if tracking is turned on
        /* XXXX
        if(new LocationRegister().locationEnabled()
                && (PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(GeneralKeys.KEY_SMAP_USER_LOCATION, false)
                || PreferenceManager.getDefaultSharedPreferences(getContext()).getBoolean(GeneralKeys.KEY_SMAP_ENABLE_GEOFENCE, false))) {
            SnackbarUtils.showLongSnackbar(getActivity().findViewById(R.id.llParent), getString(R.string.smap_location_tracking));
        }

         */

    }

    private void setupBottomSheet() {
        //bottomSheetDialog = new BottomSheetDialog(getActivity(), new ThemeUtils(getContext()).getBottomDialogTheme());  XXXX
        View sheetView = getActivity().getLayoutInflater().inflate(R.layout.bottom_sheet, null);
        final RecyclerView recyclerView = sheetView.findViewById(R.id.recyclerView);

        final SortDialogAdapter adapter = new SortDialogAdapter(getActivity(), recyclerView, sortingOptions, getSelectedSortingOrder(), new RecyclerViewClickListener() {
            @Override
            public void onItemClicked(SortDialogAdapter.ViewHolder holder, int position) {
                holder.updateItemColor(selectedSortingOrder);
                performSelectedSearch(position);
                bottomSheetDialog.dismiss();
            }
        });
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        recyclerView.setItemAnimator(new DefaultItemAnimator());

        bottomSheetDialog.setContentView(sheetView);
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    protected String getSortingOrderKey() {
        return TASK_MANAGER_LIST_SORTING_ORDER;
    }

    public void setData(SurveyData data) {
        /* XXXX
        if(mAdapter != null) {
            if (data != null) {
                mAdapter.setData(data.tasks);
            } else {
                mAdapter.setData(null);
            }
        }

         */
    }


    @Override
    public void onListItemClick(ListView l, View v, int position, long rowId) {
        /* XXXX
        if (MultiClickGuard.allowClick(getClass().getName())) {
            super.onListItemClick(l, v, position, rowId);

            TaskEntry entry = (TaskEntry) getListAdapter().getItem(position);

            if (entry.type.equals("task")) {
                if (entry.locationTrigger != null && entry.locationTrigger.length() > 0) {
                    Toast.makeText(
                            getActivity(),
                            getString(R.string.smap_must_start_from_nfc),
                            Toast.LENGTH_LONG).show();
                } else {
                    ((MainActivity) getActivity()).completeTask(entry, false);
                }
            } else {
                ((MainActivity) getActivity()).completeForm(entry, false, null);
            }
        }

         */
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {

        super.onCreateOptionsMenu(menu, inflater);

        getActivity().getMenuInflater().inflate(R.menu.smap_menu, menu);


        boolean odkMenus=true; /* XXXXX = PreferenceManager
                .getDefaultSharedPreferences(getContext())
                .getBoolean(GeneralKeys.KEY_SMAP_ODK_STYLE_MENUS, true);
                */

        if(odkMenus) {
            menu
                    .add(0, MENU_ENTERDATA, 0, R.string.enter_data)
                    .setIcon(android.R.drawable.ic_menu_edit)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

            menu
                    .add(0, MENU_GETFORMS, 0, R.string.get_forms)
                    .setIcon(android.R.drawable.ic_input_add)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

            menu
                    .add(0, MENU_SENDDATA, 0, R.string.send_data)
                    .setIcon(android.R.drawable.ic_menu_send)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

            menu
                    .add(0, MENU_MANAGEFILES, 0, R.string.manage_files)
                    .setIcon(android.R.drawable.ic_delete)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }

        menu
                .add(0, MENU_HISTORY, 0, R.string.smap_history)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        menu
                .add(0, MENU_EXIT, 0, R.string.exit)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);

        boolean adminMenu = true; /* XXXXX PreferenceManager
                .getDefaultSharedPreferences(getContext())
                .getBoolean(GeneralKeys.KEY_SMAP_ODK_ADMIN_MENU, false);
            */

        if(adminMenu) {
            menu
                    .add(0, R.id.menu_admin_preferences, 0,
                            R.string.admin_preferences)
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
       }

        final MenuItem sortItem = menu.findItem(R.id.menu_sort);
        final MenuItem searchItem = menu.findItem(R.id.menu_filter);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
        searchView.setQueryHint(getResources().getString(R.string.search));
        searchView.setMaxWidth(Integer.MAX_VALUE);

        if(filterText == null) {
            filterText = "";
        }

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterText = query;
                updateAdapter();
                searchView.clearFocus();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if(!filterText.equals(newText)) {
                    filterText = newText;
                    updateAdapter();
                }
                return false;
            }
        });

        MenuItemCompat.setOnActionExpandListener(searchItem, new MenuItemCompat.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                sortItem.setVisible(false);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                sortItem.setVisible(true);
                return true;
            }
        });

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_about:
                Intent aboutIntent = new Intent(getActivity(), AboutActivity.class);
                startActivity(aboutIntent);
                return true;
            case R.id.menu_general_preferences:
                //Intent ig = new Intent(getActivity(), PreferencesActivity.class); XXXX
                //startActivity(ig);   XXXX
                return true;
            case R.id.menu_admin_preferences:
                /* XXXX
                String pw = adminPreferences.getString(
                        AdminKeys.KEY_ADMIN_PW, "");
                if ("".equalsIgnoreCase(pw)) {
                    Intent i = new Intent(getActivity(),
                            AdminPreferencesActivity.class);
                    startActivity(i);
                } else {
                    ((MainActivity) getActivity()).processAdminMenu();
                }
                */
                return true;
            case R.id.menu_gettasks:
                ((MainActivity) getActivity()).processGetTask(true);
                return true;
            case MENU_ENTERDATA:
                processEnterData();
                return true;
            case MENU_GETFORMS:
                processGetForms();
                return true;
            case MENU_SENDDATA:
                processSendData();
                return true;
            case MENU_MANAGEFILES:
                processManageFiles();
                return true;
            case MENU_HISTORY:
                ((MainActivity) getActivity()).processHistory();
                return true;
            case R.id.menu_sort:
                bottomSheetDialog.show();
                return true;
            case MENU_EXIT:
                ((MainActivity) getActivity()).exit();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void performSelectedSearch(int position) {
        saveSelectedSortingOrder(position);
        updateAdapter();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    protected String getFormSortingOrder() {
        String sortOrder = "BY_NAME_ASC";
        switch (getSelectedSortingOrder()) {

            case BY_NAME_ASC:
                sortOrder = "BY_NAME_ASC";
                break;
            case BY_NAME_DESC:
                sortOrder = "BY_NAME_DESC";
                break;
            case BY_DATE_ASC:
                sortOrder = "BY_DATE_ASC";
                break;
            case BY_DATE_DESC:
                sortOrder = "BY_DATE_DESC";
                break;
            case BY_STATUS_ASC:
                sortOrder = "BY_PROJECT_ASC";
                break;
            case BY_STATUS_DESC:
                sortOrder = "BY_PROJECT_DESC";
                break;
        }
        return sortOrder;
    }

    private void saveSelectedSortingOrder(int selectedStringOrder) {
        selectedSortingOrder = selectedStringOrder;
        PreferenceManager.getDefaultSharedPreferences(Collect.getInstance())
                .edit()
                .putInt(getSortingOrderKey(), selectedStringOrder)
                .apply();
    }

    protected void restoreSelectedSortingOrder() {
        selectedSortingOrder = PreferenceManager
                .getDefaultSharedPreferences(getContext())
                .getInt(getSortingOrderKey(), BY_NAME_ASC);
    }

    protected int getSelectedSortingOrder() {
        if (selectedSortingOrder == null) {
            restoreSelectedSortingOrder();
        }
        return selectedSortingOrder;
    }

    protected CharSequence getFilterText() {
        return filterText != null ? filterText : "";
        //return inputSearch != null ? inputSearch.getText() : "";
    }

    protected void updateAdapter() {

        //MapDataLoader taskLoader =  ((MainActivity) getActivity()).getTaskLoader();   // loader
        //if(taskLoader != null) {                                                  // loader
        //    taskLoader.updateFormSortOrder(getFormSortingOrder());                // loader
        //    taskLoader.updateFilter(getFilterText());                             // loader
        //    taskLoader.forceLoad();                                               // loader
        //}                                                                         // loader

        /* XXXX
        if(model != null) {
            model.updateFormSortOrder(getFormSortingOrder());
            model.updateFilter(getFilterText());
            model.loadData();
        }
        */
    }

    private void processEnterData() {
        /* XXXX
        if (MultiClickGuard.allowClick(getClass().getName())) {
            Intent i = new Intent(getContext(),
                    FillBlankFormActivity.class);
            startActivity(i);
        }
         */
    }

    // Get new forms
    private void processGetForms() {

        Intent i = new Intent(getContext(), FormDownloadListActivity.class);
        startActivity(i);
    }

    // Send data
    private void processSendData() {
        Intent i = new Intent(getContext(), org.odk.collect.android.activities.InstanceUploaderListActivity.class);
        startActivity(i);
    }

    private void processManageFiles() {
        Intent i = new Intent(getContext(), DeleteSavedFormActivity.class);
        startActivity(i);
    }

    /*
     * Handle a long click on a list item
     */
    protected boolean onLongListItemClick(View v, int position, long id) {

        TaskEntry task = (TaskEntry) getListAdapter().getItem(position);

        if(task.type.equals("task")) {
            Intent i = new Intent(getActivity(), TaskStatusActivity.class);
            i.putExtra("id", task.id);

            startActivity(i);
        } else {
            /* XXXX
            final Uri formUri = ContentUris.withAppendedId(FormsProviderAPI.FormsColumns.CONTENT_URI, task.id);
            final Intent intent = new Intent(Intent.ACTION_EDIT, formUri, getActivity(), FormMapActivity.class);
            permissionsProvider.requestLocationPermissions(getActivity(), new PermissionListener() {
                @Override public void granted() {
                    startActivity(intent);
                }

                @Override public void denied() { }
            });
            */
        }
        return true;
    }

}
