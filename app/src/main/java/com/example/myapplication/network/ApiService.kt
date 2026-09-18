package com.example.myapplication.network

import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.ObjectModel
import com.example.myapplication.data.models.Project
import com.example.myapplication.data.models.SendCodeRequest
import com.example.myapplication.data.models.UserResponse
import com.example.myapplication.data.models.VerifyCodeRequest
import com.example.myapplication.data.models.WorkItem
import com.example.myapplication.network.models.SessionCheckResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("api/auth/logout")
    suspend fun logout(): Response<Unit>

    @POST("api/auth/send-code")
    suspend fun sendCode(@Body request: SendCodeRequest): Response<Unit>

    @POST("api/auth/verify")
    suspend fun verifyCode(@Body request: VerifyCodeRequest): Response<UserResponse>

    @GET("api/auth/user/{userId}")
    suspend fun getUser(@Path("userId") userId: String): Response<UserResponse>

    @GET("api/auth/session/check")
    suspend fun checkSession(): Response<SessionCheckResponse>

    @GET("api/auth/session/check-with-device")
    suspend fun checkSessionWithDevice(
        @Query("deviceId") deviceId: String
    ): Response<SessionCheckResponse>

    @GET("api/projects")
    suspend fun getAllProjects(): Response<List<Project>>

    @GET("api/projects/user/{userId}")
    suspend fun getProjectsForUser(@Path("userId") userId: String): Response<List<Project>>

    @GET("api/projects/{id}")
    suspend fun getProject(@Path("id") id: String): Response<Project>

    @POST("api/projects")
    suspend fun createProject(@Body project: Project): Response<Project>

    @PUT("api/projects/{id}")
    suspend fun updateProject(
        @Path("id") id: String,
        @Body project: Project
    ): Response<Project>

    @DELETE("api/projects/{id}")
    suspend fun deleteProject(@Path("id") id: String): Response<Unit>

    @GET("api/projects/{projectId}/materials")
    suspend fun getMaterials(@Path("projectId") projectId: String): Response<List<Material>>

    @GET("api/projects/{projectId}/work-items")
    suspend fun getWorkItems(@Path("projectId") projectId: String): Response<List<WorkItem>>

    @POST("api/projects/{projectId}/share")
    suspend fun shareProject(
        @Path("projectId") projectId: String,
        @Body request: Map<String, String>
    ): Response<Unit>

    @GET("api/projects/pending/{userId}")
    suspend fun getPendingProjects(@Path("userId") userId: String): Response<List<Project>>

    @POST("api/projects/{projectId}/accept")
    suspend fun acceptShare(@Path("projectId") projectId: String): Response<Unit>

    @POST("api/projects/{projectId}/decline")
    suspend fun declineShare(@Path("projectId") projectId: String): Response<Unit>

    @POST("api/materials")
    suspend fun addMaterial(@Body material: Material): Response<Material>

    @PUT("api/materials/{id}")
    suspend fun updateMaterial(
        @Path("id") id: String,
        @Body material: Material
    ): Response<Material>

    @DELETE("api/materials/{id}")
    suspend fun deleteMaterial(@Path("id") id: String): Response<Unit>

    @POST("api/work-items")
    suspend fun addWorkItem(@Body workItem: WorkItem): Response<WorkItem>

    @PUT("api/work-items/{id}")
    suspend fun updateWorkItem(
        @Path("id") id: String,
        @Body workItem: WorkItem
    ): Response<WorkItem>

    @DELETE("api/work-items/{id}")
    suspend fun deleteWorkItem(@Path("id") id: String): Response<Unit>

    @PATCH("api/work-items/{id}/complete")
    suspend fun markWorkItemCompleted(@Path("id") id: String): Response<WorkItem>

    @GET("api/contacts")
    suspend fun getAllContacts(): Response<List<Contact>>

    @GET("api/contacts/{id}")
    suspend fun getContact(@Path("id") id: String): Response<Contact>

    @GET("api/contacts/{contactId}/methods")
    suspend fun getContactMethods(@Path("contactId") contactId: String): Response<List<ContactMethod>>

    @POST("api/contacts")
    suspend fun createContact(@Body contact: Contact): Response<Contact>

    @PUT("api/contacts/{id}")
    suspend fun updateContact(
        @Path("id") id: String,
        @Body contact: Contact
    ): Response<Contact>

    @DELETE("api/contacts/{id}")
    suspend fun deleteContact(@Path("id") id: String): Response<Unit>

    @POST("api/contacts/methods")
    suspend fun addContactMethod(@Body method: ContactMethod): Response<ContactMethod>

    @PUT("api/contacts/methods/{id}")
    suspend fun updateContactMethod(
        @Path("id") id: String,
        @Body method: ContactMethod
    ): Response<ContactMethod>

    @DELETE("api/contacts/methods/{id}")
    suspend fun deleteContactMethod(@Path("id") id: String): Response<Unit>

    @GET("api/objects/root")
    suspend fun getRootObjects(): Response<List<ObjectModel>>

    @GET("api/objects/{id}")
    suspend fun getObject(@Path("id") id: String): Response<ObjectModel>

    @GET("api/objects/{parentId}/children")
    suspend fun getChildObjects(@Path("parentId") parentId: String): Response<List<ObjectModel>>

    @POST("api/objects")
    suspend fun createObject(@Body obj: ObjectModel): Response<ObjectModel>

    @PUT("api/objects/{id}")
    suspend fun updateObject(
        @Path("id") id: String,
        @Body obj: ObjectModel
    ): Response<ObjectModel>

    @DELETE("api/objects/{id}")
    suspend fun deleteObject(@Path("id") id: String): Response<Unit>
}