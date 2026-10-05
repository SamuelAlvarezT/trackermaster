package com.trackermaster.feature.expense

import android.content.Context
import android.content.res.Configuration
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.trackermaster.core.data.money.ReceiptFiles
import com.trackermaster.core.data.money.ReceiptSource
import com.trackermaster.core.domain.money.*
import com.trackermaster.core.ui.theme.TrackermasterTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MoneyReceiptActivity : FragmentActivity() {
    private val receipts by viewModels<ReceiptViewModel>()
    private val money by viewModels<MoneyViewModel>()
    @Inject lateinit var files: ReceiptFiles
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { receipts.receive(ReceiptSource.SharedFile(it)) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        if(savedInstanceState==null) {
            try { receiptSource(intent)?.let(receipts::receive) }
            catch(e:ReceiptException) {receipts.onEvent(ReceiptEvent.Failure(e.reason))}
        }
        setContent {
            val state by receipts.state.collectAsStateWithLifecycle()
            val accounts by money.uiState.collectAsStateWithLifecycle()
            var authenticated by remember {mutableStateOf(false)}
            MoneyLocale(state.settings.localeTag) { TrackermasterTheme(state.settings.themeMode,state.settings.accentIndex) {
                BackHandler { if(!state.saving) {
                    if(state.settings.biometricEnabled && !authenticated) finish()
                    else if(state.receipt!=null || state.reading) receipts.onEvent(ReceiptEvent.Back) else finish()
                } }
                ReceiptAccess(state,authenticated,gate={ReceiptAuthentication(this,{authenticated=true},{finish()})},failure={
                    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {Column(Modifier.padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.r_error_read))
                        MoneyOutlinedButton({receipts.onEvent(ReceiptEvent.Retry)}){Text(stringResource(R.string.m_retry))}
                        MoneyTextButton({finish()}){Text(stringResource(R.string.r_close))}
                    }}
                }) {
                    ReceiptScreen(state,receipts::onEvent,{picker.launch(arrayOf("image/*","application/pdf","text/plain"))},{finish()},
                        onNewAccount={money.onEvent(MoneyEvent.Currency(state.form.currency));money.onEvent(MoneyEvent.OpenAccount())},
                        onView={ReceiptViewer.open(this,it.path,state.settings.localeTag)},
                        preview=state.receipt?.previewPath?.let {runCatching{files.resolve(it)}.getOrNull()})
                    MoneyPanels(accounts,money::onEvent)
                }
            } }
        }
    }
}

/** Only a single content URI or plain text is accepted. Extras cannot choose financial fields. */
internal fun receiptSource(intent: Intent): ReceiptSource? {
    if(intent.action==Intent.ACTION_SEND_MULTIPLE) throw ReceiptException(ReceiptFailure.MULTIPLE_FILES)
    if(intent.action!=Intent.ACTION_SEND) return null
    val uris=mutableListOf<Uri>()
    IntentCompat.getParcelableExtra(intent,Intent.EXTRA_STREAM,Uri::class.java)?.let(uris::add)
    intent.clipData?.let { clip -> for(i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let(uris::add) }
    val distinct=uris.distinct()
    if(distinct.size>1) throw ReceiptException(ReceiptFailure.MULTIPLE_FILES)
    distinct.singleOrNull()?.let {
        if(it.scheme!="content") throw ReceiptException(ReceiptFailure.UNSUPPORTED)
        return ReceiptSource.SharedFile(it)
    }
    if(intent.type=="text/plain") return ReceiptSource.SharedText(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty())
    throw ReceiptException(ReceiptFailure.UNSUPPORTED)
}

internal object ReceiptViewer {
    // All three languages are bundled (app disables language splits).
    @android.annotation.SuppressLint("AppBundleLocaleChanges")
    fun open(context: Context,path: String,localeTag:String?=null) {
        try {
            val file=ReceiptFiles(context.applicationContext).resolve(path)
            require(file.isFile)
            val mime=when(file.extension) {"pdf"->"application/pdf";"png"->"image/png";"webp"->"image/webp";"jpg"->"image/jpeg";else->"text/plain"}
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.money.receipts",file)
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch(_:Exception) {
            val localized=if(localeTag==null) context else context.createConfigurationContext(Configuration(context.resources.configuration).apply{setLocale(java.util.Locale.forLanguageTag(localeTag))})
            Toast.makeText(context,localized.getString(R.string.r_no_viewer),Toast.LENGTH_LONG).show()
        }
    }
}
