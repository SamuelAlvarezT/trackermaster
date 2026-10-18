package com.trackermaster.feature.habits

import com.trackermaster.core.data.repository.HabitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class HabitsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockRepo: HabitRepository
    private lateinit var viewModel: HabitsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockRepo = mock(HabitRepository::class.java)
        
        `when`(mockRepo.observeDay()).thenReturn(MutableStateFlow(java.time.LocalDate.now()))
        // Setup mock flows
        `when`(mockRepo.observeActiveHabits()).thenReturn(MutableStateFlow(emptyList()))
        `when`(mockRepo.observeAchievements()).thenReturn(MutableStateFlow(emptyList()))
        // we use any() for date matching or just return empty flows
        `when`(mockRepo.observeActiveHabitsWithLogs(org.mockito.kotlin.any())).thenReturn(MutableStateFlow(emptyList()))
        `when`(mockRepo.observeArchivedHabitsWithLogs(org.mockito.kotlin.any())).thenReturn(MutableStateFlow(emptyList()))

        viewModel = HabitsViewModel(mockRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState has selectedTab 0 and isReorderMode false`() = runTest {
        val state = viewModel.uiState.value
        assertEquals(0, state.selectedTab)
        assertEquals(false, state.isReorderMode)
    }

    @Test
    fun `selectTab updates selectedTab and resets isReorderMode`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        viewModel.toggleReorderMode() // set to true
        viewModel.selectTab(1)
        runCurrent()
        
        val state = viewModel.uiState.value
        assertEquals(1, state.selectedTab)
        assertEquals(false, state.isReorderMode)
    }

    @Test
    fun `toggleReorderMode toggles the state correctly`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        viewModel.toggleReorderMode()
        runCurrent()
        assertEquals(true, viewModel.uiState.value.isReorderMode)
        
        viewModel.toggleReorderMode()
        runCurrent()
        assertEquals(false, viewModel.uiState.value.isReorderMode)
    }
}
