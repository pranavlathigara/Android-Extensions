package com.tunjid.androidx.fragments


import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.DividerItemDecoration.VERTICAL
import com.tunjid.androidx.PlaceHolder
import com.tunjid.androidx.R
import com.tunjid.androidx.adapters.ServiceClickedListener
import com.tunjid.androidx.baseclasses.AppBaseFragment
import com.tunjid.androidx.core.content.themeColorAt
import com.tunjid.androidx.isDarkTheme
import com.tunjid.androidx.recyclerview.ListManager
import com.tunjid.androidx.recyclerview.ListManagerBuilder
import com.tunjid.androidx.recyclerview.adapterOf
import com.tunjid.androidx.setLoading
import com.tunjid.androidx.view.util.inflate
import com.tunjid.androidx.viewholders.NSDViewHolder
import com.tunjid.androidx.viewmodels.NsdViewModel
import com.tunjid.androidx.viewmodels.routeName

/**
 * A [Fragment] listing supported NSD servers
 */
class NsdScanFragment : AppBaseFragment(R.layout.fragment_nsd_scan),
        ServiceClickedListener {

    private val viewModel by viewModels<NsdViewModel>()

    private lateinit var listManager: ListManager<NSDViewHolder, PlaceHolder.State>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        viewModel.scanChanges.observe(this) { listManager.onDiff(it) }
        viewModel.isScanning.observe(this) { uiState = uiState.copy(toolbarInvalidated = true) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        uiState = uiState.copy(
                toolbarTitle = this::class.java.routeName,
                toolbarShows = true,
                toolBarMenu = R.menu.menu_nsd_scan,
                fabShows = false,
                showsBottomNav = true,
                lightStatusBar = !requireContext().isDarkTheme,
                navBarColor = requireContext().themeColorAt(R.attr.nav_bar_color)
        )

        val placeHolder = PlaceHolder(view.findViewById(R.id.placeholder_container))
        placeHolder.bind(PlaceHolder.State(R.string.no_nsd_devices, R.drawable.ic_signal_wifi__24dp))

        listManager = ListManagerBuilder<NSDViewHolder, PlaceHolder.State>()
                .withRecyclerView(view.findViewById(R.id.list))
                .addDecoration(DividerItemDecoration(requireActivity(), VERTICAL))
                .withAdapter(
                        adapterOf(
                                itemsSource = viewModel::services,
                                viewHolderCreator = { parent, _ -> NSDViewHolder(parent.inflate(R.layout.viewholder_nsd_list), this) },
                                viewHolderBinder = { viewHolder, service, _ -> viewHolder.bind(service) }
                        )
                )
                .withPlaceholder(placeHolder)
                .withLinearLayoutManager()
                .build()
    }

    override fun onResume() {
        super.onResume()
        scanDevices(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listManager.clear()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        val currentlyScanning = viewModel.isScanning.value ?: false

        menu.findItem(R.id.menu_stop)?.isVisible = currentlyScanning
        menu.findItem(R.id.menu_scan)?.isVisible = !currentlyScanning

        val refresh = menu.findItem(R.id.menu_refresh)

        refresh?.isVisible = currentlyScanning
        if (currentlyScanning) refresh?.setLoading(requireContext().themeColorAt(R.attr.prominent_text_color))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.menu_scan -> scanDevices(true).let { true }
        R.id.menu_stop -> scanDevices(false).let { true }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onServiceClicked(serviceInfo: NsdServiceInfo) = Unit

    override fun isSelf(serviceInfo: NsdServiceInfo): Boolean = false

    private fun scanDevices(enable: Boolean) =
            if (enable) viewModel.findDevices()
            else viewModel.stopScanning()

    companion object {
        fun newInstance(): NsdScanFragment = NsdScanFragment().apply { arguments = Bundle() }
    }
}
