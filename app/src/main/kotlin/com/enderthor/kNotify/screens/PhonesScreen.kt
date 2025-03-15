package com.enderthor.kNotify.screens

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.enderthor.kNotify.data.ConfigData
import com.enderthor.kNotify.extension.loadPreferencesFlow
import com.enderthor.kNotify.extension.savePreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhonesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf<ConfigData?>(null) }
    var phoneNumber1 by remember { mutableStateOf("") }
    var phoneNumber2 by remember { mutableStateOf("") }
    var phoneNumber3 by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    var isPhone1Valid by remember { mutableStateOf(true) }
    var isPhone2Valid by remember { mutableStateOf(true) }
    var isPhone3Valid by remember { mutableStateOf(true) }


    var ignoreAutoSave by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        context.loadPreferencesFlow().collect { configs ->
            if (configs.isNotEmpty()) {
                val savedConfig = configs.first()
                config = savedConfig
                val phoneNumbers = savedConfig.phoneNumbers
                if (phoneNumbers.isNotEmpty()) {
                    phoneNumber1 = phoneNumbers[0]
                    if (phoneNumbers.size > 1) phoneNumber2 = phoneNumbers[1]
                    if (phoneNumbers.size > 2) phoneNumber3 = phoneNumbers[2]
                }
                ignoreAutoSave = false
            }
        }
    }

   fun validatePhoneNumber(phoneNumber: String): Pair<Boolean, String> {
        val trimmedPhone = phoneNumber.trim()

        if (trimmedPhone.isEmpty()) {
            return Pair(true, "")
        }

        if (trimmedPhone.contains("+")) {
            return Pair(false, "Phone number must be entered without the '+' symbol and with country code. Example: 34675123123")
        }

        if (!trimmedPhone.all { it.isDigit() }) {
            return Pair(false, "Phone number must contain only digits. Example: 34675123123")
        }

        if (trimmedPhone.length < 10 || trimmedPhone.length > 15) {
            return Pair(false, "Phone number must have between 10-15 digits, country code is required. Example: 34675123123")
        }

        return Pair(true, "")
    }


    fun saveData() {
        if (ignoreAutoSave) return


        val phone1Result = validatePhoneNumber(phoneNumber1)
        val phone2Result = validatePhoneNumber(phoneNumber2)
        val phone3Result = validatePhoneNumber(phoneNumber3)

        isPhone1Valid = phone1Result.first
        isPhone2Valid = phone2Result.first
        isPhone3Valid = phone3Result.first

        if (isPhone1Valid && isPhone2Valid && isPhone3Valid) {
            val phoneNumbers = listOfNotNull(
                phoneNumber1.trim().takeIf { it.isNotBlank() },
                phoneNumber2.trim().takeIf { it.isNotBlank() },
                phoneNumber3.trim().takeIf { it.isNotBlank() }
            )

            val updatedConfig = config?.copy(
                phoneNumbers = phoneNumbers
            ) ?: ConfigData(
                phoneNumbers = phoneNumbers
            )

            scope.launch {
                savePreferences(context, mutableListOf(updatedConfig))
                statusMessage = "Phone numbers saved"
                kotlinx.coroutines.delay(2000)
                statusMessage = null
            }
        } else {
            statusMessage = "Cannot save: Some phone numbers have invalid format"
            scope.launch {
                kotlinx.coroutines.delay(3000)
                statusMessage = null
            }
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Phone Numbers",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "Enter up to 3 phone numbers that will receive notifications",
                    style = MaterialTheme.typography.bodyMedium
                )

                // Error 1: validatePhoneNumber devuelve un Pair<Boolean, String>, no un Boolean
                var phone1ErrorMessage by remember { mutableStateOf("") }
                var phone2ErrorMessage by remember { mutableStateOf("") }
                var phone3ErrorMessage by remember { mutableStateOf("") }

                PhoneNumberInput(
                    value = phoneNumber1,
                    onValueChange = {
                        phoneNumber1 = it
                        val (valid, message) = validatePhoneNumber(it)
                        isPhone1Valid = valid
                        phone1ErrorMessage = message
                    },
                    label = "Number 1",
                    isValid = isPhone1Valid,
                    errorMessage = phone1ErrorMessage,
                    onClear = {
                        phoneNumber1 = ""
                        isPhone1Valid = true
                        phone1ErrorMessage = ""
                        saveData()
                    },
                    onDone = { saveData() },
                    onFocusChange = { focusState ->
                        if (!focusState.isFocused) {
                            saveData()
                        }
                    }
                )

                PhoneNumberInput(
                    value = phoneNumber2,
                    onValueChange = {
                        phoneNumber2 = it
                        val (valid, message) = validatePhoneNumber(it)
                        isPhone2Valid = valid
                        phone2ErrorMessage = message
                    },
                    label = "Number 2",
                    isValid = isPhone2Valid,
                    errorMessage = phone2ErrorMessage,
                    onClear = {
                        phoneNumber2 = ""
                        isPhone2Valid = true
                        phone2ErrorMessage = ""
                        saveData()
                    },
                    onDone = { saveData() },
                    onFocusChange = { focusState ->
                        if (!focusState.isFocused) {
                            saveData()
                        }
                    }
                )

                PhoneNumberInput(
                    value = phoneNumber3,
                    onValueChange = {
                        phoneNumber3 = it
                        val (valid, message) = validatePhoneNumber(it)
                        isPhone3Valid = valid
                        phone3ErrorMessage = message
                    },
                    label = "Number 3",
                    isValid = isPhone3Valid,
                    errorMessage = phone3ErrorMessage,
                    onClear = {
                        phoneNumber3 = ""
                        isPhone3Valid = true
                        phone3ErrorMessage = ""
                        saveData()
                    },
                    onDone = { saveData() },
                    onFocusChange = { focusState ->
                        if (!focusState.isFocused) {
                            saveData()
                        }
                    }
                )
            }

            statusMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun PhoneNumberInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isValid: Boolean,
    errorMessage: String,
    onClear: () -> Unit,
    onDone: () -> Unit,
    onFocusChange: (FocusState) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text("34675123123") }, // Cambiado para ser coherente con la validación
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged(onFocusChange)
            .focusable(),
        singleLine = true,
        isError = !isValid,
        supportingText = {
            if (!isValid) {
                Text(errorMessage)
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(
            onDone = { onDone() }
        ),
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear")
                }
            }
        }
    )
}