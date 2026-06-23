package com.haloxtraffic.core.sync.api

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit contract for the offline-first sync backend (§13). The app is fully functional offline;
 * these calls are opportunistic sync + optional enrichment and never sit in the live capture path.
 */
interface HaloxApi {

    @POST("v1/auth/token")
    suspend fun authToken(@Body request: AuthRequest): AuthResponse

    @GET("v1/config")
    suspend fun config(@Query("jurisdictionId") jurisdictionId: String): ConfigResponse

    @GET("v1/junctions")
    suspend fun junctions(@Query("jurisdictionId") jurisdictionId: String): List<JunctionDto>

    /** Idempotent on the client session UUID. */
    @POST("v1/sessions")
    suspend fun upsertSession(@Body manifest: SessionManifestDto): UpsertResponse

    /** Append-only, idempotent batch upsert of cases with sealed-package hashes. */
    @POST("v1/cases")
    suspend fun upsertCases(@Body batch: CaseBatchRequest): UpsertResponse

    /** Multipart media upload; server re-verifies hash + signature. */
    @Multipart
    @POST("v1/cases/{id}/evidence")
    suspend fun uploadEvidence(
        @Path("id") caseId: String,
        @Part media: List<MultipartBody.Part>,
    ): UpsertResponse

    /** Optional, server-side. Never blocks the local flow. */
    @POST("v1/vahan-lookup")
    suspend fun vahanLookup(@Body request: VahanLookupRequest): VahanLookupResponse
}
