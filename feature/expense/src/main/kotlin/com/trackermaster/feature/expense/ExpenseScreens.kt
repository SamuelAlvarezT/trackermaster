package com.trackermaster.feature.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.trackermaster.core.data.repository.ExpenseRepository
import com.trackermaster.core.domain.model.*
import com.trackermaster.core.ui.components.SectionHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ExpenseViewModel @Inject constructor(private val repo: ExpenseRepository) : ViewModel() {
    val accounts = repo.observeAccounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = repo.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val transactions = repo.observeTransactions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val balance = repo.totalBalance().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)
    val budgets = repo.observeBudgets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init { viewModelScope.launch { repo.seedDefaultCategories() } }

    fun addTransaction(amount: Double, categoryId: Long, accountId: Long, type: TransactionType) = viewModelScope.launch {
        repo.addTransaction(Transaction(accountId = accountId, amount = amount, type = type, categoryId = categoryId, date = LocalDate.now()))
    }

    fun addAccount(name: String, currency: String = "USD") = viewModelScope.launch {
        repo.addAccount(Account(name = name, type = AccountType.CHECKING, currencyCode = currency))
    }
}

@Composable
fun ExpenseScreen(vm: ExpenseViewModel = hiltViewModel()) {
    val balance by vm.balance.collectAsStateWithLifecycle()
    val txs by vm.transactions.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(floatingActionButton = { FloatingActionButton({ showAdd = true }) { Icon(Icons.Default.Add, null) } }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp)) {
                        Text("Total Balance", style = MaterialTheme.typography.labelLarge)
                        Text("$${"%.2f".format(balance)}", style = MaterialTheme.typography.displaySmall)
                    }
                }
            }
            item { SectionHeader("Transactions") }
            items(txs.take(20)) { tx ->
                val cat = categories.find { it.id == tx.categoryId }
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(cat?.name ?: "—")
                        Text("${if (tx.type == TransactionType.INCOME) "+" else "-"}$${"%.2f".format(tx.amount)}")
                    }
                }
            }
        }
    }
    if (showAdd && accounts.isNotEmpty() && categories.isNotEmpty()) {
        var amount by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Quick expense") },
            text = { OutlinedTextField(amount, { amount = it }, label = { Text("Amount") }) },
            confirmButton = {
                TextButton({
                    amount.toDoubleOrNull()?.let { v ->
                        vm.addTransaction(v, categories.first { !it.isIncome }.id, accounts.first().id, TransactionType.EXPENSE)
                    }
                    showAdd = false
                }) { Text("Save") }
            },
        )
    }
}

@Composable
fun ExpenseReportsScreen(vm: ExpenseViewModel = hiltViewModel()) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val txs by vm.transactions.collectAsStateWithLifecycle()
    val expenseByCategory = txs.filter { it.type == TransactionType.EXPENSE }
        .groupBy { it.categoryId }.mapValues { (_, list) -> list.sumOf { it.amount } }
    Column(Modifier.padding(16.dp)) {
        SectionHeader("Spending by category")
        expenseByCategory.forEach { (catId, total) ->
            val name = categories.find { it.id == catId }?.name ?: "?"
            LinearProgressIndicator(progress = { (total / (expenseByCategory.values.maxOrNull() ?: 1.0)).toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            Text("$name: $${"%.2f".format(total)}")
        }
    }
}
