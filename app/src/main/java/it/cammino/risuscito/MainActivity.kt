package it.cammino.risuscito

import android.annotation.SuppressLint
import android.arch.lifecycle.ViewModelProviders
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.annotation.ColorInt
import android.support.design.widget.FloatingActionButton
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v4.widget.SlidingPaneLayout
import android.util.Log
import android.view.MenuItem
import android.view.View
import com.afollestad.materialcab.MaterialCab
import com.afollestad.materialdialogs.color.ColorChooserDialog
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.google.firebase.analytics.FirebaseAnalytics
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.crossfader.Crossfader
import com.mikepenz.crossfader.view.ICrossFadeSlidingPaneLayout
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.materialdrawer.*
import com.mikepenz.materialdrawer.model.DividerDrawerItem
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.ProfileDrawerItem
import com.mikepenz.materialdrawer.model.ProfileSettingDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IProfile
import com.mikepenz.materialize.util.UIUtils
import it.cammino.risuscito.database.RisuscitoDatabase
import it.cammino.risuscito.dialogs.SimpleDialogFragment
import it.cammino.risuscito.ui.CrossfadeWrapper
import it.cammino.risuscito.ui.ThemeableActivity
import it.cammino.risuscito.viewmodels.MainActivityViewModel
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.common_circle_progress.*
import kotlinx.android.synthetic.main.risuscito_toolbar_noelevation.*
import kotlinx.android.synthetic.main.risuscito_toolbar_noelevation.view.*
import java.lang.ref.WeakReference
import java.util.*

class MainActivity : ThemeableActivity(), ColorChooserDialog.ColorCallback, SimpleDialogFragment.SimpleCallback {
    private var mViewModel: MainActivityViewModel? = null
    private var mLUtils: LUtils? = null
    var materialCab: MaterialCab? = null
        private set
    var drawer: Drawer? = null
        private set
    private var mMiniDrawer: MiniDrawer? = null
    private var crossFader: Crossfader<*>? = null
    private var mAccountHeader: AccountHeader? = null
    var isOnTablet: Boolean = false
        private set
    private var acct: GoogleSignInAccount? = null
    private var mSignInClient: GoogleSignInClient? = null
    private var mRegularFont: Typeface? = null
    private var mMediumFont: Typeface? = null

