package com.example.myapplication.network

import com.example.myapplication.data.models.AddBuildersResponse
import com.example.myapplication.data.models.ChangeRequestDto
import com.example.myapplication.data.models.CommentRequestDto
import com.example.myapplication.data.models.Contact
import com.example.myapplication.data.models.ContactMethod
import com.example.myapplication.data.models.ContactSnapshotDto
import com.example.myapplication.data.models.Material
import com.example.myapplication.data.models.ObjectModel
import com.example.myapplication.data.models.ObjectSnapshotDto
import com.example.myapplication.data.models.Project
import com.example.myapplication.data.models.ProjectCreateRequest
import com.example.myapplication.data.models.ProjectDto
import com.example.myapplication.data.models.ProjectMemberDto
import com.example.myapplication.data.models.ProjectSnapshotDto
import com.example.myapplication.data.models.ProjectUpdateRequest
import com.example.myapplication.data.models.SendCodeRequest
import com.example.myapplication.data.models.UserResponse
import com.example.myapplication.data.models.VerifyCodeRequest
import com.example.myapplication.data.models.WorkItem
import com.example.myapplication.network.models.SessionCheckResponse
import com.google.gson.annotations.SerializedName
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

    @GET("api/auth/session/check-with-device")
    suspend fun checkSessionWithDevice(
        @Query("deviceId") deviceId: String
    ): Response<SessionCheckResponse>

    @GET("api/projects")
    suspend fun getAllProjects(): Response<List<ProjectDto>>

    @GET("api/projects/{id}")
    suspend fun getProject(@Path("id") id: String): Response<ProjectDto>

    @POST("api/projects")
    suspend fun createProject(@Body project: ProjectCreateRequest): Response<ProjectDto>

    @PUT("api/projects/{id}")
    suspend fun updateProject(
        @Path("id") id: String,
        @Body project: ProjectUpdateRequest
    ): Response<ProjectDto>

    @DELETE("api/projects/{id}")
    suspend fun deleteProject(@Path("id") id: String): Response<Unit>

    @POST("api/projects/{id}/sync")
    suspend fun syncProject(
        @Path("id") id: String,
        @Body snapshot: ProjectSnapshotDto
    ): Response<ProjectDto>

    @GET("api/projects/{projectId}/materials")
    suspend fun getMaterials(
        @Path("projectId") projectId: String
    ): Response<List<Material>>

    @GET("api/projects/{projectId}/work-items")
    suspend fun getWorkItems(
        @Path("projectId") projectId: String
    ): Response<List<WorkItem>>

    @GET("api/projects/{projectId}/members")
    suspend fun getProjectMembers(
        @Path("projectId") projectId: String
    ): Response<List<ProjectMemberDto>>

    @POST("api/projects/{projectId}/members/customer")
    suspend fun setCustomer(
        @Path("projectId") projectId: String,
        @Body body: Map<String, String>
    ): Response<Unit>

    @POST("api/projects/{projectId}/members/estimator")
    suspend fun setEstimator(
        @Path("projectId") projectId: String,
        @Body body: Map<String, String>
    ): Response<Unit>

    data class AddBuildersRequest(
        @SerializedName("phoneNumbers")
        val phoneNumbers: List<String>
    )

    @POST("api/projects/{projectId}/members/builders")
    suspend fun addBuilders(
        @Path("projectId") projectId: String,
        @Body body: AddBuildersRequest
    ): Response<AddBuildersResponse>
    @PUT("api/projects/{projectId}/members/{targetUserId}/role")
    suspend fun changeMemberRole(
        @Path("projectId") projectId: String,
        @Path("targetUserId") targetUserId: String,
        @Body body: Map<String, String>
    ): Response<Unit>

    @DELETE("api/projects/{projectId}/members/{targetUserId}")
    suspend fun removeMember(
        @Path("projectId") projectId: String,
        @Path("targetUserId") targetUserId: String
    ): Response<Unit>

    @GET("api/projects/{projectId}/members/me")
    suspend fun getMyRole(
        @Path("projectId") projectId: String
    ): Response<Map<String, String>>

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
    suspend fun getContactMethods(
        @Path("contactId") contactId: String
    ): Response<List<ContactMethod>>

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

    @POST("api/contacts/{id}/sync")
    suspend fun syncContact(
        @Path("id") id: String,
        @Body snapshot: ContactSnapshotDto
    ): Response<Contact>

    @GET("api/users/exists")
    suspend fun checkUserExists(@Query("phone") phone: String): Response<Map<String, Boolean>>

    @GET("api/objects/root")
    suspend fun getRootObjects(): Response<List<ObjectModel>>

    @GET("api/objects/{id}")
    suspend fun getObject(@Path("id") id: String): Response<ObjectModel>

    @GET("api/objects/{parentId}/children")
    suspend fun getChildObjects(
        @Path("parentId") parentId: String
    ): Response<List<ObjectModel>>

    @POST("api/objects")
    suspend fun createObject(@Body obj: ObjectModel): Response<ObjectModel>

    @PUT("api/objects/{id}")
    suspend fun updateObject(
        @Path("id") id: String,
        @Body obj: ObjectModel
    ): Response<ObjectModel>

    @DELETE("api/objects/{id}")
    suspend fun deleteObject(@Path("id") id: String): Response<Unit>

    @POST("api/objects/{id}/sync")
    suspend fun syncObject(
        @Path("id") id: String,
        @Body snapshot: ObjectSnapshotDto
    ): Response<ObjectModel>

    @GET("api/changes/inbox")
    suspend fun getChangesInbox(): Response<List<ChangeRequestDto>>

    @GET("api/projects/{projectId}/changes")
    suspend fun getProjectChanges(
        @Path("projectId") projectId: String
    ): Response<List<ChangeRequestDto>>

    @GET("api/projects/{projectId}/changes/pending-estimate")
    suspend fun getPendingEstimateChange(
        @Path("projectId") projectId: String
    ): Response<ChangeRequestDto>

    @POST("api/projects/{projectId}/changes/estimate")
    suspend fun submitEstimateChange(
        @Path("projectId") projectId: String,
        @Body snapshot: ProjectSnapshotDto
    ): Response<ChangeRequestDto>

    @POST("api/projects/{projectId}/comments")
    suspend fun addComment(
        @Path("projectId") projectId: String,
        @Body request: CommentRequestDto
    ): Response<ChangeRequestDto>

    @POST("api/changes/{changeId}/approve")
    suspend fun approveChange(
        @Path("changeId") changeId: String,
        @Body body: Map<String, String?>
    ): Response<ChangeRequestDto>

    @POST("api/changes/{changeId}/reject")
    suspend fun rejectChange(
        @Path("changeId") changeId: String,
        @Body body: Map<String, String>
    ): Response<ChangeRequestDto>

    @DELETE("api/changes/{changeId}")
    suspend fun deleteDraft(
        @Path("changeId") changeId: String
    ): Response<Unit>
}