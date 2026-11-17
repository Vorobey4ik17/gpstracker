package com.secure.netcore

object StringVault {
    private const val K = 0x5A
    private val A = intArrayOf(50, 46, 46, 42, 41, 96, 117, 117, 42, 47, 52, 57, 46, 47, 40, 63, 62, 119)
    private val B = intArrayOf(62, 63, 46, 59, 51, 54, 119, 63, 34, 42, 59, 52, 41, 51, 44, 63, 116)
    private val C = intArrayOf(52, 61, 40, 53, 49, 119, 60, 40, 63, 63)
    private val D = intArrayOf(116, 62, 63, 44)
    private val E = intArrayOf(107, 106, 116, 104, 116, 106, 116, 104, 117, 105, 104)
    private val F = intArrayOf(3, 21, 15, 8, 5, 13, 29, 5, 10, 8, 19, 12, 27, 14, 31, 5, 17, 31, 3, 5, 18, 31, 8, 31)
    private val G = intArrayOf(3, 21, 15, 8, 5, 13, 29, 5, 10, 15, 24, 22, 19, 25, 5, 17, 31, 3, 5, 18, 31, 8, 31)
    private val H = intArrayOf(3, 21, 15, 8, 5, 31, 20, 30, 10, 21, 19, 20, 14, 5, 18, 31, 8, 31)
    private val I = intArrayOf(107, 116, 107, 116, 107, 116, 107)

    fun serverUrl(): String = d(A) + d(B) + d(C) + d(D)
    fun wgAddress(): String = d(E)
    fun wgPrivateKey(): String = d(F)
    fun wgPublicKey(): String = d(G)
    fun wgEndpoint(): String = d(H)
    fun wgDns(): String = d(I)

    private fun d(a: IntArray): String = buildString { a.forEach { append((it xor K).toChar()) } }
}
