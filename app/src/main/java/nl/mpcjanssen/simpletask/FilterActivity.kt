package nl.mpcjanssen.simpletask

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.google.android.material.tabs.TabLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import androidx.lifecycle.Lifecycle
import com.google.android.material.tabs.TabLayoutMediator
import nl.mpcjanssen.simpletask.remote.FileDialog
import nl.mpcjanssen.simpletask.remote.FileStore
import nl.mpcjanssen.simpletask.task.Priority
import nl.mpcjanssen.simpletask.task.TodoList
import nl.mpcjanssen.simpletask.util.*
import java.io.File
import java.io.IOException
import java.util.*

class FilterActivity : ThemedNoActionBarActivity() {

    internal var asWidgetConfigure = false
    internal var asWidgetReConfigure = false
    internal lateinit var mFilter: Query

    internal lateinit var m_app: TodoApplication
    val prefs = TodoApplication.config.prefs

    private var pager: ViewPager2? = null
    private var m_menu: Menu? = null
    private var pagerAdapter: ScreenSlidePagerAdapter? = null
    private var scriptFragment: FilterScriptFragment? = null
    private var m_page = 0

    override fun onBackPressed() {
        if (!asWidgetConfigure && !asWidgetReConfigure) {
            applyFilter()
        }
        super.onBackPressed()
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "Called with intent: " + intent.toString())
        m_app = application as TodoApplication

