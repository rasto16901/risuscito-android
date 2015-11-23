package it.cammino.risuscito;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.color.ColorChooserDialog;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.OptionalPendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.DriveFolder;
import com.google.android.gms.drive.DriveId;
import com.google.android.gms.drive.DriveResource;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.android.gms.drive.query.Filters;
import com.google.android.gms.drive.query.Query;
import com.google.android.gms.drive.query.SearchableField;
import com.squareup.picasso.Picasso;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

import it.cammino.risuscito.ui.ThemeableActivity;

public class MainActivity extends ThemeableActivity
        implements ColorChooserDialog.ColorCallback, NavigationView.OnNavigationItemSelectedListener, GoogleApiClient.OnConnectionFailedListener{

    public DrawerLayout mDrawerLayout;
        protected static final String SELECTED_ITEM = "oggetto_selezionato";
    private static final String SHOW_SNACKBAR = "mostra_snackbar";
    private int selectedItemIndex = 0;

    private int prevOrientation;

    private NavigationView mNavigationView;

//    private static final int TALBLET_DP = 600;
//    private static final int WIDTH_320 = 320;
//    private static final int WIDTH_400 = 400;

    MaterialDialog mProgressDialog;

    private boolean showSnackbar;
    private GoogleSignInAccount acct;
    private ImageView profileImage;
    private ImageView profileBackground;
    //    private View copertinaAccount;
    private ImageView accountMenu;
    private TextView usernameTextView;
    private TextView emailTextView;

    /* Request code used to invoke sign in user interactions. */
    private static final int RC_SIGN_IN = 9001;
    //    private static final String STATE_RESOLVING_ERROR = "resolving_error";
//    // Request code to use when launching the resolution activity
//    private static final int REQUEST_RESOLVE_ERROR = 1001;
//    // Unique tag for the error dialog fragment
//    private static final String DIALOG_ERROR = "dialog_error";
    /* Client used to interact with Google APIs. */
    private GoogleApiClient mGoogleApiClient;
    // Bool to track whether the app is already resolving an error
//    private boolean mResolvingError = false;

    private MaterialDialog backupDialog, restoreDialog;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.hasNavDrawer = true;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (getIntent().getBooleanExtra(Utility.DB_RESET, false)) {
            (new TranslationTask()).execute();
        }

        setupNavDrawer();

        View header = mNavigationView.getHeaderView(0);

        profileImage = (ImageView) header.findViewById(R.id.profile_image);
        profileImage.setVisibility(View.INVISIBLE);
        usernameTextView = (TextView) header.findViewById(R.id.username);
        usernameTextView.setVisibility(View.INVISIBLE);
        emailTextView = (TextView) header.findViewById(R.id.email);
        emailTextView.setVisibility(View.INVISIBLE);
        profileBackground = (ImageView) header.findViewById(R.id.copertina);
//        copertinaAccount = header.findViewById(R.id.copertina_account);
        accountMenu = (ImageView) header.findViewById(R.id.account_menu);
        Drawable drawable = DrawableCompat.wrap(accountMenu.getDrawable());
        DrawableCompat.setTint(drawable, ContextCompat.getColor(MainActivity.this, android.R.color.white));
        accountMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                accountMenu.setSelected(!accountMenu.isSelected());
                mNavigationView.getMenu().clear();
                mNavigationView.inflateMenu(accountMenu.isSelected()
                        ? R.menu.drawer_account_menu : R.menu.drawer_menu);
            }
        });

