package com.michal.mvvmcompose

import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.appcompattheme.AppCompatTheme
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@HiltAndroidApp
class MvvvmComposeApplication : Application()

@AndroidEntryPoint
class MvvmComposeActivity : ComponentActivity() {

    private val viewModel: MvvmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppCompatTheme {
                MvvmApp(viewModel)
            }
        }
    }
}

@Composable
fun MvvmApp(
    viewModel: MvvmViewModel
) {
    val navController = rememberNavController()
    NavHost(
        navController,
        startDestination = "question"
    ) {
        composable("question") {
            QuestionPage(viewModel = viewModel) {
                navController.navigate("result")
            }
        }
        composable("result") {
            ResultPage(viewModel = viewModel)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
class MvvmHiltModule {

    @Provides
    @Singleton
    fun provideAnswerService(): AnswerService = AnswerService()
}

@Composable
fun QuestionPage(
    viewModel: MvvmViewModel,
    onConfirm: () -> Unit
) {
    val textFieldState = remember { mutableStateOf(TextFieldValue()) }

    LaunchedEffect("key") {
        viewModel.navigateToResults
            .onEach {
                onConfirm.invoke()
            }
            .collect()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "What do you call a mexican cheese?")
        TextField(
            value = textFieldState.value,
            onValueChange = { textFieldState.value = it }
        )
        if (viewModel.isLoading.collectAsState().value) {
            CircularProgressIndicator()
        } else {
            Button(onClick = { viewModel.confirmAnswer(textFieldState.value.text) }) {
                Text(text = "Confirm")
            }
        }
    }
}

@Composable
fun ResultPage(
    viewModel: MvvmViewModel
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = viewModel.textToDisplay.collectAsState().value)
    }
}

class AnswerService {
    suspend fun save(answer: String) {
        Log.v("Api call", "Make a call to an api")
        delay(1000)
    }
}

@HiltViewModel
class MvvmViewModel @Inject constructor(
    private val answerService: AnswerService
) : ViewModel() {

    private val _isLoading: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _textToDisplay: MutableStateFlow<String> = MutableStateFlow("")
    val textToDisplay = _textToDisplay.asStateFlow()

    // See https://proandroiddev.com/android-singleliveevent-redux-with-kotlin-flow-b755c70bb055
    // For why channel > SharedFlow/StateFlow in this case
    private val _navigateToResults = Channel<Boolean>(Channel.BUFFERED)
    val navigateToResults = _navigateToResults.receiveAsFlow()

    fun confirmAnswer(answer: String) {
        viewModelScope.launch {
            _isLoading.value = true
            withContext(Dispatchers.IO) { answerService.save(answer) }
            val text = if (answer == "Nacho cheese") {
                "You've heard too many cheese jokes"
            } else {
                "Nacho cheese"
            }
            _textToDisplay.emit(text)
            _navigateToResults.send(true)
            _isLoading.value = false
        }
    }
}