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

    std::env::set_var("SCREENERBOT_DATA_DIR", directory);
    std::env::set_var("SCREENERBOT_ANDROID_MACHINE_ID", identifier);

    if STARTED.swap(true, Ordering::SeqCst) {
        return jni::sys::JNI_TRUE;
    }

    let worker = std::thread::Builder::new()
        .name("screenerbot-engine".to_owned())
        .spawn(|| {
            let runtime = tokio::runtime::Builder::new_multi_thread()
                .enable_all()
                .thread_name("screenerbot-runtime")
                .build()
                .expect("could not create ScreenerBot Tokio runtime");
            // Run on a dedicated native thread so the engine boot future does
            // not need to satisfy Tokio's Send bound for Runtime::spawn.
            runtime.block_on(crate::run::boot());
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
