package it.cammino.risuscito

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.preference.PreferenceManager
import android.util.Log
import android.util.SparseArray
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.content.res.ResourcesCompat
import androidx.core.os.postDelayed
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.viewpager.widget.PagerAdapter
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.input.getInputField
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetSequence
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.leinardi.android.speeddial.SpeedDialView
import com.mikepenz.community_material_typeface_library.CommunityMaterial
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.utils.IconicsMenuInflaterUtil
import it.cammino.risuscito.database.RisuscitoDatabase
import it.cammino.risuscito.database.entities.ListaPers
import it.cammino.risuscito.dialogs.InputTextDialogFragment
import it.cammino.risuscito.dialogs.SimpleDialogFragment
import it.cammino.risuscito.ui.ThemeableActivity
import it.cammino.risuscito.utils.ThemeUtils
import it.cammino.risuscito.viewmodels.CustomListsViewModel
import kotlinx.android.synthetic.main.tabs_layout.*

class CustomLists : Fragment(), InputTextDialogFragment.SimpleInputCallback, SimpleDialogFragment.SimpleCallback {

    private var mCustomListsViewModel: CustomListsViewModel? = null
    private var mSectionsPagerAdapter: SectionsPagerAdapter? = null
    private var titoliListe: Array<String?>? = null
    private var idListe: IntArray? = null
    private var movePage: Boolean = false
    private var mMainActivity: MainActivity? = null
    private var mRegularFont: Typeface? = null
    private var tabs: TabLayout? = null

    private val themeUtils: ThemeUtils
        get() = (activity as MainActivity).themeUtils!!

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.tabs_layout, container, false)

        mCustomListsViewModel = ViewModelProviders.of(this).get(CustomListsViewModel::class.java)

        mMainActivity = activity as MainActivity?

        mRegularFont = ResourcesCompat.getFont(mMainActivity!!, R.font.googlesans_regular)

        mMainActivity!!.setupToolbarTitle(R.string.title_activity_custom_lists)

        titoliListe = arrayOfNulls(0)
        idListe = IntArray(0)

        movePage = savedInstanceState != null

        val iFragment = InputTextDialogFragment.findVisible((activity as AppCompatActivity?)!!, "NEW_LIST")
        iFragment?.setmCallback(this@CustomLists)
        var sFragment = SimpleDialogFragment.findVisible((activity as AppCompatActivity?)!!, "RESET_LIST")
        sFragment?.setmCallback(this@CustomLists)
        sFragment = SimpleDialogFragment.findVisible((activity as AppCompatActivity?)!!, "DELETE_LIST")
        sFragment?.setmCallback(this@CustomLists)

        val mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        Log.d(
                TAG,
                "onCreate - INTRO_CUSTOMLISTS: " + mSharedPrefs.getBoolean(Utility.INTRO_CUSTOMLISTS, false))
        if (!mSharedPrefs.getBoolean(Utility.INTRO_CUSTOMLISTS, false)) playIntro()

        return rootView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mSectionsPagerAdapter = SectionsPagerAdapter(childFragmentManager)
        mMainActivity!!.enableBottombar(false)
//        activity!!.view_pager!!.adapter = mSectionsPagerAdapter
        view_pager.adapter = mSectionsPagerAdapter

