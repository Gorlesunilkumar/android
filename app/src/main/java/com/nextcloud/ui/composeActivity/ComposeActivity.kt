/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Alper Ozturk <alper.ozturk@nextcloud.com>
 * SPDX-FileCopyrightText: 2024 Nextcloud GmbH
 * SPDX-License-Identifier: AGPL-3.0-or-later OR GPL-2.0-only
 */
package com.nextcloud.ui.composeActivity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.coroutineScope
import com.google.gson.GsonBuilder
import com.nextcloud.client.assistant.AssistantScreen
import com.nextcloud.client.assistant.AssistantViewModel
import com.nextcloud.client.assistant.repository.AssistantRepository
import com.nextcloud.common.JSONRequestBody
import com.nextcloud.common.NextcloudClient
import com.nextcloud.operations.GetMethod
import com.nextcloud.operations.PostMethod
import com.nextcloud.ui.DeclarativeUiScreen
import com.nextcloud.utils.extensions.getSerializableArgument
import com.owncloud.android.R
import com.owncloud.android.databinding.ActivityComposeBinding
import com.owncloud.android.lib.resources.declarativeui.DeclarativeUI
import com.owncloud.android.lib.resources.declarativeui.Element
import com.owncloud.android.lib.resources.declarativeui.ElementTypeAdapter
import com.owncloud.android.lib.resources.declarativeui.Endpoint
import com.owncloud.android.lib.resources.status.Method
import com.owncloud.android.ui.activity.DrawerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.RequestBody
import java.net.HttpURLConnection.HTTP_OK

class ComposeActivity : DrawerActivity() {

    lateinit var binding: ActivityComposeBinding

    companion object {
        const val DESTINATION = "DESTINATION"
        const val TITLE = "TITLE"
        const val TITLE_STRING = "TITLE_STRING"
        const val ARGS_ENDPOINT = "ARGS_ENDPOINT"
        const val ARGS_FILE_ID = "ARGS_FILE_ID"
        const val ARGS_FILE_PATH = "ARGS_FILE_PATH"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityComposeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val destination = intent.getSerializableArgument(DESTINATION, ComposeDestination::class.java)
        var title = intent.getStringExtra(TITLE_STRING)

        if (title == null || title.isEmpty()) {
            title = getString(intent.getIntExtra(TITLE, R.string.empty))
        }

        if (destination == ComposeDestination.AssistantScreen) {
            setupDrawer()

            setupToolbarShowOnlyMenuButtonAndTitle(title) {
                openDrawer()
            }
        } else {
            setSupportActionBar(null)
            if (findViewById<View?>(R.id.appbar) != null) {
                findViewById<View?>(R.id.appbar)?.visibility = View.GONE
            }
        }

        // if (false) {
        //     val actionBar = getDelegate().supportActionBar
        //     actionBar?.setDisplayHomeAsUpEnabled(true)
        //     actionBar?.setDisplayShowTitleEnabled(true)
        //
        //     val menuIcon = ResourcesCompat.getDrawable(
        //         getResources(),
        //         R.drawable.ic_arrow_back,
        //         null
        //     )
        //     viewThemeUtils.androidx.themeActionBar(
        //         this,
        //         actionBar!!,
        //         title!!,
        //         menuIcon!!
        //     )
        // }

        binding.composeView.setContent {
            MaterialTheme(
                colorScheme = viewThemeUtils.getColorScheme(this),
                content = {
                    Content(destination, intent)
                }
            )
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            super.onBackPressed()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    @SuppressLint("CoroutineCreationDuringComposition")
    @Composable
    private fun Content(destination: ComposeDestination?, intent1: Intent) {
        var nextcloudClient by remember { mutableStateOf<NextcloudClient?>(null) }

        LaunchedEffect(Unit) {
            nextcloudClient = clientRepository.getNextcloudClient()
        }

        if (destination == ComposeDestination.AssistantScreen) {
            binding.bottomNavigation.menu.findItem(R.id.nav_assistant).run {
                isChecked = true
            }

            nextcloudClient?.let { client ->
                AssistantScreen(
                    viewModel = AssistantViewModel(
                        repository = AssistantRepository(client, capabilities)
                    ),
                    activity = this,
                    capability = capabilities
                )
            }
        } else if (destination == ComposeDestination.DeclarativeUi) {
            binding.bottomNavigation.visibility = View.GONE

            val endpoint: Endpoint? = intent.getParcelableExtra(ARGS_ENDPOINT)
            val fileId = intent.getLongExtra(ARGS_FILE_ID, -1)
            val remotePath = intent.getStringExtra(ARGS_FILE_PATH).orEmpty()

            if (nextcloudClient != null && endpoint != null) {
                var test by remember { mutableStateOf<String?>(null) }
                var baseUrl by remember { mutableStateOf<String?>(null) }
                lifecycle.coroutineScope.launch(Dispatchers.IO) {
                    // construct url
                    var url = nextcloudClient!!.baseUri.toString() + endpoint.url
                    baseUrl = nextcloudClient!!.baseUri.toString()

                    val method = when (endpoint.method) {
                        Method.GET -> {
                            endpoint.params!!.forEach {
                                when (it.value) {
                                    "{fileId}" -> url = url.replace(it.key, fileId.toString(), false)
                                    "{filePath}" -> url = url.replace(it.key, remotePath, false)
                                }
                            }
                            GetMethod(url, true)
                        }

                        Method.POST -> {
                            val requestBody = if (endpoint.params?.isNotEmpty() == true) {
                                val jsonRequestBody = JSONRequestBody()
                                endpoint.params!!.forEach {
                                    when (it.value) {
                                        "{filePath}" -> jsonRequestBody.put(it.key, remotePath)
                                        "{fileId}" -> jsonRequestBody.put(it.key, fileId.toString())
                                    }
                                }

                                jsonRequestBody.get()
                            } else {
                                RequestBody.EMPTY
                            }

                            PostMethod(url, true, requestBody)
                        }

                        else -> GetMethod(url, true)
                    }

                    val result = try {
                        nextcloudClient?.execute(method)
                    } catch (exception: Exception) {
                        val e = exception
                        TODO("Add error handling here")
                    }
                    test = method.getResponseBodyAsString()

                    val success = result == HTTP_OK


                    if (success) {
                        //DeclarativeUiScreen(parseResult(test))
                    }
                }

                if (test != null) {
                    DeclarativeUiScreen(parseResult(test), baseUrl!!)
                }
            }
        }
    }

    fun parseResult(response: String?): DeclarativeUI {
        val gson =
            GsonBuilder()
                .registerTypeHierarchyAdapter(Element::class.java, ElementTypeAdapter())
                .create()

        return gson.fromJson(response, DeclarativeUI::class.java)
    }
}
