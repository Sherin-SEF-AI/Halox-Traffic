package com.haloxtraffic.core.evidence.di

import com.haloxtraffic.core.evidence.DefaultEvidenceSealer
import com.haloxtraffic.core.evidence.EvidenceSealer
import com.haloxtraffic.core.evidence.KeystoreSigner
import com.haloxtraffic.core.evidence.Signer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EvidenceModule {
    @Binds
    @Singleton
    abstract fun bindEvidenceSealer(impl: DefaultEvidenceSealer): EvidenceSealer

    @Binds
    @Singleton
    abstract fun bindSigner(impl: KeystoreSigner): Signer
}
