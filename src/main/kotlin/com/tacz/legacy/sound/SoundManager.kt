package com.tacz.legacy.sound

public object SoundManager {
    public const val GUN: String = "gun"
    public const val TARGET_BLOCK_HIT: String = "target_block_hit"

    @JvmStatic
    public fun knownKeys(): Set<String> = linkedSetOf(
        GUN,
        TARGET_BLOCK_HIT,
    )
}
