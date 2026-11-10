package com.example.data.remote.supabase

const val SUPABASE_AUTH_REDIRECT_SCHEME = "com.aistudio.crashintelligence.vyfrip"
const val SUPABASE_AUTH_REDIRECT_HOST = "auth"
const val SUPABASE_PASSWORD_RECOVERY_REDIRECT_URI =
    "$SUPABASE_AUTH_REDIRECT_SCHEME://$SUPABASE_AUTH_REDIRECT_HOST/recovery"
