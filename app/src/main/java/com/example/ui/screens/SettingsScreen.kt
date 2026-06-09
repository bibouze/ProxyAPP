package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ProxySettings
import com.example.proxy.ProxyViewModel

@Composable
fun SettingsScreen(viewModel: ProxyViewModel) {
    val currentSettings by viewModel.settingsState.collectAsState()
    val scrollState = rememberScrollState()

    // Form inputs state
    var httpPortStr by remember { mutableStateOf("") }
    var socksPortStr by remember { mutableStateOf("") }
    var authEnabled by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var ipWhitelist by remember { mutableStateOf("") }
    
    // Reverse Tunnel inputs
    var tunnelEnabled by remember { mutableStateOf(false) }
    var vpsAddress by remember { mutableStateOf("") }
    var vpsSecret by remember { mutableStateOf("") }
    var remotePortStr by remember { mutableStateOf("") }

    // System WakeLocks
    var wifiLockEnabled by remember { mutableStateOf(true) }
    var wakeLockEnabled by remember { mutableStateOf(true) }
    var autoRestart by remember { mutableStateOf(true) }

    var passwordVisible by remember { mutableStateOf(false) }
    var vpsSecretVisible by remember { mutableStateOf(false) }

    // Initialize from DB
    LaunchedEffect(currentSettings) {
        httpPortStr = currentSettings.httpPort.toString()
        socksPortStr = currentSettings.socksPort.toString()
        authEnabled = currentSettings.authEnabled
        username = currentSettings.username
        password = currentSettings.password
        ipWhitelist = currentSettings.ipWhitelist
        tunnelEnabled = currentSettings.reverseTunnelEnabled
        vpsAddress = currentSettings.vpsAddress
        vpsSecret = currentSettings.vpsSecret
        remotePortStr = currentSettings.remotePort.toString()
        wifiLockEnabled = currentSettings.wifiLockEnabled
        wakeLockEnabled = currentSettings.wakeLockEnabled
        autoRestart = currentSettings.autoRestartOnNetworkChange
    }

    // Modern High Density TextField Colors
    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color(0xFFD0BCFF),
        unfocusedBorderColor = Color(0x1AFFFFFF),
        focusedLabelColor = Color(0xFFD0BCFF),
        unfocusedLabelColor = Color(0xFF919196),
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedContainerColor = Color(0xFF111318),
        unfocusedContainerColor = Color(0xFF111318),
        focusedLeadingIconColor = Color(0xFFD0BCFF),
        unfocusedLeadingIconColor = Color(0xFF919196)
    )

    // Dynamic Switch & Checkbox colors matching Purple / Mint Green theme
    val customSwitchColors = SwitchDefaults.colors(
        checkedTrackColor = Color(0xFFB4E2B4),
        checkedThumbColor = Color(0xFF111318),
        uncheckedTrackColor = Color(0x33FFFFFF),
        uncheckedThumbColor = Color(0xFF919196)
    )

    val customCheckboxColors = CheckboxDefaults.colors(
        checkedColor = Color(0xFFD0BCFF),
        uncheckedColor = Color(0xFF919196)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column {
            Text(
                text = "Configuration de l'Hôte",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Modifiez les ports d'écoute et la sécurité du proxy local.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF919196)
            )
        }

        // 1. Core network settings card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F26)),
            border = BorderStroke(1.dp, Color(0x0DFFFFFF))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Ports d'Écoute réseaux",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD0BCFF),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = httpPortStr,
                        onValueChange = { httpPortStr = it },
                        label = { Text("Port HTTP") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        leadingIcon = { Icon(Icons.Default.Share, "portHttp") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = textFieldColors
                    )

                    OutlinedTextField(
                        value = socksPortStr,
                        onValueChange = { socksPortStr = it },
                        label = { Text("Port SOCKS5") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        leadingIcon = { Icon(Icons.Default.Share, "portSocks5") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = textFieldColors
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = ipWhitelist,
                    onValueChange = { ipWhitelist = it },
                    label = { Text("IPs Autorisées (Optionnel, sép. par virgule)") },
                    placeholder = { Text("Ex: 192.168.1.15, 10.0.0.4") },
                    leadingIcon = { Icon(Icons.Default.Lock, "whitelist") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = textFieldColors
                )
            }
        }

        // 2. Authentication Configuration
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F26)),
            border = BorderStroke(1.dp, Color(0x0DFFFFFF))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Sécurité et Authentification",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD0BCFF),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Exiger un mot de passe basic pour la connexion",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF919196)
                        )
                    }
                    Switch(
                        checked = authEnabled,
                        onCheckedChange = { authEnabled = it },
                        colors = customSwitchColors
                    )
                }

                if (authEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Nom d'utilisateur") },
                        leadingIcon = { Icon(Icons.Default.Person, "user") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = textFieldColors
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Mot de passe") },
                            leadingIcon = { Icon(Icons.Default.Lock, "pw") },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Visualiser",
                                        tint = Color(0xFF919196)
                                    )
                                }
                            },
                            modifier = Modifier.weight(1.8f),
                            singleLine = true,
                            colors = textFieldColors
                        )

                        Button(
                            onClick = { password = viewModel.generateRandomPassword() },
                            modifier = Modifier
                                .weight(1.1f)
                                .height(54.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2D3139),
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color(0x1AFFFFFF))
                        ) {
                            Text("Générer", fontSize = 12.sp, maxLines = 1, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // 3. Reverse tunneling card config
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F26)),
            border = BorderStroke(1.dp, Color(0x0DFFFFFF))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tunnel Inverse (FRP VPS Bridge)",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD0BCFF),
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Contourner le CGNAT de la 4G/5G mobile",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF919196)
                        )
                    }
                    Switch(
                        checked = tunnelEnabled,
                        onCheckedChange = { tunnelEnabled = it },
                        colors = customSwitchColors
                    )
                }

                if (tunnelEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = vpsAddress,
                        onValueChange = { vpsAddress = it },
                        label = { Text("Adresse du VPS distant") },
                        placeholder = { Text("Ex: vps.mondomaine.com ou IP fixe") },
                        leadingIcon = { Icon(Icons.Default.Home, "vpsHost") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = textFieldColors
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = vpsSecret,
                            onValueChange = { vpsSecret = it },
                            label = { Text("Clé secrète / Token") },
                            visualTransformation = if (vpsSecretVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { vpsSecretVisible = !vpsSecretVisible }) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Visualiser VPS",
                                        tint = Color(0xFF919196)
                                    )
                                }
                            },
                            modifier = Modifier.weight(1.3f),
                            singleLine = true,
                            colors = textFieldColors
                        )

                        OutlinedTextField(
                            value = remotePortStr,
                            onValueChange = { remotePortStr = it },
                            label = { Text("Port distant") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.7f),
                            singleLine = true,
                            colors = textFieldColors
                        )
                    }
                }
            }
        }

        // 4. Background and Lock persistence configurations
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F26)),
            border = BorderStroke(1.dp, Color(0x0DFFFFFF))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Stabilité en Arrière-Plan",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD0BCFF),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "CPU WakeLock actif",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Checkbox(
                        checked = wakeLockEnabled,
                        onCheckedChange = { wakeLockEnabled = it },
                        colors = customCheckboxColors
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Wi-Fi Lock haute performance",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Checkbox(
                        checked = wifiLockEnabled,
                        onCheckedChange = { wifiLockEnabled = it },
                        colors = customCheckboxColors
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Rechargement lors de variation IP/Réseau",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Checkbox(
                        checked = autoRestart,
                        onCheckedChange = { autoRestart = it },
                        colors = customCheckboxColors
                    )
                }
            }
        }

        // 5. Bypass Doze Mode System manual settings card helper (White/Orange theme inside nice dark container)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1F26)),
            border = BorderStroke(1.dp, Color(0x33FF9100)) // Orange 20%
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Doze Mode",
                        tint = Color(0xFFFF9100),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Contourner le Doze Mode Android",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFF9100),
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Pour s'assurer que le serveur proxy ne soit pas détruit en veille par le système, veuillez suivre manuellement ces étapes :\n\n" +
                            "1. Allez dans les Paramètres de Batterie du téléphone.\n" +
                            "2. Sélectionnez 'Applications non optimisées' ou cherchez 'Accès spécial'.\n" +
                            "3. Trouvez 'MobileProxy Host' et réglez-le sur 'Ne pas optimiser'.\n" +
                            "4. Bloquez l'application dans la liste des tâches récentes si votre OS est chinois (MIUI/HyperOS/RealmeUI/ColorOS).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFE2E2E6),
                    lineHeight = 16.sp
                )
            }
        }

        // Save Settings Action Trigger
        Button(
            onClick = {
                val httpVal = httpPortStr.toIntOrNull() ?: 8080
                val socksVal = socksPortStr.toIntOrNull() ?: 1080
                val remoteVal = remotePortStr.toIntOrNull() ?: 10080

                val finalSettings = ProxySettings(
                    id = 1,
                    httpPort = httpVal,
                    socksPort = socksVal,
                    authEnabled = authEnabled,
                    username = username,
                    password = password,
                    ipWhitelist = ipWhitelist,
                    reverseTunnelEnabled = tunnelEnabled,
                    vpsAddress = vpsAddress,
                    vpsSecret = vpsSecret,
                    remotePort = remoteVal,
                    wifiLockEnabled = wifiLockEnabled,
                    wakeLockEnabled = wakeLockEnabled,
                    autoRestartOnNetworkChange = autoRestart
                )
                viewModel.updateSettings(finalSettings)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFD0BCFF),
                contentColor = Color(0xFF381E72)
            )
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = "Enregistrer")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Enregistrer les configurations", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
