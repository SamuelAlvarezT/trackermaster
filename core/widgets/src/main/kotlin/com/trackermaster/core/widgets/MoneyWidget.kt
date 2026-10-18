package com.trackermaster.core.widgets

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.trackermaster.core.data.money.MoneyPreferences
import com.trackermaster.core.domain.model.AppSettings
import com.trackermaster.core.ui.theme.trackermasterColors
import com.trackermaster.core.domain.money.*
import com.trackermaster.core.ui.R as P8R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import java.time.LocalDate

class ExpenseBalanceWidget : GlanceAppWidget() {
    override val sizeMode=SizeMode.Responsive(setOf(DpSize(110.dp,110.dp),DpSize(240.dp,200.dp),DpSize(280.dp,360.dp)))
    // app bundles EN/ES/ZH together (bundle.language.enableSplit=false); library lint cannot see it.
    @android.annotation.SuppressLint("AppBundleLocaleChanges")
    override suspend fun provideGlance(context: Context,id: GlanceId) {
        val entry=WidgetHiltEntryPoint.get(context)
        val data=combine(entry.moneyRepository().observeWorkspace(),entry.moneyPreferencesRepository().preferences) { data,prefs ->
            MoneyWidgetProjection.build(data,LocalDate.now(),MoneyWidgetSelection(prefs.widgetCurrency,prefs.widgetSpaceId,prefs.widgetAccountId)) }
            .map<MoneyWidgetSummary,MoneyWidgetSummary?> { it }
            .flowOn(Dispatchers.Default).catch { emit(null) }
        val initial=data.first()
        val privacy=entry.moneyPreferencesRepository().preferences.catch { emit(MoneyPreferences(hideAmounts=true)) }
        val settings=entry.settingsRepository().settings
        provideContent {
            val state by data.collectAsState(initial)
            val prefs by privacy.collectAsState(MoneyPreferences(hideAmounts=true))
            val theme by settings.collectAsState(AppSettings())
            val localized=remember(theme.localeTag,context) { context.createConfigurationContext(
                android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag(theme.localeTag)) }) }
            val locale=localized.resources.configuration.locales[0]
            val palette=remember(theme.themeMode,theme.accentIndex) { trackermasterColors(theme.themeMode,theme.accentIndex) }
            val foreground=ColorProvider(palette.onSurface)
            val background=ColorProvider(palette.surface)
            val size=LocalSize.current
            val expanded=size.height>=340.dp
            val medium=size.height>=200.dp && size.width>=230.dp
            fun text(id: Int)=localized.getString(id)
            fun amount(value: Money)=if(prefs.hideAmounts) text(P8R.string.p8_widget_privacy) else value.format(locale)
            fun action(kind: String?=null)=actionStartActivity(Intent().setComponent(ComponentName(context.packageName,"com.trackermaster.app.MainActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .setAction("com.trackermaster.money.WIDGET_${kind ?: "OPEN"}").putExtra("tab","expense").apply {
                    if(kind!=null) putExtra("money_quick_entry",kind)
                })
            Column(GlanceModifier.fillMaxSize().background(background).padding(12.dp)) {
                Column(GlanceModifier.fillMaxWidth().defaultWeight().clickable(action())) {
                    Text(text(P8R.string.p8_section_money),style=TextStyle(color=foreground,fontSize=16.sp))
                    val current=state
                    if(current==null) Text(text(P8R.string.p8_widget_error),style=TextStyle(color=foreground,fontSize=12.sp))
                    else if(!current.hasAccounts) Text(text(P8R.string.p8_widget_empty),style=TextStyle(color=foreground,fontSize=12.sp))
                    else {
                        if(current.balances.isEmpty()) Text(text(P8R.string.p8_widget_missing),style=TextStyle(color=foreground,fontSize=12.sp))
                        current.balances.forEach { Text(amount(it),style=TextStyle(color=foreground,fontSize=14.sp)) }
                        if(expanded) {
                        current.scopes.take(4).forEach { row ->
                            val title=when(row.space.kind) {
                                MoneySpaceKind.PERSONAL->text(P8R.string.p8_widget_personal)
                                MoneySpaceKind.BUSINESS->text(P8R.string.p8_widget_business)
                                MoneySpaceKind.SAVINGS->text(P8R.string.p8_widget_savings)
                                MoneySpaceKind.TRAVEL->text(P8R.string.p8_widget_wyt)
                                else->row.space.name.ifBlank { text(P8R.string.p8_widget_unassigned) }
                            }
                            Text(localized.getString(P8R.string.p8_widget_scope,title,amount(row.balance)),style=TextStyle(color=foreground,fontSize=12.sp),maxLines=1)
                        }
                        if(current.scopes.size>4) Text(localized.getString(P8R.string.p8_widget_more,current.scopes.size-4),style=TextStyle(color=foreground,fontSize=12.sp),maxLines=1)
                        }
                        if(medium && (expanded || current.balances.size<=1)) {
                            Text(text(P8R.string.p8_widget_month),style=TextStyle(color=foreground,fontSize=12.sp))
                            current.month.filter { value -> current.balances.any { it.currencyCode==value.income.currencyCode } }.forEach {
                                Text(localized.getString(P8R.string.p8_widget_income,amount(it.income)),style=TextStyle(color=foreground,fontSize=12.sp),maxLines=1)
                                Text(localized.getString(P8R.string.p8_widget_expense,amount(it.expenses)),style=TextStyle(color=foreground,fontSize=12.sp),maxLines=1)
                            }
                            if(expanded) Text(localized.getString(P8R.string.p8_widget_pending,current.upcomingPayments),style=TextStyle(color=foreground,fontSize=12.sp))
                        }
                    }
                }
                if(medium) Row(GlanceModifier.fillMaxWidth()) {
                    Text(text(P8R.string.p8_widget_quick_expense),modifier=GlanceModifier.defaultWeight().height(48.dp).clickable(action("EXPENSE")),style=TextStyle(color=foreground,fontSize=12.sp))
                    Text(text(P8R.string.p8_widget_quick_income),modifier=GlanceModifier.defaultWeight().height(48.dp).clickable(action("INCOME")),style=TextStyle(color=foreground,fontSize=12.sp))
                }
            }
        }
    }
}
