package com.example

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.MeshBridgeService
import com.example.data.MeshPeer
import com.example.data.WifiAwareMeshManager
import com.example.ui.MainViewModel
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val serviceIntent = Intent(this, MeshBridgeService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        setContent {
            MeshDropTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp() {
    val viewModel: MainViewModel = viewModel()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "radar"

    val connectionState by viewModel.connectionState.collectAsState()
    val selectedPeer by viewModel.selectedPeerForTrust.collectAsState()

    val permissionsToRequest = remember {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val anyGranted = result.values.any { it }
        viewModel.onPermissionsResult(anyGranted)
    }

    val onRequestPermissions: () -> Unit = {
        permissionLauncher.launch(permissionsToRequest)
    }

    LaunchedEffect(Unit) {
        if (!viewModel.hasRequiredPermissions()) {
            permissionLauncher.launch(permissionsToRequest)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(NeonCyan, ElectricViolet)
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                tint = DeepVoid,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                "MeshDrop",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 19.sp,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                "Zero-Router P2P",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    val isConnected = connectionState is WifiAwareMeshManager.ConnectionState.Connected
                    val peerName = (connectionState as? WifiAwareMeshManager.ConnectionState.Connected)?.peer?.name
                    ConnectionStatusChip(isConnected = isConnected, peerName = peerName)
                }
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    icon = {
                        Icon(
                            if (currentRoute == "radar") Icons.Filled.Radar else Icons.Outlined.Radar,
                            contentDescription = "Radar"
                        )
                    },
                    label = { Text("Radar", fontWeight = FontWeight.SemiBold) },
                    selected = currentRoute == "radar",
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DeepVoid,
                        indicatorColor = NeonCyan,
                        selectedTextColor = NeonCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    onClick = {
                        if (currentRoute != "radar") {
                            navController.navigate("radar") {
                                popUpTo("radar") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                )
                NavigationBarItem(
                    icon = {
                        Icon(
                            if (currentRoute == "dashboard") Icons.Filled.FolderShared else Icons.Outlined.FolderShared,
                            contentDescription = "Transfer"
                        )
                    },
                    label = { Text("Files & Drop", fontWeight = FontWeight.SemiBold) },
                    selected = currentRoute == "dashboard",
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DeepVoid,
                        indicatorColor = NeonCyan,
                        selectedTextColor = NeonCyan,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    onClick = {
                        if (currentRoute != "dashboard") {
                            navController.navigate("dashboard") {
                                popUpTo("radar")
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(DeepVoid, DarkSurface)
                    )
                )
        ) {
            NavHost(navController = navController, startDestination = "radar") {
                composable("radar") {
                    RadarScreen(
                        viewModel = viewModel,
                        onRequestPermissions = onRequestPermissions,
                        onNavigateToDashboard = {
                            navController.navigate("dashboard") {
                                popUpTo("radar")
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable("dashboard") {
                    TransferDashboard(viewModel = viewModel)
                }
            }
        }
    }

    selectedPeer?.let { peer ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissTrustDialog() },
            shape = RoundedCornerShape(24.dp),
            containerColor = DarkSurface,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ElectricViolet.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                    }
                    Text("Verify Peer Trust (TOFU)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Establishing direct encrypted connection with:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Surface(
                        color = SurfaceContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = NeonCyan)
                            Text(peer.name, fontWeight = FontWeight.Bold, color = TextPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Encrypted 4-Digit Security PIN:",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary
                    )

                    Surface(
                        color = Color(0xFF1B1630),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ElectricViolet.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    ) {
                        Text(
                            text = peer.verificationPin,
                            modifier = Modifier.padding(vertical = 14.dp),
                            textAlign = TextAlign.Center,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 8.sp,
                                color = NeonCyan
                            )
                        )
                    }

                    Text(
                        "Verify this 4-digit PIN on both devices to guarantee zero man-in-the-middle interception.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmAndConnect(peer) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonCyan,
                        contentColor = DeepVoid
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("accept_trust_button")
                ) {
                    Text("Accept & Connect", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.dismissTrustDialog() },
                    modifier = Modifier.testTag("cancel_trust_button")
                ) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun ConnectionStatusChip(isConnected: Boolean, peerName: String?) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Surface(
        color = if (isConnected) MintEmerald.copy(alpha = 0.15f) else NeonCyan.copy(alpha = 0.12f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isConnected) MintEmerald.copy(alpha = 0.5f) else NeonCyan.copy(alpha = 0.3f)
        ),
        modifier = Modifier.padding(end = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isConnected) MintEmerald else NeonCyan.copy(alpha = alphaAnim)
                    )
            )
            Text(
                text = if (isConnected) (peerName ?: "Connected") else "Scanning",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isConnected) MintEmerald else NeonCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RadarScreen(
    viewModel: MainViewModel,
    onRequestPermissions: () -> Unit,
    onNavigateToDashboard: () -> Unit
) {
    val peers by viewModel.peers.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, OutlineColor)
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Decentralized Mesh",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Zero-Router Radar",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(ElectricViolet.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.WifiTethering, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Direct peer discovery using Wi-Fi Aware NAN (802.11aq). Transfers happen directly between hardware radios without routers, internet, or mobile data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Active Connected Banner
        when (val state = connectionState) {
            is WifiAwareMeshManager.ConnectionState.Connected -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, MintEmerald.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MintEmerald.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MintEmerald, modifier = Modifier.size(20.dp))
                                }
                                Column {
                                    Text(
                                        "Connected Mesh Link",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MintEmerald,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = state.peer.name,
                                        fontWeight = FontWeight.ExtraBold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = TextPrimary
                                    )
                                }
                            }
                            Surface(
                                color = Color(0xFF1B1630),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    "PIN: ${state.peer.verificationPin}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = NeonCyan
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = onNavigateToDashboard,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonCyan,
                                    contentColor = DeepVoid
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Drop Files & Photos", fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { viewModel.disconnect() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f))
                            ) {
                                Text("Disconnect")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
            is WifiAwareMeshManager.ConnectionState.Error -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("permission_error_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.12f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = ErrorRed,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = if (state.isPermissionError) "Permissions Required" else "Connection Notice",
                                color = ErrorRed,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = state.message,
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (state.isPermissionError) {
                                Button(
                                    onClick = onRequestPermissions,
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepVoid),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("grant_permissions_button")
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Grant Access", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.startSimulationMode() },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("demo_mode_button")
                                ) {
                                    Text("Demo Mode", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                }
                            } else {
                                Button(
                                    onClick = { viewModel.retryDiscovery() },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = DeepVoid),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("retry_discovery_button")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry Discovery", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
            else -> {}
        }

        // Searching status bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, OutlineColor)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = NeonCyan
                    )
                    val isConnected = connectionState is WifiAwareMeshManager.ConnectionState.Connected
                    Text(
                        text = if (isConnected) "Mesh Channel Active" else "Searching for Peers...",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isConnected) MintEmerald else TextPrimary
                    )
                }
                Surface(
                    color = SurfaceContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "Wi-Fi Aware",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Discovered Peers Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Nearby Devices",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Surface(
                color = SurfaceVariant,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "${peers.size} Found",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (peers.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.7f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineColor.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(SurfaceContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DevicesOther, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(28.dp))
                    }
                    Text(
                        "No Devices Detected Yet",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Open MeshDrop on another device nearby. Ensure both devices have Wi-Fi turned on.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            for (peer in peers) {
                val isCurrentPeer = (connectionState as? WifiAwareMeshManager.ConnectionState.Connected)?.peer?.id == peer.id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clickable {
                            if (!isCurrentPeer) {
                                viewModel.onPeerClicked(peer)
                            }
                        }
                        .testTag("peer_card_${peer.id}"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrentPeer) SurfaceContainer else DarkSurface
                    ),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isCurrentPeer) NeonCyan.copy(alpha = 0.5f) else OutlineColor
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isCurrentPeer) MintEmerald.copy(alpha = 0.15f) else ElectricViolet.copy(alpha = 0.18f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PhoneAndroid,
                                    contentDescription = null,
                                    tint = if (isCurrentPeer) MintEmerald else NeonCyan,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = peer.name,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.NetworkCheck,
                                        contentDescription = null,
                                        tint = if (peer.rssi > -60) MintEmerald else TextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${peer.rssi} dBm",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                    Text("•", style = MaterialTheme.typography.labelSmall, color = OutlineColor)
                                    Text(
                                        text = peer.fingerprint,
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = ElectricViolet
                                        )
                                    )
                                }
                            }
                        }

                        if (isCurrentPeer) {
                            FilledTonalButton(
                                onClick = onNavigateToDashboard,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MintEmerald.copy(alpha = 0.2f),
                                    contentColor = MintEmerald
                                )
                            ) {
                                Text("Active", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = { viewModel.onPeerClicked(peer) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonCyan,
                                    contentColor = DeepVoid
                                ),
                                modifier = Modifier.testTag("connect_btn_${peer.id}")
                            ) {
                                Text("Connect", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun TransferDashboard(viewModel: MainViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isPinVerified by viewModel.isPinVerified.collectAsState()
    val transferProgress by viewModel.transferProgress.collectAsState()
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }
    var showManualInput by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && connectionState is WifiAwareMeshManager.ConnectionState.Connected && isPinVerified) {
            val socket = (connectionState as WifiAwareMeshManager.ConnectionState.Connected).socket
            viewModel.sendFile(uri, socket)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when (val state = connectionState) {
            is WifiAwareMeshManager.ConnectionState.Connected -> {
                if (!isPinVerified) {
                    // Security Vault Lock Screen - Numeric Comparison
                    Spacer(modifier = Modifier.height(20.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(12.dp, RoundedCornerShape(24.dp)),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineColor)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(ElectricViolet.copy(alpha = 0.3f), NeonCyan.copy(alpha = 0.1f))
                                        )
                                    )
                                    .border(1.5.dp, ElectricViolet.copy(alpha = 0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.VerifiedUser,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Security Code Verification",
                                fontWeight = FontWeight.ExtraBold,
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Confirm this 4-digit code matches the code on ${state.peer.name} to unlock the transfer stream:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(18.dp))

                            // Large Glowing Numeric Comparison Badge
                            Surface(
                                color = Color(0xFF1B1630),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.5.dp, NeonCyan.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "SHORT AUTHENTICATION STRING (SAS)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeonCyan,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = state.peer.verificationPin,
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 38.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 10.sp,
                                            color = Color.White
                                        )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            // 1-Tap Primary Action: Confirm Numbers Match (No tedious typing needed!)
                            Button(
                                onClick = {
                                    viewModel.confirmPinMatch()
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NeonCyan,
                                    contentColor = DeepVoid
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("verify_pin_button")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Confirm Code Matches", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Optional Manual Entry Toggle if user prefers typing
                            if (!showManualInput) {
                                TextButton(
                                    onClick = {
                                        showManualInput = true
                                        enteredPin = state.peer.verificationPin
                                    }
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Enter PIN manually", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = enteredPin,
                                        onValueChange = { input ->
                                            if (input.length <= 4 && input.all { it.isDigit() }) {
                                                enteredPin = input
                                                pinError = false
                                            }
                                        },
                                        label = { Text("4-Digit Security PIN") },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            letterSpacing = 6.sp,
                                            color = NeonCyan
                                        ),
                                        trailingIcon = {
                                            TextButton(
                                                onClick = {
                                                    enteredPin = state.peer.verificationPin
                                                    pinError = false
                                                }
                                            ) {
                                                Text("Fill", color = NeonCyan, fontWeight = FontWeight.Bold)
                                            }
                                        },
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Done
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                val success = viewModel.verifyPin(enteredPin, state.peer.verificationPin)
                                                if (!success) {
                                                    pinError = true
                                                }
                                            }
                                        ),
                                        isError = pinError,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = NeonCyan,
                                            unfocusedBorderColor = OutlineColor,
                                            focusedContainerColor = SurfaceContainer,
                                            unfocusedContainerColor = SurfaceContainer
                                        ),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("pin_input")
                                    )

                                    if (pinError) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            "Incorrect PIN. Please re-check the receiver screen.",
                                            color = ErrorRed,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            val success = viewModel.verifyPin(enteredPin, state.peer.verificationPin)
                                            if (!success) {
                                                pinError = true
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Unlock with Entered PIN", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            TextButton(onClick = { viewModel.disconnect() }) {
                                Text("Cancel Connection", color = TextSecondary)
                            }
                        }
                    }
                } else {
                    // Stream Unlocked - Transfer Dashboard
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MintEmerald.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MintEmerald.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MintEmerald, modifier = Modifier.size(20.dp))
                                    }
                                    Column {
                                        Text(
                                            state.peer.name,
                                            fontWeight = FontWeight.ExtraBold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = TextPrimary
                                        )
                                        Text(
                                            "AES-GCM-256 Stream Unlocked",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MintEmerald,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                OutlinedButton(
                                    onClick = { viewModel.disconnect() },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.4f)),
                                    modifier = Modifier.testTag("disconnect_button")
                                ) {
                                    Text("Disconnect")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Transfer Action Grid Cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Send Files Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(140.dp)
                                .clickable { filePickerLauncher.launch("*/*") }
                                .testTag("send_file_button"),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                            border = androidx.compose.foundation.BorderStroke(1.dp, OutlineColor)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(NeonCyan.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AttachFile, contentDescription = null, tint = NeonCyan)
                                }
                                Column {
                                    Text(
                                        "Send File / APK",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = TextPrimary
                                    )
                                    Text(
                                        "Documents & archives",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }

                        // Send Photos Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(140.dp)
                                .clickable { filePickerLauncher.launch("image/*") }
                                .testTag("send_photo_button"),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceContainer),
                            border = androidx.compose.foundation.BorderStroke(1.dp, OutlineColor)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(ElectricViolet.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = ElectricViolet)
                                }
                                Column {
                                    Text(
                                        "Send Photos",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = TextPrimary
                                    )
                                    Text(
                                        "Original uncompressed",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Transfer Progress Card
                    when (val progress = transferProgress) {
                        is MeshBridgeService.TransferProgress.Transferring -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, NeonCyan.copy(alpha = 0.6f))
                            ) {
                                Column(modifier = Modifier.padding(18.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            CircularProgressIndicator(
                                                progress = { progress.bytesTransferred.toFloat() / progress.totalBytes.coerceAtLeast(1L) },
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 3.dp,
                                                color = NeonCyan
                                            )
                                            Text(
                                                progress.fileName,
                                                fontWeight = FontWeight.Bold,
                                                color = TextPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Surface(
                                            color = NeonCyan.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                "${progress.percentage}%",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                fontWeight = FontWeight.ExtraBold,
                                                color = NeonCyan,
                                                fontSize = 14.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(14.dp))
                                    LinearProgressIndicator(
                                        progress = { progress.bytesTransferred.toFloat() / progress.totalBytes.coerceAtLeast(1L) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = NeonCyan,
                                        trackColor = SurfaceContainer
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            String.format("%.2f MB/s", progress.speedMBs),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            "${progress.bytesTransferred / 1024} KB / ${progress.totalBytes / 1024} KB",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                        is MeshBridgeService.TransferProgress.Completed -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MintEmerald.copy(alpha = 0.15f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MintEmerald.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MintEmerald)
                                    Text(
                                        progress.message,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        is MeshBridgeService.TransferProgress.Error -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = ErrorRed.copy(alpha = 0.15f)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed)
                                    Text(
                                        progress.error,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        else -> {
                            // Standby helper card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, OutlineColor)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Icon(Icons.Default.Security, contentDescription = null, tint = NeonCyan)
                                    Column {
                                        Text(
                                            "Direct P2P Data Path",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary
                                        )
                                        Text(
                                            "Tap a category above to select items. Files stream straight over localized Wi-Fi radio.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                // Not connected state
                Spacer(modifier = Modifier.height(48.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OutlineColor)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(SurfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Text(
                            "No Peer Connected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Text(
                            "Head over to the Radar tab to discover and establish an encrypted P2P handshake with a nearby MeshDrop device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}
