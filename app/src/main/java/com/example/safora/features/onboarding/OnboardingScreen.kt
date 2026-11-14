package com.example.safora.features.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.safora.core.utils.ContactPickerHelper
import com.example.safora.core.utils.PermissionHelper
import com.example.safora.data.model.Contact
import com.example.safora.data.model.User
import com.example.safora.data.repository.SaforaRepository
import com.example.safora.ui.theme.*

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    val repository = SaforaRepository.instance
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var bloodGroup by remember { mutableStateOf("O+") }
    var emergencyContactName by remember { mutableStateOf("") }
    var emergencyContactPhone by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Android Native Contact Picker
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri ->
        contactUri?.let { uri ->
            val contact = ContactPickerHelper.resolveContactFromUri(context, uri)
            contact?.let {
                emergencyContactName = it.name
                emergencyContactPhone = it.phone
            }
        }
    }

    // Android Runtime Permissions Launcher
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Permissions handled; finish onboarding
        repository.updateUser(
            User(
                name = name.ifBlank { "SAFORA User" },
                phone = phone.ifBlank { "+91 (Device Number)" },
                bloodGroup = bloodGroup,
                isOnboarded = true
            )
        )
        if (emergencyContactName.isNotBlank() && emergencyContactPhone.isNotBlank()) {
            repository.addContact(
                Contact(
                    id = "c_${System.currentTimeMillis()}",
                    name = emergencyContactName,
                    phone = emergencyContactPhone,
                    relationship = "Emergency Contact",
                    isEmergencyContact = true
                )
            )
        }
        onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalDark)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        Surface(
            color = CrimsonEmergency.copy(alpha = 0.15f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = CrimsonEmergency,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Welcome to SAFORA",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
        Text(
            text = "Intelligent Personal Safety Setup",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Profile Details Card
        Text(
            text = "YOUR PROFILE DETAILS",
            color = TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Your Name") },
            placeholder = { Text("e.g. Jane Doe") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BluePrimary,
                unfocusedBorderColor = CharcoalBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            label = { Text("Your Mobile Number") },
            placeholder = { Text("+91 98765 43210") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BluePrimary,
                unfocusedBorderColor = CharcoalBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = bloodGroup,
            onValueChange = { bloodGroup = it },
            label = { Text("Blood Group (for Emergency Dispatch)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BluePrimary,
                unfocusedBorderColor = CharcoalBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = CharcoalBorder)
        Spacer(modifier = Modifier.height(16.dp))

        // Emergency Contact Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "PRIMARY EMERGENCY CONTACT",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Will receive instant SMS & live GPS when SOS fires",
                    color = TextTertiary,
                    fontSize = 10.sp
                )
            }

            OutlinedButton(
                onClick = { contactPickerLauncher.launch(null) },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Contacts, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Pick Contact", color = CyanAccent, fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = emergencyContactName,
            onValueChange = { emergencyContactName = it },
            label = { Text("Emergency Contact Name") },
            placeholder = { Text("e.g. Dad / Sister / Roommate") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BluePrimary,
                unfocusedBorderColor = CharcoalBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = emergencyContactPhone,
            onValueChange = { emergencyContactPhone = it },
            label = { Text("Emergency Contact Phone Number") },
            placeholder = { Text("+91 98111 22334") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BluePrimary,
                unfocusedBorderColor = CharcoalBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(errorMessage ?: "", color = CrimsonEmergency, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Grant Permissions and Finish
        Button(
            onClick = {
                if (name.isBlank()) {
                    name = "Jane Doe"
                }
                // Request critical Android system permissions
                val neededPermissions = PermissionHelper.getRequiredRuntimePermissions()
                permissionsLauncher.launch(neededPermissions.toTypedArray())
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BluePrimary),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Grant System Permissions & Launch", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}
