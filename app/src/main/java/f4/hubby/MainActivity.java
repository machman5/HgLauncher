package f4.hubby;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.RecyclerView;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import f4.hubby.helpers.RecyclerClick;

public class MainActivity extends AppCompatActivity {

    boolean anim, icon_hide, list_order, shade_view;
    String launch_anim;
    private List<AppDetail> appList = new ArrayList<>();
    private PackageManager manager;
    private AppAdapter apps = new AppAdapter(appList);
    private RecyclerView list;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        setContentView(R.layout.activity_main);

        final View touchReceiver = findViewById(R.id.touch_receiver);
        registerForContextMenu(touchReceiver);

        LinearLayoutManager mLayoutManager = new LinearLayoutManager(this,
                LinearLayoutManager.VERTICAL, false);

        apps.setHasStableIds(true);

        list = findViewById(R.id.apps_list);
        list.setAdapter(apps);
        list.setLayoutManager(mLayoutManager);
        list.setItemAnimator(new DefaultItemAnimator());

        // Start loading and intialising everything.
        loadPref();
        loadApps();
        addClickListener();

        if (!list_order) {
            mLayoutManager.setReverseLayout(true);
            mLayoutManager.setStackFromEnd(true);
        }

        // Show context menu when touchReceiver is long pressed.
        touchReceiver.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                touchReceiver.showContextMenu();
                return true;
            }
        });

        // Overlays touchReceiver with the shade background.
        if (shade_view) {
           touchReceiver.setBackgroundResource(R.drawable.image_inner_shadow);
       }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return onOptionsItemSelected(item);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_settings:
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
                return true;
            case R.id.update_wallpaper:
                intent = new Intent(Intent.ACTION_SET_WALLPAPER);
                startActivity(Intent.createChooser(intent, getString(R.string.action_wallpaper)));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadApps() {
        manager = getPackageManager();

        Intent i = new Intent(Intent.ACTION_MAIN, null);
        i.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> availableActivities = manager.queryIntentActivities(i, 0);
        Collections.sort(availableActivities, new ResolveInfo.DisplayNameComparator(manager));

        // Fetch and add every app into our list
        for (ResolveInfo ri : availableActivities) {
            String label = ri.loadLabel(manager).toString();
            String name = ri.activityInfo.packageName;
            Drawable icon = null;
            // Only show icons if user chooses so.
            if (!icon_hide) {
                icon = ri.activityInfo.loadIcon(manager);
            }
            AppDetail app = new AppDetail(icon, label, name);
            appList.add(app);
            apps.notifyItemInserted(appList.size() - 1);
        }

        // Update our view cache size, now that we have got all apps on the list
        list.setItemViewCacheSize(appList.size() - 1);

        // Start list at the bottom
        if (list_order) {
            list.scrollToPosition(appList.size() - 1);
        } else {
            list.scrollToPosition(0);
        }
    }

    private void addClickListener() {
        // Add short click/click listener to the app list.
        RecyclerClick.addTo(list).setOnItemClickListener(new RecyclerClick.OnItemClickListener() {
            @Override
            public void onItemClicked(RecyclerView recyclerView, int position, View v) {
                Intent i = manager.getLaunchIntentForPackage(appList.get(position).getName().toString());
                // Override app launch animation when needed.
                switch (launch_anim) {
                    case "default":
                        MainActivity.this.startActivity(i);
                        break;
                    case "pull_up":
                        MainActivity.this.startActivity(i);
                        overridePendingTransition(R.anim.pull_up, 0);
                        break;
                    case "slide_in":
                        MainActivity.this.startActivity(i);
                        overridePendingTransition(R.anim.slide_in, 0);
                        break;
                }
            }
        });

        // Add long click action to app list. Long click shows a menu to manage selected app.
        RecyclerClick.addTo(list).setOnItemLongClickListener(new RecyclerClick.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClicked(RecyclerView recyclerView, int position, View v) {
                final Uri packageName = Uri.parse("package:" + appList.get(position).getName());
                PopupMenu appMenu = new PopupMenu(MainActivity.this, v);
                appMenu.getMenuInflater().inflate(R.menu.menu_app, appMenu.getMenu());
                appMenu.show();

                //TODO: Why does this look so hackish.
                appMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.action_info:
                                startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        packageName));
                                break;
                            case R.id.action_uninstall:
                                startActivity(new Intent(Intent.ACTION_DELETE, packageName));
                                break;
                            case R.id.action_hide:
                                // Placeholder until there's a mechanism to hide apps.
                                Toast.makeText(MainActivity.this,
                                        "Whoops, this is a placeholder", Toast.LENGTH_SHORT).show();
                                break;
                        }
                        return true;
                    }
                });
                return false;
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        loadPref();
        //TODO: Fix app list not refreshing.
        //appList.clear();
        //loadApps();
    }

    // Don't do anything when back is pressed.
    // Fixes the issue of launcher going AWOL.
    @Override
    public void onBackPressed() {}

    // Load available preferences.
    //TODO: This is suboptimal. Maybe try coming up with a better hax?
    private void loadPref() {
        launch_anim = prefs.getString("launch_anim", "default");
        icon_hide = prefs.getBoolean("icon_hide_switch", false);
        list_order = prefs.getString("list_order", "alphabetical").equals("invertedAlphabetical");
        shade_view = prefs.getBoolean("shade_view_switch", false);
    }
}