        setContentView(R.layout.filter)
        val toolbar = findViewById<Toolbar>(R.id.toolbar_edit_filter)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp)

        val intent = intent
        val environment: String = intent.action?.let {
            asWidgetConfigure = it == AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0)
            "widget" + id.toString()
        } ?: "mainui"


        val context = applicationContext

        if (!asWidgetConfigure) {
            mFilter = Query(intent, luaModule = environment)
        } else if (intent.getBooleanExtra(Constants.EXTRA_WIDGET_RECONFIGURE, false)) {
            asWidgetReConfigure = true
            asWidgetConfigure = false
            setTitle(R.string.config_widget)
            val prefsName = intent.getIntExtra(Constants.EXTRA_WIDGET_ID, -1).toString()
            val preferences = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            mFilter = Query(preferences, luaModule = environment)
        } else {
            setTitle(R.string.create_widget)
            mFilter = Query(prefs, luaModule = environment)
        }
        pagerAdapter = ScreenSlidePagerAdapter(supportFragmentManager, lifecycle)

        val contextTab = FilterListFragment()
        contextTab.arguments = Bundle().apply {
            val contexts = alfaSort(
                TodoApplication.todoList.contexts,
                TodoApplication.config.sortCaseSensitive,
                "-"
            )
            putStringArrayList(FILTER_ITEMS, contexts)
            putStringArrayList(INITIAL_SELECTED_ITEMS, mFilter.contexts)
            putBoolean(INITIAL_NOT, mFilter.contextsNot)
            putString(TAB_TYPE, CONTEXT_TAB)
        }
        pagerAdapter!!.add(contextTab)

        val projectTab = FilterListFragment()
        projectTab.arguments = Bundle().apply {
            val projects = alfaSort(
                TodoApplication.todoList.projects,
                TodoApplication.config.sortCaseSensitive,
                "-"
            )
            putStringArrayList(FILTER_ITEMS, projects)
            putStringArrayList(INITIAL_SELECTED_ITEMS, mFilter.projects)
            putBoolean(INITIAL_NOT, mFilter.projectsNot)
            putString(TAB_TYPE, PROJECT_TAB)
        }
        pagerAdapter!!.add(projectTab)

        val prioTab = FilterListFragment()
        prioTab.arguments = Bundle().apply {
            putStringArrayList(FILTER_ITEMS, Priority.inCode(TodoApplication.todoList.priorities))
            putStringArrayList(INITIAL_SELECTED_ITEMS, Priority.inCode(mFilter.priorities))
            putBoolean(INITIAL_NOT, mFilter.prioritiesNot)
            putString(TAB_TYPE, PRIO_TAB)
        }
        pagerAdapter!!.add(prioTab)

        val otherTab = FilterOtherFragment()
        otherTab.arguments = Bundle().apply {
            putBoolean(Query.INTENT_HIDE_COMPLETED_FILTER, mFilter.hideCompleted)
            putBoolean(Query.INTENT_HIDE_FUTURE_FILTER, mFilter.hideFuture)
            putBoolean(Query.INTENT_HIDE_LISTS_FILTER, mFilter.hideLists)
            putBoolean(Query.INTENT_HIDE_TAGS_FILTER, mFilter.hideTags)
            putBoolean(Query.INTENT_HIDE_CREATE_DATE_FILTER, mFilter.hideCreateDate)
            putBoolean(Query.INTENT_HIDE_HIDDEN_FILTER, mFilter.hideHidden)
            putBoolean(Query.INTENT_CREATE_AS_THRESHOLD, mFilter.createIsThreshold)
            putString(TAB_TYPE, OTHER_TAB)
        }
        pagerAdapter!!.add(otherTab)

        // Fill arguments for fragment
        val sortTab = FilterSortFragment()
        sortTab.arguments = Bundle().apply {
            putStringArrayList(FILTER_ITEMS, mFilter.getSort(TodoApplication.config.defaultSorts))
            putString(TAB_TYPE, SORT_TAB)
        }
        pagerAdapter!!.add(sortTab)


        val scriptTab = FilterScriptFragment()
        scriptFragment = scriptTab
        scriptTab.arguments = Bundle().apply {
            putString(Query.INTENT_LUA_MODULE, environment)
            putBoolean(Query.INTENT_USE_SCRIPT_FILTER, mFilter.useScript)
            putString(Query.INTENT_SCRIPT_FILTER, mFilter.script)
            putString(Query.INTENT_SCRIPT_TEST_TASK_FILTER, mFilter.scriptTestTask)
            putString(TAB_TYPE, SCRIPT_TAB)
        }
        pagerAdapter!!.add(scriptTab)

        pager = findViewById<ViewPager2>(R.id.pager)
        pager!!.adapter = pagerAdapter

        val tabLayout = findViewById<TabLayout>(R.id.sliding_tabs)
        TabLayoutMediator(tabLayout, pager!!) { tab, position ->
            tab.text = pagerAdapter?.getPageTitle(position)
        }.attach()
        tabLayout.tabMode = TabLayout.MODE_SCROLLABLE

        pager?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
            }

            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
            }

            override fun onPageSelected(position: Int) {
                Log.i(TAG, "Page $position selected")
                m_page = position
            }
        })
        val activePage = prefs.getInt(getString(R.string.last_open_filter_tab), 0)
        if (activePage < (pagerAdapter?.itemCount ?: 0)) {
            pager?.setCurrentItem(activePage, false)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        val inflater = menuInflater
        inflater.inflate(R.menu.filter, menu)
        m_menu = menu
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }

            R.id.menu_filter_action -> {
                when {
                    asWidgetConfigure -> askWidgetName()
                    asWidgetReConfigure -> {
                        updateWidget()
                        finish()
                    }

                    else -> applyFilter()
                }
            }

            R.id.menu_filter_load_script -> openScript { contents ->
                runOnUiThread { setScript(contents) }
            }
        }
        return true
    }

    private fun openScript(file_read: (String) -> Unit) {
    }

    private fun createFilterIntent(): Intent {
        val target = Intent(this, Simpletask::class.java)
        target.action = Constants.INTENT_START_FILTER
        updateFilterFromFragments()
        mFilter.saveInIntent(target)

        target.putExtra("name", mFilter.proposedName)
        return target
    }

    private fun updateFilterFromFragments() {
        pagerAdapter?.let { adapter -> // Ensure adapter is not null
            for (i in 0 until adapter.itemCount) {
                // TODO: It's safer to get the fragment from FragmentManager if it's already created
                // or rely on the adapter's way if it caches them.
                // However, `FragmentStateAdapter` doesn't guarantee fragments are always in memory.
                // The current approach of iterating `adapter.fragments` is specific to your
                // custom `ScreenSlidePagerAdapter` which directly holds fragment instances.
                // This is generally fine but be aware of how FragmentStateAdapter manages fragments.
                val f = adapter.fragments[i] // This relies on your custom `fragments` list
                when (f.arguments?.getString(TAB_TYPE, "") ?: "") {
                    "" -> {
                    }

                    OTHER_TAB -> {
                        val of = f as FilterOtherFragment
                        mFilter.hideCompleted = of.hideCompleted
                        mFilter.hideFuture = of.hideFuture
                        mFilter.hideLists = of.hideLists
                        mFilter.hideTags = of.hideTags
                        mFilter.hideCreateDate = of.hideCreateDate
                        mFilter.hideHidden = of.hideHidden
                        mFilter.createIsThreshold = of.createAsThreshold
                    }

                    CONTEXT_TAB -> {
                        val lf = f as FilterListFragment
                        mFilter.contexts = lf.getSelectedItems()
                        mFilter.contextsNot = lf.getNot()
                    }

                    PROJECT_TAB -> {
                        val pf = f as FilterListFragment
                        mFilter.projects = pf.getSelectedItems()
                        mFilter.projectsNot = pf.getNot()
                    }

                    PRIO_TAB -> {
                        val prf = f as FilterListFragment
                        mFilter.priorities = Priority.toPriority(prf.getSelectedItems())
                        mFilter.prioritiesNot = prf.getNot()
                    }

                    SORT_TAB -> {
                        val sf = f as FilterSortFragment
                        mFilter.setSort(sf.selectedItem)
                    }

                    SCRIPT_TAB -> {
                        val scrf = f as FilterScriptFragment
                        mFilter.useScript = scrf.useScript
                        mFilter.script = scrf.script
                        mFilter.scriptTestTask = scrf.testTask
                    }
                }
            }
        }
    }

    private fun setScript(script: String?) {
        if (scriptFragment == null) {
            // fragment was never intialized
            showToastShort(this, "Script tab not visible??")
        } else {
            script?.let { scriptFragment!!.script = script }
        }
    }

    private fun updateWidget() {
        updateFilterFromFragments()
        val widgetId = intent.getIntExtra(Constants.EXTRA_WIDGET_ID, 0)
        Log.i(TAG, "Saving settings for widget $widgetId")
        val preferences =
            applicationContext.getSharedPreferences("" + widgetId, Context.MODE_PRIVATE)
        mFilter.saveInPrefs(preferences)
        broadcastRefreshWidgets(m_app.localBroadCastManager)
    }

    private fun createWidget(name: String) {
        val mAppWidgetId: Int

        val intent = intent
        val extras = intent.extras
        updateFilterFromFragments()
        if (extras != null) {
            mAppWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )

            val context = applicationContext

            // Store widget applyFilter
            val preferences = context.getSharedPreferences("" + mAppWidgetId, Context.MODE_PRIVATE)
            val namedFilter = NamedQuery(name, mFilter)
            namedFilter.saveInPrefs(preferences)

            val appWidgetManager = AppWidgetManager.getInstance(context)
            MyAppWidgetProvider.updateAppWidget(
                context, appWidgetManager,
                mAppWidgetId, name
            )

            val resultValue = Intent(applicationContext, AppWidgetService::class.java)
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId)
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }

    private fun applyFilter() {
        val data = createFilterIntent()
        startActivity(data)
        finish()
    }

    private fun askWidgetName() {
        val name: String
        val alert = AlertDialog.Builder(this)

        alert.setTitle("Create widget")
        alert.setMessage("Widget title")
        updateFilterFromFragments()
        name = mFilter.proposedName

        // Set an EditText view to get user input
        val input = EditText(this)
        alert.setView(input)
        input.setText(name)

        alert.setPositiveButton("Ok") { _, _ ->
            val value = input.text.toString()
            if (value == "") {
                showToastShort(applicationContext, R.string.widget_name_empty)
            } else {
                createWidget(value)
            }
        }

        alert.setNegativeButton("Cancel") { _, _ -> }

        alert.show()

    }

    override fun onDestroy() {
        super.onDestroy()
        prefs.edit().putInt(getString(R.string.last_open_filter_tab), m_page).apply()
        pager?.unregisterOnPageChangeCallback(mPageChangeCallback)
    }

    private val mPageChangeCallback = object : ViewPager2.OnPageChangeCallback() {
        override fun onPageSelected(position: Int) {
            Log.i(TAG, "Page $position selected")
            m_page = position
        }
    }

    /**
     * A simple pager adapter that represents Fragment objects, in
     * sequence.
     */
    private inner class ScreenSlidePagerAdapter(fm: FragmentManager, lifecycle: Lifecycle) :
        FragmentStateAdapter(fm, lifecycle) {
        val fragments: ArrayList<Fragment> = ArrayList()

        fun add(frag: Fragment) {
            fragments.add(frag)
        }

        fun getPageTitle(position: Int): CharSequence {
            val f = fragments[position]
            val type = f.arguments?.getString(TAB_TYPE, "unknown") ?: "unknown"
            return when (type) {
                PROJECT_TAB -> TodoApplication.config.tagTerm
                CONTEXT_TAB -> TodoApplication.config.listTerm
                PRIO_TAB -> getString(R.string.filter_tab_header_prio)
                OTHER_TAB -> getString(R.string.filter_tab_header_other)
                SORT_TAB -> getString(R.string.filter_tab_header_sort)
                SCRIPT_TAB -> getString(R.string.filter_tab_header_script)
                else -> type
            }
        }

        override fun createFragment(position: Int): Fragment {
            return fragments[position]
        }

        override fun getItemCount(): Int {
            return fragments.size
        }
    }

    companion object {

        val TAG = "FilterActivity"
        val TAB_TYPE = "type"
        val CONTEXT_TAB = "context"
        val PROJECT_TAB = "project"
        val PRIO_TAB = getString(R.string.filter_tab_header_prio)
        val OTHER_TAB = getString(R.string.filter_tab_header_other)
        val SORT_TAB = getString(R.string.filter_tab_header_sort)
        val SCRIPT_TAB = getString(R.string.filter_tab_header_script)

        // Constants for saving state
        val FILTER_ITEMS = "items"
        val INITIAL_SELECTED_ITEMS = "initialSelectedItems"
        val INITIAL_NOT = "initialNot"
    }
}