    private val nextStepReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Implement UI change code here once notification is received
            try {
                Log.v(TAG, "BROADCAST_NEXT_STEP")
                if (intent.getStringExtra("WHICH") != null) {
                    val which = intent.getStringExtra("WHICH")
                    Log.v(TAG, "NEXT_STEP: " + which)
                    if (which.equals("RESTORE", ignoreCase = true)) {
                        val sFragment = SimpleDialogFragment.findVisible(this@MainActivity, "RESTORE_RUNNING")
                        sFragment?.setContent(R.string.restoring_settings)
                    } else {
                        val sFragment = SimpleDialogFragment.findVisible(this@MainActivity, "BACKUP_RUNNING")
                        sFragment?.setContent(R.string.backup_settings)
                    }
                }
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, e.localizedMessage, e)
            }

        }
    }

    private val lastStepReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Implement UI change code here once notification is received
            try {
                Log.v(TAG, "BROADCAST_LAST_STEP")
                if (intent.getStringExtra("WHICH") != null) {
                    val which = intent.getStringExtra("WHICH")
                    Log.v(TAG, "NEXT_STEP: " + which)
                    if (which.equals("RESTORE", ignoreCase = true)) {
                        dismissDialog("RESTORE_RUNNING")
                        SimpleDialogFragment.Builder(this@MainActivity, this@MainActivity, "RESTART")
                                .title(R.string.general_message)
                                .content(R.string.gdrive_restore_success)
                                .positiveButton(android.R.string.ok)
                                .show()
                    } else {
                        dismissDialog("BACKUP_RUNNING")
                        Snackbar.make(
                                findViewById(R.id.main_content),
                                R.string.gdrive_backup_success,
                                Snackbar.LENGTH_LONG)
                                .show()
                    }
                }
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, e.localizedMessage, e)
            }

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.hasNavDrawer = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mViewModel = ViewModelProviders.of(this).get(MainActivityViewModel::class.java)

        mRegularFont = Typeface.createFromAsset(assets, "fonts/Roboto-Regular.ttf")
        mMediumFont = Typeface.createFromAsset(assets, "fonts/Roboto-Medium.ttf")

        val icon = IconicsDrawable(this)
                .icon(CommunityMaterial.Icon.cmd_menu)
                .color(Color.WHITE)
                .sizeDp(24)
                .paddingDp(2)

        risuscito_toolbar!!.setBackgroundColor(themeUtils!!.primaryColor())
        risuscito_toolbar!!.navigationIcon = icon
        setSupportActionBar(risuscito_toolbar)

        supportActionBar!!.setDisplayShowTitleEnabled(false)

        if (intent.getBooleanExtra(Utility.DB_RESET, false)) {
            TranslationTask(this@MainActivity).execute()
        }

        mLUtils = LUtils.getInstance(this@MainActivity)
        isOnTablet = mLUtils!!.isOnTablet
        Log.d(TAG, "onCreate: isOnTablet = " + isOnTablet)

        if (isOnTablet && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            window.statusBarColor = themeUtils!!.primaryColorDark()

        if (isOnTablet)
            tabletToolbarBackground!!.setBackgroundColor(themeUtils!!.primaryColor())
        else
            material_tabs!!.setBackgroundColor(themeUtils!!.primaryColor())

        setupNavDrawer(savedInstanceState)

        materialCab = MaterialCab(this, R.id.cab_stub)
                .setBackgroundColor(themeUtils!!.primaryColorDark())
                .setPopupMenuTheme(R.style.ThemeOverlay_AppCompat_Light)
                .setContentInsetStartRes(R.dimen.mcab_default_content_inset)

        if (savedInstanceState == null) {
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.content_frame, Risuscito(), R.id.navigation_home.toString())
                    .commit()
        }
        if (!isOnTablet) toolbar_layout!!.setExpanded(true, false)

        // [START configure_signin]
        // Configure sign-in to request the user's ID, email address, and basic
        // profile. ID and basic profile are included in DEFAULT_SIGN_IN.
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                //                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .requestScopes(Scope(Scopes.DRIVE_FILE), Scope(Scopes.DRIVE_FILE))
                .build()
        // [END configure_signin]

        // [START build_client]
        mSignInClient = GoogleSignIn.getClient(this, gso)
        // [END build_client]

        FirebaseAnalytics.getInstance(this)

        setDialogCallback("BACKUP_ASK")
        setDialogCallback("RESTORE_ASK")
        setDialogCallback("SIGNOUT")
        setDialogCallback("REVOKE")
        setDialogCallback("RESTART")

        // registra un receiver per ricevere la notifica di preparazione della registrazione
        registerReceiver(nextStepReceiver, IntentFilter("BROADCAST_NEXT_STEP"))
        registerReceiver(lastStepReceiver, IntentFilter("BROADCAST_LAST_STEP"))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(nextStepReceiver)
        unregisterReceiver(lastStepReceiver)
    }

    public override fun onStart() {
        super.onStart()
        val task = mSignInClient!!.silentSignIn()
        if (task.isSuccessful) {
            // If the user's cached credentials are valid, the OptionalPendingResult will be "done"
            // and the GoogleSignInResult will be available instantly.
            Log.d(TAG, "Got cached sign-in")
            handleSignInResult(task)
        } else {
            // If the user has not previously signed in on this device or the sign-in has expired,
            // this asynchronous branch will attempt to sign in the user silently.  Cross-device
            // single sign-on will occur in this branch.
            showProgressDialog()

            task.addOnCompleteListener({ mTask: Task<GoogleSignInAccount> ->
                Log.d(TAG, "Reconnected")
                handleSignInResult(mTask)
            })
        }
    }

    override fun onResume() {
        super.onResume()
        hideProgressDialog()
    }

    public override fun onSaveInstanceState(savedInstanceState: Bundle?) {
        val mSavedInstanceState = drawer!!.saveInstanceState(savedInstanceState)
        super.onSaveInstanceState(mSavedInstanceState)
    }

    private fun setupNavDrawer(savedInstanceState: Bundle?) {

        val profile = ProfileDrawerItem()
                .withName("")
                .withEmail("")
                .withIcon(R.mipmap.profile_picture)
                .withIdentifier(PROF_ID)
                .withTypeface(mRegularFont)

        // Create the AccountHeader
        mAccountHeader = AccountHeaderBuilder()
                .withActivity(this@MainActivity)
                .withTranslucentStatusBar(!isOnTablet)
                .withSelectionListEnabledForSingleProfile(false)
                .withHeaderBackground(
                        if (isOnTablet)
                            ColorDrawable(ContextCompat.getColor(this, R.color.floating_background))
                        else
                            ColorDrawable(themeUtils!!.primaryColor()))
                .withSavedInstance(savedInstanceState)
                .addProfiles(profile)
                .withNameTypeface(mMediumFont!!)
                .withEmailTypeface(mRegularFont!!)
                .withOnAccountHeaderListener { _, mProfile, _ ->
                    // sample usage of the onProfileChanged listener
                    // if the clicked item has the identifier 1 add a new profile ;)
                    if (mProfile is IDrawerItem<*, *> && mProfile.getIdentifier() == R.id.gdrive_backup.toLong()) {
                        SimpleDialogFragment.Builder(
                                this@MainActivity, this@MainActivity, "BACKUP_ASK")
                                .title(R.string.gdrive_backup)
                                .content(R.string.gdrive_backup_content)
                                .positiveButton(android.R.string.yes)
                                .negativeButton(android.R.string.no)
                                .show()
                    } else if (mProfile is IDrawerItem<*, *> && mProfile.getIdentifier() == R.id.gdrive_restore.toLong()) {
                        SimpleDialogFragment.Builder(
                                this@MainActivity, this@MainActivity, "RESTORE_ASK")
                                .title(R.string.gdrive_restore)
                                .content(R.string.gdrive_restore_content)
                                .positiveButton(android.R.string.yes)
                                .negativeButton(android.R.string.no)
                                .show()
                    } else if (mProfile is IDrawerItem<*, *> && mProfile.getIdentifier() == R.id.gplus_signout.toLong()) {
                        SimpleDialogFragment.Builder(
                                this@MainActivity, this@MainActivity, "SIGNOUT")
                                .title(R.string.gplus_signout)
                                .content(R.string.dialog_acc_disconn_text)
                                .positiveButton(android.R.string.yes)
                                .negativeButton(android.R.string.no)
                                .show()
                    } else if (mProfile is IDrawerItem<*, *> && mProfile.getIdentifier() == R.id.gplus_revoke.toLong()) {
                        SimpleDialogFragment.Builder(
                                this@MainActivity, this@MainActivity, "REVOKE")
                                .title(R.string.gplus_revoke)
                                .content(R.string.dialog_acc_revoke_text)
                                .positiveButton(android.R.string.yes)
                                .negativeButton(android.R.string.no)
                                .show()
                    }

                    // false if you have not consumed the event and it should close the drawer
                    false
                }
                .build()

        val mDrawerBuilder = DrawerBuilder()
                .withActivity(this)
                .withToolbar(risuscito_toolbar!!)
                .withHasStableIds(true)
                .withAccountHeader(mAccountHeader!!)
                .addDrawerItems(
                        PrimaryDrawerItem()
                                .withName(R.string.activity_homepage)
                                .withIcon(CommunityMaterial.Icon.cmd_home)
                                .withIdentifier(R.id.navigation_home.toLong())
                                .withSelectedIconColor(themeUtils!!.primaryColor())
                                .withSelectedTextColor(themeUtils!!.primaryColor())
                                .withTypeface(mMediumFont),
                        PrimaryDrawerItem()
                                .withName(R.string.search_name_text)
                                .withIcon(CommunityMaterial.Icon.cmd_magnify)
                                .withIdentifier(R.id.navigation_search.toLong())
                                .withSelectedIconColor(themeUtils!!.primaryColor())
                                .withSelectedTextColor(themeUtils!!.primaryColor())
                                .withTypeface(mMediumFont),
                        PrimaryDrawerItem()
                                .withName(R.string.title_activity_general_index)
                                .withIcon(CommunityMaterial.Icon.cmd_view_list)
                                .withIdentifier(R.id.navigation_indexes.toLong())
                                .withSelectedIconColor(themeUtils!!.primaryColor())
                                .withSelectedTextColor(themeUtils!!.primaryColor())
                                .withTypeface(mMediumFont),
                        PrimaryDrawerItem()
                                .withName(R.string.title_activity_custom_lists)
                                .withIcon(CommunityMaterial.Icon.cmd_view_carousel)
                                .withIdentifier(R.id.navitagion_lists.toLong())
                                .withSelectedIconColor(themeUtils!!.primaryColor())
                                .withSelectedTextColor(themeUtils!!.primaryColor())
                                .withTypeface(mMediumFont),
                        PrimaryDrawerItem()
                                .withName(R.string.action_favourites)
                                .withIcon(CommunityMaterial.Icon.cmd_heart)
                                .withIdentifier(R.id.navigation_favorites.toLong())
                                .withSelectedIconColor(themeUtils!!.primaryColor())
                                .withSelectedTextColor(themeUtils!!.primaryColor())
                                .withTypeface(mMediumFont),
                        PrimaryDrawerItem()
                                .withName(R.string.title_activity_consegnati)
                                .withIcon(CommunityMaterial.Icon.cmd_clipboard_check)
                                .withIdentifier(R.id.navigation_consegnati.toLong())
                                .withSelectedIconColor(themeUtils!!.primaryColor())
                                .withSelectedTextColor(themeUtils!!.primaryColor())
                                .withTypeface(mMediumFont),
                        PrimaryDrawerItem()
                                .withName(R.string.title_activity_history)
                                .withIcon(CommunityMaterial.Icon.cmd_history)
                                .withIdentifier(R.id.navigation_history.toLong())
                                .withSelectedIconColor(themeUtils!!.primaryColor())
                                .withSelectedTextColor(themeUtils!!.primaryColor())
                                .withTypeface(mMediumFont),
                        PrimaryDrawerItem()
                                .withName(R.string.title_activity_settings)
                                .withIcon(CommunityMaterial.Icon.cmd_settings)
                                .withIdentifier(R.id.navigation_settings.toLong())
                                .withSelectedIconColor(themeUtils!!.primaryColor())
                                .withSelectedTextColor(themeUtils!!.primaryColor())
                                .withTypeface(mMediumFont),
                        DividerDrawerItem(),
                        PrimaryDrawerItem()
                                .withName(R.string.title_activity_about)
                                .withIcon(CommunityMaterial.Icon.cmd_information_outline)
                                .withIdentifier(R.id.navigation_changelog.toLong())
                                .withSelectedIconColor(themeUtils!!.primaryColor())
                                .withSelectedTextColor(themeUtils!!.primaryColor())
                                .withTypeface(mMediumFont))
                .withOnDrawerItemClickListener(
                        Drawer.OnDrawerItemClickListener { _, _, drawerItem ->
                            // check if the drawerItem is set.
                            // there are different reasons for the drawerItem to be null
                            // --> click on the header
                            // --> click on the footer
                            // those items don't contain a drawerItem

                            if (drawerItem != null) {
                                val fragment: Fragment
                                if (drawerItem.identifier == R.id.navigation_home.toLong()) {
                                    fragment = Risuscito()
                                    if (!isOnTablet)
                                        toolbar_layout!!.setExpanded(true, true)
                                } else if (drawerItem.identifier == R.id.navigation_search.toLong()) {
                                    fragment = GeneralSearch()
                                } else if (drawerItem.identifier == R.id.navigation_indexes.toLong()) {
                                    fragment = GeneralIndex()
                                } else if (drawerItem.identifier == R.id.navitagion_lists.toLong()) {
                                    fragment = CustomLists()
                                } else if (drawerItem.identifier == R.id.navigation_favorites.toLong()) {
                                    fragment = FavouritesActivity()
                                } else if (drawerItem.identifier == R.id.navigation_settings.toLong()) {
                                    fragment = SettingsFragment()
                                } else if (drawerItem.identifier == R.id.navigation_changelog.toLong()) {
                                    fragment = AboutFragment()
                                } else if (drawerItem.identifier == R.id.navigation_consegnati.toLong()) {
                                    fragment = ConsegnatiFragment()
                                } else if (drawerItem.identifier == R.id.navigation_history.toLong()) {
                                    fragment = HistoryFragment()
                                } else
                                    return@OnDrawerItemClickListener true

                                // creo il nuovo fragment solo se non è lo stesso che sto già visualizzando
                                val myFragment = supportFragmentManager
                                        .findFragmentByTag(drawerItem.identifier.toString())
                                if (myFragment == null || !myFragment.isVisible) {
                                    val transaction = supportFragmentManager.beginTransaction()
                                    if (!isOnTablet)
                                        transaction.setCustomAnimations(
                                                R.anim.slide_in_right, R.anim.slide_out_left)
                                    transaction
                                            .replace(
                                                    R.id.content_frame,
                                                    fragment,
                                                    drawerItem.identifier.toString())
                                            .commit()
                                }

                                if (isOnTablet) mMiniDrawer!!.setSelection(drawerItem.identifier)
                            }
                            isOnTablet
                        })
                .withGenerateMiniDrawer(isOnTablet)
                .withSavedInstance(savedInstanceState)
                .withTranslucentStatusBar(!isOnTablet)

        if (isOnTablet) {
            drawer = mDrawerBuilder.buildView()
            // the MiniDrawer is managed by the Drawer and we just get it to hook it into the Crossfader
            mMiniDrawer = drawer!!
                    .miniDrawer
                    .withEnableSelectedMiniDrawerItemBackground(true)
                    .withIncludeSecondaryDrawerItems(true)

            // get the widths in px for the first and second panel
            val firstWidth = UIUtils.convertDpToPixel(302f, this).toInt()
            val secondWidth = UIUtils.convertDpToPixel(72f, this).toInt()

            // create and build our crossfader (see the MiniDrawer is also builded in here, as the build
            // method returns the view to be used in the crossfader)
            crossFader = Crossfader<MyClass>()
                    .withContent(findViewById<View>(R.id.main_frame))
                    .withFirst(drawer!!.slider, firstWidth)
                    .withSecond(mMiniDrawer!!.build(this), secondWidth)
                    .withSavedInstance(savedInstanceState)
                    .withGmailStyleSwiping()
                    .build()

            // define the crossfader to be used with the miniDrawer. This is required to be able to
            // automatically toggle open / close
            mMiniDrawer!!.withCrossFader(CrossfadeWrapper(crossFader as Crossfader<*>))

            // define a shadow (this is only for normal LTR layouts if you have a RTL app you need to
            // define the other one
            crossFader!!
                    .getCrossFadeSlidingPaneLayout()
                    .setShadowResourceLeft(R.drawable.material_drawer_shadow_left)
            crossFader!!
                    .getCrossFadeSlidingPaneLayout()
                    .setShadowResourceRight(R.drawable.material_drawer_shadow_right)
        } else {
            drawer = mDrawerBuilder.build()
            drawer!!.drawerLayout.setStatusBarBackgroundColor(themeUtils!!.primaryColorDark())
        }
    }

    override fun onBackPressed() {
        Log.d(TAG, "onBackPressed: ")
        if (isOnTablet) {
            if (crossFader != null && crossFader!!.isCrossFaded()) {
                crossFader!!.crossFade()
                return
            }
        } else {
            if (drawer != null && drawer!!.isDrawerOpen) {
                drawer!!.closeDrawer()
                return
            }
        }

        val myFragment = supportFragmentManager.findFragmentByTag(R.id.navigation_home.toString())
        if (myFragment != null && myFragment.isVisible) {
            finish()
            return
        }

        if (isOnTablet)
            mMiniDrawer!!.setSelection(R.id.navigation_home.toLong())
        else {
            toolbar_layout!!.setExpanded(true, true)
        }
        drawer!!.setSelection(R.id.navigation_home.toLong())
    }

    override fun onColorSelection(
            colorChooserDialog: ColorChooserDialog, @ColorInt color: Int) {
        if (colorChooserDialog.isAccentMode)
            themeUtils!!.accentColor(color)
        else
            themeUtils!!.primaryColor(color)

        recreate()
    }

    override fun onColorChooserDismissed(dialog: ColorChooserDialog) {}

    // converte gli accordi salvati dalla lingua vecchia alla nuova
    //  private void convertTabs(SQLiteDatabase db, String conversion) {
    //  private void convertTabs(String conversion) {
    private fun convertTabs() {
        val conversion = intent.getStringExtra(Utility.CHANGE_LANGUAGE)

        var accordi1 = CambioAccordi.accordi_it
        Log.d(TAG, "convertTabs - from: " + conversion.substring(0, 2))
        when (conversion.substring(0, 2)) {
            "uk" -> accordi1 = CambioAccordi.accordi_uk
            "en" -> accordi1 = CambioAccordi.accordi_en
        }

        var accordi2 = CambioAccordi.accordi_it
        Log.d(TAG, "convertTabs - to: " + conversion.substring(3, 5))
        when (conversion.substring(3, 5)) {
            "uk" -> accordi2 = CambioAccordi.accordi_uk
            "en" -> accordi2 = CambioAccordi.accordi_en
        }

        val mappa = HashMap<String, String>()
        for (i in CambioAccordi.accordi_it.indices) mappa.put(accordi1[i], accordi2[i])

        val mDao = RisuscitoDatabase.getInstance(this@MainActivity).cantoDao()
        val canti = mDao.allByName
        for (canto in canti) {
            if (canto.savedTab != null && !canto.savedTab!!.isEmpty()) {
                Log.d(
                        TAG,
                        "convertTabs: "
                                + "ID "
                                + canto.id
                                + " -> CONVERTO DA "
                                + canto.savedTab
                                + " A "
                                + mappa[canto.savedTab!!])
                canto.savedTab = mappa[canto.savedTab!!]
                mDao.updateCanto(canto)
            }
        }
    }

    // converte gli accordi salvati dalla lingua vecchia alla nuova
    //  private void convertiBarre(SQLiteDatabase db, String conversion) {
    //  private void convertiBarre(String conversion) {
    private fun convertiBarre() {
        val conversion = intent.getStringExtra(Utility.CHANGE_LANGUAGE)

        var barre1 = CambioAccordi.barre_it
        Log.d(TAG, "convertiBarre - from: " + conversion.substring(0, 2))
        when (conversion.substring(0, 2)) {
            "uk" -> barre1 = CambioAccordi.barre_uk
            "en" -> barre1 = CambioAccordi.barre_en
        }

        var barre2 = CambioAccordi.barre_it
        Log.d(TAG, "convertiBarre - to: " + conversion.substring(3, 5))
        when (conversion.substring(3, 5)) {
            "uk" -> barre2 = CambioAccordi.barre_uk
            "en" -> barre2 = CambioAccordi.barre_en
        }

        val mappa = HashMap<String, String>()
        for (i in CambioAccordi.barre_it.indices) mappa.put(barre1[i], barre2[i])

        val mDao = RisuscitoDatabase.getInstance(this@MainActivity).cantoDao()
        val canti = mDao.allByName
        for (canto in canti) {
            if (canto.savedTab != null && !canto.savedTab!!.isEmpty()) {
                Log.d(
                        TAG,
                        "convertiBarre: "
                                + "ID "
                                + canto.id
                                + " -> CONVERTO DA "
                                + canto.savedBarre
                                + " A "
                                + mappa[canto.savedBarre])
                canto.savedBarre = mappa[canto.savedBarre]
                mDao.updateCanto(canto)
            }
        }
    }

    fun setupToolbarTitle(titleResId: Int) {
        risuscito_toolbar!!.main_toolbarTitle.setText(titleResId)
    }

    fun enableFab(enable: Boolean) {
        Log.d(TAG, "enableFab: " + enable)
        val mFab = findViewById<FloatingActionButton>(R.id.fab_pager)
        if (enable)
            mFab.show()
        else
            mFab.hide()
    }

    fun enableBottombar(enabled: Boolean) {
        Log.d(TAG, "enableBottombar - enabled: " + enabled)
        val mBottomBar = findViewById<View>(R.id.bottom_bar)
        if (enabled)
            mLUtils!!.animateIn(mBottomBar)
        else
            mBottomBar.visibility = View.GONE
        //            mBottomBar.setVisibility(enabled ? View.VISIBLE : View.GONE);
    }
    // [END signIn]

    // [START signIn]
    fun signIn() {
        //    Intent signInIntent = Auth.GoogleSignInApi.getSignInIntent(mGoogleApiClient);
        val signInIntent = mSignInClient!!.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }
    // [END signOut]

    // [START signOut]
    private fun signOut() {
        mSignInClient!!
                .signOut()
                .addOnCompleteListener {
                    updateUI(false)
                    val editor = PreferenceManager.getDefaultSharedPreferences(this@MainActivity).edit()
                    editor.putBoolean(Utility.SIGNED_IN, false)
                    editor.apply()
                    Snackbar.make(
                            findViewById(R.id.main_content),
                            R.string.disconnected,
                            Snackbar.LENGTH_SHORT)
                            .show()
                }
    }
    // [END revokeAccess]

    // [START revokeAccess]
    private fun revokeAccess() {
        mSignInClient!!
                .revokeAccess()
                .addOnCompleteListener {
                    updateUI(false)
                    val editor = PreferenceManager.getDefaultSharedPreferences(this@MainActivity).edit()
                    editor.putBoolean(Utility.SIGNED_IN, false)
                    editor.apply()
                    Snackbar.make(
                            findViewById(R.id.main_content),
                            R.string.disconnected,
                            Snackbar.LENGTH_SHORT)
                            .show()
                }
    }

    // [START onActivityResult]
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.d(TAG, "requestCode: " + requestCode)
        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        when (requestCode) {
            RC_SIGN_IN -> handleSignInResult(GoogleSignIn.getSignedInAccountFromIntent(data))
            else -> {
            }
        }
    }
    // [END handleSignInResult]

    // [START handleSignInResult]
    private fun handleSignInResult(task: Task<GoogleSignInAccount>) {
        //    Log.d(getClass().getName(), "handleSignInResult:" + result.isSuccess());
        Log.d(TAG, "handleSignInResult:" + task.isSuccessful)
        if (task.isSuccessful) {
            // Signed in successfully, show authenticated UI.
            acct = GoogleSignIn.getLastSignedInAccount(this@MainActivity)
            val editor = PreferenceManager.getDefaultSharedPreferences(this@MainActivity).edit()
            editor.putBoolean(Utility.SIGNED_IN, true)
            editor.apply()
            if (mViewModel!!.showSnackbar) {
                Snackbar.make(
                        findViewById(R.id.main_content),
                        getString(R.string.connected_as, acct!!.displayName),
                        Snackbar.LENGTH_SHORT)
                        .show()
                mViewModel!!.showSnackbar = false
            }
            updateUI(true)
        } else {
            // Sign in failed, handle failure and update UI
            Snackbar.make(
                    findViewById(R.id.main_content),
                    getString(
                            R.string.login_failed,
                            -1,
                            task.exception!!.localizedMessage),
                    Snackbar.LENGTH_SHORT)
                    .show()
            acct = null
            updateUI(false)
        }
    }

    private fun updateUI(signedIn: Boolean) {
        //        AccountHeader headerResult;
        val intentBroadcast = Intent(Risuscito.BROADCAST_SIGNIN_VISIBLE)
        Log.d(TAG, "updateUI: DATA_VISIBLE " + !signedIn)
        intentBroadcast.putExtra(Risuscito.DATA_VISIBLE, !signedIn)
        sendBroadcast(intentBroadcast)
        if (signedIn) {
            val profile: IProfile<*>
            val profilePhoto = acct!!.photoUrl
            if (profilePhoto != null) {
                var personPhotoUrl = profilePhoto.toString()
                personPhotoUrl = personPhotoUrl.substring(0, personPhotoUrl.length - 2) + 400
                profile = ProfileDrawerItem()
                        .withName(acct!!.displayName)
                        .withEmail(acct!!.email)
                        .withIcon(personPhotoUrl)
                        .withIdentifier(PROF_ID)
                        .withTypeface(mRegularFont)
            } else {
                profile = ProfileDrawerItem()
                        .withName(acct!!.displayName)
                        .withEmail(acct!!.email)
                        .withIcon(R.mipmap.profile_picture)
                        .withIdentifier(PROF_ID)
                        .withTypeface(mRegularFont)
            }
            // Create the AccountHeader
            mAccountHeader!!.updateProfile(profile)
            if (mAccountHeader!!.profiles.size == 1) {
                mAccountHeader!!.addProfiles(
                        ProfileSettingDrawerItem()
                                .withName(getString(R.string.gdrive_backup))
                                .withIcon(CommunityMaterial.Icon.cmd_cloud_upload)
                                .withIdentifier(R.id.gdrive_backup.toLong()),
                        ProfileSettingDrawerItem()
                                .withName(getString(R.string.gdrive_restore))
                                .withIcon(CommunityMaterial.Icon.cmd_cloud_download)
                                .withIdentifier(R.id.gdrive_restore.toLong()),
                        ProfileSettingDrawerItem()
                                .withName(getString(R.string.gplus_signout))
                                .withIcon(CommunityMaterial.Icon.cmd_account_remove)
                                .withIdentifier(R.id.gplus_signout.toLong()),
                        ProfileSettingDrawerItem()
                                .withName(getString(R.string.gplus_revoke))
                                .withIcon(CommunityMaterial.Icon.cmd_account_key)
                                .withIdentifier(R.id.gplus_revoke.toLong()))
            }
            if (isOnTablet) mMiniDrawer!!.onProfileClick()
        } else {
            val profile = ProfileDrawerItem()
                    .withName("")
                    .withEmail("")
                    .withIcon(R.mipmap.profile_picture)
                    .withIdentifier(PROF_ID)
                    .withTypeface(mRegularFont)
            if (mAccountHeader!!.profiles.size > 1) {
                mAccountHeader!!.removeProfile(1)
                mAccountHeader!!.removeProfile(1)
                mAccountHeader!!.removeProfile(1)
                mAccountHeader!!.removeProfile(1)
            }
            mAccountHeader!!.updateProfile(profile)
            if (isOnTablet) mMiniDrawer!!.onProfileClick()
        }
        hideProgressDialog()
    }

    private fun showProgressDialog() {
        loadingBar!!.visibility = View.VISIBLE
    }

    private fun hideProgressDialog() {
        loadingBar!!.visibility = View.GONE
    }

    fun setShowSnackbar() {
        this.mViewModel!!.showSnackbar = true
    }

    override fun onPositive(tag: String) {
        Log.d(TAG, "onPositive: TAG " + tag)
        when (tag) {
            "BACKUP_ASK" -> {
                SimpleDialogFragment.Builder(this@MainActivity, this@MainActivity, "BACKUP_RUNNING")
                        .title(R.string.backup_running)
                        .content(R.string.backup_database)
                        .showProgress()
                        .progressIndeterminate(true)
                        .progressMax(0)
                        .show()
                BackupTask().execute()
            }
            "RESTORE_ASK" -> {
                SimpleDialogFragment.Builder(this@MainActivity, this@MainActivity, "RESTORE_RUNNING")
                        .title(R.string.restore_running)
                        .content(R.string.restoring_database)
                        .showProgress()
                        .progressIndeterminate(true)
                        .progressMax(0)
                        .show()
                RestoreTask().execute()
            }
            "SIGNOUT" -> signOut()
            "REVOKE" -> revokeAccess()
            "RESTART" -> {
                val i = baseContext
                        .packageManager
                        .getLaunchIntentForPackage(baseContext.packageName)
                i?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(i)
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        Log.d(TAG, "onOptionsItemSelected: " + item.itemId)
        if (isOnTablet && item.itemId == android.R.id.home) {
            crossFader!!.crossFade()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onNegative(tag: String) {}

    override fun onNeutral(tag: String) {}

    private fun dismissDialog(tag: String) {
        val sFragment = SimpleDialogFragment.findVisible(this@MainActivity, tag)
        sFragment?.dismiss()
    }

    private fun setDialogCallback(tag: String) {
        val sFragment = SimpleDialogFragment.findVisible(this@MainActivity, tag)
        sFragment?.setmCallback(this@MainActivity)
    }

    private class TranslationTask internal constructor(activity: MainActivity) : AsyncTask<Void, Void, Void>() {

        private val activityWeakReference: WeakReference<MainActivity> = WeakReference(activity)

        override fun doInBackground(vararg sUrl: Void): Void? {
            activityWeakReference.get()!!.intent.removeExtra(Utility.DB_RESET)
            val listaCanti = DatabaseCanti(activityWeakReference.get())
            val db = listaCanti.readableDatabase
            listaCanti.reCreateDatabse(db)
            db.close()
            listaCanti.close()
            RisuscitoDatabase.getInstance(activityWeakReference.get()!!)
                    .recreateDB(activityWeakReference.get()!!)
            activityWeakReference.get()!!.convertTabs()
            activityWeakReference.get()!!.convertiBarre()
            return null
        }

        override fun onPreExecute() {
            super.onPreExecute()
            SimpleDialogFragment.Builder(
                    activityWeakReference.get()!!, activityWeakReference.get()!!, "TRANSLATION")
                    .content(R.string.translation_running)
                    .showProgress()
                    .progressIndeterminate(true)
                    .progressMax(0)
                    .show()
        }

        override fun onPostExecute(result: Void?) {
            super.onPostExecute(result)
            activityWeakReference.get()!!.intent.removeExtra(Utility.CHANGE_LANGUAGE)
            try {
                activityWeakReference.get()!!.dismissDialog("TRANSLATION")
            } catch (e: IllegalArgumentException) {
                Log.e(javaClass.name, e.localizedMessage, e)
            }

        }
    }

    @SuppressLint("StaticFieldLeak")
    private inner class BackupTask : AsyncTask<Void, Void, Void>() {

        override fun doInBackground(vararg sUrl: Void): Void? {
            try {
                checkDuplTosave(RisuscitoDatabase.dbName, "application/x-sqlite3", true)
                val intentBroadcast = Intent("BROADCAST_NEXT_STEP")
                intentBroadcast.putExtra("WHICH", "BACKUP")
                sendBroadcast(intentBroadcast)
                checkDuplTosave(PREF_DRIVE_FILE_NAME, "application/json", false)
            } catch (e: Exception) {
                Log.e(TAG, "Exception: " + e.localizedMessage, e)
                val error = "error: " + e.localizedMessage
                Snackbar.make(findViewById(R.id.main_content), error, Snackbar.LENGTH_SHORT).show()
            }

            return null
        }

        override fun onPostExecute(result: Void?) {
            super.onPostExecute(result)
            val intentBroadcast = Intent("BROADCAST_LAST_STEP")
            intentBroadcast.putExtra("WHICH", "BACKUP")
            sendBroadcast(intentBroadcast)
        }
    }

    @SuppressLint("StaticFieldLeak")
    private inner class RestoreTask : AsyncTask<Void, Void, Void>() {

        override fun doInBackground(vararg sUrl: Void): Void? {
            try {
                if (checkDupl(RisuscitoDatabase.dbName))
                    restoreNewDbBackup()
                else
                    restoreOldDriveBackup()
                val intentBroadcast = Intent("BROADCAST_NEXT_STEP")
                intentBroadcast.putExtra("WHICH", "RESTORE")
                sendBroadcast(intentBroadcast)
                restoreDrivePrefBackup(PREF_DRIVE_FILE_NAME)
            } catch (e: Exception) {
                Log.e(TAG, "Exception: " + e.localizedMessage, e)
                val error = "error: " + e.localizedMessage
                Snackbar.make(findViewById(R.id.main_content), error, Snackbar.LENGTH_SHORT).show()
            }

            return null
        }

        override fun onPostExecute(result: Void?) {
            super.onPostExecute(result)
            val intentBroadcast = Intent("BROADCAST_LAST_STEP")
            intentBroadcast.putExtra("WHICH", "RESTORE")
            sendBroadcast(intentBroadcast)
        }
    }

    private inner class MyClass(context: Context) : SlidingPaneLayout(context), ICrossFadeSlidingPaneLayout {
        override fun setCanSlide(canSlide: Boolean) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun setOffset(slideOffset: Float) {
            TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        }
    }

    companion object {
        /* Request code used to invoke sign in user interactions. */
        private val RC_SIGN_IN = 9001
        private val PREF_DRIVE_FILE_NAME = "preferences_backup"
        private val PROF_ID = 5428471L
        private val TAG = MainActivity::class.java.canonicalName
    }
}