package com.infra.core.crypto.provider;

import com.infra.core.crypto.keys.AsymmetricKeyMaterial;
import com.infra.core.crypto.exception.KeyResolutionException;

/**
 * Interface for solving Asymmetric active key derivation.
 */
public interface AsymmetricKeyProvider {

    /**
     * Resolves the current active key pair for a given usage context.
     * @param context Identifier for the encryption zone (e.g. mTLS_keys).
     * @return non-null AsymmetricKeyMaterial.
     * @throws KeyResolutionException if unable to find/decrypt key from source.
     */
    AsymmetricKeyMaterial getActiveKey(String context);

    /**
     * Resolves historical key pairs or verification certificates by an explicit KID.
     * @param keyId Unique key identifier matching the payload header.
     * @return non-null AsymmetricKeyMaterial.
     * @throws KeyResolutionException if the KID is not found.
     */
    AsymmetricKeyMaterial getKeyById(String keyId);
}
