package com.mazidulhaque.kokhon.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.mazidulhaque.kokhon.KokhonApplication

/** The application object graph, for creating view models. */
@Composable
fun kokhonApp(): KokhonApplication = LocalContext.current.applicationContext as KokhonApplication