//        tabs = activity!!.material_tabs
        tabs = mMainActivity!!.getMaterialTabs()
        tabs!!.visibility = View.VISIBLE
        tabs!!.setupWithViewPager(view_pager)

        populateDb()
        subscribeUiFavorites()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        IconicsMenuInflaterUtil.inflate(
                activity!!.menuInflater, activity, R.menu.help_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        when (item!!.itemId) {
            R.id.action_help -> {
                playIntro()
                return true
            }
        }
        return false
    }

    /** @param outState Bundle in which to place your saved state.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
//        mCustomListsViewModel!!.indexToShow = activity!!.view_pager!!.currentItem
        mCustomListsViewModel!!.indexToShow = view_pager.currentItem
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        //        Log.i(TAG, "requestCode: " + requestCode);
        super.onActivityResult(requestCode, resultCode, data)
        if ((requestCode == TAG_CREA_LISTA || requestCode == TAG_MODIFICA_LISTA) && resultCode == Activity.RESULT_OK) {
            mCustomListsViewModel!!.indexToShow = mCustomListsViewModel!!.indDaModif
            movePage = true
        }
    }

    override fun onPositive(tag: String, dialog: MaterialDialog) {
        Log.d(TAG, "onPositive: $tag")
        when (tag) {
            "NEW_LIST" -> {
                val bundle = Bundle()
                val mEditText = dialog.getInputField()
                bundle.putString(
                        "titolo", if (mEditText != null) dialog.getInputField()!!.text.toString() else "NULL")
                bundle.putBoolean("modifica", false)
                mCustomListsViewModel!!.indDaModif = 2 + idListe!!.size
                startActivityForResult(
                        Intent(activity, CreaListaActivity::class.java).putExtras(bundle), TAG_CREA_LISTA)
                activity!!.overridePendingTransition(R.anim.slide_in_bottom, R.anim.hold_on)
            }
        }
    }

    override fun onNegative(tag: String, dialog: MaterialDialog) {}

    override fun onNeutral(tag: String, dialog: MaterialDialog) {}

    override fun onPositive(tag: String) {
        Log.d(TAG, "onPositive: $tag")
        when (tag) {
            "RESET_LIST" -> {
//                val mView = mSectionsPagerAdapter!!.getRegisteredFragment(activity!!.view_pager!!.currentItem).view
                val mView = mSectionsPagerAdapter!!.getRegisteredFragment(view_pager.currentItem).view
                mView?.findViewById<View>(R.id.button_pulisci)?.performClick()
            }
            "DELETE_LIST" ->
                Thread(
                        Runnable {
                            val mDao = RisuscitoDatabase.getInstance(context!!).listePersDao()
                            val listToDelete = ListaPers()
                            listToDelete.id = mCustomListsViewModel!!.idDaCanc
                            mDao.deleteList(listToDelete)
                            mCustomListsViewModel!!.indexToShow = 0
                            movePage = true
                            Snackbar.make(
                                    activity!!.findViewById(R.id.main_content),
                                    getString(R.string.list_removed)
                                            + mCustomListsViewModel!!.titoloDaCanc
                                            + "'!",
                                    Snackbar.LENGTH_LONG)
                                    .setAction(
                                            getString(android.R.string.cancel).toUpperCase()
                                    ) {
                                        mCustomListsViewModel!!.indexToShow = mCustomListsViewModel!!.listaDaCanc + 2
                                        movePage = true
                                        Thread(
                                                Runnable {
                                                    val mListePersDao = RisuscitoDatabase.getInstance(context!!)
                                                            .listePersDao()
                                                    val listaToRestore = ListaPers()
                                                    listaToRestore.id = mCustomListsViewModel!!.idDaCanc
                                                    listaToRestore.titolo = mCustomListsViewModel!!.titoloDaCanc
                                                    listaToRestore.lista = mCustomListsViewModel!!.celebrazioneDaCanc
                                                    mListePersDao.insertLista(listaToRestore)
                                                })
                                                .start()
                                    }
                                    .setActionTextColor(themeUtils.accentColor())
                                    .show()
                        })
                        .start()
        }
    }

    override fun onNegative(tag: String) {}

    override fun onNeutral(tag: String) {}

    private fun playIntro() {
        enableFab(true)
        val doneDrawable = IconicsDrawable(activity!!, CommunityMaterial.Icon.cmd_check)
                .sizeDp(24)
                .paddingDp(4)
        TapTargetSequence(activity!!)
                .continueOnCancel(true)
                .targets(
                        TapTarget.forView(
                                getFab(),
                                getString(R.string.showcase_listepers_title),
                                getString(R.string.showcase_listepers_desc1))
                                .outerCircleColorInt(
                                        themeUtils.primaryColor()) // Specify a color for the outer circle
                                .targetCircleColorInt(Color.WHITE) // Specify a color for the target circle
                                .textTypeface(mRegularFont) // Specify a typeface for the text
                                .titleTextColor(R.color.primary_text_default_material_dark)
                                .textColor(R.color.secondary_text_default_material_dark)
                                .descriptionTextSize(15)
                                .tintTarget(false) // Whether to tint the target view's color
                        ,
                        TapTarget.forView(
                                getFab(),
                                getString(R.string.showcase_listepers_title),
                                getString(R.string.showcase_listepers_desc3))
                                .outerCircleColorInt(
                                        themeUtils.primaryColor()) // Specify a color for the outer circle
                                .targetCircleColorInt(Color.WHITE) // Specify a color for the target circle
                                .icon(doneDrawable)
                                .textTypeface(mRegularFont) // Specify a typeface for the text
                                .titleTextColor(R.color.primary_text_default_material_dark)
                                .textColor(R.color.secondary_text_default_material_dark))
                .listener(
                        object : TapTargetSequence.Listener { // The listener can listen for regular clicks, long clicks or cancels
                            override fun onSequenceFinish() {
                                if (context != null) PreferenceManager.getDefaultSharedPreferences(context).edit { putBoolean(Utility.INTRO_CUSTOMLISTS, true) }
                            }

                            override fun onSequenceStep(tapTarget: TapTarget, b: Boolean) {}

                            override fun onSequenceCanceled(tapTarget: TapTarget) {
                                if (context != null) PreferenceManager.getDefaultSharedPreferences(context).edit { putBoolean(Utility.INTRO_CUSTOMLISTS, true) }
                            }
                        })
                .start()
    }

    private fun populateDb() {
        mCustomListsViewModel!!.createDb()
    }

    private fun subscribeUiFavorites() {
        mCustomListsViewModel!!
                .customListResult!!
                .observe(
                        this,
                        Observer { list ->
                            titoliListe = arrayOfNulls(list!!.size)
                            idListe = IntArray(list.size)

                            for (i in list.indices) {
                                titoliListe!![i] = list[i].titolo
                                idListe!![i] = list[i].id
                            }
                            mSectionsPagerAdapter!!.notifyDataSetChanged()
//                            tabs!!.setupWithViewPager(activity!!.view_pager)
                            tabs!!.setupWithViewPager(view_pager)
                            if (movePage) {
                                Handler().postDelayed(200) {
                                    tabs!!.getTabAt(mCustomListsViewModel!!.indexToShow)!!.select()
                                    mCustomListsViewModel!!.indexToShow = 0
                                    movePage = false
                                }
                            }
                        })
    }

    private inner class SectionsPagerAdapter internal constructor(fm: FragmentManager) : FragmentStatePagerAdapter(fm) {
        internal var registeredFragments = SparseArray<Fragment>()

        override fun getItem(position: Int): Fragment {
            return when (position) {
                0 -> CantiParolaFragment()
                1 -> CantiEucarestiaFragment()
                else -> {
                    val bundle = Bundle()
                    bundle.putInt("idLista", idListe!![position - 2])
                    val listaPersFrag = ListaPersonalizzataFragment()
                    listaPersFrag.arguments = bundle
                    listaPersFrag
                }
            }
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            val fragment = super.instantiateItem(container, position) as Fragment
            registeredFragments.put(position, fragment)
            return fragment
        }

        override fun destroyItem(container: ViewGroup, position: Int, `object`: Any) {
            registeredFragments.remove(position)
            super.destroyItem(container, position, `object`)
        }

        internal fun getRegisteredFragment(position: Int): Fragment {
            return registeredFragments.get(position)
        }

        override fun getCount(): Int {
            return 2 + titoliListe!!.size
        }

        override fun getPageTitle(position: Int): CharSequence? {
            val l = ThemeableActivity.getSystemLocalWrapper(activity!!.resources.configuration)
            return when (position) {
                0 -> getString(R.string.title_activity_canti_parola).toUpperCase(l)
                1 -> getString(R.string.title_activity_canti_eucarestia).toUpperCase(l)
                else -> titoliListe!![position - 2]!!.toUpperCase(l)
            }
        }

        override fun getItemPosition(`object`: Any): Int {
            return PagerAdapter.POSITION_NONE
        }
    }

    fun getFab(): FloatingActionButton {
        return mMainActivity!!.getFab()
    }

    private fun enableFab(enabled: Boolean) {
        mMainActivity!!.enableFab(enabled)
    }

    private fun closeFabMenu() {
        mMainActivity!!.closeFabMenu()
    }

    private fun toggleFabMenu() {
        mMainActivity!!.toggleFabMenu()
    }

    fun initFabOptions(customList: Boolean) {
        val icon = IconicsDrawable(activity!!)
                .icon(CommunityMaterial.Icon2.cmd_plus)
                .color(Color.WHITE)
                .sizeDp(24)
                .paddingDp(4)

        val actionListener = SpeedDialView.OnActionSelectedListener {
            when (it.id) {
                R.id.fab_pulisci -> {
                    closeFabMenu()
                    SimpleDialogFragment.Builder(
                            (activity as AppCompatActivity?)!!, this@CustomLists, "RESET_LIST")
                            .title(R.string.dialog_reset_list_title)
                            .content(R.string.reset_list_question)
                            .positiveButton(android.R.string.yes)
                            .negativeButton(android.R.string.no)
                            .show()
                    true
                }
                R.id.fab_add_lista -> {
                    closeFabMenu()
                    InputTextDialogFragment.Builder(
                            (activity as AppCompatActivity?)!!, this@CustomLists, "NEW_LIST")
                            .title(R.string.lista_add_desc)
                            .positiveButton(android.R.string.ok)
                            .negativeButton(android.R.string.cancel)
                            .show()
                    true
                }
                R.id.fab_condividi -> {
                    closeFabMenu()
                    val mView = mSectionsPagerAdapter!!
//                            .getRegisteredFragment(activity!!.view_pager!!.currentItem)
                            .getRegisteredFragment(view_pager.currentItem)
                            .view
                    mView?.findViewById<View>(R.id.button_condividi)?.performClick()
                    true
                }
                R.id.fab_edit_lista -> {
                    closeFabMenu()
                    val bundle = Bundle()
//                    bundle.putInt("idDaModif", idListe!![activity!!.view_pager!!.currentItem - 2])
                    bundle.putInt("idDaModif", idListe!![view_pager.currentItem - 2])
                    bundle.putBoolean("modifica", true)
//                    mCustomListsViewModel!!.indDaModif = activity!!.view_pager!!.currentItem
                    mCustomListsViewModel!!.indDaModif = view_pager.currentItem
                    startActivityForResult(
                            Intent(activity, CreaListaActivity::class.java).putExtras(bundle),
                            TAG_MODIFICA_LISTA)
                    activity!!.overridePendingTransition(R.anim.slide_in_bottom, R.anim.hold_on)
                    true
                }
                R.id.fab_delete_lista -> {
                    closeFabMenu()
//                    mCustomListsViewModel!!.listaDaCanc = activity!!.view_pager!!.currentItem - 2
                    mCustomListsViewModel!!.listaDaCanc = view_pager.currentItem - 2
                    mCustomListsViewModel!!.idDaCanc = idListe!![mCustomListsViewModel!!.listaDaCanc]
                    Thread(
                            Runnable {
                                val mDao = RisuscitoDatabase.getInstance(activity!!).listePersDao()
                                val lista = mDao.getListById(mCustomListsViewModel!!.idDaCanc)
                                mCustomListsViewModel!!.titoloDaCanc = lista?.titolo
                                mCustomListsViewModel!!.celebrazioneDaCanc = lista?.lista
                                SimpleDialogFragment.Builder(
                                        (activity as AppCompatActivity?)!!,
                                        this@CustomLists,
                                        "DELETE_LIST")
                                        .title(R.string.action_remove_list)
                                        .content(R.string.delete_list_dialog)
                                        .positiveButton(android.R.string.yes)
                                        .negativeButton(android.R.string.no)
                                        .show()
                            })
                            .start()
                    true
                }
                R.id.fab_condividi_file -> {
                    closeFabMenu()
                    val mView = mSectionsPagerAdapter!!
//                            .getRegisteredFragment(activity!!.view_pager!!.currentItem)
                            .getRegisteredFragment(view_pager.currentItem)
                            .view
                    mView?.findViewById<View>(R.id.button_invia_file)!!.performClick()
                    true
                }
                else -> {
                    closeFabMenu()
                    false
                }
            }
        }

        val click = View.OnClickListener {
            toggleFabMenu()
        }

        mMainActivity!!.initFab(true, icon, click, actionListener, customList)
    }

    companion object {
        const val TAG_CREA_LISTA = 111
        const val TAG_MODIFICA_LISTA = 222
        private val TAG = CustomLists::class.java.canonicalName
    }
}
