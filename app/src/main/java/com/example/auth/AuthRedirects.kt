package com.example.auth

const val APP_AUTH_REDIRECT_SCHEME = "com.aistudio.crashintelligence.vyfrip"
const val APP_AUTH_REDIRECT_HOST = "auth"
const val APP_PASSWORD_RECOVERY_REDIRECT_URI =
    "$APP_AUTH_REDIRECT_SCHEME://$APP_AUTH_REDIRECT_HOST/recovery"
const val APP_EMAIL_CONFIRMATION_REDIRECT_URI =
    "$APP_AUTH_REDIRECT_SCHEME://$APP_AUTH_REDIRECT_HOST/email-confirmation"
