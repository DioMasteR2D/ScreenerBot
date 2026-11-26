// Copyright (c) 2024-2026 ScreenerBot (screenerbot.io)
// SPDX-License-Identifier: BUSL-1.1

//! Process boot sequence: CLI early-exits, logger, config, panic hook,
//! shutdown signal, then the bot lifecycle.

use crate::{
    arguments::{print_banner, print_help, print_version, set_cmd_args},
    config::utils::load_config,
    logger::{self, LogTag},
};

/// Full process boot sequence: CLI early-exits, banner, logger, config, panic
/// hook, ctrl-c shutdown signal, one-shot wallet reset, run the bot, and
/// restart-after-graceful-shutdown handling. Called once from `main()`.
pub async fn boot() {
    #[cfg(target_os = "android")]
    crate::android::record_startup_stage("Boot sequence started");
    crate::net::install_tls_crypto_provider();
    #[cfg(target_os = "android")]
    crate::android::record_startup_stage("TLS provider installed");

    // Store command line arguments. Android uses the same secured GUI-mode
    // listener as the desktop shell: dynamic loopback port plus session token.
    let mut command_args: Vec<String> = std::env::args().collect();
    #[cfg(target_os = "android")]
    command_args.push("--gui".to_owned());
    set_cmd_args(command_args);
    #[cfg(target_os = "android")]
    crate::android::record_startup_stage("Command arguments initialized");

    // MCP is a protocol subprocess. It must run before the banner, logger, or
    // config boot so stdout is exclusively JSON-RPC framing.
    let args = crate::arguments::get_cmd_args();
    if crate::mcp::is_mcp_command(&args) {
        if let Err(error) = crate::mcp::dispatch(&args).await {
            eprintln!("ScreenerBot MCP failed: {error}");
            std::process::exit(1);
        }
        return;
    }

    // Handle help flag
    if std::env::args().any(|arg| arg == "--help" || arg == "-h") {
        print_help();
        return;
    }

    // Handle version flag
    if std::env::args().any(|arg| arg == "--version" || arg == "-v") {
        print_version();
        return;
    }

    #[cfg(target_os = "android")]
    crate::android::record_startup_stage("Initializing logger");
    print_banner();

    // Initialize logger
    crate::logger::init();
    #[cfg(target_os = "android")]
    crate::android::record_startup_stage("Logger initialized");
    logger::info(
        LogTag::System,
        "Logger initialized, attempting to load config...",
    );

    // Android's first launch has no config.toml yet. In that state the app
    // must enter the normal first-run initialization flow in run_bot(), where
    // the local dashboard lets the user choose Explore Mode or complete setup.
    // Calling load_config() unconditionally here used to treat a missing file
    // as fatal and process::exit(1) terminated the entire Android application.
    // Existing configs are still loaded and validated before service startup.
    #[cfg(target_os = "android")]
    let should_load_config = crate::paths::get_config_path().exists();
    #[cfg(not(target_os = "android"))]
    let should_load_config = true;

    #[cfg(target_os = "android")]
    crate::android::record_startup_stage("Checking Android configuration");
    if should_load_config {
        if let Err(e) = load_config() {
            let detail = e.to_string();
            crate::errors::StartupError::from(super::Error::config_load(e)).emit();
            logger::flush();
            #[cfg(target_os = "android")]
            {
                crate::android::record_startup_error(detail);
                return;
            }
            #[cfg(not(target_os = "android"))]
            std::process::exit(1);
        }
        logger::info(LogTag::System, "Configuration loaded successfully");
    } else {
        logger::info(
            LogTag::System,
            "No Android config.toml found - continuing to first-run initialization",
        );
    }

    // Detect and log the system/network proxy once (used by HTTP, RPC and WS).
    // Must run AFTER config load so `[network] proxy` is available.
    crate::net::log_detected_proxy();
    #[cfg(target_os = "android")]
    crate::android::record_startup_stage("Network initialized");

    // Set up panic hook for crash notifications (after config is loaded)
    crate::process::panic_hook::install();

    logger::info(LogTag::System, "ScreenerBot starting...");
    #[cfg(target_os = "android")]
    crate::android::record_startup_stage("Entering bot startup");

    // Set up shutdown signal handler
    #[cfg(target_os = "android")]
    crate::android::record_startup_stage("Starting bot lifecycle");
    super::shutdown::spawn_initial_ctrl_c_listener();

    // Run the bot in headless mode. On a fatal startup failure, surface a
    // structured error to the terminal/log file and the GUI shell, then exit
    // with a non-zero code so callers (Electron, systemd) can tell a failed
    // boot apart from a clean shutdown.
    if let Err(e) = super::run_bot().await {
        let detail = e.to_string();
        crate::errors::StartupError::from(e).emit();
        logger::flush();
        #[cfg(target_os = "android")]
        {
            crate::android::record_startup_error(detail);
            crate::android::record_startup_stage("Startup failed");
            return;
        }
        #[cfg(not(target_os = "android"))]
        std::process::exit(1);
    }

    if crate::global::is_restart_requested() {
        crate::process::restart::restart_after_graceful_shutdown();
    }

    logger::info(LogTag::System, "ScreenerBot shutdown complete");
}
