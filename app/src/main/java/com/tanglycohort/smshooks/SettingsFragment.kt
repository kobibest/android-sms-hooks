package com.tanglycohort.smshooks

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavBackStackEntry
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import java.io.FileNotFoundException

class SettingsFragment : PreferenceFragmentCompat() {
    /**
     * Simple class that extends [ActivityResultContracts.CreateDocument]
     * Just sets the category (openable) & MIME type to text/plain
     */
    class CreateTextFile : CreateDocument("text/plain") {
        override fun createIntent(context: Context, input: String): Intent {
            return super.createIntent(context, input).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
            }
        }
    }

    private lateinit var preferences: SharedPreferences
    private lateinit var userId: Preference

    private val registrationToCreateTextFile =
        registerForActivityResult(
            CreateTextFile()
        ) { uri: Uri? -> onCreateTextFileResult(uri) }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        preferences = preferenceManager.sharedPreferences!!
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPreferences()
        findNavController().currentBackStackEntry?.also {
            observeDialogResults(it)
        }
    }

    private fun setupPreferences() {
        (findPreference("userId") as Preference?)?.apply {
            userId = this
            summary = preferences.getString(key, "Not set")
            setOnPreferenceClickListener { preference -> onUserIdClick(preference) }
        }
        (findPreference("exportLogs") as Preference?)?.apply {
            setOnPreferenceClickListener { onExportPreferenceClick() }
        }
        (findPreference(MainActivity.BACKGROUND_MODE_KEY) as Preference?)?.apply {
            setOnPreferenceChangeListener { _, newValue ->
                onBackgroundModePreferenceChange(newValue as Boolean)
            }
        }
    }

    private fun observeDialogResults(backStackEntry: NavBackStackEntry) {
        LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (backStackEntry.savedStateHandle.contains(userId.key)) {
                    backStackEntry.savedStateHandle.get<String>(userId.key).also {
                        setPreference(userId.key, it ?: "")
                        userId.summary = it ?: "Not set"
                    }
                }
            }
        }.also {
            backStackEntry.lifecycle.addObserver(it)
            viewLifecycleOwner.lifecycle.addObserver(LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_DESTROY) {
                    backStackEntry.lifecycle.removeObserver(it)
                }
            })
        }
    }

    private fun onUserIdClick(preference: Preference): Boolean {
        SettingsFragmentDirections.apply {
            actionSettingsFragmentToUrlPreferenceDialogFragment(
                initialValue = preferences.getString(preference.key, "")!!,
                key = preference.key,
                forceHttps = false
            ).also { findNavController().navigate(it) }
        }
        return true
    }

    private fun onExportPreferenceClick(): Boolean {
        registrationToCreateTextFile.launch("smshooks_log.txt")
        return true
    }

    private fun onBackgroundModePreferenceChange(isEnabled: Boolean): Boolean {
        context?.also {
            if (isEnabled) {
                SmsHooksForegroundService.start(it)
            } else {
                SmsHooksForegroundService.stop(it)
            }
        }
        return true
    }

    /**
     * After use chooses a location and name to save the log file,
     * we get a file URI as a result
     */
    private fun onCreateTextFileResult(uri: Uri?) {
        if (uri == null)
            return
        preferenceManager.context.also { context ->
            try {
                context.openFileInput("log").use { privateLogFile ->
                    context.contentResolver.openOutputStream(uri)?.use { outputLogFile ->
                        privateLogFile.copyTo(outputLogFile)
                    }
                }
            } catch (e: FileNotFoundException) {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            }
        }
    }

    private fun setPreference(key: String, value: Any) {
        preferences.edit().apply {
            putString(key, value as String)
            apply()
        }
    }
}
