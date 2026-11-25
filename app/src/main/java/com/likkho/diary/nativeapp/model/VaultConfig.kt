package com.likkho.diary.nativeapp.model

/**
 * Domain-separated dual-vault configuration matching `src/utils/cryptoVault.ts`
 * and `src/utils/chunkedVaultStream.ts` byte-for-byte.
 */
data class VaultDomainConfig(
    val mode: VaultMode,
    val dbFilename: String,
    val safFolderName: String,
    val kdfAlgorithm: String,
    val kdfDigestName: String,
    val kdfIterations: Int,
    val streamKdfContextPrefix: String,
    val legacyEnvelopeContextSalt: String,
    val internalFolderName: String
)

object VaultConfig {
    val PRIMARY = VaultDomainConfig(
        mode = VaultMode.PRIMARY,
        dbFilename = "real_vault.db",
        safFolderName = ".likkho_backups",
        kdfAlgorithm = "PBKDF2WithHmacSHA256",
        kdfDigestName = "SHA-256",
        kdfIterations = 100_000,
        streamKdfContextPrefix = "LIKKHO_STREAM_VAULT_KDF_real_vault.db::",
        legacyEnvelopeContextSalt = "WIKILOG_PRIMARY_REAL_VAULT_KDF_V1::",
        internalFolderName = "primary"
    )

    val DECOY = VaultDomainConfig(
        mode = VaultMode.DECOY,
        dbFilename = "decoy_vault.db",
        safFolderName = ".likkho_decoy_backups",
        kdfAlgorithm = "PBKDF2WithHmacSHA512",
        kdfDigestName = "SHA-512",
        kdfIterations = 210_000,
        streamKdfContextPrefix = "LIKKHO_STREAM_VAULT_KDF_decoy_vault.db::",
        legacyEnvelopeContextSalt = "WIKILOG_SECONDARY_DECOY_VAULT_KDF_V2::",
        internalFolderName = "decoy"
    )

    fun forMode(mode: VaultMode): VaultDomainConfig =
        if (mode == VaultMode.DECOY) DECOY else PRIMARY
}
