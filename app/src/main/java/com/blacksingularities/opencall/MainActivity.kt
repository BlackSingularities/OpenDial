package com.blacksingularities.opencall

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.blacksingularities.opencall.call.CallManager
import com.blacksingularities.opencall.data.PhoneText
import com.blacksingularities.opencall.ui.AppRoot
import com.blacksingularities.opencall.ui.AppViewModel
import com.blacksingularities.opencall.ui.Route
import com.blacksingularities.opencall.ui.theme.OpenDialTheme
import com.blacksingularities.opencall.update.UpdateManager

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.reload()
        // Po nadaniu roli od razu pozostałe uprawnienia (część system nadaje razem z rolą).
        if (vm.missingPermissions.isNotEmpty()) permissionLauncher.launch(vm.missingPermissions)
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        UpdateManager.clearUpdatedNotification(this)
        CallManager.attach(this)
        if (savedInstanceState == null) handleIntent(intent)

        // Pierwsze uruchomienie: najpierw prośba o rolę aplikacji telefonu, potem o uprawnienia.
        if (savedInstanceState == null && !vm.settings.onboardingDone) {
            vm.updateSettings { it.copy(onboardingDone = true) }
            requestRole()
        }

        setContent {
            OpenDialTheme {
                AppRoot(vm, onRequestRole = ::requestRole, onRequestPermissions = ::requestPermissions)
            }
        }
    }

    private fun requestRole() {
        val intent = com.blacksingularities.opencall.call.Dialer.roleRequest(this)
        if (intent != null) {
            runCatching { roleLauncher.launch(intent) }.onFailure { requestPermissions() }
        } else {
            requestPermissions()
        }
    }

    private fun requestPermissions() {
        val missing = vm.missingPermissions
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing)
        } else {
            vm.reload()
        }
    }

    override fun onResume() {
        super.onResume()
        vm.reload()
        vm.checkUpdateIfDue()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** tel:, DIAL, kontakty z innych aplikacji, skróty z powiadomień i „dodaj połączenie”. */
    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_DIAL, Intent.ACTION_VIEW, Intent.ACTION_CALL_BUTTON -> {
                val data = intent.data
                when {
                    data?.scheme == "tel" -> openDialer(data.schemeSpecificPart ?: "")
                    data?.scheme == "voicemail" -> vm.voicemail()
                    data != null && data.authority == ContactsContract.AUTHORITY -> openContact(data)
                    intent.action == Intent.ACTION_DIAL || intent.action == Intent.ACTION_CALL_BUTTON -> openDialer("")
                }
            }
            Intent.ACTION_INSERT -> {
                vm.stack.clear()
                vm.newContact(intent.getStringExtra(ContactsContract.Intents.Insert.PHONE), intent.getStringExtra(ContactsContract.Intents.Insert.NAME))
            }
            ACTION_SHOW_RECENTS -> {
                vm.stack.clear()
                vm.selectTab(1)
            }
            ACTION_SHOW_BLOCKED -> {
                vm.stack.clear()
                vm.push(Route.Blocked)
            }
            ACTION_SHOW_AI -> {
                vm.stack.clear()
                vm.push(Route.AiHistory)
            }
            ACTION_ADD_CALL -> {
                vm.stack.clear()
                vm.addingCall = true
                vm.dialed = ""
                vm.openKeypad()
            }
        }
    }

    private fun openDialer(number: String) {
        vm.stack.clear()
        vm.dialed = PhoneText.digits(Uri.decode(number)).ifEmpty { number.filter { it.isDigit() || it in "+*#" } }
        vm.openKeypad()
    }

    private fun openContact(uri: Uri) {
        val id = vm.contactsRepo.idForLookupUri(uri) ?: return
        vm.stack.clear()
        vm.push(Route.ContactInfo(id))
    }

    companion object {
        const val ACTION_SHOW_RECENTS = "com.blacksingularities.opencall.SHOW_RECENTS"
        const val ACTION_SHOW_BLOCKED = "com.blacksingularities.opencall.SHOW_BLOCKED"
        const val ACTION_SHOW_AI = "com.blacksingularities.opencall.SHOW_AI"
        const val ACTION_ADD_CALL = "com.blacksingularities.opencall.ADD_CALL"
    }
}
