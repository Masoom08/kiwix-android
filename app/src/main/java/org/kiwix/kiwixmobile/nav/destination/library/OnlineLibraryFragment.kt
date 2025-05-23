/*
 * Kiwix Android
 * Copyright (c) 2020 Kiwix <android.kiwix.org>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.kiwix.kiwixmobile.nav.destination.library

import android.Manifest
import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tonyodev.fetch2.Status
import eu.mhutti1.utils.storage.STORAGE_SELECT_STORAGE_TITLE_TEXTVIEW_SIZE
import eu.mhutti1.utils.storage.StorageDevice
import eu.mhutti1.utils.storage.StorageSelectDialog
import kotlinx.coroutines.launch
import org.kiwix.kiwixmobile.R
import org.kiwix.kiwixmobile.cachedComponent
import org.kiwix.kiwixmobile.core.R.string
import org.kiwix.kiwixmobile.core.base.BaseActivity
import org.kiwix.kiwixmobile.core.base.BaseFragment
import org.kiwix.kiwixmobile.core.base.FragmentActivityExtensions
import org.kiwix.kiwixmobile.core.downloader.Downloader
import org.kiwix.kiwixmobile.core.extensions.ActivityExtensions.hasNotificationPermission
import org.kiwix.kiwixmobile.core.extensions.ActivityExtensions.isManageExternalStoragePermissionGranted
import org.kiwix.kiwixmobile.core.extensions.ActivityExtensions.navigate
import org.kiwix.kiwixmobile.core.extensions.ActivityExtensions.requestNotificationPermission
import org.kiwix.kiwixmobile.core.extensions.ActivityExtensions.viewModel
import org.kiwix.kiwixmobile.core.extensions.closeKeyboard
import org.kiwix.kiwixmobile.core.extensions.coreMainActivity
import org.kiwix.kiwixmobile.core.extensions.isKeyboardVisible
import org.kiwix.kiwixmobile.core.extensions.setBottomMarginToFragmentContainerView
import org.kiwix.kiwixmobile.core.extensions.setUpSearchView
import org.kiwix.kiwixmobile.core.extensions.snack
import org.kiwix.kiwixmobile.core.extensions.toast
import org.kiwix.kiwixmobile.core.main.CoreMainActivity
import org.kiwix.kiwixmobile.core.navigateToAppSettings
import org.kiwix.kiwixmobile.core.navigateToSettings
import org.kiwix.kiwixmobile.core.utils.BookUtils
import org.kiwix.kiwixmobile.core.utils.EXTERNAL_SELECT_POSITION
import org.kiwix.kiwixmobile.core.utils.INTERNAL_SELECT_POSITION
import org.kiwix.kiwixmobile.core.utils.NetworkUtils
import org.kiwix.kiwixmobile.core.utils.REQUEST_POST_NOTIFICATION_PERMISSION
import org.kiwix.kiwixmobile.core.utils.REQUEST_STORAGE_PERMISSION
import org.kiwix.kiwixmobile.core.utils.SharedPreferenceUtil
import org.kiwix.kiwixmobile.core.utils.SimpleRecyclerViewScrollListener
import org.kiwix.kiwixmobile.core.utils.SimpleTextListener
import org.kiwix.kiwixmobile.core.utils.dialog.AlertDialogShower
import org.kiwix.kiwixmobile.core.utils.dialog.DialogShower
import org.kiwix.kiwixmobile.core.utils.dialog.KiwixDialog
import org.kiwix.kiwixmobile.core.utils.dialog.KiwixDialog.YesNoDialog.WifiOnly
import org.kiwix.kiwixmobile.core.zim_manager.NetworkState
import org.kiwix.kiwixmobile.databinding.FragmentDestinationDownloadBinding
import org.kiwix.kiwixmobile.main.KiwixMainActivity
import org.kiwix.kiwixmobile.zimManager.ZimManageViewModel
import org.kiwix.kiwixmobile.zimManager.libraryView.AvailableSpaceCalculator
import org.kiwix.kiwixmobile.zimManager.libraryView.adapter.LibraryAdapter
import org.kiwix.kiwixmobile.zimManager.libraryView.adapter.LibraryDelegate
import org.kiwix.kiwixmobile.zimManager.libraryView.adapter.LibraryListItem
import javax.inject.Inject

class OnlineLibraryFragment : BaseFragment(), FragmentActivityExtensions {
  @Inject lateinit var conMan: ConnectivityManager

  @Inject lateinit var downloader: Downloader

  @Inject lateinit var dialogShower: DialogShower

  @Inject lateinit var sharedPreferenceUtil: SharedPreferenceUtil

  @Inject lateinit var viewModelFactory: ViewModelProvider.Factory

  @Inject lateinit var bookUtils: BookUtils

  @Inject lateinit var availableSpaceCalculator: AvailableSpaceCalculator

  @Inject lateinit var alertDialogShower: AlertDialogShower
  private var fragmentDestinationDownloadBinding: FragmentDestinationDownloadBinding? = null
  private val lock = Any()
  private var downloadBookItem: LibraryListItem.BookItem? = null
  private val zimManageViewModel by lazy {
    requireActivity().viewModel<ZimManageViewModel>(viewModelFactory)
  }

  @VisibleForTesting
  fun getOnlineLibraryList() = libraryAdapter.items

  private val libraryAdapter: LibraryAdapter by lazy {
    LibraryAdapter(
      LibraryDelegate.BookDelegate(
        bookUtils,
        ::onBookItemClick,
        availableSpaceCalculator,
        lifecycleScope
      ),
      LibraryDelegate.DownloadDelegate(
        {
          if (it.currentDownloadState == Status.FAILED) {
            if (isNotConnected) {
              noInternetSnackbar()
            } else {
              downloader.retryDownload(it.downloadId)
            }
          } else {
            dialogShower.show(
              KiwixDialog.YesNoDialog.StopDownload,
              { downloader.cancelDownload(it.downloadId) }
            )
          }
        },
        {
          context?.let { context ->
            if (isNotConnected) {
              noInternetSnackbar()
              return@let
            }
            downloader.pauseResumeDownload(
              it.downloadId,
              it.downloadState.toReadableState(context) == getString(string.paused_state)
            )
          }
        }
      ),
      LibraryDelegate.DividerDelegate
    )
  }

  private val noWifiWithWifiOnlyPreferenceSet
    get() = sharedPreferenceUtil.prefWifiOnly && !NetworkUtils.isWiFi(requireContext())

  private val isNotConnected: Boolean
    get() = !NetworkUtils.isNetworkAvailable(requireActivity())

  override fun inject(baseActivity: BaseActivity) {
    baseActivity.cachedComponent.inject(this)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    fragmentDestinationDownloadBinding =
      FragmentDestinationDownloadBinding.inflate(inflater, container, false)
    val toolbar = fragmentDestinationDownloadBinding?.root?.findViewById<Toolbar>(R.id.toolbar)
    val activity = activity as CoreMainActivity
    activity.setSupportActionBar(toolbar)
    activity.supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
      setTitle(string.download)
    }
    if (toolbar != null) {
      activity.setupDrawerToggle(toolbar)
    }
    return fragmentDestinationDownloadBinding?.root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    fragmentDestinationDownloadBinding?.librarySwipeRefresh?.setOnRefreshListener(::refreshFragment)
    fragmentDestinationDownloadBinding?.libraryList?.run {
      adapter = libraryAdapter
      layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
      setHasFixedSize(true)
    }
    zimManageViewModel.libraryItems.observe(viewLifecycleOwner, Observer(::onLibraryItemsChange))
      .also {
        coreMainActivity.navHostContainer
          .setBottomMarginToFragmentContainerView(0)
      }
    zimManageViewModel.libraryListIsRefreshing.observe(
      viewLifecycleOwner,
      Observer(::onRefreshStateChange)
    )
    zimManageViewModel.networkStates.observe(viewLifecycleOwner, Observer(::onNetworkStateChange))
    zimManageViewModel.shouldShowWifiOnlyDialog.observe(
      viewLifecycleOwner
    ) {
      if (it && !NetworkUtils.isWiFi(requireContext())) {
        showInternetAccessViaMobileNetworkDialog()
        hideProgressBarOfFetchingOnlineLibrary()
      }
    }
    zimManageViewModel.downloadProgress.observe(viewLifecycleOwner, ::onLibraryStatusChanged)
    setupMenu()

    // hides keyboard when scrolled
    fragmentDestinationDownloadBinding?.libraryList?.addOnScrollListener(simpleScrollListener)
  }

  private var simpleScrollListener = SimpleRecyclerViewScrollListener { _, newState ->
    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
      fragmentDestinationDownloadBinding?.libraryList?.closeKeyboard()
    }
  }

  private fun setupMenu() {
    (requireActivity() as MenuHost).addMenuProvider(
      object : MenuProvider {
        override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
          menuInflater.inflate(R.menu.menu_zim_manager, menu)
          val searchItem = menu.findItem(R.id.action_search)
          val getZimItem = menu.findItem(R.id.get_zim_nearby_device)
          getZimItem?.isVisible = false

          searchItem.setOnActionExpandListener(
            object : MenuItem.OnActionExpandListener {
              override fun onMenuItemActionExpand(p0: MenuItem): Boolean = true

              override fun onMenuItemActionCollapse(p0: MenuItem): Boolean {
                // Clear search query when user reset the search.
                zimManageViewModel.onlineBooksSearchedQuery.value = null
                return true
              }
            }
          )

          (searchItem?.actionView as? SearchView)?.apply {
            setUpSearchView(requireActivity())
            setOnQueryTextListener(
              SimpleTextListener { query, _ ->
                if (query.isNotEmpty()) {
                  // Store only when query is not empty because when device going to sleep,
                  // then `viewLifecycleOwner` tries to clear the written text in searchView
                  // and due to that, this listener fired with empty query which resets the search.
                  zimManageViewModel.onlineBooksSearchedQuery.value = query
                }
                zimManageViewModel.requestFiltering.onNext(query)
              }
            )

            val closeButton = findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
            closeButton?.setOnClickListener {
              // Reset search query when user clicks on close image button in searchView.
              zimManageViewModel.onlineBooksSearchedQuery.value = null
              setQuery("", false)
            }
            zimManageViewModel.onlineBooksSearchedQuery.value.takeIf { it?.isNotEmpty() == true }
              ?.let {
                // Expand the searchView if there is previously saved query exist.
                searchItem.expandActionView()
                // Set the query in searchView which was previously set.
                setQuery(it, false)
              } ?: kotlin.run {
              // If no previously saved query found then normally initiate the search.
              zimManageViewModel.onlineBooksSearchedQuery.value = ""
              zimManageViewModel.requestFiltering.onNext("")
            }
          }
        }

        override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
          when (menuItem.itemId) {
            R.id.select_language -> {
              requireActivity().navigate(R.id.languageFragment)
              closeKeyboard()
              return true
            }
          }
          return false
        }
      },
      viewLifecycleOwner,
      Lifecycle.State.RESUMED
    )
  }

  private fun showInternetAccessViaMobileNetworkDialog() {
    dialogShower.show(
      WifiOnly,
      {
        showRecyclerviewAndHideSwipeDownForLibraryErrorText()
        sharedPreferenceUtil.putPrefWifiOnly(false)
        zimManageViewModel.shouldShowWifiOnlyDialog.value = false
      },
      {
        context.toast(
          resources.getString(string.denied_internet_permission_message),
          Toast.LENGTH_SHORT
        )
        hideRecyclerviewAndShowSwipeDownForLibraryErrorText()
      }
    )
  }

  private fun showRecyclerviewAndHideSwipeDownForLibraryErrorText() {
    fragmentDestinationDownloadBinding?.apply {
      libraryErrorText.visibility = View.GONE
      libraryList.visibility = View.VISIBLE
    }
    showProgressBarOfFetchingOnlineLibrary()
  }

  private fun hideRecyclerviewAndShowSwipeDownForLibraryErrorText() {
    fragmentDestinationDownloadBinding?.apply {
      libraryErrorText.setText(
        string.swipe_down_for_library
      )
      libraryErrorText.visibility = View.VISIBLE
      libraryList.visibility = View.GONE
    }
    hideProgressBarOfFetchingOnlineLibrary()
  }

  private fun showProgressBarOfFetchingOnlineLibrary() {
    onRefreshStateChange(false)
    fragmentDestinationDownloadBinding?.apply {
      libraryErrorText.visibility = View.GONE
      librarySwipeRefresh.isEnabled = false
      onlineLibraryProgressLayout.visibility = View.VISIBLE
      onlineLibraryProgressStatusText.setText(string.reaching_remote_library)
    }
  }

  private fun hideProgressBarOfFetchingOnlineLibrary() {
    onRefreshStateChange(false)
    fragmentDestinationDownloadBinding?.apply {
      librarySwipeRefresh.isEnabled = true
      onlineLibraryProgressLayout.visibility = View.GONE
      onlineLibraryProgressStatusText.setText(string.reaching_remote_library)
    }
  }

  private fun onLibraryStatusChanged(libraryStatus: String) {
    synchronized(lock) {
      fragmentDestinationDownloadBinding?.apply {
        onlineLibraryProgressStatusText.text = libraryStatus
      }
    }
  }

  override fun onDestroyView() {
    super.onDestroyView()
    fragmentDestinationDownloadBinding?.apply {
      librarySwipeRefresh.setOnRefreshListener(null)
      libraryList.removeOnScrollListener(simpleScrollListener)
      libraryList.adapter = null
      root.removeAllViews()
    }
    fragmentDestinationDownloadBinding = null
  }

  override fun onBackPressed(activity: AppCompatActivity): FragmentActivityExtensions.Super {
    if (isKeyboardVisible()) {
      closeKeyboard()
      return FragmentActivityExtensions.Super.ShouldNotCall
    }
    return FragmentActivityExtensions.Super.ShouldCall
  }

  private fun onRefreshStateChange(isRefreshing: Boolean?) {
    var refreshing = isRefreshing == true
    // do not show the refreshing when the online library is downloading
    if (fragmentDestinationDownloadBinding?.onlineLibraryProgressLayout?.isVisible == true ||
      fragmentDestinationDownloadBinding?.libraryErrorText?.isVisible == true
    ) {
      refreshing = false
    }
    fragmentDestinationDownloadBinding?.librarySwipeRefresh?.isRefreshing = refreshing
  }

  private fun onNetworkStateChange(networkState: NetworkState?) {
    when (networkState) {
      NetworkState.CONNECTED -> {
        if (NetworkUtils.isWiFi(requireContext())) {
          refreshFragment()
        } else if (noWifiWithWifiOnlyPreferenceSet) {
          hideRecyclerviewAndShowSwipeDownForLibraryErrorText()
        } else if (!noWifiWithWifiOnlyPreferenceSet) {
          if (libraryAdapter.items.isEmpty()) {
            showProgressBarOfFetchingOnlineLibrary()
          }
        }
      }

      NetworkState.NOT_CONNECTED -> {
        showNoInternetConnectionError()
      }

      else -> {}
    }
  }

  private fun showNoInternetConnectionError() {
    if (libraryAdapter.itemCount > 0) {
      noInternetSnackbar()
    } else {
      fragmentDestinationDownloadBinding?.libraryErrorText?.setText(
        string.no_network_connection
      )
      fragmentDestinationDownloadBinding?.libraryErrorText?.visibility = View.VISIBLE
    }
    hideProgressBarOfFetchingOnlineLibrary()
  }

  private fun noInternetSnackbar() {
    fragmentDestinationDownloadBinding?.libraryList?.snack(
      string.no_network_connection,
      requireActivity().findViewById(R.id.bottom_nav_view),
      string.menu_settings,
      ::openNetworkSettings
    )
  }

  private fun openNetworkSettings() {
    startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
  }

  private fun onLibraryItemsChange(it: List<LibraryListItem>?) {
    if (it != null) {
      libraryAdapter.items = it
    }
    hideProgressBarOfFetchingOnlineLibrary()
    if (it?.isEmpty() == true) {
      fragmentDestinationDownloadBinding?.libraryErrorText?.setText(
        if (isNotConnected) {
          string.no_network_connection
        } else {
          string.no_items_msg
        }
      )
      fragmentDestinationDownloadBinding?.libraryErrorText?.visibility = View.VISIBLE
    } else {
      fragmentDestinationDownloadBinding?.libraryErrorText?.visibility = View.GONE
    }
  }

  private fun refreshFragment() {
    if (isNotConnected) {
      showNoInternetConnectionError()
    } else {
      zimManageViewModel.requestDownloadLibrary.onNext(Unit)
      showRecyclerviewAndHideSwipeDownForLibraryErrorText()
    }
  }

  private fun downloadFile() {
    downloadBookItem?.book?.let {
      downloader.download(it)
      downloadBookItem = null
    }
  }

  private fun storeDeviceInPreferences(
    storageDevice: StorageDevice
  ) {
    sharedPreferenceUtil.showStorageOption = false
    sharedPreferenceUtil.putPrefStorage(
      sharedPreferenceUtil.getPublicDirectoryPath(storageDevice.name)
    )
    sharedPreferenceUtil.putStoragePosition(
      if (storageDevice.isInternal) {
        INTERNAL_SELECT_POSITION
      } else {
        EXTERNAL_SELECT_POSITION
      }
    )
    clickOnBookItem()
  }

  private fun requestNotificationPermission() {
    if (!shouldShowRationale(POST_NOTIFICATIONS)) {
      requireActivity().requestNotificationPermission()
    } else {
      alertDialogShower.show(
        KiwixDialog.NotificationPermissionDialog,
        requireActivity()::navigateToAppSettings
      )
    }
  }

  private fun checkExternalStorageWritePermission(): Boolean {
    if (!sharedPreferenceUtil.isPlayStoreBuildWithAndroid11OrAbove()) {
      return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        true
      } else {
        hasPermission(WRITE_EXTERNAL_STORAGE).also { permissionGranted ->
          if (!permissionGranted) {
            if (shouldShowRationale(WRITE_EXTERNAL_STORAGE)) {
              alertDialogShower.show(
                KiwixDialog.WriteStoragePermissionRationale,
                {
                  requestPermission(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    REQUEST_STORAGE_PERMISSION
                  )
                }
              )
            } else {
              alertDialogShower.show(
                KiwixDialog.WriteStoragePermissionRationale,
                requireActivity()::navigateToAppSettings
              )
            }
          }
        }
      }
    }
    return true
  }

  private fun requestPermission(permission: String, requestCode: Int) {
    ActivityCompat.requestPermissions(
      requireActivity(),
      arrayOf(permission),
      requestCode
    )
  }

  private fun shouldShowRationale(permission: String) =
    ActivityCompat.shouldShowRequestPermissionRationale(requireActivity(), permission)

  @Suppress("DEPRECATION")
  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == REQUEST_STORAGE_PERMISSION &&
      permissions.isNotEmpty() &&
      permissions[0] == Manifest.permission.WRITE_EXTERNAL_STORAGE
    ) {
      if (grantResults[0] != PERMISSION_GRANTED) {
        if (!sharedPreferenceUtil.isPlayStoreBuildWithAndroid11OrAbove()) {
          checkExternalStorageWritePermission()
        }
      }
    } else if (requestCode == REQUEST_POST_NOTIFICATION_PERMISSION &&
      permissions.isNotEmpty() &&
      permissions[0] == POST_NOTIFICATIONS
    ) {
      if (grantResults[0] == PERMISSION_GRANTED) {
        downloadBookItem?.let(::onBookItemClick)
      }
    }
  }

  private fun hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(requireActivity(), permission) == PERMISSION_GRANTED

  @Suppress("NestedBlockDepth")
  private fun onBookItemClick(item: LibraryListItem.BookItem) {
    lifecycleScope.launch {
      if (checkExternalStorageWritePermission()) {
        downloadBookItem = item
        if (requireActivity().hasNotificationPermission(sharedPreferenceUtil)) {
          when {
            isNotConnected -> {
              noInternetSnackbar()
              return@launch
            }

            noWifiWithWifiOnlyPreferenceSet -> {
              dialogShower.show(WifiOnly, {
                sharedPreferenceUtil.putPrefWifiOnly(false)
                clickOnBookItem()
              })
              return@launch
            }

            else ->
              if (sharedPreferenceUtil.showStorageOption) {
                // Show the storage selection dialog for configuration if there is an SD card available.
                if (getStorageDeviceList().size > 1) {
                  showStorageSelectDialog(getStorageDeviceList())
                } else {
                  // If only internal storage is available, proceed with the ZIM file download directly.
                  // Displaying a configuration dialog is unnecessary in this case.
                  sharedPreferenceUtil.showStorageOption = false
                  onBookItemClick(item)
                }
              } else if (!requireActivity().isManageExternalStoragePermissionGranted(
                  sharedPreferenceUtil
                )
              ) {
                showManageExternalStoragePermissionDialog()
              } else {
                availableSpaceCalculator.hasAvailableSpaceFor(
                  item,
                  { downloadFile() },
                  {
                    fragmentDestinationDownloadBinding?.libraryList?.snack(
                      """ 
                      ${getString(string.download_no_space)}
                      ${getString(string.space_available)} $it
                      """.trimIndent(),
                      requireActivity().findViewById(R.id.bottom_nav_view),
                      string.download_change_storage,
                      {
                        lifecycleScope.launch {
                          showStorageSelectDialog(getStorageDeviceList())
                        }
                      }
                    )
                  }
                )
              }
          }
        } else {
          requestNotificationPermission()
        }
      }
    }
  }

  private fun showStorageSelectDialog(storageDeviceList: List<StorageDevice>) =
    StorageSelectDialog()
      .apply {
        onSelectAction = ::storeDeviceInPreferences
        titleSize = STORAGE_SELECT_STORAGE_TITLE_TEXTVIEW_SIZE
        setStorageDeviceList(storageDeviceList)
        setShouldShowCheckboxSelected(false)
      }
      .show(parentFragmentManager, getString(string.choose_storage_to_download_book))

  private fun clickOnBookItem() {
    if (!requireActivity().isManageExternalStoragePermissionGranted(sharedPreferenceUtil)) {
      showManageExternalStoragePermissionDialog()
    } else {
      downloadBookItem?.let(::onBookItemClick)
    }
  }

  private fun showManageExternalStoragePermissionDialog() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      dialogShower.show(
        KiwixDialog.ManageExternalFilesPermissionDialog,
        {
          this.activity?.let(FragmentActivity::navigateToSettings)
        }
      )
    }
  }

  private suspend fun getStorageDeviceList() =
    (activity as KiwixMainActivity).getStorageDeviceList()
}
