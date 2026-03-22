package com.infra.core.crypto.provider;

import com.infra.core.crypto.keys.SymmetricKeyMaterial;
import com.infra.core.crypto.exception.KeyResolutionException;

/**
 * Interface for solving Symmetric active key derivation.
 */
public interface SymmetricKeyProvider {

    /**
     * Resolves the current active key for a given usage context.
     * @param context Identifier for the encryption zone (e.g. database_credentials).
     * @return non-null SymmetricKeyMaterial.
     * @throws KeyResolutionException if unable to find/decrypt key from source.
     */
    SymmetricKeyMaterial getActiveKey(String context);

    /**
     * Resolves a historical key used previously based on an explicit KID.
     * @param keyId Unique key identifier matching the encrypted payload header.
     * @return non-null SymmetricKeyMaterial.
     * @throws KeyResolutionException if the KID is expired, invalidated, or not found.
     */
    SymmetricKeyMaterial getKeyById(String keyId);
}
