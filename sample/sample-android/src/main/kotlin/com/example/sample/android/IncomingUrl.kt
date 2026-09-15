package com.example.sample.android

/**
 * One arrival of one URL, carried by identity rather than by value: firing the same deep link a
 * second time is a second arrival, and the app should react to it again.
 */
class IncomingUrl(val url: String)
