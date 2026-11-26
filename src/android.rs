// Copyright (c) 2024-2026 ScreenerBot (screenerbot.io)
// SPDX-License-Identifier: BUSL-1.1

//! Android JNI entry point. The existing ScreenerBot boot sequence and service
//! manager remain the single source of truth for engine startup.
use jni::{
    objects::{JClass, JString},
    sys::{jboolean, jstring},
    JNIEnv,
};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Mutex, OnceLock};

static STARTED: AtomicBool = AtomicBool::new(false);
static STARTUP_ERROR: OnceLock<Mutex<Option<String>>> = OnceLock::new();
static STARTUP_STAGE: OnceLock<Mutex<String>> = OnceLock::new();

pub(crate) fn record_startup_stage(message: impl Into<String>) {
    if let Ok(mut stage) = STARTUP_STAGE.get_or_init(|| Mutex::new("Starting".to_owned())).lock() {
        *stage = message.into();
    }
}

pub(crate) fn record_startup_error(message: impl Into<String>) {
    if let Ok(mut error) = STARTUP_ERROR.get_or_init(|| Mutex::new(None)).lock() {
        *error = Some(message.into());
    }
}


#[no_mangle]
pub extern "system" fn Java_io_screenerbot_mobile_NativeEngine_start(
    mut env: JNIEnv,
    _class: JClass,
    data_directory: JString,
    machine_identifier: JString,
) -> jboolean {
    let directory: String = match env.get_string(&data_directory) {
        Ok(value) => value.into(),
        Err(error) => {
            eprintln!("ScreenerBot Android: invalid data directory: {error}");
            return jni::sys::JNI_FALSE;
        }
    };

    if directory.trim().is_empty() {
        eprintln!("ScreenerBot Android: data directory is empty");
        return jni::sys::JNI_FALSE;
    }

    // Keep every database, config file, and wallet store inside this app's
    // private sandbox. The stable machine identity is unwrapped by the Kotlin
    // shell from Android Keystore and passed only into this process environment.
    let identifier: String = match env.get_string(&machine_identifier) {
        Ok(value) => value.into(),
        Err(error) => {
            eprintln!("ScreenerBot Android: invalid Keystore identity: {error}");
            return jni::sys::JNI_FALSE;
        }
    };
    if identifier.trim().is_empty() {
        eprintln!("ScreenerBot Android: Keystore identity is empty");
        return jni::sys::JNI_FALSE;
    }

    if let Ok(mut error) = STARTUP_ERROR.get_or_init(|| Mutex::new(None)).lock() {
        *error = None;
    }
    record_startup_stage("Preparing native engine");

    std::env::set_var("SCREENERBOT_DATA_DIR", directory);
    std::env::set_var("SCREENERBOT_ANDROID_MACHINE_ID", identifier);

    if STARTED.swap(true, Ordering::SeqCst) {
        return jni::sys::JNI_TRUE;
    }

    let worker = std::thread::Builder::new()
        .name("screenerbot-engine".to_owned())
        .spawn(|| {
            record_startup_stage("Creating Tokio runtime");
            let runtime = match tokio::runtime::Builder::new_multi_thread()
                .worker_threads(2)
                .max_blocking_threads(4)
                .enable_all()
                .thread_name("screenerbot-runtime")
                .build()
            {
                Ok(runtime) => runtime,
                Err(error) => {
                    record_startup_error(format!("Tokio runtime creation failed: {error}"));
                    STARTED.store(false, Ordering::SeqCst);
                    return;
                }
            };
            // Run on a dedicated native thread so the engine boot future does
            // not need to satisfy Tokio's Send bound for Runtime::spawn.
            record_startup_stage("Starting ScreenerBot core");
            runtime.block_on(crate::run::boot());
            record_startup_stage("Engine stopped");
            STARTED.store(false, Ordering::SeqCst);
        });

    if let Err(error) = worker {
        STARTED.store(false, Ordering::SeqCst);
        eprintln!("ScreenerBot Android: could not start engine thread: {error}");
        return jni::sys::JNI_FALSE;
    }

    jni::sys::JNI_TRUE
}

#[no_mangle]
pub extern "system" fn Java_io_screenerbot_mobile_NativeEngine_dashboardConnection(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let port = crate::global::get_webserver_port();
    let Some(token) = crate::global::get_security_token() else {
        return std::ptr::null_mut();
    };
    match env.new_string(format!("{port}:{token}")) {
        Ok(value) => value.into_raw(),
        Err(error) => {
            eprintln!("ScreenerBot Android: could not return dashboard session: {error}");
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_io_screenerbot_mobile_NativeEngine_startupError(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let message = STARTUP_ERROR
        .get_or_init(|| Mutex::new(None))
        .lock()
        .ok()
        .and_then(|error| error.clone());
    match message.and_then(|value| env.new_string(value).ok()) {
        Some(value) => value.into_raw(),
        None => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_io_screenerbot_mobile_NativeEngine_startupDiagnostics(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let stage = STARTUP_STAGE.get_or_init(|| Mutex::new("Not started".to_owned()))
        .lock().map(|value| value.clone()).unwrap_or_else(|_| "Stage unavailable".to_owned());
    let error = STARTUP_ERROR.get_or_init(|| Mutex::new(None))
        .lock().ok().and_then(|value| value.clone()).unwrap_or_default();
    let port = crate::global::get_webserver_port();
    let token_ready = crate::global::get_security_token().is_some();
    let message = format!("stage={stage}; port={port}; token_ready={token_ready}; error={error}");
    match env.new_string(message) {
        Ok(value) => value.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}