//        mResolvingError = savedInstanceState != null
//                && savedInstanceState.getBoolean(STATE_RESOLVING_ERROR, false);

        showSnackbar = savedInstanceState == null
                || savedInstanceState.getBoolean(SHOW_SNACKBAR, true);

        if (findViewById(R.id.content_frame) != null) {
            // However, if we're being restored from a previous state,
            // then we don't need to do anything and should return or else
            // we could end up with overlapping fragments.
            if (savedInstanceState != null) {
                selectedItemIndex = savedInstanceState.getInt(SELECTED_ITEM, 0);
                mNavigationView.getMenu().getItem(selectedItemIndex).setChecked(true);
            }
            else
                getSupportFragmentManager().beginTransaction().replace(R.id.content_frame, new Risuscito(), String.valueOf(R.id.navigation_home)).commit();
//            if (savedInstanceState == null)
//                getSupportFragmentManager().beginTransaction().replace(R.id.content_frame, new Risuscito(), String.valueOf(R.id.navigation_home)).commit();
        }

        mProgressDialog = new MaterialDialog.Builder(this)
                .title(R.string.connection_running)
                .progress(true, 0)
                .build();

        // [START configure_signin]
        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(Scopes.DRIVE_APPFOLDER))
                .build();
        // [END configure_signin]

        // [START build_client]
        // Build a GoogleApiClient with access to the Google Sign-In API and the
        // options specified by gso.
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .enableAutoManage(this /* FragmentActivity */, this /* OnConnectionFailedListener */)
                .addApi(Auth.GOOGLE_SIGN_IN_API, gso)
                .addApi(Drive.API)
                .build();
        // [END build_client]

    }

    @Override
    public void onStart() {
        super.onStart();

        OptionalPendingResult<GoogleSignInResult> opr = Auth.GoogleSignInApi.silentSignIn(mGoogleApiClient);
        if (opr.isDone()) {
            // If the user's cached credentials are valid, the OptionalPendingResult will be "done"
            // and the GoogleSignInResult will be available instantly.
            Log.d(getClass().getName(), "Got cached sign-in");
            GoogleSignInResult result = opr.get();
            handleSignInResult(result);
        } else {
            // If the user has not previously signed in on this device or the sign-in has expired,
            // this asynchronous branch will attempt to sign in the user silently.  Cross-device
            // single sign-on will occur in this branch.
            showProgressDialog();
            opr.setResultCallback(new ResultCallback<GoogleSignInResult>() {
                @Override
                public void onResult(GoogleSignInResult googleSignInResult) {
                    hideProgressDialog();
                    handleSignInResult(googleSignInResult);
                }
            });
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
//        for (int i = 0; mNavigationView.getMenu().getItem(i) != null; i++) {
//            if (mNavigationView.getMenu().getItem(i).isChecked()) {
//                savedInstanceState.putInt(SELECTED_ITEM, i);
//                break;
//            }
//        }
        savedInstanceState.putInt(SELECTED_ITEM, selectedItemIndex);
        //questo pezzo salva l'elenco dei titoli checkati del fragment ConsegnatiFragment, quando si ruota lo schermo
        ConsegnatiFragment consegnatiFragment = (ConsegnatiFragment)getSupportFragmentManager().findFragmentByTag(String.valueOf(R.id.navigation_consegnati));
        if (consegnatiFragment != null && consegnatiFragment.isVisible() && consegnatiFragment.getTitoliChoose() != null) {
            ConsegnatiFragment.RetainedFragment dataFragment = new ConsegnatiFragment.RetainedFragment();
            getSupportFragmentManager().beginTransaction().add(dataFragment, ConsegnatiFragment.TITOLI_CHOOSE).commit();
            dataFragment.setData(consegnatiFragment.getTitoliChoose());
        }

        savedInstanceState.putBoolean(SHOW_SNACKBAR, showSnackbar);
//        savedInstanceState.putBoolean(STATE_RESOLVING_ERROR, mResolvingError);

        super.onSaveInstanceState(savedInstanceState);
    }

    private void setupNavDrawer() {

        mDrawerLayout = (DrawerLayout) findViewById(R.id.my_drawer_layout);
        if (mDrawerLayout == null) {
            return;
        }
        mDrawerLayout.setStatusBarBackgroundColor(getThemeUtils().primaryColorDark());
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);

        mNavigationView = (NavigationView) findViewById(R.id.navigation_view);
        mNavigationView.setNavigationItemSelectedListener(this);

        ColorStateList mIconStateList = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked}, //1
                        new int[]{} //2
                },
                new int[] {
                        getThemeUtils().primaryColor(), //1
                        ContextCompat.getColor(MainActivity.this, R.color.navdrawer_icon_tint) // 2
                }
        );

        ColorStateList mTextStateList = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked}, //1
                        new int[]{} //2
                },
                new int[] {
                        getThemeUtils().primaryColor(), //1
                        ContextCompat.getColor(MainActivity.this, R.color.navdrawer_text_color) //2
                }
        );

        mNavigationView.setItemIconTintList(mIconStateList);
        mNavigationView.setItemTextColor(mTextStateList);

    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                mDrawerLayout.closeDrawer(GravityCompat.START);
                return true;
            }

            Fragment myFragment = getSupportFragmentManager().findFragmentByTag(String.valueOf(R.id.navigation_home));
            if (myFragment != null && myFragment.isVisible()) {
                finish();
                return true;
            }

            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.setCustomAnimations(R.anim.slide_in_left, R.anim.slide_out_right);
            transaction.replace(R.id.content_frame, new Risuscito(), String.valueOf(R.id.navigation_home)).commit();
            mNavigationView.getMenu().getItem(0).setChecked(true);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onColorSelection(@NonNull ColorChooserDialog colorChooserDialog, @ColorInt int color) {
        if (colorChooserDialog.isAccentMode())
            getThemeUtils().accentColor(color);
        else
            getThemeUtils().primaryColor(color);

        if (android.os.Build.VERSION.SDK_INT >= 11) {
            recreate();
        }
        else {
            Intent i = getBaseContext().getPackageManager()
                    .getLaunchIntentForPackage(getBaseContext().getPackageName());
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        }
    }

    //converte gli accordi salvati dalla lingua vecchia alla nuova
    private void convertTabs(SQLiteDatabase db, String conversion) {
//        Log.i(getClass().toString(), "CONVERSION: " + conversion);
        HashMap<String, String> mappa = null;
        if (conversion.equalsIgnoreCase("it-uk")) {
            mappa = new HashMap<>();
            for (int i = 0; i < CambioAccordi.accordi_it.length; i++)
                mappa.put(CambioAccordi.accordi_it[i], CambioAccordi.accordi_uk[i]);
        }
        if (conversion.equalsIgnoreCase("uk-it")) {
            mappa = new HashMap<>();
            for (int i = 0; i < CambioAccordi.accordi_it.length; i++)
                mappa.put(CambioAccordi.accordi_uk[i], CambioAccordi.accordi_it[i]);
        }
        if (mappa != null) {
            String query = "SELECT _id, saved_tab" +
                    "  FROM ELENCO";
            Cursor cursor = db.rawQuery(query, null);

            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                if (cursor.getString(1) != null && !cursor.getString(1).equals("")) {
//                Log.i(getClass().toString(),"ID " + cursor.getInt(0) +  " -> CONVERTO DA " + cursor.getString(1) + " A " + mappa.get(cursor.getString(1)) );
                    query = "UPDATE ELENCO" +
                            "  SET saved_tab = \'" + mappa.get(cursor.getString(1)) + "\' " +
                            "  WHERE _id =  " + cursor.getInt(0);
                    db.execSQL(query);
                }
                cursor.moveToNext();
            }
            cursor.close();
        }
    }

    private class TranslationTask extends AsyncTask<String, Integer, String> {

        public TranslationTask() {}

        private MaterialDialog translationDialog;

        @Override
        protected String doInBackground(String... sUrl) {
            getIntent().removeExtra(Utility.DB_RESET);
            DatabaseCanti listaCanti = new DatabaseCanti(MainActivity.this);
            SQLiteDatabase db = listaCanti.getReadableDatabase();
            DatabaseCanti.Backup[] backup = listaCanti.backupTables(db.getVersion(), db.getVersion(), db);
            DatabaseCanti.BackupLocalLink[] backupLink = listaCanti.backupLocalLink(db.getVersion(), db.getVersion(), db);
            listaCanti.reCreateDatabse(db);
            listaCanti.repopulateDB(db.getVersion(), db.getVersion(), db, backup, backupLink);
            convertTabs(db, getIntent().getStringExtra(Utility.CHANGE_LANGUAGE));
            db.close();
            listaCanti.close();
            return "";
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            prevOrientation = getRequestedOrientation();
            Utility.blockOrientation(MainActivity.this);
            translationDialog = new MaterialDialog.Builder(MainActivity.this)
                    .content(R.string.translation_running)
                    .progress(true, 0)
                    .dismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            setRequestedOrientation(prevOrientation);
                        }
                    })
                    .show();
        }

        @Override
        protected void onPostExecute(String result) {
            getIntent().removeExtra(Utility.CHANGE_LANGUAGE);
            try {
                if (translationDialog != null && translationDialog.isShowing())
                    translationDialog.dismiss();
            }
            catch (IllegalArgumentException e) {
                Log.e(getClass().getName(), e.getLocalizedMessage(), e);
            }
        }
    }

    public void setupToolbar(View toolbar, int titleResId) {
        Toolbar mActionToolbar = (Toolbar) toolbar;
        setSupportActionBar(mActionToolbar);
        mActionToolbar.setBackgroundColor(getThemeUtils().primaryColor());
        getSupportActionBar().setTitle("");
        ((TextView)toolbar.findViewById(R.id.main_toolbarTitle)).setText(titleResId);
        mActionToolbar.setNavigationIcon(R.drawable.ic_menu_24dp);
        Drawable drawable = DrawableCompat.wrap(mActionToolbar.getNavigationIcon());
        DrawableCompat.setTint(drawable, ContextCompat.getColor(MainActivity.this, android.R.color.white));
        mActionToolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mDrawerLayout.openDrawer(GravityCompat.START);
            }
        });
    }

    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        item.setChecked(true);
        Fragment fragment;

        switch (item.getItemId()) {
            case R.id.navigation_home:
                selectedItemIndex = 0;
                fragment = new Risuscito();
                break;
            case R.id.navigation_search:
                selectedItemIndex = 1;
                fragment = new GeneralSearch();
                break;
            case R.id.navigation_indexes:
                selectedItemIndex = 2;
                fragment = new GeneralIndex();
                break;
            case R.id.navitagion_lists:
                selectedItemIndex = 3;
                fragment = new CustomLists();
                break;
            case R.id.navigation_favorites:
                selectedItemIndex = 4;
                fragment = new FavouritesActivity();
                break;
            case R.id.navigation_settings:
                selectedItemIndex = 5;
                fragment = new PreferencesFragment();
                break;
            case R.id.navigation_changelog:
                selectedItemIndex = 6;
                fragment = new AboutActivity();
                break;
            case R.id.navigation_donate:
                selectedItemIndex = 7;
                fragment = new DonateActivity();
                break;
            case R.id.navigation_consegnati:
                selectedItemIndex = 8;
                fragment = new ConsegnatiFragment();
                break;
            case R.id.navigation_history:
                selectedItemIndex = 9;
                fragment = new HistoryFragment();
                break;
            case R.id.gdrive_backup:
                accountMenu.performClick();
                mDrawerLayout.closeDrawer(GravityCompat.START);
                prevOrientation = getRequestedOrientation();
                Utility.blockOrientation(MainActivity.this);
                MaterialDialog dialog = new MaterialDialog.Builder(MainActivity.this)
                        .title(R.string.gdrive_backup)
                        .content(R.string.gdrive_backup_content)
                        .positiveText(R.string.confirm)
                        .negativeText(R.string.dismiss)
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                backupDialog = new MaterialDialog.Builder(MainActivity.this)
                                        .content(R.string.backup_running)
                                        .progress(true, 0)
                                        .dismissListener(new DialogInterface.OnDismissListener() {
                                            @Override
                                            public void onDismiss(DialogInterface dialog) {
                                                setRequestedOrientation(prevOrientation);
                                            }
                                        })
                                        .show();
                                backupDialog.setCancelable(false);
//                                saveToDrive(
                                saveCheckDupl(
                                        Drive.DriveApi.getAppFolder(mGoogleApiClient),
                                        DatabaseCanti.getDbName(),
                                        "application/x-sqlite3",
                                        getDbPath()
                                );
                            }
                        })
                        .onNegative(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                setRequestedOrientation(prevOrientation);
                            }
                        })
                        .show();
                dialog.setOnKeyListener(new Dialog.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface arg0, int keyCode,
                                         KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_BACK
                                && event.getAction() == KeyEvent.ACTION_UP) {
                            arg0.dismiss();
                            setRequestedOrientation(prevOrientation);
                            return true;
                        }
                        return false;
                    }
                });
                dialog.setCancelable(false);
                return true;
            case R.id.gdrive_restore:
                accountMenu.performClick();
                mDrawerLayout.closeDrawer(GravityCompat.START);
                prevOrientation = getRequestedOrientation();
                Utility.blockOrientation(MainActivity.this);
                dialog = new MaterialDialog.Builder(MainActivity.this)
                        .title(R.string.gdrive_restore)
                        .content(R.string.gdrive_restore_content)
                        .positiveText(R.string.confirm)
                        .negativeText(R.string.dismiss)
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                restoreDialog = new MaterialDialog.Builder(MainActivity.this)
                                        .content(R.string.restore_running)
                                        .progress(true, 0)
                                        .dismissListener(new DialogInterface.OnDismissListener() {
                                            @Override
                                            public void onDismiss(DialogInterface dialog) {
                                                setRequestedOrientation(prevOrientation);
                                            }
                                        })
                                        .show();
                                restoreDialog.setCancelable(false);
                                restoreDriveBackup();
                            }
                        })
                        .onNegative(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                setRequestedOrientation(prevOrientation);
                            }
                        })
                        .show();
                dialog.setOnKeyListener(new Dialog.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface arg0, int keyCode,
                                         KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_BACK
                                && event.getAction() == KeyEvent.ACTION_UP) {
                            arg0.dismiss();
                            setRequestedOrientation(prevOrientation);
                            return true;
                        }
                        return false;
                    }
                });
                dialog.setCancelable(false);
                return true;
            case R.id.gplus_signout:
                accountMenu.performClick();
                mDrawerLayout.closeDrawer(GravityCompat.START);
                prevOrientation = getRequestedOrientation();
                Utility.blockOrientation(MainActivity.this);
                dialog = new MaterialDialog.Builder(MainActivity.this)
                        .title(R.string.gplus_signout)
                        .content(R.string.dialog_acc_disconn_text)
                        .positiveText(R.string.confirm)
                        .negativeText(R.string.dismiss)
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                signOut();
                                setRequestedOrientation(prevOrientation);
                            }
                        })
                        .onNegative(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                setRequestedOrientation(prevOrientation);
                            }
                        })
                        .show();
                dialog.setOnKeyListener(new Dialog.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface arg0, int keyCode,
                                         KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_BACK
                                && event.getAction() == KeyEvent.ACTION_UP) {
                            arg0.dismiss();
                            setRequestedOrientation(prevOrientation);
                            return true;
                        }
                        return false;
                    }
                });
                dialog.setCancelable(false);
                return true;
            case R.id.gplus_revoke:
                accountMenu.performClick();
                mDrawerLayout.closeDrawer(GravityCompat.START);
                prevOrientation = getRequestedOrientation();
                Utility.blockOrientation(MainActivity.this);
                dialog = new MaterialDialog.Builder(MainActivity.this)
                        .title(R.string.gplus_revoke)
                        .content(R.string.dialog_acc_revoke_text)
                        .positiveText(R.string.confirm)
                        .negativeText(R.string.dismiss)
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                revokeAccess();
                                setRequestedOrientation(prevOrientation);
                            }
                        })
                        .onNegative(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                setRequestedOrientation(prevOrientation);
                            }
                        })
                        .show();
                dialog.setOnKeyListener(new Dialog.OnKeyListener() {
                    @Override
                    public boolean onKey(DialogInterface arg0, int keyCode,
                                         KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_BACK
                                && event.getAction() == KeyEvent.ACTION_UP) {
                            arg0.dismiss();
                            setRequestedOrientation(prevOrientation);
                            return true;
                        }
                        return false;
                    }
                });
                dialog.setCancelable(false);
                return true;
            default:
                fragment = new Risuscito();
                break;
        }

        //creo il nuovo fragment solo se non è lo stesso che sto già visualizzando
        Fragment myFragment = getSupportFragmentManager().findFragmentByTag(String.valueOf(item.getItemId()));
        if (myFragment == null || !myFragment.isVisible()) {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left);
            transaction.replace(R.id.content_frame, fragment, String.valueOf(item.getItemId())).commit();

            android.os.Handler mHandler = new android.os.Handler();
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mDrawerLayout.closeDrawer(GravityCompat.START);
                }
            }, 250);
        }
        return true;
    }

    // [START signIn]
    public void signIn() {
        Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        startActivityForResult(signInIntent, RC_SIGN_IN);
    }
    // [END signIn]

    // [START signOut]
    private void signOut() {
        Auth.GoogleSignInApi.signOut(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        // [START_EXCLUDE]
                        updateUI(false);
                        SharedPreferences.Editor editor = PreferenceManager
                                .getDefaultSharedPreferences(MainActivity.this)
                                .edit();
                        editor.putBoolean(Utility.SIGNED_IN, false);
                        editor.apply();
                        Snackbar.make(findViewById(android.R.id.content), R.string.disconnected, Snackbar.LENGTH_SHORT).show();
                        // [END_EXCLUDE]
                    }
                });
    }
    // [END signOut]

    // [START revokeAccess]
    private void revokeAccess() {
        Auth.GoogleSignInApi.revokeAccess(mGoogleApiClient).setResultCallback(
                new ResultCallback<Status>() {
                    @Override
                    public void onResult(Status status) {
                        // [START_EXCLUDE]
                        updateUI(false);
                        SharedPreferences.Editor editor = PreferenceManager
                                .getDefaultSharedPreferences(MainActivity.this)
                                .edit();
                        editor.putBoolean(Utility.SIGNED_IN, false);
                        editor.apply();
                        Snackbar.make(findViewById(android.R.id.content), R.string.disconnected, Snackbar.LENGTH_SHORT).show();
                        // [END_EXCLUDE]
                    }
                });
    }
    // [END revokeAccess]

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        // An unresolvable error has occurred and Google APIs (including Sign-In) will not
        // be available.
        Log.d(getClass().getName(), "onConnectionFailed:" + connectionResult);
    }

    // [START onActivityResult]
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(getClass().getName(), "requestCode: " + requestCode);
        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(data);
            handleSignInResult(result);
        }
    }
    // [END onActivityResult]

    // [START handleSignInResult]
    private void handleSignInResult(GoogleSignInResult result) {
        Log.d(getClass().getName(), "handleSignInResult:" + result.isSuccess());
        if (result.isSuccess()) {
            // Signed in successfully, show authenticated UI.
            acct = result.getSignInAccount();
            SharedPreferences.Editor editor = PreferenceManager
                    .getDefaultSharedPreferences(MainActivity.this)
                    .edit();
            editor.putBoolean(Utility.SIGNED_IN, true);
            editor.apply();
            if (showSnackbar) {
                Snackbar.make(findViewById(android.R.id.content), getString(R.string.connected_as, acct.getDisplayName()), Snackbar.LENGTH_SHORT).show();
                showSnackbar = false;
            }
            updateUI(true);
        } else {
            // Signed out, show unauthenticated UI.
            acct = null;
            updateUI(false);
        }
    }
    // [END handleSignInResult]

    @SuppressWarnings("deprecation")
    private void updateUI(boolean signedIn) {
        if (signedIn) {
//            Log.d(getClass().getName(), "currentPerson: " + Plus.PeopleApi.getCurrentPerson(mGoogleApiClient));
//            if (mGoogleApiClient.isConnected() && Plus.PeopleApi.getCurrentPerson(mGoogleApiClient) != null) {
//                Person currentPerson = Plus.PeopleApi.getCurrentPerson(mGoogleApiClient);
//
//                String personCoverUrl = currentPerson.getCover().getCoverPhoto().getUrl();
//                Picasso.with(this)
//                        .load(personCoverUrl)
//                        .error(R.drawable.copertina_about)
//                        .into(profileBackground);
            Picasso.with(this)
                    .load(R.drawable.gplus_default_cover)
                    .error(R.drawable.copertina_about)
                    .into(profileBackground);
//            if (LUtils.hasJB())
//                copertinaAccount.setBackground(new ColorDrawable(getThemeUtils().primaryColor()));
//            else
//                copertinaAccount.setBackgroundDrawable(new ColorDrawable(getThemeUtils().primaryColor()));
//            profileBackground.setVisibility(View.INVISIBLE);

//            Log.d(getClass().getName(), "acct.getPhotoUrl().toString():" + acct.getPhotoUrl().toString());
            Uri profilePhoto = acct.getPhotoUrl();
            if (profilePhoto != null) {
                String personPhotoUrl = profilePhoto.toString();
                // by default the profile url gives 50x50 px image only
                // we can replace the value with whatever dimension we want by
                // replacing sz=X
                personPhotoUrl = personPhotoUrl.substring(0,
                        personPhotoUrl.length() - 2)
                        + 400;
                Picasso.with(this)
                        .load(personPhotoUrl)
                        .error(R.drawable.gplus_default_avatar)
                        .into(profileImage);
            }
            else {
                Picasso.with(this)
                        .load(R.drawable.gplus_default_avatar)
                        .into(profileImage);
            }

            profileImage.setVisibility(View.VISIBLE);

            String personName = acct.getDisplayName();
//                Log.d(getClass().getName(), "personName: " + personName);
            usernameTextView.setText(personName);
            usernameTextView.setVisibility(View.VISIBLE);

//                String email = Plus.AccountApi.getAccountName(mGoogleApiClient);
            String email = acct.getEmail();
//                Log.d(getClass().getName(), "email: " + email);
            emailTextView.setText(email);
            emailTextView.setVisibility(View.VISIBLE);

            if (findViewById(R.id.sign_in_button) != null)
                findViewById(R.id.sign_in_button).setVisibility(View.INVISIBLE);

            accountMenu.setVisibility(View.VISIBLE);
//            }
        }
        else {
            profileImage.setVisibility(View.INVISIBLE);
            usernameTextView.setVisibility(View.INVISIBLE);
            emailTextView.setVisibility(View.INVISIBLE);
//            if (LUtils.hasJB())
//                copertinaAccount.setBackground(new ColorDrawable(ContextCompat.getColor(MainActivity.this, android.R.color.transparent)));
//            else
//                copertinaAccount.setBackgroundDrawable(new ColorDrawable(ContextCompat.getColor(MainActivity.this, android.R.color.transparent)));
//            profileBackground.setVisibility(View.VISIBLE);
//            profileBackground.setImageResource(R.drawable.copertina_about);
            Picasso.with(this)
                    .load(R.drawable.copertina_about)
                    .error(R.drawable.copertina_about)
                    .into(profileBackground);
            if (findViewById(R.id.sign_in_button) != null)
                findViewById(R.id.sign_in_button).setVisibility(View.VISIBLE);
            accountMenu.setVisibility(View.INVISIBLE);
        }
    }

    private void showProgressDialog() {
        if (mProgressDialog != null && !mProgressDialog.isShowing())
            mProgressDialog.show();
    }

    private void hideProgressDialog() {
        if (mProgressDialog != null && mProgressDialog.isShowing())
            mProgressDialog.hide();
    }

    public void setShowSnackbar(boolean showSnackbar) {
        this.showSnackbar = showSnackbar;
    }

    public GoogleApiClient getmGoogleApiClient() {
        return mGoogleApiClient;
    }

    /******************************************************************
     * controlla se il file è già esistente; se esiste lo cancella e poi lo ricrea
     * @param pFldr parent's ID
     * @param titl  file name
     * @param mime  file mime type  (application/x-sqlite3)
     * @param file  file (with content) to create
     */
    void saveCheckDupl(final DriveFolder pFldr, final String titl,
                       final String mime, final java.io.File file) {
        if (mGoogleApiClient != null && pFldr != null && titl != null && mime != null && file != null) try {
            // create content from file
            Query query = new Query.Builder()
                    .addFilter(Filters.eq(SearchableField.TITLE, DatabaseCanti.getDbName()))
                    .build();

            Drive.DriveApi.query(mGoogleApiClient, query).setResultCallback(new ResultCallback<DriveApi.MetadataBufferResult>() {
                @Override
                public void onResult(DriveApi.MetadataBufferResult metadataBufferResult) {

                    int count = metadataBufferResult.getMetadataBuffer().getCount() - 1;
                    Log.d(getClass().getName(), "Count: " + count);
                    if (count >= 0) {
                        DriveId mDriveId = metadataBufferResult.getMetadataBuffer().get(count).getDriveId();
                        Log.d(getClass().getName(), "driveIdRetrieved: " + mDriveId);
                        Log.d(getClass().getName(), "filesize in cloud " + metadataBufferResult.getMetadataBuffer().get(0).getFileSize());
                        metadataBufferResult.getMetadataBuffer().release();

                        DriveFile mfile = Drive.DriveApi.getFile(mGoogleApiClient, mDriveId);
                        mfile.delete(mGoogleApiClient).setResultCallback(new ResultCallback<Status>() {
                            @Override
                            public void onResult(Status status) {
                                if (status.isSuccess()) {
                                    Log.d(getClass().getName(), "CANCELLAZIONE OK: " + status.getStatusCode());
                                    saveToDrive(pFldr, titl, mime, file);
                                }
                                else {
                                    String errore = "ERRORE CANCELLAZIONE: " + status.getStatusCode() + "-" + status.getStatusMessage();
                                    Log.e(getClass().getName(), errore);
                                    if (backupDialog != null && backupDialog.isShowing())
                                        backupDialog.dismiss();
                                    Snackbar.make(findViewById(android.R.id.content), errore, Snackbar.LENGTH_SHORT).show();
                                }
                            }
                        });
                    }
                    else
                        saveToDrive(pFldr, titl, mime, file);
                }
            });
        } catch (Exception e) {
            Log.e(getClass().getName(), "ExceptionD: " + e.getLocalizedMessage(), e);
            if (backupDialog != null && backupDialog.isShowing())
                backupDialog.dismiss();
            String error = "error: "  + e.getLocalizedMessage();
            Snackbar.make(findViewById(android.R.id.content), error, Snackbar.LENGTH_SHORT).show();
        }
    }

    /******************************************************************
     * create file in GOODrive
     * @param pFldr parent's ID
     * @param titl  file name
     * @param mime  file mime type  (application/x-sqlite3)
     * @param file  file (with content) to create
     */
    void saveToDrive(final DriveFolder pFldr, final String titl,
                     final String mime, final java.io.File file) {
        if (mGoogleApiClient != null && pFldr != null && titl != null && mime != null && file != null) try {
            // create content from file
            Drive.DriveApi.newDriveContents(mGoogleApiClient).setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {
                @Override
                public void onResult(DriveApi.DriveContentsResult driveContentsResult) {
                    DriveContents cont = driveContentsResult != null && driveContentsResult.getStatus().isSuccess() ?
                            driveContentsResult.getDriveContents() : null;

                    // write file to content, chunk by chunk
                    if (cont != null) try {
                        OutputStream oos = cont.getOutputStream();
                        if (oos != null) try {
                            InputStream is = new FileInputStream(file);
                            byte[] buf = new byte[4096];
                            int c;
                            while ((c = is.read(buf, 0, buf.length)) > 0) {
                                oos.write(buf, 0, c);
                                oos.flush();
                            }
                        }
                        finally { oos.close();}

                        // content's COOL, create metadata
                        MetadataChangeSet meta = new MetadataChangeSet.Builder().setTitle(titl).setMimeType(mime).build();

                        // now create file on GooDrive
                        pFldr.createFile(mGoogleApiClient, meta, cont).setResultCallback(new ResultCallback<DriveFolder.DriveFileResult>() {
                            @Override
                            public void onResult(DriveFolder.DriveFileResult driveFileResult) {
                                if (driveFileResult != null && driveFileResult.getStatus().isSuccess()) {
                                    DriveFile dFil = driveFileResult != null && driveFileResult.getStatus().isSuccess() ?
                                            driveFileResult.getDriveFile() : null;
                                    if (dFil != null) {
                                        // BINGO , file uploaded
                                        dFil.getMetadata(mGoogleApiClient).setResultCallback(new ResultCallback<DriveResource.MetadataResult>() {
                                            @Override
                                            public void onResult(DriveResource.MetadataResult metadataResult) {
                                                if (metadataResult != null && metadataResult.getStatus().isSuccess()) {
                                                    DriveId mDriveId = metadataResult.getMetadata().getDriveId();
                                                    Log.d(getClass().getName(), "driveIdSaved: " + mDriveId);
                                                    if (backupDialog != null && backupDialog.isShowing())
                                                        backupDialog.dismiss();
                                                    String error = "FILE CARICATO - CODE: "  + metadataResult.getStatus().getStatusCode();
                                                    Log.d(getClass().getName(), error);
                                                    Snackbar.make(findViewById(android.R.id.content), R.string.gdrive_backup_success, Snackbar.LENGTH_SHORT).show();
                                                }
                                            }
                                        });
                                    }
                                } else {
                                 /* report error */
                                    if (backupDialog != null && backupDialog.isShowing())
                                        backupDialog.dismiss();
                                    String error = "driveFile error: "  + driveFileResult.getStatus().getStatusCode() + " - " + driveFileResult.getStatus().getStatusMessage();
                                    Snackbar.make(findViewById(android.R.id.content), error, Snackbar.LENGTH_SHORT).show();
                                }
                            }
                        });
                    } catch (Exception e) {
                        Log.e(getClass().getName(), "Exception1: " + e.getLocalizedMessage(), e);
                        if (backupDialog != null && backupDialog.isShowing())
                            backupDialog.dismiss();
                        String error = "error: "  + e.getLocalizedMessage();
                        Snackbar.make(findViewById(android.R.id.content), error, Snackbar.LENGTH_SHORT).show();
                    }
                }
            });
        } catch (Exception e) {
            Log.e(getClass().getName(), "Exception2: " + e.getLocalizedMessage(), e);
            if (backupDialog != null && backupDialog.isShowing())
                backupDialog.dismiss();
            String error = "error: "  + e.getLocalizedMessage();
            Snackbar.make(findViewById(android.R.id.content), error, Snackbar.LENGTH_SHORT).show();
        }
    }

    private File getDbPath() {
        Log.d(getClass().getName(), "dbpath:" + getDatabasePath(DatabaseCanti.getDbName()));
        return getDatabasePath(DatabaseCanti.getDbName());
    }

    void restoreDriveBackup() {
        Query query = new Query.Builder()
                .addFilter(Filters.eq(SearchableField.TITLE, DatabaseCanti.getDbName()))
                .build();

        Drive.DriveApi.query(mGoogleApiClient, query).setResultCallback(new ResultCallback<DriveApi.MetadataBufferResult>() {
            @Override
            public void onResult(DriveApi.MetadataBufferResult metadataBufferResult) {

            /*for(int i = 0 ;i < metadataBufferResult.getMetadataBuffer().getCount() ;i++) {
                debug("got index "+i);
                debug("filesize in cloud "+ metadataBufferResult.getMetadataBuffer().get(i).getFileSize());
                debug("driveId(1): "+ metadataBufferResult.getMetadataBuffer().get(i).getDriveId());
            }*/

                int count = metadataBufferResult.getMetadataBuffer().getCount() - 1;
                Log.d(getClass().getName(), "Count: " + count);
                DriveId mDriveId = metadataBufferResult.getMetadataBuffer().get(0).getDriveId();
                Log.d(getClass().getName(), "driveIdRetrieved: " + mDriveId);
                Log.d(getClass().getName(), "filesize in cloud " + metadataBufferResult.getMetadataBuffer().get(0).getFileSize());
                metadataBufferResult.getMetadataBuffer().release();


                DriveFile mfile = Drive.DriveApi.getFile(mGoogleApiClient, mDriveId);
                mfile.open(mGoogleApiClient, DriveFile.MODE_READ_ONLY, new DriveFile.DownloadProgressListener() {
                    @Override
                    public void onProgress(long bytesDown, long bytesExpected) {}
                })
                        .setResultCallback(restoreContentsCallback);
            }
        });
    }

    final private ResultCallback<DriveApi.DriveContentsResult> restoreContentsCallback =
            new ResultCallback<DriveApi.DriveContentsResult>() {
                @Override
                public void onResult(DriveApi.DriveContentsResult result) {
                    if (!result.getStatus().isSuccess()) {
//                        mToast("Unable to open file, try again.");
                        String error = "restore error: "  + result.getStatus().getStatusCode() + " - " + result.getStatus().getStatusMessage();
                        Log.e(getClass().getName(), error);
                        if (restoreDialog != null && restoreDialog.isShowing())
                            restoreDialog.dismiss();
                        Snackbar.make(findViewById(android.R.id.content), error, Snackbar.LENGTH_SHORT).show();
                        return;
                    }

                    DatabaseCanti listaCanti = new DatabaseCanti(MainActivity.this);
                    listaCanti.close();
//                    utilsM.dbClose();

                    File db_file = getDbPath();
                    String path = db_file.getPath();

                    if (!db_file.exists())
                        db_file.delete();

                    db_file = new File(path);

                    DriveContents contents = result.getDriveContents();

                    try {
                        FileOutputStream fos = new FileOutputStream(db_file);
                        BufferedOutputStream bos = new BufferedOutputStream(fos);
                        BufferedInputStream in = new BufferedInputStream(contents.getInputStream());

                        byte[] buffer = new byte[1024];
                        int n, cnt = 0;


                        //debug("before read " + in.available());

                        while( ( n = in.read(buffer) ) > 0) {
                            bos.write(buffer, 0, n);
                            cnt += n;
//                            debug("buffer: " + buffer[0]);
//                            debug("buffer: " + buffer[1]);
//                            debug("buffer: " + buffer[2]);
//                            debug("buffer: " + buffer[3]);
                            bos.flush();
                        }

                        //debug(" read done: " + cnt);

                        bos.close();

                    } catch (FileNotFoundException e) {
                        Log.e(getClass().getName(), "Exception3: " + e.getLocalizedMessage(), e);
                        contents.discard(mGoogleApiClient);
                        if (restoreDialog != null && restoreDialog.isShowing())
                            restoreDialog.dismiss();
                        String error = "error: "  + e.getLocalizedMessage();
                        Snackbar.make(findViewById(android.R.id.content), error, Snackbar.LENGTH_SHORT).show();
                        return;
                    } catch (IOException e) {
                        Log.e(getClass().getName(), "Exception4: " + e.getLocalizedMessage(), e);
                        contents.discard(mGoogleApiClient);
                        if (restoreDialog != null && restoreDialog.isShowing())
                            restoreDialog.dismiss();
                        String error = "error: "  + e.getLocalizedMessage();
                        Snackbar.make(findViewById(android.R.id.content), error, Snackbar.LENGTH_SHORT).show();
                        return;
                    }

//                    mToast(act.getResources().getString(R.string.restoreComplete));
                    Snackbar.make(findViewById(android.R.id.content), R.string.gdrive_restore_success, Snackbar.LENGTH_SHORT).show();
//                    DialogFragment_Sync.dismissDialog();
                    if (restoreDialog != null && restoreDialog.isShowing())
                        restoreDialog.dismiss();

                    listaCanti = new DatabaseCanti(MainActivity.this);
                    SQLiteDatabase db = listaCanti.getReadableDatabase();
//                    utilsM.dbOpen();
                    contents.discard(mGoogleApiClient);

                }
            };
}
