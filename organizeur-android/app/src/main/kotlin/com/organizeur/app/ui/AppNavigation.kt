package com.organizeur.app.ui

sealed class Screen {
    object Dashboard : Screen()
    object CallFilter : Screen()
    object SilentMode : Screen()
    object Setup : Screen()
    object Help : Screen()
    object Camera : Screen()
    object Alarm : Screen()
    object Timer : Screen()
    object MiBandScan : Screen()
    object MiBandDevice : Screen()
    object MiBandWatchface : Screen()
    object GearScan : Screen()
    object GearDevice : Screen()
    object GearWallpaper : Screen()
    object GearClocks : Screen()
}
